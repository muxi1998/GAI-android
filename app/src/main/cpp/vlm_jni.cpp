#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include "third_party/executorch/examples/models/llava/runner/llava_runner.h"
#include <memory>
#include <vector>
#include <thread>
#include <chrono>
#include "executorch/runtime/kernel/kernel_runtime_context.h"
#include "executorch/runtime/kernel/operator_registry.h"

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
        JNIEnv* env, jobject thiz, jobject image_obj, jstring prompt) {

    // Check if runner exists and validate its state
    if (!g_runner) {
        LOGE("Error: LLaVA not initialized");
        return env->NewStringUTF("Error: LLaVA not initialized");
    }

    // Get ETImage class and method IDs
    jclass etImageClass = env->GetObjectClass(image_obj);
    jmethodID getBytesMethod = env->GetMethodID(etImageClass, "getBytes", "()[B");
    jmethodID getWidthMethod = env->GetMethodID(etImageClass, "getWidth", "()I");
    jmethodID getHeightMethod = env->GetMethodID(etImageClass, "getHeight", "()I");

    // Get image data from ETImage
    jbyteArray bytes = (jbyteArray)env->CallObjectMethod(image_obj, getBytesMethod);
    jint width = env->CallIntMethod(image_obj, getWidthMethod);
    jint height = env->CallIntMethod(image_obj, getHeightMethod);

    // Get byte array elements
    jbyte* buffer = env->GetByteArrayElements(bytes, nullptr);
    jsize length = env->GetArrayLength(bytes);

    ScopedStringChars prompt_chars(env, prompt);
    std::string result;

    try {
        // Create image vector
        std::vector<llm::Image> images;
        llm::Image img;
        img.data.assign(reinterpret_cast<uint8_t*>(buffer), 
                       reinterpret_cast<uint8_t*>(buffer) + length);
        img.width = width;
        img.height = height;
        img.channels = 3;  // ETImage provides RGB format
        images.push_back(std::move(img));

        // Release byte array
        env->ReleaseByteArrayElements(bytes, buffer, JNI_ABORT);

        // Generate response
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
        env->ReleaseByteArrayElements(bytes, buffer, JNI_ABORT);
        return env->NewStringUTF(std::string("Error during processing: ").append(e.what()).c_str());
    }

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