package com.mtkresearch.gai_android.ai;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface ASREngine {
    CompletableFuture<String> convertSpeechToText(File audioFile);
    void startListening(Consumer<String> callback);
    void stopListening();
} 