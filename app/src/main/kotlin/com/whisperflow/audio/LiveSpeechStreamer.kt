package com.whisperflow.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class LiveSpeechStreamer(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    var isSessionActive: Boolean = false
        private set
    var isStreaming: Boolean = false
        private set

    private var currentLanguageCode: String = "auto"
    private var onPartialCallback: ((String) -> Unit)? = null
    private var onSegmentFinalCallback: ((String) -> Unit)? = null
    private var onRmsCallback: ((Float) -> Unit)? = null
    private var onErrorCallback: ((Int, String) -> Unit)? = null

    fun startListening(
        languageCode: String = "auto",
        onPartialText: (String) -> Unit,
        onSegmentFinal: (String) -> Unit,
        onRmsChanged: ((Float) -> Unit)? = null,
        onError: ((Int, String) -> Unit)? = null
    ) {
        stopListening()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w("LiveSpeechStreamer", "Speech recognition not available on device")
            onError?.invoke(-1, "Speech recognition service not available on device")
            return
        }

        isSessionActive = true
        currentLanguageCode = languageCode
        onPartialCallback = onPartialText
        onSegmentFinalCallback = onSegmentFinal
        onRmsCallback = onRmsChanged
        onErrorCallback = onError

        mainHandler.post {
            createAndStartRecognizer()
        }
    }

    private fun createAndStartRecognizer() {
        if (!isSessionActive) return

        try {
            cleanupRecognizer()

            val recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                Log.d("LiveSpeechStreamer", "Binding to On-Device SpeechRecognizer")
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                val tag = when (currentLanguageCode.lowercase()) {
                    "bn" -> "bn-BD"
                    "en" -> "en-US"
                    "hi" -> "hi-IN"
                    "es" -> "es-ES"
                    "ar" -> "ar-SA"
                    "fr" -> "fr-FR"
                    else -> "bn-BD" // default to Bengali/English mixed for Kothon
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US", "bn-BD"))
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isStreaming = true
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    if (isSessionActive) {
                        // Normalize -2dB..10dB to 0..1f
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        onRmsCallback?.invoke(normalized)
                    }
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isStreaming = false
                }

                override fun onError(error: Int) {
                    isStreaming = false
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions error"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        else -> "Recognition error ($error)"
                    }
                    Log.d("LiveSpeechStreamer", "Recognition notice ($error): $errorMsg")

                    if (!isSessionActive) return

                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            // User paused speaking; seamlessly restart listening
                            mainHandler.postDelayed({
                                if (isSessionActive) {
                                    createAndStartRecognizer()
                                }
                            }, 100)
                        }
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            mainHandler.postDelayed({
                                if (isSessionActive) {
                                    createAndStartRecognizer()
                                }
                            }, 300)
                        }
                        else -> {
                            isSessionActive = false
                            onErrorCallback?.invoke(error, errorMsg)
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    isStreaming = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        onSegmentFinalCallback?.invoke(text)
                    }

                    // Continuous session loop: restart for the next sentence
                    if (isSessionActive) {
                        mainHandler.post {
                            if (isSessionActive) {
                                createAndStartRecognizer()
                            }
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partialText = matches?.firstOrNull() ?: ""
                    if (partialText.isNotBlank()) {
                        onPartialCallback?.invoke(partialText)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
            isStreaming = true
        } catch (e: Exception) {
            Log.e("LiveSpeechStreamer", "Failed to start live speech session", e)
            isStreaming = false
            isSessionActive = false
            onErrorCallback?.invoke(-2, "Failed to start live speech session: ${e.message}")
        }
    }

    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (ignored: Exception) {}
        speechRecognizer = null
        isStreaming = false
    }

    fun stopListening() {
        isSessionActive = false
        mainHandler.removeCallbacksAndMessages(null)
        cleanupRecognizer()
        onPartialCallback = null
        onSegmentFinalCallback = null
        onRmsCallback = null
        onErrorCallback = null
    }
}
