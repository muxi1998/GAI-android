#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include "third_party/executorch/examples/models/llava/runner/llava_runner.h"
#include <executorch/runtime/platform/runtime.h>
#include <memory>
#include <vector>
#include <algorithm>
#include <cmath>

#define LOG_TAG "VLMTest"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using executorch::extension::llm::Image;
using executorch::runtime::Error;

// RAII wrapper for JNI strings
class JStringWrapper {
public:
    JStringWrapper(JNIEnv* env, jstring str)
            : env_(env), jstring_(str), cstr_(nullptr) {
        if (str) {
            cstr_ = env->GetStringUTFChars(str, nullptr);
        }
    }

    ~JStringWrapper() {
        if (cstr_) {
            env_->ReleaseStringUTFChars(jstring_, cstr_);
        }
    }

    const char* get() const { return cstr_; }
    bool isValid() const { return cstr_ != nullptr; }

private:
    JNIEnv* env_;
    jstring jstring_;
    const char* cstr_;

    JStringWrapper(const JStringWrapper&) = delete;
    JStringWrapper& operator=(const JStringWrapper&) = delete;
};

// RAII wrapper for bitmap locking
class BitmapLocker {
public:
    BitmapLocker(JNIEnv* env, jobject bitmap)
            : env_(env), bitmap_(bitmap), pixels_(nullptr) {
        if (AndroidBitmap_lockPixels(env, bitmap, &pixels_) < 0) {
            throw std::runtime_error("Failed to lock bitmap pixels");
        }
    }

    ~BitmapLocker() {
        if (pixels_) {
            AndroidBitmap_unlockPixels(env_, bitmap_);
        }
    }

    void* getPixels() { return pixels_; }

private:
    JNIEnv* env_;
    jobject bitmap_;
    void* pixels_;

    BitmapLocker(const BitmapLocker&) = delete;
    BitmapLocker& operator=(const BitmapLocker&) = delete;
};

// Helper function to convert RGBA to RGB and CHW format
std::vector<uint8_t> processImageData(const uint8_t* input, int width, int height) {
    if (!input) {
        throw std::runtime_error("Null input buffer");
    }

    const int in_channels = 4;  // RGBA
    const int out_channels = 3; // RGB
    std::vector<uint8_t> output(out_channels * height * width);

    try {
        for (int c = 0; c < out_channels; c++) {
            for (int h = 0; h < height; h++) {
                for (int w = 0; w < width; w++) {
                    size_t out_idx = c * height * width + h * width + w;
                    size_t in_idx = (h * width + w) * in_channels + c;

                    if (out_idx >= output.size() || in_idx >= (width * height * in_channels)) {
                        throw std::runtime_error("Buffer overflow detected");
                    }

                    output[out_idx] = input[in_idx];
                }
            }
        }
    } catch (const std::exception& e) {
        LOGE("Error processing image: %s", e.what());
        throw;
    }

    return output;
}

// Helper function to resize image with bilinear interpolation
std::vector<uint8_t> resizeImage(const uint8_t* input, int in_width, int in_height,
                                 int target_width, int target_height, int channels) {
    std::vector<uint8_t> output(channels * target_width * target_height);

    float scale_x = static_cast<float>(in_width) / target_width;
    float scale_y = static_cast<float>(in_height) / target_height;

    for (int c = 0; c < channels; c++) {
        for (int y = 0; y < target_height; y++) {
            for (int x = 0; x < target_width; x++) {
                float src_x = x * scale_x;
                float src_y = y * scale_y;

                int x1 = static_cast<int>(std::floor(src_x));
                int y1 = static_cast<int>(std::floor(src_y));
                int x2 = std::min(x1 + 1, in_width - 1);
                int y2 = std::min(y1 + 1, in_height - 1);

                float dx = src_x - x1;
                float dy = src_y - y1;

                size_t idx11 = (y1 * in_width + x1) * channels + c;
                size_t idx12 = (y1 * in_width + x2) * channels + c;
                size_t idx21 = (y2 * in_width + x1) * channels + c;
                size_t idx22 = (y2 * in_width + x2) * channels + c;

                float value = (1 - dx) * (1 - dy) * input[idx11] +
                              dx * (1 - dy) * input[idx12] +
                              (1 - dx) * dy * input[idx21] +
                              dx * dy * input[idx22];

                output[(c * target_height + y) * target_width + x] =
                        static_cast<uint8_t>(std::round(value));
            }
        }
    }

    return output;
}

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    try {
        JNIEnv* env;
        if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
            return JNI_ERR;
        }
        LOGI("Initializing runtime...");
        torch::executor::runtime_init();
        return JNI_VERSION_1_6;
    } catch (const std::exception& e) {
        LOGE("JNI_OnLoad failed: %s", e.what());
        return JNI_ERR;
    } catch (...) {
        LOGE("JNI_OnLoad failed with unknown exception");
        return JNI_ERR;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mtkresearch_gai_1android_service_VLMEngineService_nativeTestVlm(
        JNIEnv* env, jobject thiz,
        jstring model_path,
        jstring tokenizer_path,
        jobject bitmap) {

    try {
        LOGI("Starting VLM test...");

        if (!bitmap) {
            LOGE("Null bitmap received");
            return JNI_FALSE;
        }

        // Validate input parameters
        JStringWrapper model_path_wrapper(env, model_path);
        JStringWrapper tokenizer_path_wrapper(env, tokenizer_path);

        if (!model_path_wrapper.isValid() || !tokenizer_path_wrapper.isValid()) {
            LOGE("Invalid input parameters");
            return JNI_FALSE;
        }

        // Get bitmap info
        AndroidBitmapInfo info;
        if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
            LOGE("Failed to get bitmap info");
            return JNI_FALSE;
        }

        // Validate bitmap format
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
            LOGE("Unsupported bitmap format: %d", info.format);
            return JNI_FALSE;
        }

        const int TARGET_WIDTH = 336;
        const int TARGET_HEIGHT = 240;
        const int CHANNELS = 3;

        // Process image with RAII bitmap locker
        std::vector<uint8_t> final_image;
        {
            BitmapLocker locker(env, bitmap);
            uint8_t* pixels = static_cast<uint8_t*>(locker.getPixels());

            if (!pixels) {
                LOGE("Failed to get bitmap pixels");
                return JNI_FALSE;
            }

            try {
                LOGI("Processing image data...");
                // First convert RGBA to RGB in CHW format
                auto rgb_data = processImageData(pixels, info.width, info.height);

                // Then resize if needed
                if (info.width != TARGET_WIDTH || info.height != TARGET_HEIGHT) {
                    LOGI("Resizing image from %dx%d to %dx%d",
                         info.width, info.height, TARGET_WIDTH, TARGET_HEIGHT);
                    final_image = resizeImage(
                            rgb_data.data(),
                            info.width,
                            info.height,
                            TARGET_WIDTH,
                            TARGET_HEIGHT,
                            CHANNELS
                    );
                } else {
                    final_image = std::move(rgb_data);
                }
            } catch (const std::exception& e) {
                LOGE("Image processing failed: %s", e.what());
                return JNI_FALSE;
            }
        }

        // Create runner with error checking
        LOGI("Creating LlavaRunner...");
        std::unique_ptr<example::LlavaRunner> runner;
        try {
            runner = std::make_unique<example::LlavaRunner>(
                    model_path_wrapper.get(),
                    tokenizer_path_wrapper.get(),
                    0.0f
            );
        } catch (const std::exception& e) {
            LOGE("Failed to create runner: %s", e.what());
            return JNI_FALSE;
        }

        // Load model
        LOGI("Loading model...");
        Error load_error = runner->load();
        if (load_error != Error::Ok) {
            LOGE("Failed to load model: %d", static_cast<int>(load_error));
            return JNI_FALSE;
        }

        // Prepare image for model
        std::vector<Image> images = {{
                                             .data = final_image,
                                             .width = TARGET_WIDTH,
                                             .height = TARGET_HEIGHT,
                                             .channels = CHANNELS
                                     }};

        // Setup callbacks with error handling
        auto token_callback = [](const std::string& token) {
            try {
                LOGI("Generated token: %s", token.c_str());
            } catch (...) {
                LOGE("Error in token callback");
            }
        };

        auto stats_callback = [](const ::executorch::extension::llm::Stats& stats) {
            try {
                LOGI("First token latency: %ld ms", stats.first_token_ms - stats.inference_start_ms);
                LOGI("Total tokens: %ld", stats.num_generated_tokens);
            } catch (...) {
                LOGE("Error in stats callback");
            }
        };

        // Generate response
        LOGI("Starting generation...");
        Error gen_error = runner->generate(
                std::move(images),
                "What's in this image?",
                768,  // seq_len
                token_callback,
                stats_callback,
                true  // echo
        );

        if (gen_error != Error::Ok) {
            LOGE("Generation failed with error: %d", static_cast<int>(gen_error));
            return JNI_FALSE;
        }

        LOGI("Generation completed successfully");
        return JNI_TRUE;

    } catch (const std::exception& e) {
        LOGE("Test failed with exception: %s", e.what());
        return JNI_FALSE;
    } catch (...) {
        LOGE("Test failed with unknown exception");
        return JNI_FALSE;
    }
}