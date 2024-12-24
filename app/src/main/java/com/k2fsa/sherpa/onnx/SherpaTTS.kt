package com.k2fsa.sherpa.onnx;

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class SherpaTTS private constructor(
    private val tts: OfflineTts,
    private val sampleRate: Int
) {
    private var isInitialized = AtomicBoolean(false)
    private var currentCallback: ((FloatArray) -> Int)? = null
    private var isStopped = AtomicBoolean(false)
    private var isReleased = AtomicBoolean(false)

    companion object {
        private const val TAG = "SherpaTTS"
        private const val MODEL_DIR = "vits-melo-tts-zh_en"
        private const val MODEL_NAME = "model.onnx"
        private const val LEXICON = "lexicon.txt"
        private const val DICT_DIR = "$MODEL_DIR/dict"
        private const val RULE_FSTS = "$MODEL_DIR/date.fst,$MODEL_DIR/new_heteronym.fst,$MODEL_DIR/number.fst,$MODEL_DIR/phone.fst"
        
        @Volatile
        private var instance: SherpaTTS? = null
        private val instanceLock = Any()

        fun getInstance(context: Context): SherpaTTS {
            val currentInstance = instance
            if (currentInstance != null && !currentInstance.isReleased.get()) {
                return currentInstance
            }

            synchronized(instanceLock) {
                var localInstance = instance
                if (localInstance == null || localInstance.isReleased.get()) {
                    localInstance = createInstance(context)
                    instance = localInstance
                }
                return localInstance
            }
        }

        private fun createInstance(context: Context): SherpaTTS {
            try {
                var modelDir = MODEL_DIR
                var dictDir = DICT_DIR
                var ruleFsts = RULE_FSTS
                var assets = context.assets

                // If we need to use dict, copy files to external storage
                if (dictDir.isNotEmpty()) {
                    val newDir = copyDataDir(context, modelDir)
                    modelDir = "$newDir/$modelDir"
                    dictDir = "$modelDir/dict"
                    ruleFsts = "$modelDir/phone.fst,$modelDir/date.fst,$modelDir/number.fst"
                    assets = null
                }

                // Default model configuration with asset paths
                val config = getOfflineTtsConfig(
                    modelDir = modelDir,
                    modelName = MODEL_NAME,
                    lexicon = LEXICON,
                    dataDir = "",
                    dictDir = dictDir,
                    ruleFsts = ruleFsts,
                    ruleFars = ""
                )

                Log.d(TAG, "Initializing TTS with config: $config")
                val tts = OfflineTts(assets, config)
                return SherpaTTS(tts, tts.sampleRate()).also {
                    it.isInitialized.set(true)
                    Log.d(TAG, "TTS initialization completed. Speakers: ${it.getNumSpeakers()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create SherpaTTS instance", e)
                throw e
            }
        }

        private fun copyDataDir(context: Context, dataDir: String): String {
            Log.i(TAG, "Copying data dir: $dataDir")
            copyAssets(context, dataDir)
            val newDataDir = context.getExternalFilesDir(null)!!.absolutePath
            Log.i(TAG, "New data dir: $newDataDir")
            return newDataDir
        }

        private fun copyAssets(context: Context, path: String) {
            try {
                val assets = context.assets.list(path)
                if (assets.isNullOrEmpty()) {
                    copyFile(context, path)
                } else {
                    val fullPath = "${context.getExternalFilesDir(null)}/$path"
                    File(fullPath).mkdirs()
                    assets.forEach { asset ->
                        val subPath = if (path.isEmpty()) asset else "$path/$asset"
                        copyAssets(context, subPath)
                    }
                }
            } catch (ex: IOException) {
                Log.e(TAG, "Failed to copy $path", ex)
            }
        }

        private fun copyFile(context: Context, filename: String) {
            try {
                context.assets.open(filename).use { input ->
                    val newFilename = "${context.getExternalFilesDir(null)}/$filename"
                    FileOutputStream(newFilename).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to copy $filename", ex)
            }
        }
    }

    fun isInitialized(): Boolean = isInitialized.get()

    fun checkInitialized() {
        if (!isInitialized.get()) {
            throw IllegalStateException("TTS not initialized")
        }
    }

    fun speak(text: String, speakerId: Int = 0, speed: Float = 1.0f): FloatArray {
        checkInitialized()
        return tts.generate(text, speakerId, speed).samples
    }

    fun getSampleRate(): Int {
        checkInitialized()
        return sampleRate
    }

    fun getNumSpeakers(): Int {
        checkInitialized()
        return tts.numSpeakers()
    }

    fun testTTS(): Boolean {
        try {
            checkInitialized()
            val testText = "Test."
            val samples = speak(testText)
            return samples.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "TTS test failed", e)
            return false
        }
    }

    fun release() {
        synchronized(instanceLock) {
            if (!isReleased.get()) {
                try {
                    if (isInitialized.get()) {
                        tts.release()
                    }
                    isReleased.set(true)
                    isInitialized.set(false)
                    instance = null
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing TTS resources", e)
                }
            }
        }
    }

    protected fun finalize() {
        release()
    }

    fun synthesize(
        text: String,
        speakerId: Int = 0,
        speed: Float = 1.0f,
        onSamples: (FloatArray) -> Unit,
        onComplete: () -> Unit
    ) {
        checkInitialized()
        try {
            val samples = tts.generate(text, speakerId, speed).samples
            onSamples(samples)
            onComplete()
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed", e)
            throw e
        }
    }

    fun stop() {
        isStopped.set(true)
        currentCallback = null
    }

}

// Data class to hold model configuration
data class ModelConfig(
    val modelDir: String,
    val modelName: String,
    val lexicon: String? = null,
    val dataDir: String? = null,
    val dictDir: String? = null,
    var ruleFsts: String? = null,
    val ruleFars: String? = null,
) 