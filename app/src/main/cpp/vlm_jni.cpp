#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include "include/llava/llava_runner.h"
#include <memory>
#include <vector>
#include <thread>
#include <chrono>

#define LOG_TAG "VLMNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace example;
namespace llm = ::executorch::extension::llm;

static std::unique_ptr<LlavaRunner> g_runner;
static constexpr int32_t DEFAULT_SEQ_LEN = 768;
static constexpr float DEFAULT_TEMPERATURE = 0.8f;
static constexpr int32_t TARGET_IMAGE_SIZE = 336;

// Helper function to safely release JNI strings
class ScopedStringChars {
public:
    ScopedStringChars(JNIEnv* env, jstring str)
            : env_(env), str_(str), chars_(env->GetStringUTFChars(str, nullptr)) {}
    ~ScopedStringChars() { if (chars_) env_->ReleaseStringUTFChars(str_, chars_); }
    const char* get() const { return chars_; }
private:
    JNIEnv* env_;
    jstring str_;
    const char* chars_;
};

// Helper function to resize image dimensions
std::pair<int32_t, int32_t> calculateResizedDimensions(int32_t width, int32_t height) {
    int32_t longest_edge = std::max(width, height);
    float scale_factor = static_cast<float>(TARGET_IMAGE_SIZE) / longest_edge;
    return {
        static_cast<int32_t>(width * scale_factor),
        static_cast<int32_t>(height * scale_factor)
    };
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_mtkresearch_gai_1android_service_VLMEngineService_nativeInitVlm(
        JNIEnv* env, jobject thiz, jstring model_path, jstring tokenizer_path) {
    try {
        const char* model_path_str = env->GetStringUTFChars(model_path, nullptr);
        const char* tokenizer_path_str = env->GetStringUTFChars(tokenizer_path, nullptr);

        // Create the runner
        g_runner = std::make_unique<example::LlavaRunner>(
                model_path_str, tokenizer_path_str, DEFAULT_TEMPERATURE);

        // Add detailed error checking for model loading
        LOGI("Loading LlavaRunner model");
        auto load_error = g_runner->load();
        if (load_error != ::executorch::runtime::Error::Ok) {
            LOGE("Failed to load model with error code: %d", static_cast<int>(load_error));
            if (load_error == ::executorch::runtime::Error::InvalidArgument) {
                LOGE("Invalid model configuration or missing operators");
            }
            g_runner.reset();
            return JNI_FALSE;
        }

        // Verify model files exist and are readable
        FILE* model_file = fopen(model_path_str, "rb");
        if (!model_file) {
            LOGE("Cannot open model file: %s", model_path_str);
            g_runner.reset();
            return JNI_FALSE;
        }
        fclose(model_file);

        FILE* tokenizer_file = fopen(tokenizer_path_str, "rb");
        if (!tokenizer_file) {
            LOGE("Cannot open tokenizer file: %s", tokenizer_path_str);
            g_runner.reset();
            return JNI_FALSE;
        }
        fclose(tokenizer_file);

        env->ReleaseStringUTFChars(model_path, model_path_str);
        env->ReleaseStringUTFChars(tokenizer_path, tokenizer_path_str);

        LOGI("LlavaRunner initialized successfully");
        return JNI_TRUE;

    } catch (const std::exception& e) {
        LOGE("Failed to initialize LLaVA with error: %s", e.what());
        if (g_runner) {
            g_runner.reset();
        }
        return JNI_FALSE;
    }
}

JNIEXPORT jstring JNICALL
Java_com_mtkresearch_gai_1android_service_VLMEngineService_nativeAnalyzeImage(
        JNIEnv* env, jobject thiz, jobject bitmap, jstring prompt) {

    // Check if runner exists and validate its state
    if (!g_runner) {
        LOGE("Error: LLaVA not initialized");
        return env->NewStringUTF("Error: LLaVA not initialized");
    }

    AndroidBitmapInfo info;
    void* pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 ||
        AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("Error: Failed to process image");
        return env->NewStringUTF("Error: Failed to process image");
    }

    ScopedStringChars prompt_chars(env, prompt);
    std::string result;

    try {
        // Log the state before processing
        LOGI("Processing image: %dx%d", info.width, info.height);
        
        // Calculate resized dimensions
        auto [resized_width, resized_height] = calculateResizedDimensions(
            info.width, info.height);

        // Add validation for the image processing state
        if (resized_width <= 0 || resized_height <= 0) {
            LOGE("Invalid image dimensions after resize: %dx%d", resized_width, resized_height);
            return env->NewStringUTF("Error: Invalid image dimensions");
        }

        // Process image and handle any errors
        std::vector<llm::Image> images;
        llm::Image img;
        img.data.assign(static_cast<uint8_t*>(pixels), 
                       static_cast<uint8_t*>(pixels) + (info.width * info.height * 4));
        img.width = resized_width;
        img.height = resized_height;
        img.channels = 3;
        images.push_back(std::move(img));

        // Add more detailed logging
        LOGI("Image processed successfully, starting generation");

        auto err = g_runner->generate(
                std::move(images),
                prompt_chars.get(),
                DEFAULT_SEQ_LEN,
                [&result](const std::string& token) {
                    result += token;
                    LOGI("Generated token: %s", token.c_str());
                }
        );

        if (err != ::executorch::runtime::Error::Ok) {
            LOGE("Generation error: %d", static_cast<int>(err));
            result = "Error during generation";
        }
    } catch (const std::exception& e) {
        LOGE("Error during processing: %s", e.what());
        return env->NewStringUTF(std::string("Error during processing: ").append(e.what()).c_str());
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_mtkresearch_gai_1android_service_VLMEngineService_nativeReleaseVlm(
        JNIEnv* env, jobject thiz) {
    if (g_runner) {
        LOGI("Releasing LlavaRunner");
        g_runner.reset();
    }
}

} // extern "C"