package com.whisperflow.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class LiveSpeechStreamer(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    var isStreaming: Boolean = false
        private set

    fun startListening(
        languageCode: String = "auto",
        onPartialText: (String) -> Unit,
        onFinalText: (String) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        stopListening()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w("LiveSpeechStreamer", "Speech recognition not available on device")
            return
        }

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                val tag = when (languageCode.lowercase()) {
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

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

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
                    Log.d("LiveSpeechStreamer", "Recognition notice: $errorMsg")
                    onError?.invoke(errorMsg)
                }

                override fun onResults(results: Bundle?) {
                    isStreaming = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        onFinalText(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partialText = matches?.firstOrNull() ?: ""
                    if (partialText.isNotBlank()) {
                        onPartialText(partialText)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
            isStreaming = true
        } catch (e: Exception) {
            Log.e("LiveSpeechStreamer", "Failed to start live speech streaming", e)
            isStreaming = false
        }
    }

    fun stopListening() {
        isStreaming = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // ignore
        }
        speechRecognizer = null
    }
}
