package com.mtkresearch.gai_android;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

import com.mtkresearch.gai_android.ChatActivity;
import com.mtkresearch.gai_android.R;
import com.mtkresearch.gai_android.ai.ASREngine;
import com.mtkresearch.gai_android.ai.LLMEngine;
import com.mtkresearch.gai_android.ai.TTSEngine;
import com.mtkresearch.gai_android.ai.VLMEngine;
import com.mtkresearch.gai_android.databinding.ActivityMainBinding;

import java.util.concurrent.CompletableFuture;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    
    // Static engines that can be accessed by other activities
    public static LLMEngine llmEngine;
    public static VLMEngine vlmEngine;
    public static ASREngine asrEngine;
    public static TTSEngine ttsEngine;
    
    // Backend type can be "mtk", "openai", "mock", etc.
    private final String backend = "mock"; // TODO: Get from settings
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        displayDeviceInfo();
        initializeEngines();
        setupClickListeners();
    }

    private void initializeEngines() {
        // Start initialization in background
        CompletableFuture.runAsync(() -> {
            initializeLLMEngine();
            initializeVLMEngine();
            initializeASREngine();
            initializeTTSEngine();
        });
    }

    private void initializeLLMEngine() {
        updateEngineStatus(binding.llmStatusIndicator, EngineStatus.INITIALIZING);
        try {
            llmEngine = new LLMEngine(this, backend);
            llmEngine.initialize()
                .thenAccept(success -> {
                    updateEngineStatus(binding.llmStatusIndicator, 
                        success ? EngineStatus.READY : EngineStatus.ERROR);
                    checkAllEnginesReady();
                });
        } catch (Exception e) {
            updateEngineStatus(binding.llmStatusIndicator, EngineStatus.ERROR);
        }
    }

    private void initializeVLMEngine() {
        updateEngineStatus(binding.vlmStatusIndicator, EngineStatus.INITIALIZING);
        try {
            vlmEngine = new VLMEngine(this, backend);
            vlmEngine.initialize()
                .thenAccept(success -> {
                    updateEngineStatus(binding.vlmStatusIndicator, 
                        success ? EngineStatus.READY : EngineStatus.ERROR);
                    checkAllEnginesReady();
                });
        } catch (Exception e) {
            updateEngineStatus(binding.vlmStatusIndicator, EngineStatus.ERROR);
        }
    }

    private void initializeASREngine() {
        updateEngineStatus(binding.asrStatusIndicator, EngineStatus.INITIALIZING);
        try {
            asrEngine = new ASREngine(this, backend);
            asrEngine.initialize()
                .thenAccept(success -> {
                    updateEngineStatus(binding.asrStatusIndicator, 
                        success ? EngineStatus.READY : EngineStatus.ERROR);
                    checkAllEnginesReady();
                });
        } catch (Exception e) {
            updateEngineStatus(binding.asrStatusIndicator, EngineStatus.ERROR);
        }
    }

    private void initializeTTSEngine() {
        updateEngineStatus(binding.ttsStatusIndicator, EngineStatus.INITIALIZING);
        try {
            ttsEngine = new TTSEngine(this, backend);
            ttsEngine.initialize()
                .thenAccept(success -> {
                    updateEngineStatus(binding.ttsStatusIndicator, 
                        success ? EngineStatus.READY : EngineStatus.ERROR);
                    checkAllEnginesReady();
                });
        } catch (Exception e) {
            updateEngineStatus(binding.ttsStatusIndicator, EngineStatus.ERROR);
        }
    }

    private void displayDeviceInfo() {
        binding.deviceBrand.setText("Brand: " + Build.MANUFACTURER + " " + Build.MODEL);
        binding.deviceFirmware.setText("Firmware: " + Build.VERSION.RELEASE);
        binding.deviceChip.setText("Chipset: " + Build.HARDWARE);
        
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(memoryInfo);
        long totalMemory = memoryInfo.totalMem / (1024 * 1024); // Convert to MB
        binding.deviceRam.setText("RAM: " + totalMemory + "MB");
    }

    private void updateEngineStatus(ImageView indicator, EngineStatus status) {
        runOnUiThread(() -> {
            int colorRes;
            switch (status) {
                case INITIALIZING:
                    colorRes = R.drawable.status_indicator_yellow;
                    break;
                case READY:
                    colorRes = R.drawable.status_indicator_green;
                    break;
                case ERROR:
                    colorRes = R.drawable.status_indicator_red;
                    break;
                default:
                    colorRes = R.drawable.status_indicator_red;
                    break;
            }
            indicator.setImageResource(colorRes);
        });
    }

    private void checkAllEnginesReady() {
        boolean allReady = llmEngine != null && llmEngine.isReady() &&
                          vlmEngine != null && vlmEngine.isReady() &&
                          asrEngine != null && asrEngine.isReady() &&
                          ttsEngine != null && ttsEngine.isReady();
                          
        runOnUiThread(() -> binding.startChatButton.setEnabled(allReady));
    }

    private enum EngineStatus {
        INITIALIZING,
        READY,
        ERROR
    }

    private void setupClickListeners() {
        Button startChatButton = findViewById(R.id.startChatButton);
        startChatButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatActivity.class);
            startActivity(intent);
        });
    }
} 