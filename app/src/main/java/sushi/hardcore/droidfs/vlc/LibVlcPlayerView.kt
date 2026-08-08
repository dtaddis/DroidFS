package sushi.hardcore.droidfs.vlc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import sushi.hardcore.droidfs.BuildConfig
import sushi.hardcore.droidfs.file_viewers.VlcMediaSource
import sushi.hardcore.droidfs.file_viewers.LiveSloMoEngine
import sushi.hardcore.droidfs.widgets.DoubleTapOverlay
import sushi.hardcore.droidfs.widgets.PlayerControlFeedbackListener
import sushi.hardcore.droidfs.widgets.PlayerGestureTarget
import sushi.hardcore.droidfs.widgets.PlayerSystemGestureController
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

class LibVlcPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VLCVideoLayout(context, attrs, defStyleAttr) {
    lateinit var doubleTapOverlay: DoubleTapOverlay
    var controlFeedbackListener: PlayerControlFeedbackListener? = null
    var onSingleTap: (() -> Unit)? = null
    var onHideControls: (() -> Unit)? = null
    var onPlaybackEnded: (() -> Unit)? = null
    var onPlaybackError: (() -> Unit)? = null

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var mediaSource: VlcMediaSource? = null
    private var paused = true
    private var hasFile = false
    private var playbackAttemptActive = false
    private var playbackStarted = false
    private var initialized = false
    private var scalingMode = VlcScalingMode.BEST_FIT
    private var playbackRate = 1f
    private var pendingStartPositionMs: Long? = null
    private var liveSmoothEngine: LiveSloMoEngine? = null
    private var liveSmoothThread: HandlerThread? = null
    private var liveSmoothOutput: TextureView? = null
    private var liveSmoothBackdrop: View? = null
    private var liveSmoothSource: SurfaceView? = null
    private var liveSmoothCaptureActive = false
    private var liveSmoothFrameWidth = 0
    private var liveSmoothFrameHeight = 0
    private var liveSmoothCaptureRunnable: Runnable? = null
    private var liveSmoothGeneration = 0

    private val gestureTarget = object : PlayerGestureTarget {
        override val currentPosition: Long
            get() = currentPositionMs()
        override val duration: Long
            get() = durationMs()
        override val canSeek: Boolean
            get() = hasFile && durationMs() > 0 && player?.isSeekable == true

        override fun seekTo(positionMs: Long) {
            this@LibVlcPlayerView.seekTo(positionMs)
        }
    }

    private val gestureController = PlayerSystemGestureController(
        context,
        viewWidth = { width },
        viewHeight = { height },
        player = { gestureTarget },
        doubleTapOverlay = {
            if (::doubleTapOverlay.isInitialized) {
                doubleTapOverlay
            } else {
                null
            }
        },
        singleTapHandler = { onSingleTap?.invoke() ?: performClick() },
        hideController = { onHideControls?.invoke() },
        feedbackListener = { controlFeedbackListener }
    )

    fun initialize(appContext: Context) {
        if (initialized) {
            return
        }
        val options = arrayListOf(
            "--no-video-title-show",
            "--avcodec-fast",
            "--audio-time-stretch",
            "--file-caching=750",
            "--clock-jitter=0",
            "--clock-synchro=0"
        )
        val vlc = LibVLC(appContext, options)
        val mediaPlayer = MediaPlayer(vlc)
        mediaPlayer.attachViews(this, null, false, false)
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    hasFile = true
                    paused = false
                    playbackStarted = true
                    pendingStartPositionMs?.let { position ->
                        mediaPlayer.setTime(position.coerceAtLeast(0))
                        pendingStartPositionMs = null
                    }
                    applyPlaybackRate()
                    applyScalingMode()
                }
                MediaPlayer.Event.Paused -> paused = true
                MediaPlayer.Event.Stopped -> {
                    hasFile = false
                    paused = true
                }
                MediaPlayer.Event.EndReached -> {
                    val shouldHandle = playbackAttemptActive
                    val didStart = playbackStarted
                    playbackAttemptActive = false
                    hasFile = false
                    paused = true
                    if (shouldHandle) {
                        post {
                            if (didStart) {
                                onPlaybackEnded?.invoke()
                            } else {
                                onPlaybackError?.invoke()
                            }
                        }
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    val shouldHandle = playbackAttemptActive
                    playbackAttemptActive = false
                    hasFile = false
                    paused = true
                    if (shouldHandle) {
                        post { onPlaybackError?.invoke() }
                    }
                }
                MediaPlayer.Event.Vout -> applyScalingMode()
            }
        }
        libVlc = vlc
        player = mediaPlayer
        initialized = true
    }

    fun destroy() {
        disableLiveSmoothSloMo()
        val mediaPlayer = player
        if (mediaPlayer != null) {
            mediaPlayer.setEventListener(null)
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
        }
        mediaSource?.close()
        mediaSource = null
        playbackAttemptActive = false
        playbackStarted = false
        libVlc?.release()
        libVlc = null
        player = null
        initialized = false
    }

    fun load(source: VlcMediaSource, startPositionMs: Long? = null) {
        disableLiveSmoothSloMo()
        val vlc = checkNotNull(libVlc) { "LibVLC is not initialized" }
        val mediaPlayer = checkNotNull(player) { "LibVLC player is not initialized" }
        mediaPlayer.stop()
        mediaSource?.close()
        mediaSource = null
        hasFile = false
        paused = true
        playbackAttemptActive = false
        playbackStarted = false
        pendingStartPositionMs = startPositionMs

        try {
            val media = Media(vlc, source.fileDescriptor)
            try {
                media.setHWDecoderEnabled(true, false)
                media.addOption(":file-caching=750")
                media.addOption(":avcodec-fast")
                mediaPlayer.media = media
            } finally {
                media.release()
            }
            mediaSource = source
            playbackAttemptActive = true
            mediaPlayer.play()
        } catch (e: Exception) {
            mediaSource = null
            playbackAttemptActive = false
            throw e
        }
    }

    fun setScalingMode(mode: VlcScalingMode) {
        scalingMode = mode
        val output = liveSmoothOutput
        if (output != null && liveSmoothFrameWidth > 0 && liveSmoothFrameHeight > 0) {
            configureSmoothOutputLayout(output, liveSmoothFrameWidth, liveSmoothFrameHeight)
        }
        applyScalingMode()
    }

    fun getScalingMode() = scalingMode

    fun setPlaybackRate(rate: Float) {
        require(rate > 0f) { "Playback rate must be greater than zero" }
        playbackRate = rate
        diagnosticInfo("rate set to $rate")
        if (hasFile) {
            applyPlaybackRate()
        }
    }

    fun getPlaybackRate() = playbackRate

    fun enableLiveSmoothSloMo(
        outputView: TextureView,
        backdropView: View,
        modelDirectory: File,
        onResult: (Boolean) -> Unit
    ) {
        val mediaPlayer = player
        if (mediaPlayer == null || liveSmoothEngine != null || liveSmoothThread != null) {
            onResult(false)
            return
        }
        val dimensions = interpolationDimensions(mediaPlayer)
        if (dimensions == null) {
            Log.e(DIAGNOSTIC_TAG, "enable rejected: video dimensions unavailable")
            onResult(false)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(DIAGNOSTIC_TAG, "enable rejected: PixelCopy requires Android 7+")
            onResult(false)
            return
        }
        val sourceView = findVideoSurfaceView(this)
        if (sourceView == null || !sourceView.holder.surface.isValid) {
            Log.e(DIAGNOSTIC_TAG, "enable rejected: VLC SurfaceView unavailable")
            onResult(false)
            return
        }
        diagnosticInfo(
            "enable begin rate=$playbackRate inference=${dimensions.first}x${dimensions.second} " +
                "source=${sourceView.width}x${sourceView.height} outputAvailable=${outputView.isAvailable}"
        )

        val generation = ++liveSmoothGeneration
        liveSmoothOutput = outputView
        liveSmoothBackdrop = backdropView
        liveSmoothSource = sourceView
        liveSmoothFrameWidth = dimensions.first
        liveSmoothFrameHeight = dimensions.second
        configureSmoothOutputLayout(outputView, dimensions.first, dimensions.second)
        outputView.isOpaque = true
        outputView.alpha = 0f
        outputView.visibility = View.VISIBLE
        backdropView.alpha = 0f
        backdropView.visibility = View.VISIBLE

        runWhenTextureReady(outputView) outputReady@{
                diagnosticInfo("output TextureView ready")
                if (generation != liveSmoothGeneration) {
                    return@outputReady
                }
                val workerThread = HandlerThread("LiveSmoothSloMo").apply { start() }
                liveSmoothThread = workerThread
                val workerHandler = Handler(workerThread.looper)
                workerHandler.post {
                    val engine = LiveSloMoEngine()
                    val outputTexture = outputView.surfaceTexture
                    if (outputTexture == null) {
                        post {
                            if (generation == liveSmoothGeneration) {
                                liveSmoothThread = null
                                clearSmoothViews()
                                onResult(false)
                            }
                        }
                        workerThread.quitSafely()
                        return@post
                    }
                    outputTexture.setDefaultBufferSize(dimensions.first, dimensions.second)
                    val outputSurface = Surface(outputTexture)
                    val initializationStartMs = SystemClock.elapsedRealtime()
                    diagnosticInfo("native engine initialization begin")
                    val initialized = try {
                        engine.initialize(modelDirectory, outputSurface)
                    } catch (_: Throwable) {
                        false
                    } finally {
                        outputSurface.release()
                    }
                    diagnosticInfo(
                        "native engine initialization end success=$initialized " +
                            "elapsedMs=${SystemClock.elapsedRealtime() - initializationStartMs}"
                    )
                    post {
                        if (!initialized || generation != liveSmoothGeneration || player !== mediaPlayer) {
                            workerHandler.post {
                                engine.close()
                                workerThread.quitSafely()
                            }
                            if (generation == liveSmoothGeneration) {
                                clearSmoothViews()
                                liveSmoothThread = null
                                onResult(false)
                            }
                            return@post
                        }

                        try {
                            val frameBitmap = Bitmap.createBitmap(
                                dimensions.first,
                                dimensions.second,
                                Bitmap.Config.ARGB_8888
                            )
                            diagnosticInfo(
                                "VLC remains attached; PixelCopy source=${sourceView.width}x${sourceView.height}"
                            )
                            liveSmoothEngine = engine
                            // Capture from one stable, uncropped source geometry. The selected
                            // presentation mode is applied exactly once by the output overlay.
                            liveSmoothCaptureActive = true
                            applyScalingMode()
                            startSmoothFrameCapture(
                                sourceView,
                                outputView,
                                frameBitmap,
                                engine,
                                workerHandler,
                                generation,
                                onResult
                            )
                        } catch (error: Throwable) {
                            Log.e(DIAGNOSTIC_TAG, "Smooth capture setup failed", error)
                            workerHandler.post {
                                engine.close()
                                workerThread.quitSafely()
                            }
                            liveSmoothThread = null
                            clearSmoothViews()
                            onResult(false)
                        }
                    }
                }
        }
    }

    fun disableLiveSmoothSloMo() {
        diagnosticInfo(
            "disable begin engine=${liveSmoothEngine != null} thread=${liveSmoothThread != null}"
        )
        ++liveSmoothGeneration
        val engine = liveSmoothEngine
        val thread = liveSmoothThread
        liveSmoothEngine = null
        liveSmoothThread = null
        liveSmoothCaptureActive = false
        liveSmoothCaptureRunnable?.let { runnable ->
            thread?.let { Handler(it.looper).removeCallbacks(runnable) }
        }
        liveSmoothCaptureRunnable = null

        clearSmoothViews()
        applyScalingMode()
        if (engine != null) {
            if (thread != null) {
                Handler(thread.looper).post {
                    engine.close()
                    thread.quitSafely()
                }
            } else {
                engine.close()
            }
        }
        diagnosticInfo("disable returned; native cleanup is asynchronous")
    }

    fun isLiveSmoothSloMoEnabled() = liveSmoothEngine != null

    fun playPause() {
        val mediaPlayer = player ?: return
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            paused = true
        } else {
            mediaPlayer.play()
            paused = false
        }
    }

    fun pause() {
        val mediaPlayer = player ?: return
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
        paused = true
    }

    fun play() {
        player?.play()
        paused = false
    }

    fun seekTo(positionMs: Long) {
        player?.setTime(positionMs.coerceAtLeast(0))
    }

    fun isPaused() = paused
    fun currentPositionMs() = player?.time?.coerceAtLeast(0) ?: 0
    fun durationMs() = player?.length?.coerceAtLeast(0) ?: 0

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureController.onTouchEvent(event)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val output = liveSmoothOutput
        if (output != null && liveSmoothFrameWidth > 0 && liveSmoothFrameHeight > 0) {
            configureSmoothOutputLayout(output, liveSmoothFrameWidth, liveSmoothFrameHeight)
        }
        applyScalingMode()
    }

    private fun applyScalingMode() {
        val mediaPlayer = player ?: return
        if (liveSmoothCaptureActive) {
            mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
            return
        }
        when (scalingMode) {
            VlcScalingMode.BEST_FIT -> {
                mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
            }
            VlcScalingMode.FILL -> {
                mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_FILL)
            }
            VlcScalingMode.VERTICAL -> applyAxisFit(mediaPlayer, fitHeight = true)
            VlcScalingMode.HORIZONTAL -> applyAxisFit(mediaPlayer, fitHeight = false)
        }
    }

    private fun applyPlaybackRate() {
        val mediaPlayer = player ?: return
        if (abs(mediaPlayer.rate - playbackRate) > RATE_EPSILON) {
            mediaPlayer.setRate(playbackRate)
        }
    }

    private fun interpolationDimensions(mediaPlayer: MediaPlayer): Pair<Int, Int>? {
        val displaySize = currentVideoSize(mediaPlayer) ?: return null
        val sourceWidth = displaySize.first
        val sourceHeight = displaySize.second
        if (sourceWidth <= 0f || sourceHeight <= 0f) {
            return null
        }
        val scale = minOf(1f, MAX_INTERPOLATION_EDGE / maxOf(sourceWidth, sourceHeight))
        val outputWidth = ((sourceWidth * scale).roundToInt().coerceAtLeast(64) / 2) * 2
        val outputHeight = ((sourceHeight * scale).roundToInt().coerceAtLeast(64) / 2) * 2
        return outputWidth to outputHeight
    }

    private fun configureSmoothOutputLayout(outputView: View, videoWidth: Int, videoHeight: Int) {
        val containerWidth = width.coerceAtLeast(1)
        val containerHeight = height.coerceAtLeast(1)
        val videoRatio = videoWidth / videoHeight.toFloat()
        val containerRatio = containerWidth / containerHeight.toFloat()

        val (layoutWidth, layoutHeight) = when (scalingMode) {
            VlcScalingMode.FILL -> {
                if (videoRatio > containerRatio) {
                    (containerHeight * videoRatio).roundToInt() to containerHeight
                } else {
                    containerWidth to (containerWidth / videoRatio).roundToInt()
                }
            }
            VlcScalingMode.VERTICAL -> (containerHeight * videoRatio).roundToInt() to containerHeight
            VlcScalingMode.HORIZONTAL -> containerWidth to (containerWidth / videoRatio).roundToInt()
            VlcScalingMode.BEST_FIT -> {
                if (videoRatio > containerRatio) {
                    containerWidth to (containerWidth / videoRatio).roundToInt()
                } else {
                    (containerHeight * videoRatio).roundToInt() to containerHeight
                }
            }
        }
        outputView.layoutParams = FrameLayout.LayoutParams(layoutWidth, layoutHeight).apply {
            gravity = android.view.Gravity.CENTER
        }
    }

    private fun startSmoothFrameCapture(
        sourceView: SurfaceView,
        outputView: TextureView,
        bitmap: Bitmap,
        engine: LiveSloMoEngine,
        workerHandler: Handler,
        generation: Int,
        onResult: (Boolean) -> Unit
    ) {
        var lastSignature: Int? = null
        var resultDelivered = false
        val startTimeMs = SystemClock.elapsedRealtime()
        var captureCount = 0
        var requestCount = 0
        var loggedSourceRect = false
        val captureTask = object : Runnable {
            override fun run() {
                if (generation != liveSmoothGeneration || liveSmoothEngine !== engine) {
                    return
                }
                requestCount++
                val captureStartNs = SystemClock.elapsedRealtimeNanos()
                try {
                    val sourceRect = pixelCopySourceRect(sourceView, bitmap.width, bitmap.height)
                    if (!loggedSourceRect) {
                        loggedSourceRect = true
                        diagnosticInfo(
                            "PixelCopy source surface=${sourceView.holder.surfaceFrame.width()}x" +
                                "${sourceView.holder.surfaceFrame.height()} view=${sourceView.width}x" +
                                "${sourceView.height} crop=$sourceRect destination=${bitmap.width}x${bitmap.height}"
                        )
                    }
                    PixelCopy.request(sourceView, sourceRect, bitmap, { result ->
                        if (generation != liveSmoothGeneration || liveSmoothEngine !== engine) {
                            return@request
                        }
                        val captureElapsedMs =
                            (SystemClock.elapsedRealtimeNanos() - captureStartNs) / 1_000_000f
                        if (result == PixelCopy.SUCCESS) {
                            val signature = frameSignature(bitmap)
                            if (signature != lastSignature) {
                                lastSignature = signature
                                val submitStartNs = SystemClock.elapsedRealtimeNanos()
                                engine.submit(bitmap)
                                val submitElapsedMs =
                                    (SystemClock.elapsedRealtimeNanos() - submitStartNs) / 1_000_000f
                                captureCount++
                                if (captureCount <= 12 || captureCount % 30 == 0) {
                                    diagnosticInfo(
                                        "capture#$captureCount pixelCopyMs=$captureElapsedMs " +
                                            "submitMs=$submitElapsedMs rendered=${engine.renderedFrameCount()} " +
                                            "interpolated=${engine.interpolatedFrameCount()}"
                                    )
                                }
                            } else if (requestCount <= 12) {
                                diagnosticInfo("request#$requestCount unchanged pixelCopyMs=$captureElapsedMs")
                            }
                        } else {
                            Log.w(DIAGNOSTIC_TAG, "PixelCopy request#$requestCount failed result=$result")
                        }
                        if (!finishOrTimeout()) {
                            scheduleNext()
                        }
                    }, workerHandler)
                } catch (error: Throwable) {
                    Log.w(DIAGNOSTIC_TAG, "PixelCopy request failed", error)
                    if (!finishOrTimeout()) {
                        scheduleNext()
                    }
                }
            }

            private fun scheduleNext() {
                val sourceFrameIntervalMs = (1000f / ASSUMED_SOURCE_FRAME_RATE / playbackRate)
                    .toLong()
                    .coerceIn(MIN_CAPTURE_INTERVAL_MS, MAX_CAPTURE_INTERVAL_MS)
                workerHandler.postDelayed(this, sourceFrameIntervalMs)
            }

            private fun finishOrTimeout(): Boolean {
                if (!resultDelivered && engine.interpolatedFrameCount() > 0) {
                    resultDelivered = true
                    post {
                        if (generation == liveSmoothGeneration && liveSmoothEngine === engine) {
                            liveSmoothBackdrop?.alpha = 1f
                            outputView.alpha = 1f
                            diagnosticInfo(
                                "first interpolated frame presented after " +
                                    "${SystemClock.elapsedRealtime() - startTimeMs}ms"
                            )
                            onResult(true)
                        }
                    }
                } else if (!resultDelivered &&
                    SystemClock.elapsedRealtime() - startTimeMs >= SMOOTH_START_TIMEOUT_MS) {
                    resultDelivered = true
                    Log.e(
                        DIAGNOSTIC_TAG,
                        "startup timeout requests=$requestCount captures=$captureCount " +
                            "rendered=${engine.renderedFrameCount()} " +
                            "interpolated=${engine.interpolatedFrameCount()}"
                    )
                    post {
                        if (generation == liveSmoothGeneration && liveSmoothEngine === engine) {
                            disableLiveSmoothSloMo()
                            onResult(false)
                        }
                    }
                    return true
                }
                return false
            }
        }
        liveSmoothCaptureRunnable = captureTask
        workerHandler.post(captureTask)
    }

    private fun pixelCopySourceRect(
        sourceView: SurfaceView,
        destinationWidth: Int,
        destinationHeight: Int
    ): Rect {
        val surfaceFrame = sourceView.holder.surfaceFrame
        val sourceWidth = surfaceFrame.width().takeIf { it > 0 }
            ?: sourceView.width.coerceAtLeast(1)
        val sourceHeight = surfaceFrame.height().takeIf { it > 0 }
            ?: sourceView.height.coerceAtLeast(1)
        val destinationRatio = destinationWidth / destinationHeight.toFloat()
        val sourceRatio = sourceWidth / sourceHeight.toFloat()

        return if (sourceRatio > destinationRatio) {
            val cropWidth = (sourceHeight * destinationRatio).roundToInt().coerceIn(1, sourceWidth)
            val left = (sourceWidth - cropWidth) / 2
            Rect(left, 0, left + cropWidth, sourceHeight)
        } else {
            val cropHeight = (sourceWidth / destinationRatio).roundToInt().coerceIn(1, sourceHeight)
            val top = (sourceHeight - cropHeight) / 2
            Rect(0, top, sourceWidth, top + cropHeight)
        }
    }

    private fun frameSignature(bitmap: Bitmap): Int {
        var signature = 17
        for (row in 1..7) {
            val y = row * bitmap.height / 8
            for (column in 1..7) {
                val x = column * bitmap.width / 8
                signature = 31 * signature + bitmap.getPixel(
                    x.coerceAtMost(bitmap.width - 1),
                    y.coerceAtMost(bitmap.height - 1)
                )
            }
        }
        return signature
    }

    private fun clearSmoothViews() {
        liveSmoothOutput?.apply {
            visibility = View.GONE
            alpha = 1f
        }
        liveSmoothBackdrop?.apply {
            visibility = View.GONE
            alpha = 1f
        }
        liveSmoothOutput = null
        liveSmoothBackdrop = null
        liveSmoothSource = null
        liveSmoothCaptureActive = false
        liveSmoothFrameWidth = 0
        liveSmoothFrameHeight = 0
    }

    private fun runWhenTextureReady(captureView: TextureView, action: () -> Unit) {
        if (captureView.isAvailable) {
            action()
            return
        }
        captureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                captureView.surfaceTextureListener = null
                action()
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true
        }
    }

    private fun applyAxisFit(mediaPlayer: MediaPlayer, fitHeight: Boolean) {
        val videoSize = currentVideoSize(mediaPlayer)
        if (videoSize == null || width <= 0 || height <= 0) {
            return
        }

        val (videoWidth, videoHeight) = videoSize
        mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_ORIGINAL)
        mediaPlayer.setAspectRatio(null)
        val scale = if (fitHeight) {
            height / videoHeight
        } else {
            width / videoWidth
        }
        mediaPlayer.setScale(scale.coerceAtLeast(0f))
    }

    private fun currentVideoSize(mediaPlayer: MediaPlayer): Pair<Float, Float>? {
        val track = mediaPlayer.currentVideoTrack ?: return null

        var videoWidth = track.width
        var videoHeight = track.height
        if (track.orientation == ORIENTATION_LEFT_BOTTOM ||
            track.orientation == ORIENTATION_RIGHT_TOP) {
            videoWidth = track.height
            videoHeight = track.width
        }
        val sarNum = track.sarNum.takeIf { it > 0 } ?: 1
        val sarDen = track.sarDen.takeIf { it > 0 } ?: 1
        val displayWidth = videoWidth * sarNum / sarDen.toFloat()
        val displayHeight = videoHeight.toFloat()
        if (displayWidth <= 0f || displayHeight <= 0f) {
            return null
        }
        return displayWidth to displayHeight
    }

    private fun findVideoSurfaceView(root: View): SurfaceView? {
        if (root is SurfaceView && root.holder.surface.isValid) {
            return root
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findVideoSurfaceView(root.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun diagnosticInfo(message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(DIAGNOSTIC_TAG, message)
        }
    }

    companion object {
        private const val ORIENTATION_LEFT_BOTTOM = 5
        private const val ORIENTATION_RIGHT_TOP = 6
        private const val MAX_INTERPOLATION_EDGE = 640f
        private const val ASSUMED_SOURCE_FRAME_RATE = 30f
        private const val MIN_CAPTURE_INTERVAL_MS = 33L
        private const val MAX_CAPTURE_INTERVAL_MS = 500L
        private const val SMOOTH_START_TIMEOUT_MS = 8_000L
        private const val RATE_EPSILON = 0.001f
        private const val DIAGNOSTIC_TAG = "DroidFSSmoothDiag"
    }
}

enum class VlcScalingMode {
    BEST_FIT,
    FILL,
    VERTICAL,
    HORIZONTAL
}
