#include <jni.h>
#include <android/log.h>
#include "include/llava/llava_runner.h"
#include <memory>
#include <vector>

#if defined(ET_USE_THREADPOOL)
#include <executorch/extension/threadpool/cpuinfo_utils.h>
#include <executorch/extension/threadpool/threadpool.h>
#endif

#define LOG_TAG "VLMTest"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using executorch::extension::llm::Image;

// JNI function to run the test
extern "C" JNIEXPORT jboolean JNICALL
Java_com_mtkresearch_gai_1android_service_VLMEngineService_nativeTestVlm(
        JNIEnv* env, jobject thiz, 
        jstring model_path, 
        jstring tokenizer_path, 
        jstring image_path) {

    try {
        const char* model_path_str = env->GetStringUTFChars(model_path, nullptr);
        const char* tokenizer_path_str = env->GetStringUTFChars(tokenizer_path, nullptr);
        const char* image_path_str = env->GetStringUTFChars(image_path, nullptr);

        // Use the exact same parameters as the CLI
        const char* prompt_str = "ASSISTANT:";
        const int32_t seq_len = 768;
        const float temperature = 0.0f;

        #if defined(ET_USE_THREADPOOL)
            int32_t cpu_threads = -1;
            uint32_t num_performant_cores = cpu_threads == -1
                ? ::executorch::extension::cpuinfo::get_num_performant_cores()
                : static_cast<uint32_t>(cpu_threads);
            LOGI("Resetting threadpool with num threads = %d", num_performant_cores);
            if (num_performant_cores > 0) {
                ::executorch::extension::threadpool::get_threadpool()
                    ->_unsafe_reset_threadpool(num_performant_cores);
            }
        #endif

        LOGI("Creating LlavaRunner with parameters:");
        LOGI("  Model path: %s", model_path_str);
        LOGI("  Tokenizer path: %s", tokenizer_path_str);
        LOGI("  Image path: %s", image_path_str);
        LOGI("  Prompt: %s", prompt_str);
        LOGI("  Sequence length: %d", seq_len);
        LOGI("  Temperature: %f", temperature);

        example::LlavaRunner runner(model_path_str, tokenizer_path_str, temperature);

        // Load image from .pt file
        std::vector<uint8_t> image_data;
        #ifdef LLAVA_NO_TORCH_DUMMY_IMAGE
            // Fallback to dummy image if torch is not available
            image_data.resize(3 * 240 * 336);
            std::fill(image_data.begin(), image_data.end(), 0);
            std::vector<Image> images = {
                {.data = image_data, .width = 336, .height = 240}
            };
        #else
            // Load image using ExecuTorch's tensor
            // executorch::aten::Tensor image_tensor;
            // TODO: Implement proper image loading using ExecuTorch
            // For now, use dummy image
            image_data.resize(3 * 240 * 336);
            std::fill(image_data.begin(), image_data.end(), 0);
            std::vector<Image> images = {
                {.data = image_data, .width = 336, .height = 240}
            };
            LOGI("Using dummy image 336x240");
        #endif

        auto token_callback = [](const std::string& token) {
            LOGI("Generated: %s", token.c_str());
        };

        auto stats_callback = [](const ::executorch::extension::llm::Stats& stats) {
            LOGI("First token latency: %lld ms", stats.first_token_ms - stats.inference_start_ms);
            LOGI("Total tokens: %d", stats.num_generated_tokens);
            if (stats.num_generated_tokens > 0 && stats.inference_end_ms > stats.first_token_ms) {
                float tokens_per_sec = stats.num_generated_tokens * 1000.0f /
                    (stats.inference_end_ms - stats.first_token_ms);
                LOGI("Speed: %.2f tokens/sec", tokens_per_sec);
            }
        };

        LOGI("Starting generation...");
        auto error = runner.generate(
            std::move(images), 
            prompt_str,
            seq_len,
            token_callback,
            stats_callback
        );

        // Cleanup
        env->ReleaseStringUTFChars(model_path, model_path_str);
        env->ReleaseStringUTFChars(tokenizer_path, tokenizer_path_str);
        env->ReleaseStringUTFChars(image_path, image_path_str);

        if (error != ::executorch::runtime::Error::Ok) {
            LOGE("Generation failed with error: %d", static_cast<int>(error));
            return JNI_FALSE;
        }

        LOGI("Generation completed successfully");
        return JNI_TRUE;

    } catch (const std::exception& e) {
        LOGE("Test failed with exception: %s", e.what());
        return JNI_FALSE;
    }
} 