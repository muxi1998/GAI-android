package com.mtkresearch.breeze_app.utils;

import android.content.Context;
import java.io.File;
import android.util.Log;
import java.io.IOException;
import android.app.ActivityManager;
import android.content.SharedPreferences;

public class AppConstants {
    private static final String TAG = "AppConstants";

    // Shared Preferences
    public static final String PREFS_NAME = "GAISettings";
    
    // Preference Keys
    public static final String KEY_HISTORY_LOOKBACK = "history_lookback";
    public static final String KEY_SEQUENCE_LENGTH = "sequence_length";
    public static final String KEY_DEFAULT_MODEL = "default_model";
    public static final String KEY_FIRST_LAUNCH = "first_launch";
    public static final String KEY_TEMPERATURE = "temperature";
    public static final String KEY_PREFERRED_BACKEND = "preferred_backend";
    public static final String DEFAULT_BACKEND = "cpu";  // Default to CPU backend
    
    // Service Enable Flags
    public static final boolean LLM_ENABLED = true;  // LLM is essential
    public static final boolean VLM_ENABLED = false; // VLM is experimental
    public static final boolean ASR_ENABLED = false; // ASR requires permission
    public static final boolean TTS_ENABLED = true;  // TTS is stable
    
    // Backend Constants
    public static final String BACKEND_NONE = "none";
    public static final String BACKEND_CPU = "cpu";
    public static final String BACKEND_MTK = "mtk";
    public static final String BACKEND_DEFAULT = BACKEND_CPU;  // Default to CPU backend since MTK is experimental
    
    // Backend Enable Flags
    public static final boolean MTK_BACKEND_ENABLED = false;  // Set to true to enable MTK backend
    public static volatile boolean MTK_BACKEND_AVAILABLE = false;  // Runtime state of MTK backend availability
    
    // Backend Initialization Constants
    public static final int MAX_MTK_INIT_ATTEMPTS = 5;
    public static final long MTK_CLEANUP_TIMEOUT_MS = 5000;  // 5 seconds timeout for cleanup
    public static final long MTK_NATIVE_OP_TIMEOUT_MS = 2000;  // 2 seconds timeout for native operations
    public static final long BACKEND_INIT_DELAY_MS = 200;    // Delay between backend initialization attempts
    public static final long BACKEND_CLEANUP_DELAY_MS = 100; // Delay for backend cleanup operations
    
    // LLM Service Constants
    public static final long LLM_INIT_TIMEOUT_MS = 300000;  // 5 minutes for initialization
    public static final long LLM_GENERATION_TIMEOUT_MS = Long.MAX_VALUE;  // No timeout for generation
    public static final long LLM_NATIVE_OP_TIMEOUT_MS = 10000;  // 10 seconds for native ops
    public static final long LLM_CLEANUP_TIMEOUT_MS = 10000;  // 10 seconds for cleanup
    public static final int LLM_MAX_MTK_INIT_ATTEMPTS = 3;
    public static final String DEFAULT_SYSTEM_PROMPT = "你是擁有臺灣知識的語言模型，請用繁體中文或英文回答以下問題";

    // Model Files and Paths
    public static final String LLAMA_MODEL_FILE = "Breeze-Tiny-Instruct-v0_1-2048.pte";
    public static final String BREEZE_MODEL_FILE = "Breeze-Tiny-Instruct-v0_1-2048.pte";
    public static final String BREEZE_MODEL_DISPLAY_NAME = "Breeze Tiny Instruct v0.1 (2048)";
    
    // LLM Model Size Options
    public static final String LARGE_LLM_MODEL_FILE = "Breeze-Tiny-Instruct-v0_1-2048.pte";
    public static final String SMALL_LLM_MODEL_FILE = "Breeze-Tiny-Instruct-v0_1-2048-spin.pte";
    public static final String LARGE_LLM_MODEL_DISPLAY_NAME = "Breeze2";
    public static final String SMALL_LLM_MODEL_DISPLAY_NAME = "Breeze2-spinQuant";
    
    // RAM Requirements
    public static final long MIN_RAM_REQUIRED_GB = 7; // Minimum RAM for the app to run
    public static final long LARGE_MODEL_MIN_RAM_GB = 10; // Minimum RAM for large model
    
    // Model Selection Key
    public static final String KEY_MODEL_SIZE_PREFERENCE = "model_size_preference";
    public static final String MODEL_SIZE_LARGE = "large";
    public static final String MODEL_SIZE_SMALL = "small";
    public static final String MODEL_SIZE_AUTO = "auto"; // Let the app decide based on available RAM
    
    public static final String LLAMA_MODEL_DIR = "/data/local/tmp/llama/";  // Legacy location
    public static final String APP_MODEL_DIR = "models";  // New path relative to app's private storage
    public static final String LLM_TOKENIZER_FILE = "tokenizer.bin";  // Add tokenizer filename constant
    
    // TTS Model Files and Paths
    public static final String TTS_MODEL_DIR = "Breeze2-VITS-onnx";
    public static final String TTS_MODEL_FILE = "breeze2-vits.onnx";
    public static final String TTS_LEXICON_FILE = "lexicon.txt";
    public static final String TTS_TOKENS_FILE = "tokens.txt";
    
    // Model Download Constants
    public static final String MODEL_BASE_URL = "https://huggingface.co/MediaTek-Research/Breeze-Tiny-Instruct-v0_1-mobile/resolve/main/";
    
    // Model Download URLs - defined before usage in LLM_DOWNLOAD_FILES
    public static final String[] MODEL_DOWNLOAD_URLS = {
        // Tokenizer - small file, use regular URL
        MODEL_BASE_URL + "tokenizer.bin?download=true",
        // Model file - try multiple reliable sources
        MODEL_BASE_URL + BREEZE_MODEL_FILE + "?download=true"
    };
    
    // TTS Model Download URLs
    private static final String TTS_MODEL_BASE_URL = "https://huggingface.co/MediaTek-Research/Breeze2-VITS-onnx/resolve/main/";
    private static final String TTS_HF_MIRROR_URL = "https://hf-mirror.com/MediaTek-Research/Breeze2-VITS-onnx/resolve/main/";
    
    // Download status constants
    public static final int DOWNLOAD_STATUS_PENDING = 0;
    public static final int DOWNLOAD_STATUS_IN_PROGRESS = 1;
    public static final int DOWNLOAD_STATUS_PAUSED = 2;
    public static final int DOWNLOAD_STATUS_COMPLETED = 3;
    public static final int DOWNLOAD_STATUS_FAILED = 4;
    
    // File type constants
    public static final String FILE_TYPE_LLM = "LLM Model";
    public static final String FILE_TYPE_TOKENIZER = "Tokenizer";
    public static final String FILE_TYPE_TTS_MODEL = "TTS Model";
    public static final String FILE_TYPE_TTS_LEXICON = "TTS Lexicon";
    public static final String FILE_TYPE_TTS_TOKENS = "TTS Tokens";
    
    
    // Download file information
    public static final class DownloadFileInfo {
        public final String url;
        public final String fileName;
        public final String displayName;
        public final String fileType;
        public final long fileSize;
        
        public DownloadFileInfo(String url, String fileName, String displayName, String fileType, long fileSize) {
            this.url = url;
            this.fileName = fileName;
            this.displayName = displayName;
            this.fileType = fileType;
            this.fileSize = fileSize;
        }
    }
    
    // LLM related download files
    public static final DownloadFileInfo[] LLM_DOWNLOAD_FILES = {
        new DownloadFileInfo(
            MODEL_DOWNLOAD_URLS[0], // Using first URL from MODEL_DOWNLOAD_URLS
            LLM_TOKENIZER_FILE,
            "Tokenizer",
            FILE_TYPE_TOKENIZER,
            5 * 1024 * 1024 // ~5MB estimate
        ),
        new DownloadFileInfo(
            MODEL_DOWNLOAD_URLS[1], // Using second URL from MODEL_DOWNLOAD_URLS 
            BREEZE_MODEL_FILE,
            "Language Model",
            FILE_TYPE_LLM,
            6 * 1024 * 1024 * 1024L // 6GB estimate
        )
    };
    
    // TTS related download files
    public static final DownloadFileInfo[] TTS_DOWNLOAD_FILES = {
        new DownloadFileInfo(
            TTS_MODEL_BASE_URL + TTS_MODEL_FILE + "?download=true",
            TTS_MODEL_FILE,
            "TTS Model", 
            FILE_TYPE_TTS_MODEL,
            100 * 1024 * 1024 // ~100MB estimate
        ),
        new DownloadFileInfo(
            TTS_MODEL_BASE_URL + TTS_LEXICON_FILE + "?download=true",
            TTS_LEXICON_FILE,
            "Lexicon",
            FILE_TYPE_TTS_LEXICON,
            1 * 1024 * 1024 // ~1MB estimate
        ),
        new DownloadFileInfo(
            TTS_MODEL_BASE_URL + "tokens.txt?download=true",
            "tokens.txt",
            "Tokens",
            FILE_TYPE_TTS_TOKENS,
            100 * 1024 // ~100KB estimate
        )
    };
    
    // TTS Model Download URLs (keeping for backward compatibility)
    public static final String[] TTS_MODEL_DOWNLOAD_URLS = {
        // Primary TTS model files
        TTS_MODEL_BASE_URL + TTS_MODEL_FILE + "?download=true",
        TTS_HF_MIRROR_URL + TTS_MODEL_FILE + "?download=true",
        // Lexicon file
        TTS_MODEL_BASE_URL + TTS_LEXICON_FILE + "?download=true",
        TTS_HF_MIRROR_URL + TTS_LEXICON_FILE + "?download=true",
        // Tokens file
        TTS_MODEL_BASE_URL + "tokens.txt?download=true",
        TTS_HF_MIRROR_URL + "tokens.txt?download=true"
    };

    // Check if TTS models exist in assets or app storage
    public static boolean hasTTSModels(Context context) {
        // First check app's private storage
        File ttsDir = new File(new File(context.getFilesDir(), APP_MODEL_DIR), TTS_MODEL_DIR);
        File primaryModel = new File(ttsDir, TTS_MODEL_FILE);
        File primaryLexicon = new File(ttsDir, TTS_LEXICON_FILE);
        File primaryTokens = new File(ttsDir, "tokens.txt");
        
        boolean primaryExists = primaryModel.exists() && primaryModel.isFile() && primaryModel.length() > 0 &&
                              primaryLexicon.exists() && primaryLexicon.isFile() && primaryLexicon.length() > 0 &&
                              primaryTokens.exists() && primaryTokens.isFile() && primaryTokens.length() > 0;
        
        if (primaryExists) {
            return true;
        }
        
        // Then check assets
        try {
            context.getAssets().open(TTS_MODEL_DIR + "/" + TTS_MODEL_FILE).close();
            context.getAssets().open(TTS_MODEL_DIR + "/" + TTS_LEXICON_FILE).close();
            context.getAssets().open(TTS_MODEL_DIR + "/tokens.txt").close();
            return true;
        } catch (IOException e) {
            Log.d(TAG, "TTS models not found in assets", e);
        }
        
        return false;
    }

    // Get TTS model path
    public static String getTTSModelPath(Context context) {
        // First check app's private storage
        File ttsDir = new File(new File(context.getFilesDir(), APP_MODEL_DIR), TTS_MODEL_DIR);
        File primaryModel = new File(ttsDir, TTS_MODEL_FILE);
        
        if (primaryModel.exists() && primaryModel.isFile() && primaryModel.length() > 0) {
            return primaryModel.getAbsolutePath();
        }
        
        // Then check assets
        try {
            context.getAssets().open(TTS_MODEL_DIR + "/" + TTS_MODEL_FILE).close();
            return TTS_MODEL_DIR + "/" + TTS_MODEL_FILE;
        } catch (IOException e) {
            Log.d(TAG, "TTS model not found in assets", e);
        }
        
        return null;
    }

    // Check if TTS models need to be downloaded
    public static boolean needsTTSModelDownload(Context context) {
        return !hasTTSModels(context);
    }
    
    // Get absolute path to the app's TTS model directory
    public static String getAppTTSModelDir(Context context) {
        return new File(new File(context.getFilesDir(), APP_MODEL_DIR), TTS_MODEL_DIR).getAbsolutePath();
    }

    // Get absolute path to the app's model directory
    public static String getAppModelDir(Context context) {
        return new File(context.getFilesDir(), APP_MODEL_DIR).getAbsolutePath();
    }

    // Check if model exists in legacy location
    public static boolean isModelInLegacyLocation() {
        File legacyModelFile = new File(LLAMA_MODEL_DIR, BREEZE_MODEL_FILE);
        return legacyModelFile.exists() && legacyModelFile.length() > 0;
    }

    // Get the model path to use, prioritizing legacy location
    public static String getModelPath(Context context) {
        // Get the appropriate model file based on preferences and RAM
        String modelFileName = getAppropriateModelFile(context);
        
        // First check the legacy location
        File legacyModelFile = new File(LLAMA_MODEL_DIR, modelFileName);
        Log.d("AppConstants", "Checking legacy model path: " + legacyModelFile.getAbsolutePath());
        if (legacyModelFile.exists() && legacyModelFile.length() > 0) {
            Log.d("AppConstants", "Found model in legacy directory: " + legacyModelFile.getAbsolutePath());
            return legacyModelFile.getAbsolutePath();
        }

        // If not in legacy location, use app's private storage path
        File appModelFile = new File(new File(context.getFilesDir(), APP_MODEL_DIR), modelFileName);
        Log.d("AppConstants", "Using app model path: " + appModelFile.getAbsolutePath());
        return appModelFile.getAbsolutePath();
    }

    // Get the tokenizer path to use
    public static String getTokenizerPath(Context context) {
        // First check the legacy location
        File legacyTokenizerFile = new File(LLAMA_MODEL_DIR, LLM_TOKENIZER_FILE);
        if (legacyTokenizerFile.exists() && legacyTokenizerFile.length() > 0) {
            return legacyTokenizerFile.getAbsolutePath();
        }

        // If not in legacy location, use app's private storage path
        File appTokenizerFile = new File(new File(context.getFilesDir(), APP_MODEL_DIR), LLM_TOKENIZER_FILE);
        return appTokenizerFile.getAbsolutePath();
    }

    // Check if model needs to be downloaded
    public static boolean needsModelDownload(Context context) {
        String modelFileName = getAppropriateModelFile(context);
        
        // First check if model exists in legacy location
        File legacyModelFile = new File(LLAMA_MODEL_DIR, modelFileName);
        if (legacyModelFile.exists() && legacyModelFile.length() > 0) {
            return false;
        }

        // Then check app's private storage
        File appModelFile = new File(new File(context.getFilesDir(), APP_MODEL_DIR), modelFileName);
        return !appModelFile.exists() || appModelFile.length() == 0;
    }

    // Get the current effective model path (used for sequence length calculations)
    private static String getCurrentModelPath(Context context) {
        return isModelInLegacyLocation() ? 
            new File(LLAMA_MODEL_DIR, BREEZE_MODEL_FILE).getAbsolutePath() :
            new File(new File(context.getFilesDir(), APP_MODEL_DIR), BREEZE_MODEL_FILE).getAbsolutePath();
    }

    // LLM Sequence Length Constants - these should be calculated based on the current model path
    public static int getLLMMaxSeqLength(Context context) {
        return getCurrentModelPath(context).contains("2048") ? 512 : 128;
    }

    public static int getLLMMinOutputLength(Context context) {
        return getCurrentModelPath(context).contains("2048") ? 512 : 32;
    }

    public static int getLLMMaxInputLength(Context context) {
        return getLLMMaxSeqLength(context) - getLLMMinOutputLength(context);
    }
    
    // Get the available RAM in GB
    public static long getAvailableRamGB(Context context) {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(memoryInfo);
        
        // Convert total memory from bytes to GB
        return memoryInfo.totalMem / (1024 * 1024 * 1024);
    }
    
    // Check if device has enough RAM for large model
    public static boolean canUseLargeModel(Context context) {
        return getAvailableRamGB(context) >= LARGE_MODEL_MIN_RAM_GB;
    }
    
    // Get the appropriate model file based on user preference and RAM constraints
    public static String getAppropriateModelFile(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String modelSizePreference = prefs.getString(KEY_MODEL_SIZE_PREFERENCE, MODEL_SIZE_AUTO);
        
        // For auto preference, choose based on available RAM
        if (modelSizePreference.equals(MODEL_SIZE_AUTO)) {
            return canUseLargeModel(context) ? LARGE_LLM_MODEL_FILE : SMALL_LLM_MODEL_FILE;
        }
        
        // For explicit preferences, respect the user's choice between Breeze variants
        if (modelSizePreference.equals(MODEL_SIZE_LARGE)) {
            return LARGE_LLM_MODEL_FILE; // Breeze high performance variant
        } else {
            return SMALL_LLM_MODEL_FILE; // Breeze small variant
        }
    }
    
    // LLM Response Messages
    public static final String LLM_ERROR_RESPONSE = "[!!!] LLM engine backend failed";
    public static final String LLM_DEFAULT_ERROR_RESPONSE = "I apologize, but I encountered an error generating a response. Please try again.";
    public static final String LLM_EMPTY_RESPONSE_ERROR = "I apologize, but I couldn't generate a proper response. Please try rephrasing your question.";
    public static final String LLM_INPUT_TOO_LONG_ERROR = "I apologize, but your input is too long. Please try breaking it into smaller parts.";
    public static final String LLM_INVALID_TOKEN_ERROR = "I apologize, but I was unable to generate a valid response. This might be due to the complexity of the question or current model limitations. Please try rephrasing your question.";
    
    // LLM Configuration
    public static final float LLM_TEMPERATURE = 0.0f;
    
    // When false: Send button always shows send icon and only sends messages
    // When true: Send button toggles between send and audio chat mode
    public static final boolean AUDIO_CHAT_ENABLED = false;

    // Conversation History Constants
    public static final int CONVERSATION_HISTORY_LOOKBACK = BREEZE_MODEL_FILE.contains("2048") ? 1 : 1;

    // Activity Request Codes
    public static final int PERMISSION_REQUEST_CODE = 123;
    public static final int PICK_IMAGE_REQUEST = 1;
    public static final int CAPTURE_IMAGE_REQUEST = 2;
    public static final int PICK_FILE_REQUEST = 3;

    // UI Constants
    public static final float ENABLED_ALPHA = 1.0f;
    public static final float DISABLED_ALPHA = 0.3f;

    public static final int TAPS_TO_SHOW_MAIN = 7;
    public static final long TAP_TIMEOUT_MS = 3000;
    public static final int INIT_DELAY_MS = 1000;

    // Activity Tags
    public static final String CHAT_ACTIVITY_TAG = "ChatActivity";
    public static final String MAIN_ACTIVITY_TAG = "MainActivity";
    public static final String AUDIO_CHAT_ACTIVITY_TAG = "AudioChatActivity";



    // HTTP Headers
    public static final String[][] DOWNLOAD_HEADERS = {
        {"User-Agent", "Mozilla/5.0 (Android) BreezeApp"},
        {"Accept", "*/*"},
        {"Connection", "keep-alive"}
    };

    // Logging control for downloads
    public static final boolean ENABLE_DOWNLOAD_VERBOSE_LOGGING = false; // Set to true for debug builds, false for release
    
    // File size units
    public static final String[] FILE_SIZE_UNITS = { "B", "KB", "MB", "GB", "TB" };
    
    // Optimize buffer size for large files (8MB buffer)
    public static final int MODEL_DOWNLOAD_BUFFER_SIZE = 8 * 1024 * 1024;
    
    // More frequent progress updates for better UX
    public static final int MODEL_DOWNLOAD_PROGRESS_UPDATE_INTERVAL = 1;
    
    // Increase timeout for large files (30 minutes)
    public static final long MODEL_DOWNLOAD_TIMEOUT_MS = 1800000;
    
    // Required free space (8GB)
    public static final long MODEL_DOWNLOAD_MIN_SPACE_MB = 8192;
    
    // Disable parallel downloads since servers don't support it well
    public static final boolean MODEL_DOWNLOAD_PARALLEL = false;
    
    // Temporary extension for partial downloads
    public static final String MODEL_DOWNLOAD_TEMP_EXTENSION = ".part";
} 