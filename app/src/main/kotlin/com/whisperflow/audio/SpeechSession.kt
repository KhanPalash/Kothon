package com.whisperflow.audio

interface SpeechSession {
    fun start(config: SpeechConfig, listener: Listener)
    fun stop()
    fun destroy()

    interface Listener {
        fun onReady()
        fun onPartial(text: String)
        fun onSegmentFinal(text: String)
        fun onError(code: Int, message: String, recoverable: Boolean)
        fun onEnd()
    }
}

data class SpeechConfig(
    val languageTag: String,
    val preferOnDevice: Boolean,
    val partialResults: Boolean = true,
    val maxSessionMs: Long = 5 * 60_000
)
