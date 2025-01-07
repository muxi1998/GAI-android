package com.mtkresearch.gai_android.service;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

public class VLMEngineService extends BaseEngineService {
    static {
        System.loadLibrary("llava_runner");
        System.loadLibrary("vlm_jni");
    }

    private static final String TAG = "VLMEngineService";

    public class LocalBinder extends BaseEngineService.LocalBinder<VLMEngineService> { }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                switch (backend) {
                    case "mtk":
                        return initializeMtkBackend();
                    case "local":
                    default:
                        return initializeLocalBackend();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize VLM", e);
                return false;
            }
        });
    }

    private boolean initializeMtkBackend() {
        // TODO: Implement MTK backend initialization
        Log.d(TAG, "MTK backend initialization not yet implemented");
        return false;
    }

    private boolean initializeLocalBackend() {
        try {
            String modelPath = "/data/local/tmp/llama/llava.pte";
            String tokenizerPath = "/data/local/tmp/llama/tokenizer.bin";

            boolean success = nativeInitVlm(modelPath, tokenizerPath);
            if (success) {
                isInitialized = true;
                Log.d(TAG, "Local VLM initialization successful");
            } else {
                Log.e(TAG, "Local VLM initialization failed");
            }
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize local VLM", e);
            return false;
        }
    }

    @Override
    public boolean isReady() {
        return isInitialized;
    }

    public CompletableFuture<String> analyzeImage(Uri imageUri, String userPrompt) {
        if (!isInitialized) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Engine not initialized"));
            return future;
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                switch (backend) {
                    case "mtk":
                        return mtkAnalyzeImage(imageUri, userPrompt);
                    case "local":
                    default:
                        return localCpuAnalyzeImage(imageUri, userPrompt);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to analyze image", e);
                return "Error analyzing image: " + e.getMessage();
            }
        });
    }

    private String mtkAnalyzeImage(Uri imageUri, String userPrompt) {
        // TODO: Implement MTK backend image analysis
        Log.d(TAG, "MTK backend image analysis not yet implemented");
        return "MTK backend not yet implemented";
    }

    private String localCpuAnalyzeImage(Uri imageUri, String userPrompt) throws Exception {
        InputStream inputStream = getContentResolver().openInputStream(imageUri);
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
        if (bitmap == null) {
            throw new Exception("Failed to load image");
        }

        String response = nativeAnalyzeImage(bitmap, userPrompt);
        bitmap.recycle();
        return response;
    }

    public void releaseResources() {
        if (isInitialized) {
            switch (backend) {
                case "mtk":
                    releaseMtkResources();
                    break;
                case "local":
                default:
                    releaseLocalResources();
                    break;
            }
            isInitialized = false;
        }
    }

    private void releaseMtkResources() {
        // TODO: Implement MTK resource cleanup
        Log.d(TAG, "MTK resource cleanup not yet implemented");
    }

    private void releaseLocalResources() {
        nativeReleaseVlm();
    }

    // Native methods for local CPU backend
    private native boolean nativeInitVlm(String modelPath, String tokenizerPath);
    private native String nativeAnalyzeImage(Bitmap bitmap, String prompt);
    private native void nativeReleaseVlm();
}