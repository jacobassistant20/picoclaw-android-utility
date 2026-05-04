package com.picoclaw.utility

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.os.Bundle
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Piper TTS Manager
 * Handles offline text-to-speech using Piper binary bundled in APK.
 * 
 * Usage:
 * 1. Place piper binary in app/src/main/assets/piper/piper (executable)
 * 2. Place model file in app/src/main/assets/piper/en_US-lessac-medium.onnx
 * 3. Call speak("Hello world") to play TTS
 */
class PiperTtsManager(private val context: Context) {

    companion object {
        private const val TAG = "PiperTtsManager"
        private const val ASSETS_PIPER_DIR = "piper"
        private const val PIPER_BINARY = "piper"
        private const val MODEL_FILE = "en_US-lessac-medium.onnx"
    }

    private var piperProcess: Process? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isPiperReady = false
    private var piperDir: File? = null

    init {
        extractPiperAssets()
    }

    /**
     * Extract Piper binary and model from assets to app's files directory
     */
    private fun extractPiperAssets() {
        try {
            piperDir = File(context.filesDir, "piper")
            if (!piperDir!!.exists()) {
                piperDir!!.mkdirs()
            }

            // Extract piper binary
            val piperBinary = File(piperDir, PIPER_BINARY)
            if (!piperBinary.exists()) {
                extractAsset(ASSETS_PIPER_DIR + "/" + PIPER_BINARY, piperBinary)
                piperBinary.setExecutable(true)
            }

            // Extract model file
            val modelFile = File(piperDir, MODEL_FILE)
            if (!modelFile.exists()) {
                extractAsset(ASSETS_PIPER_DIR + "/" + MODEL_FILE, modelFile)
            }

            isPiperReady = piperBinary.exists() && modelFile.exists()
            Log.d(TAG, "Piper ready: $isPiperReady")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract Piper assets: ${e.message}")
            isPiperReady = false
        }
    }

    /**
     * Extract asset file from APK to destination
     */
    private fun extractAsset(assetPath: String, destFile: File) {
        try {
            val inputStream: InputStream = context.assets.open(assetPath)
            val outputStream = FileOutputStream(destFile)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting $assetPath: ${e.message}")
        }
    }

    /**
     * Speak text using Piper TTS
     * @param text Text to speak
     * @param onComplete Callback when speech is complete
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isPiperReady) {
            Log.w(TAG, "Piper not ready, using fallback")
            useFallbackTts(text, onComplete)
            return
        }

        stop() // Stop any current playback

        Thread {
            try {
                val piperBinary = File(piperDir, PIPER_BINARY)
                val modelFile = File(piperDir, MODEL_FILE)
                val outputWav = File(context.cacheDir, "piper_output.wav")

                // Build Piper command
                // Piper reads from stdin, outputs WAV to file
                val cmd = arrayOf(
                    piperBinary.absolutePath,
                    "--model", modelFile.absolutePath,
                    "--output_file", outputWav.absolutePath
                )

                val processBuilder = ProcessBuilder(*cmd)
                processBuilder.redirectErrorStream(true)
                piperProcess = processBuilder.start()

                // Write text to Piper stdin
                piperProcess!!.outputStream.use { output ->
                    output.write(text.toByteArray())
                    output.flush()
                }

                // Wait for Piper to finish
                val exitCode = piperProcess!!.waitFor()
                Log.d(TAG, "Piper exit code: $exitCode")

                if (exitCode == 0 && outputWav.exists()) {
                    // Play the generated WAV
                    playAudioFile(outputWav, onComplete)
                } else {
                    useFallbackTts(text, onComplete)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Piper speak error: ${e.message}")
                useFallbackTts(text, onComplete)
            }
        }.start()
    }

    /**
     * Play audio file using MediaPlayer
     */
    private fun playAudioFile(audioFile: File, onComplete: (() -> Unit)?) {
        try {
            mediaPlayer = MediaPlayer()
            mediaPlayer?.apply {
                setDataSource(audioFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    onComplete?.invoke()
                }
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Play audio error: ${e.message}")
            onComplete?.invoke()
        }
    }

    /**
     * Fallback to Android's built-in TTS if Piper fails
     */
    private fun useFallbackTts(text: String, onComplete: (() -> Unit)?) {
        try {
            var tts: TextToSpeech? = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val params = Bundle()
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "piper_fallback")
                    
                    tts?.speak(
                        text,
                        TextToSpeech.QUEUE_FLUSH,
                        params,
                        "piper_fallback"
                    )
                    onComplete?.invoke()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback TTS error: ${e.message}")
            onComplete?.invoke()
        }
    }

    /**
     * Stop current TTS playback
     */
    fun stop() {
        try {
            piperProcess?.destroy()
            piperProcess = null
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Stop error: ${e.message}")
        }
    }

    /**
     * Check if Piper is ready to use
     */
    fun isReady(): Boolean = isPiperReady
}