package com.mtkresearch.gai_android.ai;

import java.util.concurrent.CompletableFuture;

public interface LLMEngine {
    CompletableFuture<String> generateResponse(String prompt);
    void setContext(String context);
} 