package com.whisperflow.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.IOException

class Recorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    var isRecording: Boolean = false
        private set

    private var timeoutHandler: Handler? = null
    private var onTimeoutListener: (() -> Unit)? = null

    var maxDurationSeconds: Long = 60

    fun start(outputFile: File? = null, onTimeout: (() -> Unit)? = null): File {
        if (isRecording) {
            stop()
        }

        val targetFile = outputFile ?: File(
            context.cacheDir,
            "kothon_audio_${System.currentTimeMillis()}.m4a"
        )
        currentOutputFile = targetFile
        onTimeoutListener = onTimeout

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(targetFile.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            isRecording = true

            // Schedule timeout cap
            timeoutHandler = Handler(Looper.getMainLooper()).apply {
                postDelayed({
                    if (isRecording) {
                        Log.d("KothonRecorder", "Auto-stopping after $maxDurationSeconds seconds cap")
                        onTimeoutListener?.invoke()
                    }
                }, maxDurationSeconds * 1000)
            }

            return targetFile
        } catch (e: Exception) {
            Log.e("KothonRecorder", "Failed to start MediaRecorder", e)
            cleanup()
            throw IOException("Failed to start audio recording: ${e.message}", e)
        }
    }

    fun stop(): File? {
        timeoutHandler?.removeCallbacksAndMessages(null)
        timeoutHandler = null

        if (!isRecording) {
            return currentOutputFile
        }

        val file = currentOutputFile
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e("KothonRecorder", "Error stopping MediaRecorder", e)
        } finally {
            cleanup()
        }
        return file
    }

    fun cancel() {
        timeoutHandler?.removeCallbacksAndMessages(null)
        timeoutHandler = null
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // ignore
        } finally {
            cleanup()
            currentOutputFile?.delete()
            currentOutputFile = null
        }
    }

    fun getMaxAmplitude(): Int {
        if (!isRecording) return 0
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun cleanup() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            // ignore
        }
        mediaRecorder = null
        isRecording = false
    }
}
