package com.mtkresearch.gai_android.ai;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public interface TTSEngine {
    CompletableFuture<File> convertTextToSpeech(String text);
    void speak(String text);
    void stop();
} 