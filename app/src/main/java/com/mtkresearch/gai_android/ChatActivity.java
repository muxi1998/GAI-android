package com.mtkresearch.gai_android;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.ContextThemeWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
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
import java.lang.reflect.Method;
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
        setupInputMode();
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
        // Prevent click events from propagating through the input container
        binding.inputContainer.setOnClickListener(v -> {
            // Consume the click event
        });

        // Attach button listeners
        View.OnClickListener attachClickListener = v -> showAttachmentOptions();
        binding.attachButton.setOnClickListener(attachClickListener);
        binding.attachButtonExpanded.setOnClickListener(attachClickListener);
        
        // Voice button listeners
        View.OnClickListener voiceClickListener = v -> startVoiceInput();
        binding.voiceButton.setOnClickListener(voiceClickListener);
        binding.voiceButtonExpanded.setOnClickListener(voiceClickListener);
        
        // Send button listeners
        View.OnClickListener sendClickListener = v -> {
            String message = binding.expandedInput.getVisibility() == View.VISIBLE ?
                binding.messageInputExpanded.getText().toString() :
                binding.messageInput.getText().toString();
                
            if (!message.isEmpty()) {
                adapter.addMessage(new ChatMessage(message, true));
                binding.messageInput.setText("");
                binding.messageInputExpanded.setText("");
                
                collapseInputSection();
                updateSendButton(false);
                
                llmEngine.generateResponse(message)
                    .thenAccept(response -> runOnUiThread(() -> {
                        adapter.addMessage(new ChatMessage(response, false));
                    }));
            }
        };
        
        binding.sendButton.setOnClickListener(sendClickListener);
        binding.sendButtonExpanded.setOnClickListener(sendClickListener);

        // Initialize send button states
        updateSendButton(false);
    }

    private void setupInputMode() {
        // Add touch listener to root view
        View.OnTouchListener outsideTouchListener = (v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                if (binding.expandedInput.getVisibility() == View.VISIBLE) {
                    String currentText = binding.messageInputExpanded.getText().toString();
                    if (currentText.isEmpty()) {
                        binding.messageInputExpanded.clearFocus();
                        collapseInputSection();
                        updateSendButton(false);
                    }
                }
            }
            return false;
        };

        // Set touch listeners for areas outside input
        binding.getRoot().setOnTouchListener(outsideTouchListener);
        binding.recyclerView.setOnTouchListener(outsideTouchListener);

        // Prevent touch events from being consumed by the input container
        binding.inputContainer.setOnTouchListener((v, event) -> {
            v.performClick();
            return false;
        });

        binding.messageInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                expandInputSection();
            }
        });

        binding.messageInputExpanded.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String currentText = binding.messageInputExpanded.getText().toString();
                if (currentText.isEmpty()) {
                    collapseInputSection();
                    updateSendButton(false);
                }
            }
        });

        // Sync text between both input fields and update button states
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButton(s.length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        binding.messageInput.addTextChangedListener(textWatcher);
        binding.messageInputExpanded.addTextChangedListener(textWatcher);
    }

    private void expandInputSection() {
        binding.collapsedInput.setVisibility(View.GONE);
        binding.expandedInput.setVisibility(View.VISIBLE);
        binding.messageInputExpanded.requestFocus();
        binding.messageInputExpanded.setText(binding.messageInput.getText());
        
        // Scroll RecyclerView to bottom when keyboard appears
        binding.recyclerView.postDelayed(() -> 
            binding.recyclerView.smoothScrollToPosition(adapter.getItemCount()), 100);
    }

    private void collapseInputSection() {
        binding.expandedInput.setVisibility(View.GONE);
        binding.collapsedInput.setVisibility(View.VISIBLE);
    }

    private void updateSendButton(boolean hasText) {
        int icon = hasText ? R.drawable.ic_send : R.drawable.ic_audio_wave;
        binding.sendButton.setImageResource(icon);
        binding.sendButtonExpanded.setImageResource(icon);
    }

    private void showAttachmentOptions() {
        PopupMenu popup = new PopupMenu(new ContextThemeWrapper(this, R.style.PopupMenuStyle), 
            binding.expandedInput.getVisibility() == View.VISIBLE ? 
            binding.attachButtonExpanded : binding.attachButton);
        
        popup.getMenu().add(0, 1, 0, "Attach Photos").setIcon(R.drawable.ic_gallery);
        popup.getMenu().add(0, 2, 0, "Take Photo").setIcon(R.drawable.ic_camera);
        popup.getMenu().add(0, 3, 0, "Attach Files").setIcon(R.drawable.ic_folder);

        // Force showing icons
        try {
            Method method = popup.getMenu().getClass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
            method.setAccessible(true);
            method.invoke(popup.getMenu(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    handleImageInput();
                    break;
                case 2:
                    handleCameraInput();
                    break;
                case 3:
                    handleFileInput();
                    break;
            }
            return true;
        });

        popup.show();
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

    private void handleCameraInput() {
        // Implement camera capture
    }

    private void handleFileInput() {
        // Implement file picker
    }

    private void speakResponse(String text) {
        ttsEngine.convertTextToSpeech(text)
            .thenAccept(audioFile -> {
                // Play the audio file
                // Implement audio playback logic here
            });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}