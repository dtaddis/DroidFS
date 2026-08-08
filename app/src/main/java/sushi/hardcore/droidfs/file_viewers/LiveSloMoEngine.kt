package sushi.hardcore.droidfs.file_viewers

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface
import java.io.Closeable
import java.io.File

class LiveSloMoEngine : Closeable {
    private var nativeHandle = 0L

    fun initialize(modelDirectory: File, outputSurface: Surface): Boolean {
        check(nativeHandle == 0L) { "Live Slo-Mo is already initialized" }
        nativeHandle = nativeCreate(modelDirectory.absolutePath, outputSurface)
        return nativeHandle != 0L
    }

    fun submit(bitmap: Bitmap) {
        val handle = nativeHandle
        if (handle == 0L) {
            return
        }
        nativeSubmitBitmap(handle, bitmap)
    }

    fun renderedFrameCount(): Long {
        val handle = nativeHandle
        return if (handle == 0L) 0 else nativeRenderedFrameCount(handle)
    }

    fun interpolatedFrameCount(): Long {
        val handle = nativeHandle
        return if (handle == 0L) 0 else nativeInterpolatedFrameCount(handle)
    }

    override fun close() {
        val handle = nativeHandle
        nativeHandle = 0L
        if (handle != 0L) {
            nativeDestroy(handle)
        }
    }

    private external fun nativeCreate(modelPath: String, outputSurface: Surface): Long

    private external fun nativeSubmitBitmap(handle: Long, bitmap: Bitmap)

    private external fun nativeRenderedFrameCount(handle: Long): Long

    private external fun nativeInterpolatedFrameCount(handle: Long): Long

    private external fun nativeDestroy(handle: Long)

    companion object {
        private const val MODEL_ASSET_DIRECTORY = "rife-v4.6"
        private val MODEL_FILES = mapOf(
            "flownet.param" to 16_749L,
            "flownet.bin" to 10_614_320L
        )

        init {
            System.loadLibrary("live_slo_mo")
        }

        fun prepareModel(context: Context): File {
            val modelDirectory = File(context.filesDir, "models/$MODEL_ASSET_DIRECTORY")
            if (!modelDirectory.exists() && !modelDirectory.mkdirs()) {
                throw IllegalStateException("Could not create the RIFE model directory")
            }
            for ((fileName, expectedLength) in MODEL_FILES) {
                val destination = File(modelDirectory, fileName)
                val assetPath = "$MODEL_ASSET_DIRECTORY/$fileName"
                if (destination.isFile && destination.length() == expectedLength) {
                    continue
                }
                val temporary = File(modelDirectory, "$fileName.tmp")
                context.assets.open(assetPath).use { input ->
                    temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                if (destination.exists() && !destination.delete()) {
                    temporary.delete()
                    throw IllegalStateException("Could not update the RIFE model")
                }
                if (!temporary.renameTo(destination)) {
                    temporary.delete()
                    throw IllegalStateException("Could not install the RIFE model")
                }
            }
            return modelDirectory
        }
    }
}
