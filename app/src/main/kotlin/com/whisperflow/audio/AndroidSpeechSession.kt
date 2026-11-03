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
import androidx.annotation.MainThread

@MainThread
class AndroidSpeechSession(private val context: Context) : SpeechSession {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var prewarmedRecognizer: SpeechRecognizer? = null
    private var listener: SpeechSession.Listener? = null
    private var config: SpeechConfig? = null

    private var sessionActive = false
    private var userStopped = false
    private var consecutiveNoMatch = 0
    private var retryAttempt = 0

    private val watchdogRunnable = Runnable {
        Log.w(TAG, "onEndOfSpeech watchdog fired; restarting")
        restartIfActive()
    }

    private val sessionTimerRunnable = Runnable {
        Log.i(TAG, "Session cap reached; auto-stopping")
        stop()
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listener?.onReady()
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            handler.postDelayed(watchdogRunnable, WATCHDOG_MS)
        }

        override fun onError(error: Int) {
            handler.removeCallbacks(watchdogRunnable)
            if (userStopped) {
                listener?.onEnd()
                return
            }
            handleError(error)
        }

        override fun onResults(results: Bundle?) {
            handler.removeCallbacks(watchdogRunnable)
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() } ?: ""
            if (text.isNotBlank()) {
                consecutiveNoMatch = 0
                listener?.onSegmentFinal(text)
            }
            if (sessionActive && !userStopped) {
                restart()
            } else {
                listener?.onEnd()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() } ?: return
            listener?.onPartial(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun start(config: SpeechConfig, listener: SpeechSession.Listener) {
        this.config = config
        this.listener = listener
        sessionActive = true
        userStopped = false
        consecutiveNoMatch = 0
        retryAttempt = 0
        createAndStart(config)
        handler.postDelayed(sessionTimerRunnable, config.maxSessionMs)
    }

    override fun stop() {
        userStopped = true
        sessionActive = false
        handler.removeCallbacks(sessionTimerRunnable)
        recognizer?.stopListening()
        // Called on main thread, no need for postDelayed
        destroyRecognizer()
        listener?.onEnd()
    }

    override fun destroy() {
        sessionActive = false
        userStopped = true
        handler.removeCallbacks(watchdogRunnable)
        handler.removeCallbacks(sessionTimerRunnable)
        destroyRecognizer()
        destroyPrewarmedRecognizer()
    }

    /**
     * Pre-warm the SpeechRecognizer to eliminate cold-start latency.
     * Creates a recognizer instance that will be reused on first start().
     */
    fun prewarm() {
        if (prewarmedRecognizer != null) return
        try {
            val rec = SpeechRecognizer.createSpeechRecognizer(context)
            prewarmedRecognizer = rec
            Log.i(TAG, "SpeechRecognizer prewarmed")
        } catch (e: Exception) {
            Log.w(TAG, "Prewarm failed: ${e.message}")
        }
    }

    private fun destroyPrewarmedRecognizer() {
        val rec = prewarmedRecognizer
        prewarmedRecognizer = null
        try {
            rec?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Prewarmed recognizer destroy error: ${e.message}")
        }
    }

    private fun createAndStart(cfg: SpeechConfig) {
        destroyRecognizer()
        // Reuse prewarmed recognizer if available and compatible
        val rec = if (prewarmedRecognizer != null) {
            val r = prewarmedRecognizer!!
            prewarmedRecognizer = null
            r
        } else {
            createRecognizer(cfg)
        }
        recognizer = rec
        rec.setRecognitionListener(recognitionListener)
        rec.startListening(buildIntent(cfg))
    }

    private fun createRecognizer(cfg: SpeechConfig): SpeechRecognizer {
        if (Build.VERSION.SDK_INT >= 31 && cfg.preferOnDevice) {
            try {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "On-device recognizer unavailable, falling back: ${e.message}")
            }
        }
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun buildIntent(cfg: SpeechConfig): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, cfg.partialResults)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, cfg.languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, cfg.completeSilenceMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, cfg.minSpeechLengthMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, cfg.possiblyCompleteSilenceMs)
        }
    }

    private fun handleError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                consecutiveNoMatch++
                if (consecutiveNoMatch < MAX_CONSECUTIVE_NO_MATCH) {
                    val delay = BACKOFF_BASE_MS * consecutiveNoMatch
                    Log.d(TAG, "No match/timeout #$consecutiveNoMatch, retry in ${delay}ms")
                    handler.postDelayed({ restartIfActive() }, delay)
                } else {
                    Log.w(TAG, "Consecutive no-match limit reached; stopping")
                    listener?.onError(error, "No speech detected. Tap mic to continue.", false)
                    listener?.onEnd()
                }
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                if (retryAttempt < 1) {
                    retryAttempt++
                    handler.postDelayed({ restartIfActive() }, BUSY_RETRY_MS)
                } else {
                    listener?.onError(error, "Recognizer busy.", false)
                    listener?.onEnd()
                }
            }
            SpeechRecognizer.ERROR_CLIENT -> {
                listener?.onError(error, "Recognition client error.", false)
                listener?.onEnd()
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                listener?.onError(error, "Microphone permission required.", false)
                listener?.onEnd()
            }
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER -> {
                listener?.onError(error, "Network error.", true)
                listener?.onEnd()
            }
            SpeechRecognizer.ERROR_AUDIO -> {
                listener?.onError(error, "Audio error; check microphone.", false)
                listener?.onEnd()
            }
            else -> {
                if (retryAttempt < 1) {
                    retryAttempt++
                    handler.postDelayed({ restartIfActive() }, BACKOFF_BASE_MS)
                } else {
                    listener?.onError(error, "Recognition error ($error).", false)
                    listener?.onEnd()
                }
            }
        }
    }

    private fun restartIfActive() {
        if (sessionActive && !userStopped) {
            restart()
        }
    }

    private fun restart() {
        val cfg = config ?: return
        createAndStart(cfg)
    }

    private fun destroyRecognizer() {
        val rec = recognizer
        recognizer = null
        try {
            rec?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Recognizer destroy error: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AndroidSpeechSession"
        private const val WATCHDOG_MS = 3_000L
        private const val STOP_WAIT_MS = 1_000L
        private const val BACKOFF_BASE_MS = 500L
        private const val BUSY_RETRY_MS = 800L
        private const val MAX_CONSECUTIVE_NO_MATCH = 3
    }
}
