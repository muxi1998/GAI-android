package com.mtkresearch.gai_android.service;

import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class TTSEngineService extends BaseEngineService {
    private static final String TAG = "TTSEngineService";
    private TextToSpeech textToSpeech;
    private boolean isTextToSpeechInitialized = false;
    private static final int TTS_DATA_CHECK_CODE = 1000;
    private static final long INIT_TIMEOUT_MS = 20000; // 20 seconds timeout

    public class LocalBinder extends BaseEngineService.LocalBinder<TTSEngineService> { }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // First check if TTS engine is installed
        if (!isTTSEngineInstalled()) {
            Log.e(TAG, "No TTS engine installed");
            promptInstallTTSEngine();
            future.complete(false);
            return future;
        }

        // Then check and prepare TTS data
        prepareTTSData().thenAccept(prepared -> {
            if (!prepared) {
                future.complete(false);
                return;
            }

            // Initialize TTS after data is prepared
            initializeTTS(future);
        });
        
        return future;
    }

    private boolean isTTSEngineInstalled() {
        PackageManager pm = getApplicationContext().getPackageManager();
        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        List<ResolveInfo> list = pm.queryIntentActivities(checkIntent, 
            PackageManager.MATCH_DEFAULT_ONLY);
        return !list.isEmpty();
    }

    private void promptInstallTTSEngine() {
        Intent installIntent = new Intent();
        installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
        installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getApplicationContext().startActivity(installIntent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Could not launch TTS installation", e);
        }
    }

    private CompletableFuture<Boolean> prepareTTSData() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                // Check if language data is available
                int result = textToSpeech.isLanguageAvailable(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.d(TAG, "Language data missing, initiating download");
                    Intent installIntent = new Intent();
                    installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        getApplicationContext().startActivity(installIntent);
                        // We return false here as the user needs to complete the installation
                        future.complete(false);
                    } catch (ActivityNotFoundException e) {
                        Log.e(TAG, "Could not launch language data installation", e);
                        future.complete(false);
                    }
                } else {
                    Log.d(TAG, "Language data is available");
                    future.complete(true);
                }
            } else {
                Log.e(TAG, "TTS initialization failed in preparation phase");
                future.complete(false);
            }
            // Cleanup this temporary TTS instance
            textToSpeech.shutdown();
        });
        
        return future;
    }

    private void initializeTTS(CompletableFuture<Boolean> future) {
        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language still not available after preparation");
                    isTextToSpeechInitialized = false;
                    future.complete(false);
                } else {
                    textToSpeech.setPitch(1.0f);
                    textToSpeech.setSpeechRate(1.0f);
                    setupUtteranceProgressListener();
                    isTextToSpeechInitialized = true;
                    isInitialized = true;
                    Log.d(TAG, "TTS successfully initialized");
                    future.complete(true);
                }
            } else {
                Log.e(TAG, "Final TTS Initialization failed");
                isTextToSpeechInitialized = false;
                future.complete(false);
            }
        });

        // Add timeout for initialization
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!future.isDone()) {
                Log.e(TAG, "TTS initialization timeout");
                future.complete(false);
            }
        }, INIT_TIMEOUT_MS);
    }

    @Override
    public boolean isReady() {
        return isInitialized && isTextToSpeechInitialized;
    }

    public CompletableFuture<Boolean> speak(String text) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        if (!isReady()) {
            Log.e(TAG, "TTS not initialized");
            future.completeExceptionally(new IllegalStateException("TTS not initialized"));
            return future;
        }

        try {
            String utteranceId = "TTS_" + System.currentTimeMillis();
            int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
            
            if (result == TextToSpeech.SUCCESS) {
                Log.d(TAG, "Started speaking text");
                future.complete(true);
            } else {
                Log.e(TAG, "Failed to speak text");
                future.complete(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error speaking text", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }

    @Override
    public void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    private CompletableFuture<File> mtkConvertTextToSpeech(String text) {
        // MTK TTS implementation
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<File> openaiConvertTextToSpeech(String text) {
        // OpenAI TTS implementation
        return CompletableFuture.completedFuture(null);
    }

    private void setupUtteranceProgressListener() {
        if (textToSpeech != null) {
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    Log.d(TAG, "Started speaking: " + utteranceId);
                }

                @Override
                public void onDone(String utteranceId) {
                    Log.d(TAG, "Finished speaking: " + utteranceId);
                }

                @Override
                public void onError(String utteranceId) {
                    Log.e(TAG, "Error speaking: " + utteranceId);
                }

                @Override
                public void onStop(String utteranceId, boolean interrupted) {
                    Log.d(TAG, "Stopped speaking: " + utteranceId + ", interrupted: " + interrupted);
                }
            });
        } else {
            Log.e(TAG, "Cannot setup UtteranceProgressListener: TTS is null");
        }
    }
}