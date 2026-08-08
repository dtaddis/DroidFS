#include <jni.h>

#include <android/log.h>
#include <android/bitmap.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <deque>
#include <memory>
#include <mutex>
#include <string>
#include <thread>

#include "gpu.h"
#include "rife.h"

namespace {

constexpr const char* TAG = "DroidFSLiveSloMo";
constexpr int64_t TARGET_FRAME_NS = 33333333LL;
constexpr int64_t MAX_FRAME_GAP_NS = 600000000LL;
constexpr int64_t MIN_INTERPOLATED_DISPLAY_NS = 16000000LL;

struct VideoFrame {
    ncnn::Mat rgb;
    std::chrono::steady_clock::time_point arrival;
};

class LiveSloMoEngine {
public:
    LiveSloMoEngine(std::string model_path, ANativeWindow* output_window)
        : model_path_(std::move(model_path)), output_window_(output_window) {}

    ~LiveSloMoEngine() {
        stop();
    }

    bool start() {
        const auto initialization_start = std::chrono::steady_clock::now();
        __android_log_print(ANDROID_LOG_INFO, TAG, "Engine start begin");
        if (!output_window_) {
            return false;
        }

        std::lock_guard<std::mutex> gpu_lock(gpu_mutex_);
        ncnn::create_gpu_instance();
        gpu_instance_created_ = true;
        const int gpu_count = ncnn::get_gpu_count();
        __android_log_print(ANDROID_LOG_INFO, TAG, "ncnn Vulkan GPU count=%d", gpu_count);
        if (gpu_count < 1) {
            __android_log_print(ANDROID_LOG_WARN, TAG, "No Vulkan-capable ncnn GPU found");
            destroy_gpu_locked();
            return false;
        }

        rife_ = std::make_unique<RIFE>(0, false, false, false, 2, false, true);
        __android_log_print(ANDROID_LOG_INFO, TAG, "RIFE model load begin");
        if (rife_->load(model_path_) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Could not load RIFE model from %s", model_path_.c_str());
            rife_.reset();
            destroy_gpu_locked();
            return false;
        }
        const int64_t initialization_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - initialization_start
        ).count();
        __android_log_print(
            ANDROID_LOG_INFO,
            TAG,
            "RIFE model load complete; worker starting after %lld ms",
            static_cast<long long>(initialization_ms)
        );

        worker_ = std::thread(&LiveSloMoEngine::worker_loop, this);
        return true;
    }

    void stop() {
        {
            std::lock_guard<std::mutex> lock(queue_mutex_);
            stopping_ = true;
            queue_.clear();
        }
        queue_ready_.notify_all();
        if (worker_.joinable()) {
            worker_.join();
        }

        {
            std::lock_guard<std::mutex> gpu_lock(gpu_mutex_);
            rife_.reset();
            destroy_gpu_locked();
        }
        if (output_window_) {
            ANativeWindow_release(output_window_);
            output_window_ = nullptr;
        }
    }

    void submit_rgba(const uint8_t* pixels, int stride, int width, int height) {
        if (stopping_ || !pixels || stride < width * 4 || width <= 0 || height <= 0) {
            return;
        }

        VideoFrame frame;
        frame.rgb.create(width, height, static_cast<size_t>(3u), 3);
        frame.arrival = std::chrono::steady_clock::now();
        auto* rgb = static_cast<uint8_t*>(frame.rgb.data);

        for (int row = 0; row < height; ++row) {
            const uint8_t* source_row = pixels + static_cast<size_t>(row) * stride;
            for (int col = 0; col < width; ++col) {
                const size_t out = (static_cast<size_t>(row) * width + col) * 3;
                rgb[out] = source_row[col * 4];
                rgb[out + 1] = source_row[col * 4 + 1];
                rgb[out + 2] = source_row[col * 4 + 2];
            }
        }

        {
            std::lock_guard<std::mutex> lock(queue_mutex_);
            if (stopping_) {
                return;
            }
            // Latency is more noticeable than a dropped source frame. Keep only
            // the newest pair when inference briefly falls behind.
            while (queue_.size() >= 2) {
                queue_.pop_front();
            }
            queue_.push_back(std::move(frame));
        }
        queue_ready_.notify_one();
    }

private:
    void destroy_gpu_locked() {
        if (gpu_instance_created_) {
            ncnn::destroy_gpu_instance();
            gpu_instance_created_ = false;
        }
    }

    void worker_loop() {
        VideoFrame previous;
        bool has_previous = false;

        while (true) {
            VideoFrame current;
            {
                std::unique_lock<std::mutex> lock(queue_mutex_);
                queue_ready_.wait(lock, [this] { return stopping_ || !queue_.empty(); });
                if (stopping_) {
                    return;
                }
                current = std::move(queue_.front());
                queue_.pop_front();
            }

            if (!has_previous || previous.rgb.w != current.rgb.w || previous.rgb.h != current.rgb.h) {
                render(current.rgb);
                previous = std::move(current);
                has_previous = true;
                continue;
            }

            int64_t interval_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(
                current.arrival - previous.arrival
            ).count();
            if (interval_ns <= 0 || interval_ns > MAX_FRAME_GAP_NS) {
                render(current.rgb);
                previous = std::move(current);
                continue;
            }

            const int nominal_outputs = std::clamp(
                static_cast<int>(std::llround(static_cast<double>(interval_ns) / TARGET_FRAME_NS)),
                1,
                6
            );
            // One source frame is free; only the in-between frames consume an
            // inference. Always try the first interpolation so the initial
            // estimate cannot permanently disable Smooth mode at 0.5x.
            const int affordable_outputs = std::clamp(
                1 + static_cast<int>(interval_ns / std::max<int64_t>(1, inference_ns_)),
                2,
                6
            );
            const int outputs = std::min(nominal_outputs, affordable_outputs);
            const int64_t output_step_ns = interval_ns / outputs;
            const auto output_start = std::chrono::steady_clock::now();
            auto last_interpolated_presentation = output_start -
                std::chrono::nanoseconds(MIN_INTERPOLATED_DISPLAY_NS);

            for (int index = 1; index <= outputs && !stopping_; ++index) {
                if (index == outputs) {
                    render(current.rgb);
                } else {
                    // RIFE's API writes into caller-owned packed RGB storage.
                    // Passing an empty Mat leaves the Vulkan download targeting
                    // a null buffer and can wedge the driver.
                    ncnn::Mat interpolated(
                        previous.rgb.w,
                        previous.rgb.h,
                        static_cast<size_t>(3u),
                        3
                    );
                    const auto inference_start = std::chrono::steady_clock::now();
                    const float timestep = static_cast<float>(index) / outputs;
                    const int result = rife_->process(previous.rgb, current.rgb, timestep, interpolated);
                    const int64_t elapsed_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(
                        std::chrono::steady_clock::now() - inference_start
                    ).count();
                    inference_ns_ = (inference_ns_ * 3 + elapsed_ns) / 4;
                    if (result == 0 && !interpolated.empty()) {
                        render(interpolated);
                        interpolated_frames_.fetch_add(1, std::memory_order_relaxed);
                        last_interpolated_presentation = std::chrono::steady_clock::now();
                        if (!logged_first_inference_) {
                            logged_first_inference_ = true;
                            __android_log_print(
                                ANDROID_LOG_INFO,
                                TAG,
                                "First interpolation %dx%d completed in %.1f ms",
                                interpolated.w,
                                interpolated.h,
                                elapsed_ns / 1000000.0
                            );
                        }
                    }
                }

                if (has_queued_frame()) {
                    // Never keep the GPU busy finishing frames the viewer will
                    // no longer see, but do not overwrite a newly generated
                    // frame before the display has had a chance to present it.
                    const auto earliest_replacement = last_interpolated_presentation +
                        std::chrono::nanoseconds(MIN_INTERPOLATED_DISPLAY_NS);
                    if (earliest_replacement > std::chrono::steady_clock::now()) {
                        std::this_thread::sleep_until(earliest_replacement);
                    }
                    render(current.rgb);
                    break;
                }

                const auto deadline = output_start + std::chrono::nanoseconds(output_step_ns * index);
                if (deadline > std::chrono::steady_clock::now()) {
                    std::this_thread::sleep_until(deadline);
                }
            }

            previous = std::move(current);
        }
    }

    bool has_queued_frame() {
        std::lock_guard<std::mutex> lock(queue_mutex_);
        return !queue_.empty();
    }

    void render(const ncnn::Mat& image) {
        if (!output_window_ || image.empty()) {
            return;
        }
        ANativeWindow_Buffer buffer{};
        if (ANativeWindow_lock(output_window_, &buffer, nullptr) != 0) {
            const int64_t failures = render_failures_.fetch_add(1, std::memory_order_relaxed) + 1;
            if (failures <= 5) {
                __android_log_print(ANDROID_LOG_ERROR, TAG, "ANativeWindow_lock failed #%lld", static_cast<long long>(failures));
            }
            return;
        }
        if (!logged_output_geometry_) {
            logged_output_geometry_ = true;
            __android_log_print(
                ANDROID_LOG_INFO,
                TAG,
                "Output buffer %dx%d stride=%d format=%d; input=%dx%d",
                buffer.width,
                buffer.height,
                buffer.stride,
                buffer.format,
                image.w,
                image.h
            );
        }
        const auto* source = static_cast<const uint8_t*>(image.data);
        auto* destination = static_cast<uint8_t*>(buffer.bits);
        for (int row = 0; row < buffer.height; ++row) {
            const int source_y = std::min(image.h - 1, row * image.h / buffer.height);
            const uint8_t* source_row = source + static_cast<size_t>(source_y) * image.w * 3;
            uint8_t* destination_row = destination + static_cast<size_t>(row) * buffer.stride * 4;
            for (int col = 0; col < buffer.width; ++col) {
                const int source_x = std::min(image.w - 1, col * image.w / buffer.width);
                destination_row[col * 4] = source_row[source_x * 3];
                destination_row[col * 4 + 1] = source_row[source_x * 3 + 1];
                destination_row[col * 4 + 2] = source_row[source_x * 3 + 2];
                destination_row[col * 4 + 3] = 255;
            }
        }
        ANativeWindow_unlockAndPost(output_window_);
        rendered_frames_.fetch_add(1, std::memory_order_relaxed);
    }

public:
    int64_t rendered_frames() const {
        return rendered_frames_.load(std::memory_order_relaxed);
    }

    int64_t interpolated_frames() const {
        return interpolated_frames_.load(std::memory_order_relaxed);
    }

private:

    std::string model_path_;
    ANativeWindow* output_window_ = nullptr;
    std::unique_ptr<RIFE> rife_;
    std::thread worker_;
    std::mutex queue_mutex_;
    std::condition_variable queue_ready_;
    std::deque<VideoFrame> queue_;
    std::atomic<bool> stopping_{false};
    bool gpu_instance_created_ = false;
    int64_t inference_ns_ = 40000000LL;
    std::atomic<int64_t> rendered_frames_{0};
    std::atomic<int64_t> interpolated_frames_{0};
    std::atomic<int64_t> render_failures_{0};
    bool logged_first_inference_ = false;
    bool logged_output_geometry_ = false;

    static std::mutex gpu_mutex_;
};

std::mutex LiveSloMoEngine::gpu_mutex_;

static LiveSloMoEngine* from_handle(jlong handle) {
    return reinterpret_cast<LiveSloMoEngine*>(handle);
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_sushi_hardcore_droidfs_file_1viewers_LiveSloMoEngine_nativeCreate(
    JNIEnv* env,
    jobject,
    jstring model_path,
    jobject surface
) {
    if (!model_path || !surface) {
        return 0;
    }
    const char* model_chars = env->GetStringUTFChars(model_path, nullptr);
    if (!model_chars) {
        return 0;
    }
    std::string path(model_chars);
    env->ReleaseStringUTFChars(model_path, model_chars);

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        return 0;
    }
    auto engine = std::make_unique<LiveSloMoEngine>(std::move(path), window);
    if (!engine->start()) {
        return 0;
    }
    return reinterpret_cast<jlong>(engine.release());
}

extern "C" JNIEXPORT void JNICALL
Java_sushi_hardcore_droidfs_file_1viewers_LiveSloMoEngine_nativeSubmitBitmap(
    JNIEnv* env,
    jobject,
    jlong handle,
    jobject bitmap
) {
    LiveSloMoEngine* engine = from_handle(handle);
    if (!engine || !bitmap) {
        return;
    }
    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return;
    }
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return;
    }
    engine->submit_rgba(
        static_cast<const uint8_t*>(pixels),
        static_cast<int>(info.stride),
        static_cast<int>(info.width),
        static_cast<int>(info.height)
    );
    AndroidBitmap_unlockPixels(env, bitmap);
}

extern "C" JNIEXPORT void JNICALL
Java_sushi_hardcore_droidfs_file_1viewers_LiveSloMoEngine_nativeDestroy(
    JNIEnv*,
    jobject,
    jlong handle
) {
    delete from_handle(handle);
}

extern "C" JNIEXPORT jlong JNICALL
Java_sushi_hardcore_droidfs_file_1viewers_LiveSloMoEngine_nativeRenderedFrameCount(
    JNIEnv*,
    jobject,
    jlong handle
) {
    const LiveSloMoEngine* engine = from_handle(handle);
    return engine ? static_cast<jlong>(engine->rendered_frames()) : 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_sushi_hardcore_droidfs_file_1viewers_LiveSloMoEngine_nativeInterpolatedFrameCount(
    JNIEnv*,
    jobject,
    jlong handle
) {
    const LiveSloMoEngine* engine = from_handle(handle);
    return engine ? static_cast<jlong>(engine->interpolated_frames()) : 0;
}
