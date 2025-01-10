package com.mtkresearch.gai_android.service;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import com.executorch.ETImage;

public class VLMEngineService extends BaseEngineService {
    static {
        boolean loaded = false;
        try {
            System.loadLibrary("vlm_jni");
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.e("VLMEngineService", "Failed to load native libraries", e);
        }
        NATIVE_LIB_LOADED = loaded;
    }

    private static final boolean NATIVE_LIB_LOADED;

    private static final String TAG = "VLMEngineService";

    public class LocalBinder extends BaseEngineService.LocalBinder<VLMEngineService> { }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
//        testVlm();

        if (!NATIVE_LIB_LOADED) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return initializeLocalBackend();
            } catch (Exception e) {
                Log.e(TAG, "Initialization failed", e);
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

            // Add file existence check
            if (!new File(modelPath).exists()) {
                Log.e(TAG, "Model file not found: " + modelPath);
                return false;
            }
            if (!new File(tokenizerPath).exists()) {
                Log.e(TAG, "Tokenizer file not found: " + tokenizerPath);
                return false;
            }

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

    private String localCpuAnalyzeImage(Uri imageUri, String prompt) {
        try {
            // Create ETImage from Uri
            ETImage image = new ETImage(getContentResolver(), imageUri);
            
            // The image is already resized in ETImage class
            return nativeAnalyzeImage(image, prompt);
        } catch (Exception e) {
            Log.e(TAG, "Failed to analyze image", e);
            return "Error: " + e.getMessage();
        }
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
    private native String nativeAnalyzeImage(ETImage image, String prompt);
    private native void nativeReleaseVlm();

    public boolean testVlm() {
        try {
            String modelPath = "/data/local/tmp/llama/llava.pte";
            String tokenizerPath = "/data/local/tmp/llama/tokenizer.bin";
            String imagePath = "/data/local/tmp/llama/dog.jpg";
            
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                Log.e(TAG, "Image file not found: " + imagePath);
                return false;
            }
            
            // Create ETImage from file path
            ETImage image = new ETImage(getContentResolver(), Uri.fromFile(imageFile));
            
            // Call native method with ETImage data
            return nativeTestVlm(
                modelPath,
                tokenizerPath,
                image.getBytes(),
                image.getWidth(),
                image.getHeight()
            );
            
        } catch (Exception e) {
            Log.e(TAG, "VLM test failed", e);
            return false;
        }
    }

    private native boolean nativeTestVlm(String modelPath, String tokenizerPath, 
                                       byte[] imageData, int width, int height);
}