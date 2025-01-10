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
        JNIEnv* env, jobject thiz, jobject image_obj, jstring prompt, jobject callback_obj) {

    // Get callback class and methods
    jclass callback_class = env->GetObjectClass(callback_obj);
    jmethodID onToken = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V");

    // Create token callback that filters out only the stop tokens and initial whitespace
    auto token_callback = [env, callback_obj, onToken](const std::string& token) {
        static bool isFirstToken = true;
        
        // Skip if token is the stop token "</s>"
        if (token != "</s>") {
            // Skip initial whitespace token
            if (isFirstToken && token.find_first_not_of(" \t\n\r") == std::string::npos) {
                isFirstToken = false;
                return;
            }
            
            jstring jtoken = env->NewStringUTF(token.c_str());
            env->CallVoidMethod(callback_obj, onToken, jtoken);
            env->DeleteLocalRef(jtoken);
            isFirstToken = false;
        }
    };

    try {
        // Get image data from ETImage
        jclass etImageClass = env->GetObjectClass(image_obj);
        jmethodID getBytesMethod = env->GetMethodID(etImageClass, "getBytes", "()[B");
        jmethodID getWidthMethod = env->GetMethodID(etImageClass, "getWidth", "()I");
        jmethodID getHeightMethod = env->GetMethodID(etImageClass, "getHeight", "()I");

        jbyteArray bytes = (jbyteArray)env->CallObjectMethod(image_obj, getBytesMethod);
        jint width = env->CallIntMethod(image_obj, getWidthMethod);
        jint height = env->CallIntMethod(image_obj, getHeightMethod);

        std::vector<llm::Image> images = {{
            .data = std::vector<uint8_t>(env->GetByteArrayElements(bytes, nullptr),
                                       env->GetByteArrayElements(bytes, nullptr) + 
                                       env->GetArrayLength(bytes)),
            .width = width,
            .height = height,
            .channels = 3
        }};

        // Let LLaVA runner handle the prompt formatting
        auto err = g_runner->generate(
            std::move(images),
            env->GetStringUTFChars(prompt, nullptr),
            DEFAULT_SEQ_LEN,
            token_callback
        );

        // Get final result from callback object
        jmethodID getFullResult = env->GetMethodID(callback_class, "getFullResult", "()Ljava/lang/String;");
        jstring result = (jstring)env->CallObjectMethod(callback_obj, getFullResult);
        
        return result;
    } catch (const std::exception& e) {
        LOGE("Error during processing: %s", e.what());
        return env->NewStringUTF(std::string("Error during processing: ").append(e.what()).c_str());
    }
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