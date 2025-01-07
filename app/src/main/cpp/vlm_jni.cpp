#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include "include/llava/llava_runner_simplified.h"
#include <memory>
#include <vector>

#define LOG_TAG "VLMNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace example;

static std::unique_ptr<LlavaRunner> g_runner;
static constexpr int32_t DEFAULT_SEQ_LEN = 768;
static constexpr float DEFAULT_TEMPERATURE = 0.8f;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_mtkresearch_gai_1android_service_VLMEngineService_nativeInitVlm(
        JNIEnv* env, jobject thiz, jstring model_path, jstring tokenizer_path) {

    const char* model_path_str = env->GetStringUTFChars(model_path, nullptr);
    const char* tokenizer_path_str = env->GetStringUTFChars(tokenizer_path, nullptr);

    try {
        g_runner = std::make_unique<LlavaRunner>(
                model_path_str,
                tokenizer_path_str,
                DEFAULT_TEMPERATURE
        );

        auto result = g_runner->load();
        bool success = result.ok();

        env->ReleaseStringUTFChars(model_path, model_path_str);
        env->ReleaseStringUTFChars(tokenizer_path, tokenizer_path_str);

        return success ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        LOGE("Failed to initialize LLaVA: %s", e.what());
        env->ReleaseStringUTFChars(model_path, model_path_str);
        env->ReleaseStringUTFChars(tokenizer_path, tokenizer_path_str);
        return JNI_FALSE;
    }
}

JNIEXPORT jstring JNICALL
Java_com_mtkresearch_gai_1android_service_VLMEngineService_nativeAnalyzeImage(
        JNIEnv* env, jobject thiz, jobject bitmap, jstring prompt) {

    if (!g_runner) {
        return env->NewStringUTF("Error: LLaVA not initialized");
    }

    AndroidBitmapInfo info;
    void* pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 ||
        AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return env->NewStringUTF("Error: Failed to process image");
    }

    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string result;

    try {
        std::vector<Image> images = {
                {
                        .data = static_cast<uint8_t*>(pixels),
                        .width = static_cast<int32_t>(info.width),
                        .height = static_cast<int32_t>(info.height)
                }
        };

        g_runner->generate(
                images,
                prompt_str,
                DEFAULT_SEQ_LEN,
                [&result](const std::string& token) {
                    result += token;
                },
                nullptr  // stats callback
        );
    } catch (const std::exception& e) {
        result = std::string("Error: ") + e.what();
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_mtkresearch_gai_1android_service_VLMEngineService_nativeReleaseVlm(
        JNIEnv* env, jobject thiz) {
g_runner.reset();
}

} // extern "C"