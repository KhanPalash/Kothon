package com.whisperflow.audio

/**
 * Hand-written fake SpeechSession for unit tests.
 * Calls are synchronous; no real recognizer involved.
 */
class FakeSpeechSession : SpeechSession {

    var lastConfig: SpeechConfig? = null
    var listener: SpeechSession.Listener? = null
    var started = false
    var stopped = false
    var destroyed = false

    override fun start(config: SpeechConfig, listener: SpeechSession.Listener) {
        lastConfig = config
        this.listener = listener
        started = true
        stopped = false
        destroyed = false
    }

    override fun stop() {
        stopped = true
    }

    override fun destroy() {
        destroyed = true
        listener = null
    }

    // Test helpers to simulate recognizer callbacks
    fun simulateReady() = listener?.onReady()
    fun simulatePartial(text: String) = listener?.onPartial(text)
    fun simulateFinal(text: String) = listener?.onSegmentFinal(text)
    fun simulateError(code: Int, message: String, recoverable: Boolean) =
        listener?.onError(code, message, recoverable)
    fun simulateEnd() = listener?.onEnd()

    // Phase 5 test API aliases
    fun sendPartial(text: String) = simulatePartial(text)
    fun sendFinal(text: String) = simulateFinal(text)
    fun isStarted(): Boolean = started
}
