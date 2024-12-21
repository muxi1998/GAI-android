package com.mtkresearch.gai_android;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.mtkresearch.gai_android.adapters.ChatMessageAdapter;
import com.mtkresearch.gai_android.ai.LLMEngine;
import com.mtkresearch.gai_android.ai.VLMEngine;
import com.mtkresearch.gai_android.ai.ASREngine;
import com.mtkresearch.gai_android.ai.TTSEngine;
import com.mtkresearch.gai_android.databinding.ActivityChatBinding;
import com.mtkresearch.gai_android.models.ChatMessage;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ChatActivity extends AppCompatActivity {
    private ActivityChatBinding binding;
    private ChatMessageAdapter adapter;
    private LLMEngine llmEngine;
    private VLMEngine vlmEngine;
    private ASREngine asrEngine;
    private TTSEngine ttsEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initializeAIEngines();
        setupRecyclerView();
        setupClickListeners();
    }

    private void initializeAIEngines() {
        // Initialize engines with default fallback responses
        llmEngine = new LLMEngine() {
            @Override
            public CompletableFuture<String> generateResponse(String prompt) {
                return CompletableFuture.completedFuture("I'm currently unavailable. Please try again later.");
            }

            @Override
            public void setContext(String context) {
                // Default implementation - no context handling
            }
        };
        
        vlmEngine = new VLMEngine() {
            @Override
            public CompletableFuture<String> analyzeImage(Uri imageUri) {
                return CompletableFuture.completedFuture("Image analysis is currently unavailable.");
            }
            
            @Override
            public CompletableFuture<Uri> generateImage(String prompt) {
                return CompletableFuture.completedFuture(null);
            }
        };
        
        asrEngine = new ASREngine() {
            @Override
            public void startListening(Consumer<String> callback) {
                callback.accept("Speech recognition is currently unavailable.");
            }
            
            @Override
            public void stopListening() {
                // No-op fallback
            }
            
            @Override
            public CompletableFuture<String> convertSpeechToText(File audioFile) {
                return CompletableFuture.completedFuture("Speech to text conversion is currently unavailable.");
            }
        };
        
        ttsEngine = new TTSEngine() {
            @Override
            public CompletableFuture<File> convertTextToSpeech(String text) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void speak(String text) {
                // Default implementation - no direct speech
            }

            @Override
            public void stop() {
                // Default implementation - nothing to stop
            }
        };
    }


    private void setupRecyclerView() {
        adapter = new ChatMessageAdapter();
        binding.recyclerView.setAdapter(adapter);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupClickListeners() {
        binding.sendButton.setOnClickListener(v -> {
            String message = binding.messageInput.getText().toString();
            if (!message.isEmpty()) {
                sendMessage(message);
            }
        });

        // Add new click listeners for voice and image features
        binding.voiceButton.setOnClickListener(v -> startVoiceInput());
        binding.imageButton.setOnClickListener(v -> handleImageInput());
    }

    private void startVoiceInput() {
        asrEngine.startListening(recognizedText -> {
            if (recognizedText != null) {
                binding.messageInput.setText(recognizedText);
            }
        });
    }

    private void handleImageInput() {
        // Example: Analyze an image from gallery or camera
        // You'll need to implement proper image picking logic
        Uri selectedImageUri = null; // Get this from image picker
        if (selectedImageUri != null) {
            vlmEngine.analyzeImage(selectedImageUri)
                .thenAccept(analysis -> runOnUiThread(() -> {
                    ChatMessage aiMessage = new ChatMessage(analysis, false);
                    adapter.addMessage(aiMessage);
                }));
        }
    }

    private void speakResponse(String text) {
        ttsEngine.convertTextToSpeech(text)
            .thenAccept(audioFile -> {
                // Play the audio file
                // Implement audio playback logic here
            });
    }

    private void sendMessage(String message) {
        // Add user message
        ChatMessage userMessage = new ChatMessage(message, true);
        adapter.addMessage(userMessage);
        binding.messageInput.setText("");

        // Generate AI response
        llmEngine.generateResponse(message)
            .thenAccept(response -> runOnUiThread(() -> {
                ChatMessage aiMessage = new ChatMessage(response, false);
                adapter.addMessage(aiMessage);
                speakResponse(response); // Add text-to-speech for AI responses
            }))
            .exceptionally(throwable -> {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + throwable.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
                return null;
            });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
} 