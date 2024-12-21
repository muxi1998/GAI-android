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
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.mtkresearch.gai_android.adapters.AudioListAdapter;
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
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.mtkresearch.gai_android.audio.AudioRecorder;

import java.io.IOException;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class ChatActivity extends AppCompatActivity {
    private ActivityChatBinding binding;
    private ChatMessageAdapter adapter;
    private LLMEngine llmEngine;
    private VLMEngine vlmEngine;
    private ASREngine asrEngine;
    private TTSEngine ttsEngine;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private Runnable visualizationRunnable;
    private AudioRecorder audioRecorder;
    private File recordingFile;
    private static final int PERMISSION_REQUEST_CODE = 123;
    private MediaPlayer currentMediaPlayer;
    private AudioListAdapter audioListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        audioRecorder = new AudioRecorder(this);

        initializeAIEngines();
        setupRecyclerView();
        setupClickListeners();
        setupInputMode();
    }

    private void initializeAIEngines() {
        String backend = "mock"; // or get from settings
        
        llmEngine = new LLMEngine(this, backend);
        asrEngine = new ASREngine(this, backend);
        vlmEngine = new VLMEngine(this, backend);
        ttsEngine = new TTSEngine(this, backend);
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
        View.OnClickListener voiceClickListener = v -> startRecording();
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

        // Recording controls
        binding.voiceButton.setOnClickListener(v -> startRecording());
        binding.voiceButtonExpanded.setOnClickListener(v -> startRecording());
        
        binding.recordingInput.cancelRecordingButton.setOnClickListener(v -> {
            stopRecording(false);
            asrEngine.stopListening();
        });
        
        binding.recordingInput.finishRecordingButton.setOnClickListener(v -> {
            asrEngine.stopListening();
            stopRecording(true);
        });

        // Add long click listeners for voice buttons
        View.OnLongClickListener voiceLongClickListener = v -> {
            showAudioList();
            return true;
        };
        binding.voiceButton.setOnLongClickListener(voiceLongClickListener);
        binding.voiceButtonExpanded.setOnLongClickListener(voiceLongClickListener);
    }

    private void setupInputMode() {
        View.OnTouchListener outsideTouchListener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
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

    private void startRecording() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
            return;
        }

        try {
            File outputDir = new File(getFilesDir(), "recordings");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            recordingFile = new File(outputDir, "recording_" + System.currentTimeMillis() + ".m4a");
            
            audioRecorder.startRecording(recordingFile);
            
            binding.collapsedInput.setVisibility(View.GONE);
            binding.expandedInput.setVisibility(View.GONE);
            binding.recordingInput.getRoot().setVisibility(View.VISIBLE);
            
            startRecordingTimer();
            startAudioVisualization();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording(boolean shouldSave) {
        audioRecorder.stopRecording();
        binding.recordingInput.getRoot().setVisibility(View.GONE);
        binding.collapsedInput.setVisibility(View.VISIBLE);
        stopRecordingTimer();
        
        if (shouldSave && recordingFile != null && recordingFile.exists()) {
            asrEngine.processRecordedFile(recordingFile, recognizedText -> {
                if (recognizedText != null) {
                    runOnUiThread(() -> {
                        binding.messageInput.setText(recognizedText);
                        binding.messageInputExpanded.setText(recognizedText);
                        updateSendButton(true);
                    });
                }
            });
        } else if (recordingFile != null && recordingFile.exists()) {
            recordingFile.delete(); // Delete the file if we don't want to save it
        }
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

    private void startRecordingTimer() {
        timerRunnable = new Runnable() {
            int seconds = 0;
            @Override
            public void run() {
                if (binding.recordingInput.getRoot().getVisibility() == View.VISIBLE) {
                    int minutes = seconds / 60;
                    int secs = seconds % 60;
                    binding.recordingInput.recordingTimer.setText(String.format("%d:%02d", minutes, secs));
                    seconds++;
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.postDelayed(timerRunnable, 1000);
    }

    private void startAudioVisualization() {
        visualizationRunnable = new Runnable() {
            @Override
            public void run() {
                if (binding.recordingInput.getRoot().getVisibility() == View.VISIBLE) {
                    int maxAmplitude = audioRecorder.getMaxAmplitude();
                    // Convert to 0-1 range with log scale
                    float amplitude = maxAmplitude > 0 
                        ? (float) (1.0 / Math.log(32768) * Math.log(maxAmplitude)) 
                        : 0f;
                    binding.recordingInput.audioWaveView.updateAmplitude(amplitude);
                    handler.postDelayed(this, 100);
                }
            }
        };
        handler.postDelayed(visualizationRunnable, 100);
    }

    private void stopRecordingTimer() {
        if (timerRunnable != null) {
            handler.removeCallbacks(timerRunnable);
        }
        if (visualizationRunnable != null) {
            handler.removeCallbacks(visualizationRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        audioRecorder.stopRecording();
        binding = null;
        stopRecordingTimer();
        if (asrEngine != null) {
            asrEngine.stopListening();
        }
        if (currentMediaPlayer != null) {
            currentMediaPlayer.release();
            currentMediaPlayer = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(this, "Microphone permission is required for voice input", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showAudioList() {
        File recordingsDir = new File(getFilesDir(), "recordings");
        if (!recordingsDir.exists() || !recordingsDir.isDirectory()) {
            Toast.makeText(this, "No recordings found", Toast.LENGTH_SHORT).show();
            return;
        }

        File[] files = recordingsDir.listFiles((dir, name) -> name.endsWith(".m4a"));
        if (files == null || files.length == 0) {
            Toast.makeText(this, "No recordings found", Toast.LENGTH_SHORT).show();
            return;
        }

        Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        List<File> audioFiles = new ArrayList<>(Arrays.asList(files));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_audio_list, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.audioListRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        audioListAdapter = new AudioListAdapter(audioFiles, new AudioListAdapter.OnAudioActionListener() {
            @Override
            public void onReplayClick(File file) {
                playAudioFile(file);
            }

            @Override
            public void onDeleteClick(File file) {
                if (file.delete()) {
                    Toast.makeText(ChatActivity.this, "Recording deleted", Toast.LENGTH_SHORT).show();
                }
            }
        });
        recyclerView.setAdapter(audioListAdapter);

        AlertDialog dialog = builder.setView(dialogView)
                .setTitle("Recorded Audio Files")
                .setNegativeButton("Close", null)
                .create();

        dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        dialog.show();
    }

    private void playAudioFile(File file) {
        if (currentMediaPlayer != null) {
            currentMediaPlayer.release();
            currentMediaPlayer = null;
        }

        currentMediaPlayer = new MediaPlayer();
        try {
            currentMediaPlayer.setDataSource(file.getAbsolutePath());
            currentMediaPlayer.prepare();
            currentMediaPlayer.start();
            
            currentMediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                currentMediaPlayer = null;
                if (audioListAdapter != null) {
                    audioListAdapter.onPlaybackCompleted();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to play audio", Toast.LENGTH_SHORT).show();
            if (audioListAdapter != null) {
                audioListAdapter.onPlaybackCompleted();
            }
        }
    }
}