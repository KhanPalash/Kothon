package com.whisperflow.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Phase 0 Device Spike Validation Utility
 *
 * This validates the requirements from new_plan.md Phase 0:
 * - Recognition service availability
 * - On-device recognition support (API 31+)
 * - Language support (bn-BD, bn-IN, en-US)
 * - Model download triggers (API 33+)
 *
 * Gate criteria:
 * - First partial latency p50 ≤ 1.5s, p95 ≤ 3s
 * - 60s session completion ≥ 90%
 * - Bangla accuracy within 10 points of ACCURATE mode
 */
object DeviceSpikeValidator {
    private const val TAG = "DeviceSpikeValidator"

    data class ValidationResult(
        val isRecognitionAvailable: Boolean,
        val isOnDeviceAvailable: Boolean,
        val supportedLanguages: List<String>,
        val deviceInfo: DeviceInfo,
        val errors: List<String> = emptyList()
    )

    data class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val androidVersion: Int,
        val buildFingerprint: String
    )

    data class LatencyMeasurement(
        val startTime: Long,
        val firstPartialTime: Long?,
        val firstFinalTime: Long?,
        val stopTime: Long
    ) {
        val firstPartialLatencyMs: Long?
            get() = firstPartialTime?.let { it - startTime }

        val firstFinalLatencyMs: Long?
            get() = firstFinalTime?.let { it - startTime }
    }

    /**
     * Check basic recognition availability
     */
    fun isRecognitionAvailable(context: Context): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Check on-device recognition availability (API 31+)
     */
    fun isOnDeviceRecognitionAvailable(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            try {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking on-device recognition", e)
                false
            }
        } else {
            false
        }
    }

    /**
     * Get device information for spike validation
     */
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.SDK_INT,
            buildFingerprint = Build.FINGERPRINT
        )
    }

    /**
     * Run full validation check
     */
    fun runValidation(context: Context): ValidationResult {
        val errors = mutableListOf<String>()

        val isAvailable = isRecognitionAvailable(context)
        if (!isAvailable) {
            errors.add("SpeechRecognizer not available on this device")
        }

        val isOnDevice = isOnDeviceRecognitionAvailable(context)

        // Test common languages
        val testLanguages = listOf("bn-BD", "bn-IN", "en-US", "en-GB")
        val supported = mutableListOf<String>()

        // Note: Actual language support check requires API 33+
        // For now, we log that these should be tested manually
        if (Build.VERSION.SDK_INT >= 33) {
            errors.add("API 33+ language support check not yet implemented. Test manually.")
        }

        return ValidationResult(
            isRecognitionAvailable = isAvailable,
            isOnDeviceAvailable = isOnDevice,
            supportedLanguages = supported,
            deviceInfo = getDeviceInfo(),
            errors = errors
        )
    }

    /**
     * Check if a specific language is supported (API 33+).
     * Returns null on older APIs or when the recognizer service cannot answer.
     */
    suspend fun checkLanguageSupport(
        context: Context,
        languageTag: String
    ): LanguageSupportResult? {
        if (Build.VERSION.SDK_INT < 33) return null
        return checkLanguageSupportApi33(context, languageTag)
    }

    @RequiresApi(33)
    private suspend fun checkLanguageSupportApi33(
        context: Context,
        languageTag: String
    ): LanguageSupportResult? = suspendCancellableCoroutine { continuation ->
        var recognizer: SpeechRecognizer? = null
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            }
            val callback = object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    val result = LanguageSupportResult(
                        language = languageTag,
                        installedOnDevice = support.installedOnDeviceLanguages.any { matches(it, languageTag) },
                        supportedOnDevice = support.supportedOnDeviceLanguages.any { matches(it, languageTag) },
                        supportedOnline = support.onlineLanguages.any { matches(it, languageTag) }
                    )
                    if (continuation.isActive) continuation.resume(result)
                    recognizer?.destroy()
                }

                override fun onError(error: Int) {
                    Log.w(TAG, "Language support check failed for $languageTag (error $error)")
                    if (continuation.isActive) continuation.resume(null)
                    recognizer?.destroy()
                }
            }
            recognizer.checkRecognitionSupport(
                intent,
                ContextCompat.getMainExecutor(context),
                callback
            )
            continuation.invokeOnCancellation { recognizer?.destroy() }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking language support for $languageTag", e)
            recognizer?.destroy()
            if (continuation.isActive) continuation.resume(null)
        }
    }

    data class LanguageSupportResult(
        val language: String,
        val installedOnDevice: Boolean,
        val supportedOnDevice: Boolean,
        val supportedOnline: Boolean
    )

    /**
     * Trigger the system speech model download prompt for a language (API 33+).
     */
    fun triggerModelDownload(context: Context, languageTag: String): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        return triggerModelDownloadApi33(context, languageTag)
    }

    @RequiresApi(33)
    private fun triggerModelDownloadApi33(context: Context, languageTag: String): Boolean {
        var recognizer: SpeechRecognizer? = null
        return try {
            recognizer = try {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } catch (e: Exception) {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            }
            recognizer.triggerModelDownload(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering model download for $languageTag", e)
            false
        } finally {
            try {
                recognizer?.destroy()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun matches(candidate: String, requested: String): Boolean {
        if (candidate.equals(requested, ignoreCase = true)) return true
        val candidateLanguage = candidate.substringBefore('-').substringBefore('_')
        val requestedLanguage = requested.substringBefore('-').substringBefore('_')
        return candidateLanguage.equals(requestedLanguage, ignoreCase = true)
    }

    /**
     * Format validation result for logging
     */
    fun formatValidationResult(result: ValidationResult): String {
        return buildString {
            appendLine("=== Device Spike Validation ===")
            appendLine("Device: ${result.deviceInfo.manufacturer} ${result.deviceInfo.model}")
            appendLine("Android: ${result.deviceInfo.androidVersion} (${Build.VERSION.RELEASE})")
            appendLine("Build: ${result.deviceInfo.buildFingerprint}")
            appendLine()
            appendLine("Recognition Available: ${result.isRecognitionAvailable}")
            appendLine("On-Device Available: ${result.isOnDeviceAvailable}")
            appendLine()
            if (result.supportedLanguages.isNotEmpty()) {
                appendLine("Supported Languages:")
                result.supportedLanguages.forEach { lang ->
                    appendLine("  - $lang")
                }
            }
            if (result.errors.isNotEmpty()) {
                appendLine()
                appendLine("Errors/Warnings:")
                result.errors.forEach { error ->
                    appendLine("  ! $error")
                }
            }
            appendLine()
            appendLine("=== Manual Testing Required ===")
            appendLine("1. Test bn-BD recognition (20 sentences)")
            appendLine("2. Test en-US recognition (20 sentences)")
            appendLine("3. Test mixed Bangla/English (20 sentences)")
            appendLine("4. Measure first partial latency (target: p50 ≤ 1.5s)")
            appendLine("5. Test 60s continuous dictation (target: 90%+ completion)")
            appendLine("6. Test in Notes, Chrome, WhatsApp, Gmail")
            appendLine("7. Verify password field voice disabled")
            appendLine("===============================")
        }
    }
}
