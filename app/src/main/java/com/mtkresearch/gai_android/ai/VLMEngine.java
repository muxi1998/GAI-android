package com.mtkresearch.gai_android.ai;

import android.net.Uri;

import java.util.concurrent.CompletableFuture;

public interface VLMEngine {
    CompletableFuture<String> analyzeImage(Uri imageUri);
    CompletableFuture<Uri> generateImage(String prompt);
} 