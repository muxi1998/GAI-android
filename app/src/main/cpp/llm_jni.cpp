#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <memory>
#include <setjmp.h>
#include <signal.h>
#include <cmath>
#include "common/dump.h"
#include "common/logging.h"
#include "common/timer.h"
#include "mtk_llm.h"
#include "tokenizer/tokenizer.h"
#include "tokenizer/tokenizer_factory.h"
#include "utils/NPUWareUtilsLib.h"
#include "utils/utils.h"

#define LOG_TAG "llmJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using TokenType = mtk::Tokenizer::TokenType;
using TokenizerUPtr = std::unique_ptr<mtk::Tokenizer>;
using mtk::TokenizerFactory;

LlmModelOptions llmModelOpt;
LlmRuntimeOptions llmRuntimeOpt;

SharedWeightsHandle* sharedWeightsHandle = nullptr;

static void* llmRuntime = nullptr;
static TokenizerUPtr tokenizer;

static jmp_buf jump_buffer;
static volatile sig_atomic_t fault_occurred = 0;

static void signal_handler(int signum) {
    fault_occurred = 1;
    longjmp(jump_buffer, 1);
}

TokenizerUPtr prepare_tokenizer() {
    auto tokenizer = TokenizerFactory().create(llmRuntimeOpt.tokenizerPath, llmRuntimeOpt.tokenizerRegex);
    const auto& specialTokens = llmRuntimeOpt.specialTokens;
    if (specialTokens.addBos)
        tokenizer->enableBosToken(specialTokens.bosId);
    return tokenizer;
}

std::tuple<std::string, std::vector<TokenType>>
get_prompt_and_tokens(const std::string& inputString, const TokenizerUPtr& tokenizer,
                      const bool parsePromptTokens) {
    auto inputTokens =
            parsePromptTokens ? utils::parseTokenString(inputString) : tokenizer->tokenize(inputString);

    const auto& inputPrompt = parsePromptTokens ? tokenizer->detokenize(inputTokens) : inputString;
    return {inputPrompt, inputTokens};
}

void llm_init(void** llmRuntime, const std::string& yamlConfigPath,
              const bool preloadSharedWeights) {
    Timer timer;
    timer.start();
    LOGI("Begin model init...");

    // Force reset config to default values
    llmModelOpt = {};
    llmRuntimeOpt = {};

    // Load yaml config
    utils::parseLlmConfigYaml(yamlConfigPath, llmModelOpt, llmRuntimeOpt);

    if (preloadSharedWeights) {
        Timer preloadTimer;
        preloadTimer.start();
        mtk_llm_preload_shared_weights(&sharedWeightsHandle, llmRuntimeOpt);
        LOGI("Preload shared weights took: %f ms", preloadTimer.reset() * 1000);
    }

    bool status = mtk_llm_init(llmRuntime, llmModelOpt, llmRuntimeOpt, sharedWeightsHandle);
    if (!status) {
        LOGE("LLM init failed");
        return;
    }
    double elapsed = timer.reset();
    LOGI("Done model init. (Time taken: %f s)", elapsed);
}

void llm_swap_model(void* llmRuntime, const size_t tokenSize = 1) {
    Timer timer;
    timer.start();
    LOGI("Hot swapping to %zu token model...", tokenSize);
    mtk_llm_swap_model(llmRuntime, tokenSize);
    double elapsed = timer.reset();
    LOGI("Done model hot swapping. (Time taken: %f s)", elapsed);
}

TokenType llm_digest_prompt(void* llmRuntime, const TokenizerUPtr& tokenizer,
                            const std::vector<TokenType>& inputTokens, const size_t modelTokenSize,
                            double& promptTokPerSec) {
    const auto logitsType = llmModelOpt.modelOutputType;
    void* lastLogits;
    const auto inpBeginIt = inputTokens.cbegin();
    const auto inputTokenCount = inputTokens.size();
    size_t inputTokenIndex = 0;

    const auto startTokenIndex = mtk_llm_get_token_index(llmRuntime);

    // Warn cache overflow
    if (startTokenIndex + inputTokenCount > llmModelOpt.cacheSize) {
        LOGE("Input prompt length (%zu) is longer than the available context length (cur token index = %zu, cache size = %zu). Cache will be overflowed.",
             inputTokenCount, startTokenIndex, llmModelOpt.cacheSize);
    }

    auto getNewTokens = [&]() {
        const size_t numInputTokenLeft = inputTokenCount - inputTokenIndex;
        const size_t remainder = numInputTokenLeft % modelTokenSize;
        const auto numNewTok = remainder ? remainder : modelTokenSize;
        const auto tokIdxStart = inputTokenIndex;
        const auto tokIdxEnd = tokIdxStart + numNewTok;
        const auto newTokens = std::vector(inpBeginIt + tokIdxStart, inpBeginIt + tokIdxEnd);
        LOGI("Feeding model with prompt tokens [%zu - %zu] (numToken=%zu)", tokIdxStart, tokIdxEnd, numNewTok);
        return newTokens;
    };

    Timer promptTimer;
    promptTimer.start();
    while (inputTokenIndex < inputTokenCount) {
        const auto curInputTokens = getNewTokens();
        const auto numNewTok = curInputTokens.size();

        auto isLastPromptStep = [&] { return inputTokenIndex + numNewTok >= inputTokenCount; };

        const auto logitsKind = isLastPromptStep() ? LogitsKind::LAST : LogitsKind::NONE;
        lastLogits = mtk_llm_inference_once(llmRuntime, curInputTokens, logitsKind);

        inputTokenIndex += numNewTok;
    }
    double promptTimeTaken = promptTimer.reset();

    const size_t idealPromptSize =
            std::ceil(float(inputTokenCount) / modelTokenSize) * modelTokenSize;
    promptTokPerSec = idealPromptSize / promptTimeTaken;

    LOGI("Done analyzing prompt in %f s (%f tok/s)", promptTimeTaken, promptTokPerSec);
    auto outputToken = utils::argmaxFrom16bitLogits(logitsType, lastLogits, tokenizer->vocabSize());
    return outputToken;
}

TokenType llm_autoregressive_per_step(void* llmRuntime, const TokenizerUPtr& tokenizer,
                                      const TokenType inputToken) {
    const auto logitsType = llmModelOpt.modelOutputType;
    void* lastLogits;

    lastLogits = mtk_llm_inference_once(llmRuntime, {inputToken});

    auto outputToken = utils::argmaxFrom16bitLogits(logitsType, lastLogits, tokenizer->vocabSize());

    return outputToken;
}

std::vector<TokenType> llm_gen_response(void* llmRuntime, const TokenizerUPtr& tokenizer,
                                        const size_t maxResponse, const TokenType firstInputToken,
                                        std::string& fullResponse, double& genTokPerSec) {
    const size_t maxTokenLength = llmModelOpt.maxTokenLength;
    auto curTokenIndex = mtk_llm_get_token_index(llmRuntime);
    const auto& sequenceLength = curTokenIndex;

    double elapsed = 0, genTotalTime = 0;
    genTokPerSec = 0;
    size_t genTokCount = 0;

    std::string response;
    utils::UTF8CharResolver utf8Resolver;
    TokenType outputToken = firstInputToken;

    std::vector<TokenType> generatedTokens = {firstInputToken};

    auto isStopToken = [](const auto token) {
        const auto& stopTokenSet = llmRuntimeOpt.specialTokens.stopToken;
        return stopTokenSet.find(token) != stopTokenSet.end();
    };

    Timer timer;
    timer.start();
    while (genTokCount < maxResponse && sequenceLength < maxTokenLength) {
        outputToken = llm_autoregressive_per_step(llmRuntime, tokenizer, outputToken);
        generatedTokens.push_back(outputToken);
        genTokCount++;
        curTokenIndex++;

        elapsed = timer.reset();
        genTotalTime += elapsed;

        if (isStopToken(outputToken)) {
            break;
        }
        const std::string tokStr = tokenizer->detokenize(outputToken);

        const bool isTokStrResolved = utf8Resolver.addBytes(tokStr);
        if (isTokStrResolved) {
            response = utf8Resolver.getResolvedStr();
            fullResponse += response;
        }
    }
    genTokPerSec = double(genTokCount) / genTotalTime;
    return generatedTokens;
}

std::tuple<std::string, double, double>
llm_inference(void* llmRuntime, const std::string& inputString, const TokenizerUPtr& tokenizer,
              const size_t maxResponse = 50, const bool parsePromptTokens = false) {
    // Convert string to tokens
    auto [inputPrompt, inputTokens] =
            get_prompt_and_tokens(inputString, tokenizer, parsePromptTokens);
    DUMP(PROMPT).fromVector("prompt_tokens", inputTokens);
    DUMP(PROMPT).fromString("prompt_text", inputPrompt);

    std::cout << "\n[Prompt]\n" << inputPrompt << '\n' << std::endl;

    // Input prompt caching
    const auto promptTokenSize = llmModelOpt.promptTokenBatchSize;
    const auto genTokenSize = llmModelOpt.genTokenBatchSize;
    double promptTokPerSec;

    auto outputToken =
            llm_digest_prompt(llmRuntime, tokenizer, inputTokens, promptTokenSize, promptTokPerSec);

    // Swap to gen mode model
    if (promptTokenSize != genTokenSize) {
        llm_swap_model(llmRuntime, genTokenSize);
    }

    std::string fullResponse;

    // Generation process
    std::cout << "\nResponse [Max Length = " << maxResponse << "]:" << std::endl;
    std::string tokStr = tokenizer->detokenize(outputToken);
    std::cout << tokStr << std::flush;
    fullResponse += tokStr;
    LOG(DEBUG) << "First output token " << outputToken << ": \"" << tokStr << "\"";
    DUMP(RESPONSE).fromValue("sampled_token", outputToken);
    DUMP(RESPONSE).fromString("sampled_text", tokStr);
    DUMP(RESPONSE).fromString("full_response", fullResponse);

    double genTokPerSec;
    const auto outputTokens = llm_gen_response(
            llmRuntime, tokenizer, maxResponse, outputToken, fullResponse, genTokPerSec);

    // Show the output tokens if the input is also tokens
    if (parsePromptTokens) {
        std::cout << "\nGenerated Tokens: " << outputTokens << std::endl;
    }
    std::cout << "\n[Latency]" << std::endl;
    std::cout << "      Prompt Mode: " << promptTokPerSec << " tok/s" << std::endl;
    std::cout << "  Generative Mode: " << genTokPerSec << " tok/s" << std::endl;
    return {fullResponse, promptTokPerSec, genTokPerSec};
}

void llm_reset(void* llmRuntime) {
    mtk_llm_reset(llmRuntime);
}

void llm_release(void* llmRuntime) {
    mtk_llm_release(llmRuntime);
    mtk_llm_free_preloaded_shared_weights(sharedWeightsHandle);
}


// Additional add based on original inference
std::tuple<std::string, double, double>
llm_streaming_inference(void* llmRuntime, const std::string& inputString, const TokenizerUPtr& tokenizer,
                        const size_t maxResponse, const bool parsePromptTokens,
                        const std::function<void(const std::string&)>& tokenCallback) {
    // Convert string to tokens
    auto [inputPrompt, inputTokens] =
            get_prompt_and_tokens(inputString, tokenizer, parsePromptTokens);
    DUMP(PROMPT).fromVector("prompt_tokens", inputTokens);
    DUMP(PROMPT).fromString("prompt_text", inputPrompt);

    std::cout << "\n[Prompt]\n" << inputPrompt << '\n' << std::endl;

    // Input prompt caching
    const auto promptTokenSize = llmModelOpt.promptTokenBatchSize;
    const auto genTokenSize = llmModelOpt.genTokenBatchSize;
    double promptTokPerSec;

    auto outputToken =
            llm_digest_prompt(llmRuntime, tokenizer, inputTokens, promptTokenSize, promptTokPerSec);

    // Swap to gen mode model
    if (promptTokenSize != genTokenSize) {
        llm_swap_model(llmRuntime, genTokenSize);
    }

    std::string fullResponse;
    std::vector<TokenType> generatedTokens;
    double genTokPerSec = 0;

    // Streaming generation process
    std::cout << "\nResponse [Max Length = " << maxResponse << "]:" << std::endl;
    std::string tokStr = tokenizer->detokenize(outputToken);
    std::cout << tokStr << std::flush;
    fullResponse += tokStr;
    tokenCallback(tokStr);  // Call the callback with the first token
    LOG(DEBUG) << "First output token " << outputToken << ": \"" << tokStr << "\"";
    DUMP(RESPONSE).fromValue("sampled_token", outputToken);
    DUMP(RESPONSE).fromString("sampled_text", tokStr);
    DUMP(RESPONSE).fromString("full_response", fullResponse);

    generatedTokens.push_back(outputToken);

    const size_t maxTokenLength = llmModelOpt.maxTokenLength;
    auto curTokenIndex = mtk_llm_get_token_index(llmRuntime);
    const auto& sequenceLength = curTokenIndex;

    double elapsed = 0, genTotalTime = 0;
    size_t genTokCount = 1;  // Start from 1 because we've already generated the first token

    utils::UTF8CharResolver utf8Resolver;

    auto isStopToken = [](const auto token) {
        const auto& stopTokenSet = llmRuntimeOpt.specialTokens.stopToken;
        return stopTokenSet.find(token) != stopTokenSet.end();
    };

    Timer timer;
    timer.start();
    while (genTokCount < maxResponse && sequenceLength < maxTokenLength) {
        outputToken = llm_autoregressive_per_step(llmRuntime, tokenizer, outputToken);
        generatedTokens.push_back(outputToken);
        genTokCount++;
        curTokenIndex++;

        elapsed = timer.reset();
        genTotalTime += elapsed;

        if (isStopToken(outputToken)) {
            break;
        }
        tokStr = tokenizer->detokenize(outputToken);

        const bool isTokStrResolved = utf8Resolver.addBytes(tokStr);
        if (isTokStrResolved) {
            std::string response = utf8Resolver.getResolvedStr();
            fullResponse += response;
            std::cout << response << std::flush;
            tokenCallback(response);  // Call the callback with the new token(s)
            DUMP(RESPONSE).fromString("full_response", fullResponse);
        }
    }
    genTokPerSec = double(genTokCount) / genTotalTime;

    std::cout << "\n[Latency]" << std::endl;
    std::cout << "      Prompt Mode: " << promptTokPerSec << " tok/s" << std::endl;
    std::cout << "  Generative Mode: " << genTokPerSec << " tok/s" << std::endl;

    return {fullResponse, promptTokPerSec, genTokPerSec};
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_llmapp_jni_LlmNative_nativeInitLlm(JNIEnv *env, jobject /* this */,
                                                    jstring yamlConfigPath,
                                                    jboolean preloadSharedWeights) {
    const char *configPath = nullptr;
    struct sigaction sa, old_sa;
    bool success = false;

    memset(&sa, 0, sizeof(struct sigaction));
    sa.sa_handler = signal_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;

    if (sigaction(SIGSEGV, &sa, &old_sa) == -1) {
        LOGE("Failed to set up signal handler");
        return JNI_FALSE;
    }

    try {
        configPath = env->GetStringUTFChars(yamlConfigPath, 0);
        if (!configPath) {
            LOGE("Failed to get config path string");
            return JNI_FALSE;
        }

        LOGI("Initializing LLM with config: %s", configPath);

        if (setjmp(jump_buffer) == 0) {
            fault_occurred = 0;
            llm_init(&llmRuntime, configPath, preloadSharedWeights);
            if (!llmRuntime) {
                LOGE("Failed to initialize LLM runtime");
                goto cleanup;
            }

            tokenizer = prepare_tokenizer();
            if (!tokenizer) {
                LOGE("Failed to create tokenizer");
                goto cleanup;
            }

            success = true;
        } else {
            LOGE("Segmentation fault occurred during LLM initialization");
        }
    } catch (const std::exception &e) {
        LOGE("Exception during LLM initialization: %s", e.what());
    } catch (...) {
        LOGE("Unknown exception during LLM initialization");
    }

    cleanup:
    if (configPath) {
        env->ReleaseStringUTFChars(yamlConfigPath, configPath);
    }

    // Restore the old signal handler
    if (sigaction(SIGSEGV, &old_sa, NULL) == -1) {
        LOGE("Failed to restore old signal handler");
    }

    if (fault_occurred) {
        if (llmRuntime) {
            llm_release(llmRuntime);
            llmRuntime = nullptr;
        }
        if (tokenizer) {
            tokenizer.reset();
        }
    }

    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobject JNICALL
Java_com_example_llmapp_jni_LlmNative_nativeInference(JNIEnv *env, jobject /* this */,
                                                      jstring inputString, jint maxResponse,
                                                      jboolean parsePromptTokens) {
    jstring preformatterName = env->NewStringUTF("Llama3NoInput");

    if (!llmRuntime || !tokenizer) {
        LOGE("LLM not initialized");
        return nullptr;
    }

    const char *input = env->GetStringUTFChars(inputString, 0);
    std::string prompt(input);
    env->ReleaseStringUTFChars(inputString, input);

    LOGI("Performing inference for input: %s", prompt.c_str());

    // Handle preformatting if needed
    if (!parsePromptTokens && preformatterName != nullptr) {
        const char *preformatterNameStr = env->GetStringUTFChars(preformatterName, 0);
        std::string preformatterNameCpp(preformatterNameStr);
        env->ReleaseStringUTFChars(preformatterName, preformatterNameStr);

        if (!preformatterNameCpp.empty()) {
            if (utils::addPreformatter(preformatterNameCpp, prompt)) {
                LOGI("Preformatted prompt with '%s'", preformatterNameCpp.c_str());
            } else {
                LOGE("Invalid preformatter: '%s'", preformatterNameCpp.c_str());
            }
        }
    }

    // Call llm_inference
    double promptTokPerSec, genTokPerSec;
    std::string fullResponse;
    std::vector<int> generatedTokens;
    std::tie(fullResponse, promptTokPerSec, genTokPerSec) =
            llm_inference(llmRuntime, prompt, tokenizer, maxResponse, parsePromptTokens);

    // Create the result object
    jclass resultClass = env->FindClass("com/example/llmapp/data/model/InferenceResult");
    if (resultClass == nullptr) {
        LOGE("Failed to find InferenceResult class");
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(Ljava/lang/String;DD[I)V");
    if (constructor == nullptr) {
        LOGE("Failed to find InferenceResult constructor");
        return nullptr;
    }

    // Create the jintArray for generatedTokens
    jintArray jGeneratedTokens = env->NewIntArray(generatedTokens.size());
    if (jGeneratedTokens != nullptr) {
        env->SetIntArrayRegion(jGeneratedTokens, 0, generatedTokens.size(),
                               reinterpret_cast<const jint*>(generatedTokens.data()));
    } else {
        LOGE("Failed to create jintArray for generatedTokens");
        return nullptr;
    }

    // Create the InferenceResult object
    jobject result = env->NewObject(resultClass, constructor,
                                    env->NewStringUTF(fullResponse.c_str()),
                                    (jdouble)promptTokPerSec,
                                    (jdouble)genTokPerSec,
                                    jGeneratedTokens);

    if (result == nullptr) {
        LOGE("Failed to create InferenceResult object");
        env->DeleteLocalRef(jGeneratedTokens);
        return nullptr;
    }

    // Clean up local references
    env->DeleteLocalRef(jGeneratedTokens);
    env->DeleteLocalRef(preformatterName);

    return result;
}

JNIEXPORT jobject JNICALL
Java_com_example_llmapp_jni_LlmNative_nativeStreamingInference(JNIEnv *env, jobject /* this */,
                                                               jstring inputString, jint maxResponse,
                                                               jboolean parsePromptTokens,
                                                               jobject callback) {
    jstring preformatterName = env->NewStringUTF("Llama3NoInput");

    if (!llmRuntime || !tokenizer) {
        LOGE("LLM not initialized");
        return nullptr;
    }

    const char *input = env->GetStringUTFChars(inputString, 0);
    std::string prompt(input);
    env->ReleaseStringUTFChars(inputString, input);

    LOGI("Performing streaming inference for input: %s", prompt.c_str());

    // Handle preformatting if needed
    if (!parsePromptTokens && preformatterName != nullptr) {
        const char *preformatterNameStr = env->GetStringUTFChars(preformatterName, 0);
        std::string preformatterNameCpp(preformatterNameStr);
        env->ReleaseStringUTFChars(preformatterName, preformatterNameStr);

        if (!preformatterNameCpp.empty()) {
            if (utils::addPreformatter(preformatterNameCpp, prompt)) {
                LOGI("Preformatted prompt with '%s'", preformatterNameCpp.c_str());
            } else {
                LOGE("Invalid preformatter: '%s'", preformatterNameCpp.c_str());
            }
        }
    }

    // Prepare for streaming
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");

    // Call llm_inference with a callback
    double promptTokPerSec, genTokPerSec;
    std::string fullResponse;
    std::vector<int> generatedTokens;

    auto tokenCallback = [&](const std::string& token) {
        jstring jToken = env->NewStringUTF(token.c_str());
        env->CallVoidMethod(callback, onTokenMethod, jToken);
        env->DeleteLocalRef(jToken);
    };

    std::tie(fullResponse, promptTokPerSec, genTokPerSec) =
            llm_streaming_inference(llmRuntime, prompt, tokenizer, maxResponse, parsePromptTokens, tokenCallback);

    // Create and return the InferenceResult object (same as before)
    jclass resultClass = env->FindClass("com/example/llmapp/data/model/InferenceResult");
    if (resultClass == nullptr) {
        LOGE("Failed to find InferenceResult class");
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(Ljava/lang/String;DD[I)V");
    if (constructor == nullptr) {
        LOGE("Failed to find InferenceResult constructor");
        return nullptr;
    }

    // Create the jintArray for generatedTokens
    jintArray jGeneratedTokens = env->NewIntArray(generatedTokens.size());
    if (jGeneratedTokens != nullptr) {
        env->SetIntArrayRegion(jGeneratedTokens, 0, generatedTokens.size(),
                               reinterpret_cast<const jint*>(generatedTokens.data()));
    } else {
        LOGE("Failed to create jintArray for generatedTokens");
        return nullptr;
    }

    jobject result = env->NewObject(resultClass, constructor,
                                    env->NewStringUTF(fullResponse.c_str()),
                                    (jdouble)promptTokPerSec,
                                    (jdouble)genTokPerSec,
                                    jGeneratedTokens);

    if (result == nullptr) {
        LOGE("Failed to create InferenceResult object");
        env->DeleteLocalRef(jGeneratedTokens);
        return nullptr;
    }

    // Clean up local references
    env->DeleteLocalRef(jGeneratedTokens);
    env->DeleteLocalRef(preformatterName);

    return result;
}

JNIEXPORT jobject JNICALL
Java_com_example_llmapp_jni_LlmNative_nativeGenResponse(JNIEnv *env, jobject /* this */,
                                                        jstring inputString, jint maxResponse,
                                                        jint firstInputToken) {
    if (!llmRuntime || !tokenizer) {
        LOGE("LLM not initialized");
        return nullptr;
    }

    const char *input = env->GetStringUTFChars(inputString, 0);
    std::string inputStr(input);
    env->ReleaseStringUTFChars(inputString, input);

    std::string fullResponse;
    double genTokPerSec;

    std::vector<mtk::Tokenizer::TokenType> generatedTokens = llm_gen_response(
            llmRuntime, tokenizer, maxResponse,
            static_cast<mtk::Tokenizer::TokenType>(firstInputToken),
            fullResponse, genTokPerSec);

    jintArray jGeneratedTokens = env->NewIntArray(generatedTokens.size());
    if (jGeneratedTokens == nullptr) {
        LOGE("Failed to create Java array for generated tokens");
        return nullptr;
    }
    env->SetIntArrayRegion(jGeneratedTokens, 0, generatedTokens.size(),
                           reinterpret_cast<jint *>(generatedTokens.data()));

    jclass resultClass = env->FindClass("com/example/llmapp/data/model/InferenceResult");
    if (resultClass == nullptr) {
        LOGE("Failed to find InferenceResult class");
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(Ljava/lang/String;DD[I)V");
    if (constructor == nullptr) {
        LOGE("Failed to find InferenceResult constructor");
        return nullptr;
    }

    jobject result = env->NewObject(resultClass, constructor,
                                    env->NewStringUTF(fullResponse.c_str()),
                                    0.0,
                                    genTokPerSec,
                                    jGeneratedTokens);

    if (result == nullptr) {
        LOGE("Failed to create InferenceResult object");
        return nullptr;
    }

    return result;
}

JNIEXPORT void JNICALL
Java_com_example_llmapp_jni_LlmNative_nativeReleaseLlm(JNIEnv *env, jobject /* this */) {
    if (llmRuntime) {
        llm_release(llmRuntime);
        llmRuntime = nullptr;
    }
    tokenizer.reset();
    LOGI("LLM resources released");
}

JNIEXPORT jboolean JNICALL
Java_com_example_llmapp_jni_LlmNative_nativeResetLlm(JNIEnv *env, jobject /* this */) {
    if (!llmRuntime) {
        LOGE("LLM not initialized");
        return JNI_FALSE;
    }

    llm_reset(llmRuntime);

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_llmapp_jni_LlmNative_nativeSwapModel(JNIEnv *env, jobject /* this */, jint tokenSize) {
    if (!llmRuntime) {
        LOGE("LLM not initialized");
        return JNI_FALSE;
    }

    llm_swap_model(llmRuntime, static_cast<size_t>(tokenSize));

    return JNI_TRUE;
}

}