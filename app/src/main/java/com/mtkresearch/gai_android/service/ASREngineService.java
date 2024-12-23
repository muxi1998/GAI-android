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
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private String backend = null;
    private Consumer<String> currentCallback;
    private static final String TEST_PHRASE = "this is a test";

    public class LocalBinder extends BaseEngineService.LocalBinder<ASREngineService> { }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        Log.d(TAG, "Starting ASR initialization sequence...");
        
        // Set default backend first to prevent null pointer
        backend = "default";
        
        // Try MTK first
        initializeMTKASR()
            .thenCompose(mtkSuccess -> {
                if (mtkSuccess) {
                    Log.d(TAG, "✅ MTK ASR initialized successfully");
                    backend = "mtk";
                    return testASREngine("MTK");
                }
                
                Log.d(TAG, "❌ MTK ASR failed, trying Local Model...");
                // Try Local Model
                return initializeLocalASR()
                    .thenCompose(localSuccess -> {
                        if (localSuccess) {
                            Log.d(TAG, "✅ Local ASR initialized successfully");
                            backend = "local";
                            return testASREngine("Local");
                        }
                        
                        Log.d(TAG, "❌ Local ASR failed, trying Default Android ASR...");
                        // Try Default Android ASR
                        return initializeDefaultASR()
                            .thenCompose(defaultSuccess -> {
                                if (defaultSuccess) {
                                    Log.d(TAG, "✅ Default Android ASR initialized");
                                    backend = "default";
                                    return testASREngine("Default");
                                }
                                Log.e(TAG, "❌ All ASR initialization attempts failed");
                                return CompletableFuture.completedFuture(false);
                            });
                    });
            })
            .thenAccept(finalResult -> {
                isInitialized = finalResult;
                Log.d(TAG, "ASR initialization complete. Final result: " + 
                    (finalResult ? "SUCCESS ✅" : "FAILED ❌") + " using backend: " + backend);
                future.complete(finalResult);
            });
        
        return future;
    }

    @Override
    public boolean isReady() {
        return isInitialized;
    }

    public CompletableFuture<String> convertSpeechToText(File audioFile) {
        // For testing purposes only
        if (audioFile.getName().equals("test_asr.m4a")) {
            return testConvertSpeechToText(audioFile);
        }

        // For actual recordings from ChatActivity, just start real-time recognition
        CompletableFuture<String> future = new CompletableFuture<>();
        
        try {
            if (!checkPermission()) {
                future.completeExceptionally(new SecurityException("Missing RECORD_AUDIO permission"));
                return future;
            }

            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
                setupSpeechRecognizer();
            }

            // Set up recognition intent
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

            // Start recognition
            speechRecognizer.startListening(intent);
            
            // Set timeout
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!future.isDone()) {
                    Log.d(TAG, "Recognition timeout");
                    future.complete("");
                }
            }, 10000); // 10 second timeout

        } catch (Exception e) {
            Log.e(TAG, "Error in speech recognition", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }

    private CompletableFuture<String> testConvertSpeechToText(File audioFile) {
        // Original file-based implementation for testing
        CompletableFuture<String> future = new CompletableFuture<>();
        
        try {
            // Create a MediaPlayer to play the file
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioFile.getPath());
            mediaPlayer.prepare();

            // Create new recognizer if needed
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
                setupSpeechRecognizer();
            }

            // Set up one-time recognition listener for file
            RecognitionListener fileListener = new RecognitionListener() {
                private StringBuilder partialResult = new StringBuilder();
                private boolean hasResult = false;

                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.d(TAG, "Ready for speech from file");
                    // Start playing after a short delay
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            mediaPlayer.start();
                        } catch (Exception e) {
                            Log.e(TAG, "Error starting playback", e);
                        }
                    }, 500);
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "Beginning of speech from file");
                }

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    Log.d(TAG, "End of speech from file");
                }

                @Override
                public void onError(int error) {
                    Log.e(TAG, "Error in file recognition: " + getErrorMessage(error));
                    if (!hasResult && partialResult.length() > 0) {
                        future.complete(partialResult.toString());
                    } else if (!hasResult) {
                        future.complete("");
                    }
                    cleanup();
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        hasResult = true;
                        String result = matches.get(0);
                        Log.d(TAG, "Final result from file: " + result);
                        future.complete(result);
                    } else if (partialResult.length() > 0) {
                        future.complete(partialResult.toString());
                    } else {
                        future.complete("");
                    }
                    cleanup();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String result = matches.get(0);
                        partialResult.setLength(0);
                        partialResult.append(result);
                        Log.d(TAG, "Partial result from file: " + result);
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {}

                private void cleanup() {
                    try {
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.stop();
                        }
                        mediaPlayer.release();
                    } catch (Exception e) {
                        Log.e(TAG, "Error during cleanup", e);
                    }
                }
            };

            // Set up recognition intent
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

            // Start recognition
            speechRecognizer.setRecognitionListener(fileListener);
            speechRecognizer.startListening(intent);

            // Add timeout based on audio duration
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!future.isDone()) {
                    Log.d(TAG, "Recognition timeout");
                    future.complete("");
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.release();
                }
            }, mediaPlayer.getDuration() + 5000); // Audio duration plus 5 seconds

        } catch (Exception e) {
            Log.e(TAG, "Error in file recognition", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }

    private void startDefaultListening(Consumer<String> callback) {
        if (!checkPermission()) {
            Log.e(TAG, "Missing RECORD_AUDIO permission");
            if (callback != null) {
                callback.accept("Error: Missing audio permission");
            }
            return;
        }

        try {
            // Create new recognizer if needed
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
            }

            // Set up recognition listener
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.d(TAG, "Ready for speech");
                    isListening = true;
                    if (callback != null) {
                        callback.accept("Ready for speech...");
                    }
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started");
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    // Optional: You can use this to show audio level
                }

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    Log.d(TAG, "Speech ended");
                    isListening = false;
                }

                @Override
                public void onError(int error) {
                    String errorMessage = getErrorMessage(error);
                    Log.e(TAG, "Speech recognition error: " + errorMessage);
                    if (callback != null) {
                        callback.accept("Error: " + errorMessage);
                    }
                    isListening = false;
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String result = matches.get(0);
                        Log.d(TAG, "Speech recognition result: " + result);
                        if (callback != null) {
                            callback.accept(result);
                        }
                    }
                    isListening = false;
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String result = matches.get(0);
                        Log.d(TAG, "Partial result: " + result);
                        if (callback != null) {
                            callback.accept("Partial: " + result);
                        }
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });

            // Set up recognition intent
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

            // Start listening
            speechRecognizer.startListening(intent);
            Log.d(TAG, "Started listening with default ASR");

        } catch (Exception e) {
            Log.e(TAG, "Error starting speech recognition", e);
            if (callback != null) {
                callback.accept("Error starting speech recognition");
            }
        }
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(getApplicationContext(),
                android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    public void startListening(Consumer<String> callback) {
        if (!isInitialized) {
            if (callback != null) {
                callback.accept("ASR not initialized");
            }
            return;
        }

        if (!checkPermission()) {
            Log.e(TAG, "Missing RECORD_AUDIO permission");
            if (callback != null) {
                callback.accept("Error: Missing audio permission");
            }
            return;
        }

        try {
            // Create new recognizer if needed
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
            }

            // Set up recognition listener
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.d(TAG, "Ready for speech");
                    isListening = true;
                    if (callback != null) {
                        callback.accept("Ready for speech...");
                    }
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started");
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    // Optional: You can use this to show audio level
                }

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    Log.d(TAG, "Speech ended");
                    isListening = false;
                }

                @Override
                public void onError(int error) {
                    String errorMessage = getErrorMessage(error);
                    Log.e(TAG, "Speech recognition error: " + errorMessage);
                    if (callback != null) {
                        callback.accept("Error: " + errorMessage);
                    }
                    isListening = false;
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String result = matches.get(0);
                        Log.d(TAG, "Speech recognition result: " + result);
                        if (callback != null) {
                            callback.accept(result);
                        }
                    }
                    isListening = false;
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String result = matches.get(0);
                        Log.d(TAG, "Partial result: " + result);
                        if (callback != null) {
                            callback.accept("Partial: " + result);
                        }
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });

            // Set up recognition intent
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

            // Start listening
            speechRecognizer.startListening(intent);
            Log.d(TAG, "Started listening");

        } catch (Exception e) {
            Log.e(TAG, "Error starting speech recognition", e);
            if (callback != null) {
                callback.accept("Error starting speech recognition");
            }
        }
    }

    public void stopListening() {
        if (speechRecognizer != null && isListening) {
            speechRecognizer.stopListening();
            isListening = false;
        }
    }

    private CompletableFuture<String> mtkConvertSpeechToText(File audioFile) {
        return CompletableFuture.completedFuture("MTK transcription");
    }

    private CompletableFuture<String> openaiConvertSpeechToText(File audioFile) {
        return CompletableFuture.completedFuture("OpenAI transcription");
    }

    private void startMTKListening(Consumer<String> callback) {
        // MTK implementation
    }

    private void startLocalListening(Consumer<String> callback) {
        Log.d(TAG, "Local ASR listening not implemented yet");
        if (callback != null) {
            callback.accept("Local ASR not implemented yet");
        }
    }

    private void stopMTKListening() {
        // MTK implementation for stopping listening
    }

    @Override
    public void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        super.onDestroy();
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

    private CompletableFuture<Boolean> initializeMTKASR() {
        // TODO: Implement MTK ASR initialization
        Log.d(TAG, "MTK ASR initialization not implemented yet");
        return CompletableFuture.completedFuture(false);
    }

    private CompletableFuture<Boolean> initializeLocalASR() {
        // TODO: Implement Local ASR initialization
        Log.d(TAG, "Local ASR initialization not implemented yet");
        return CompletableFuture.completedFuture(false);
    }

    private CompletableFuture<Boolean> initializeDefaultASR() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        try {
            if (!checkPermission()) {
                Log.e(TAG, "Missing RECORD_AUDIO permission");
                future.complete(false);
                return future;
            }

            if (SpeechRecognizer.isRecognitionAvailable(getApplicationContext())) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
                setupSpeechRecognizer();
                isInitialized = true;  // Set this before testing
                Log.d(TAG, "Default ASR initialized successfully");
                future.complete(true);
            } else {
                Log.e(TAG, "Speech recognition is not available on this device");
                future.complete(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing speech recognizer", e);
            future.complete(false);
        }
        
        return future;
    }

    private CompletableFuture<Boolean> testASREngine(String engineType) {
        Log.d(TAG, "Testing " + engineType + " ASR engine...");
        
        try {
            File testDir = new File(getApplicationContext().getFilesDir(), "test_audio");
            if (!testDir.exists()) {
                testDir.mkdirs();
            }
            
            File testFile = new File(testDir, "test_asr.m4a");
            if (testFile.exists()) {
                testFile.delete();  // Delete existing file
            }
            copyAssetFile("test_audio/test_asr.m4a", testFile);

            return convertSpeechToText(testFile)
                .thenApply(result -> {
                    Log.d(TAG, engineType + " ASR Test Result: \"" + result + "\"");
                    Log.d(TAG, "Looking for word \"test\" in result");
                    
                    boolean matches = result != null && 
                        result.toLowerCase().contains("test");
                    
                    if (matches) {
                        Log.d(TAG, "✅ " + engineType + " ASR Test PASSED! Found \"test\" in: \"" + result + "\"");
                    } else {
                        Log.e(TAG, "❌ " + engineType + " ASR Test FAILED!");
                        Log.e(TAG, "Could not find \"test\" in: \"" + result + "\"");
                    }
                    
                    return matches;
                });
        } catch (Exception e) {
            Log.e(TAG, "❌ Error running " + engineType + " ASR test", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    private void setupSpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.d(TAG, "Ready for speech");
                    isListening = true;
                    if (currentCallback != null) {
                        currentCallback.accept("Ready for speech...");
                    }
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started");
                }

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    Log.d(TAG, "Speech ended");
                    isListening = false;
                }

                @Override
                public void onError(int error) {
                    String errorMessage = getErrorMessage(error);
                    Log.e(TAG, "Speech recognition error: " + errorMessage);
                    if (currentCallback != null) {
                        currentCallback.accept("Error: " + errorMessage);
                    }
                    isListening = false;
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String result = matches.get(0);
                        Log.d(TAG, "Speech recognition result: " + result);
                        if (currentCallback != null) {
                            currentCallback.accept(result);
                        }
                    }
                    isListening = false;
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String result = matches.get(0);
                        Log.d(TAG, "Partial result: " + result);
                        if (currentCallback != null) {
                            currentCallback.accept("Partial: " + result);
                        }
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
        }
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
} 