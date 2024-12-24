package com.mtkresearch.gai_android.service;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ASREngineService extends BaseEngineService {
    private static final String TAG = "ASREngineService";
    private static final String TEST_PHRASE = "test";
    private static final int RECOGNITION_TIMEOUT = 10000; // 10 seconds
    
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private String backend = "default";
    private Consumer<String> currentCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public class LocalBinder extends BaseEngineService.LocalBinder<ASREngineService> {
        @Override
        public ASREngineService getService() {
            return ASREngineService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        Log.d(TAG, "Starting ASR initialization sequence...");
        
        // Try backends in order: MTK -> Local -> Default
        initializeMTKASR()
            .thenCompose(mtkSuccess -> handleBackendInitialization("MTK", mtkSuccess))
            .thenCompose(success -> success ? CompletableFuture.completedFuture(true) 
                : initializeLocalASR().thenCompose(localSuccess -> 
                    handleBackendInitialization("Local", localSuccess)))
            .thenCompose(success -> success ? CompletableFuture.completedFuture(true)
                : initializeDefaultASR().thenCompose(defaultSuccess ->
                    handleBackendInitialization("Default", defaultSuccess)))
            .thenAccept(finalResult -> {
                isInitialized = finalResult;
                Log.d(TAG, String.format("ASR initialization complete. Result: %s using backend: %s",
                    finalResult ? "SUCCESS ✅" : "FAILED ❌", backend));
                future.complete(finalResult);
            });
        
        return future;
    }

    private CompletableFuture<Boolean> handleBackendInitialization(String backendType, boolean success) {
        if (success) {
            Log.d(TAG, "✅ " + backendType + " ASR initialized successfully");
            backend = backendType.toLowerCase();
            return testASREngine(backendType);
        }
        Log.d(TAG, "❌ " + backendType + " ASR failed");
        return CompletableFuture.completedFuture(false);
    }

    public void startListening(Consumer<String> callback) {
        if (!validateListeningPrerequisites(callback)) return;
        
        try {
            setupSpeechRecognizer();
            startRecognition();
            currentCallback = callback;
        } catch (Exception e) {
            Log.e(TAG, "Error starting speech recognition", e);
            if (callback != null) {
                callback.accept("Error: Failed to start speech recognition");
            }
        }
    }

    public void stopListening() {
        if (speechRecognizer != null && isListening) {
            speechRecognizer.stopListening();
            isListening = false;
        }
    }

    private boolean validateListeningPrerequisites(Consumer<String> callback) {
        if (!isInitialized) {
            notifyError(callback, "ASR not initialized");
            return false;
        }
        if (!checkPermission()) {
            notifyError(callback, "Missing audio permission");
            return false;
        }
        return true;
    }

    private void notifyError(Consumer<String> callback, String message) {
        Log.e(TAG, message);
        if (callback != null) {
            callback.accept("Error: " + message);
        }
    }

    private void setupSpeechRecognizer() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
        }
        speechRecognizer.setRecognitionListener(createRecognitionListener());
    }

    private void startRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        
        speechRecognizer.startListening(intent);
        Log.d(TAG, "Started listening with " + backend + " ASR");
    }

    private RecognitionListener createRecognitionListener() {
        return new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "Ready for speech");
                isListening = true;
                notifyCallback("Ready for speech...");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "Speech started");
            }

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "Speech ended");
                isListening = false;
            }

            @Override
            public void onError(int error) {
                String errorMessage = getErrorMessage(error);
                Log.e(TAG, "Speech recognition error: " + errorMessage);
                notifyCallback("Error: " + errorMessage);
                isListening = false;
            }

            @Override
            public void onResults(Bundle results) {
                processResults(results, false);
                isListening = false;
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                processResults(partialResults, true);
            }

            // Unused callbacks
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        };
    }

    private void processResults(Bundle results, boolean isPartial) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String result = matches.get(0);
            Log.d(TAG, (isPartial ? "Partial" : "Final") + " result: " + result);
            notifyCallback(isPartial ? "Partial: " + result : result);
        }
    }

    private void notifyCallback(String message) {
        if (currentCallback != null) {
            currentCallback.accept(message);
        }
    }

    private CompletableFuture<Boolean> testASREngine(String engineType) {
        Log.d(TAG, "Testing " + engineType + " ASR engine...");
        
        try {
            File testFile = prepareTestFile();
            return convertSpeechToText(testFile)
                .thenApply(result -> validateTestResult(result, engineType));
        } catch (Exception e) {
            Log.e(TAG, "❌ Error running " + engineType + " ASR test", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    private File prepareTestFile() throws IOException {
        File testDir = new File(getApplicationContext().getFilesDir(), "test_audio");
        if (!testDir.exists()) {
            testDir.mkdirs();
        }
        
        File testFile = new File(testDir, "test_asr.m4a");
        if (testFile.exists()) {
            testFile.delete();
        }
        copyAssetFile("test_audio/test_asr.m4a", testFile);
        return testFile;
    }

    private boolean validateTestResult(String result, String engineType) {
        boolean matches = result != null && result.toLowerCase().contains(TEST_PHRASE);
        Log.d(TAG, String.format("%s %s ASR Test! %s",
            matches ? "✅" : "❌",
            engineType,
            matches ? "Found \"test\" in: \"" + result + "\"" 
                   : "Could not find \"test\" in: \"" + result + "\""));
        return matches;
    }

    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Client side error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No match found";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Recognition service busy";
            case SpeechRecognizer.ERROR_SERVER:
                return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "No speech input";
            default:
                return "Unknown error: " + errorCode;
        }
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(getApplicationContext(),
                android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void copyAssetFile(String assetPath, File destFile) throws IOException {
        try (InputStream in = getApplicationContext().getAssets().open(assetPath);
             OutputStream out = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    // Placeholder methods for different backends
    private CompletableFuture<Boolean> initializeMTKASR() {
        return CompletableFuture.completedFuture(false);
    }

    private CompletableFuture<Boolean> initializeLocalASR() {
        return CompletableFuture.completedFuture(false);
    }

    private CompletableFuture<Boolean> initializeDefaultASR() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        try {
            if (!checkPermission()) {
                Log.e(TAG, "Missing RECORD_AUDIO permission");
                return CompletableFuture.completedFuture(false);
            }

            boolean isAvailable = SpeechRecognizer.isRecognitionAvailable(getApplicationContext());
            if (isAvailable) {
                setupSpeechRecognizer();
                Log.d(TAG, "Default ASR initialized successfully");
            } else {
                Log.e(TAG, "Speech recognition is not available on this device");
            }
            future.complete(isAvailable);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing speech recognizer", e);
            future.complete(false);
        }
        
        return future;
    }

    private CompletableFuture<String> convertSpeechToText(File audioFile) {
        CompletableFuture<String> future = new CompletableFuture<>();
        MediaPlayer mediaPlayer = new MediaPlayer();
        
        try {
            mediaPlayer.setDataSource(audioFile.getPath());
            mediaPlayer.prepare();
            
            // Keep track of MediaPlayer state
            final boolean[] isReleased = {false};
            
            // Set up completion listener for MediaPlayer
            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "Test audio playback completed");
                if (!isReleased[0]) {
                    mp.release();
                    isReleased[0] = true;
                }
            });
            
            setupSpeechRecognizer();
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.d(TAG, "Speech recognizer ready, starting playback");
                    if (!isReleased[0]) {
                        mediaPlayer.start();
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        Log.d(TAG, "Got test recognition result: " + matches.get(0));
                        future.complete(matches.get(0));
                    } else {
                        Log.d(TAG, "No recognition results");
                        future.complete(null);
                    }
                    
                    // Clean up MediaPlayer if still active
                    if (!isReleased[0]) {
                        mediaPlayer.release();
                        isReleased[0] = true;
                    }
                }

                @Override
                public void onError(int error) {
                    Log.e(TAG, "Recognition error during test: " + getErrorMessage(error));
                    future.complete(null);
                    
                    // Clean up MediaPlayer if still active
                    if (!isReleased[0]) {
                        mediaPlayer.release();
                        isReleased[0] = true;
                    }
                }

                @Override public void onBeginningOfSpeech() {
                    Log.d(TAG, "Speech began");
                }
                @Override public void onEndOfSpeech() {
                    Log.d(TAG, "Speech ended");
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });

            // Start recognition
            startRecognition();

            // Set a timeout
            mainHandler.postDelayed(() -> {
                if (!future.isDone()) {
                    Log.d(TAG, "Recognition test timed out");
                    future.complete(null);
                }
                // Clean up MediaPlayer if still active
                if (!isReleased[0]) {
                    try {
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.stop();
                        }
                        mediaPlayer.release();
                        isReleased[0] = true;
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Error stopping MediaPlayer", e);
                    }
                }
            }, RECOGNITION_TIMEOUT);

        } catch (Exception e) {
            Log.e(TAG, "Error during speech to text conversion", e);
            if (!future.isDone()) {
                future.complete(null);
            }
            mediaPlayer.release();
        }
        
        return future;
    }

    @Override
    public void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        super.onDestroy();
    }
} 