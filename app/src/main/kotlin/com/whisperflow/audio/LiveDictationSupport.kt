package com.whisperflow.audio

import com.whisperflow.settings.KothonPreferences

/**
 * Shared LIVE-mode helpers used by both the IME and the studio screen.
 *
 * The Android speech recognizer must never receive the sentinel values
 * `auto` / `original`; those are only meaningful to the AI refinement layer.
 */
object LiveDictationSupport {

    private val CJK_LANGUAGE_CODES = setOf("zh", "ja", "ko")

    /**
     * Common Android recognizer tags. A language code without a region often
     * fails or falls back to the device locale on some recognizer services,
     * so map the app's short codes to a stable BCP-47 tag.
     */
    private val SPEECH_TAGS = mapOf(
        "bn" to "bn-BD",
        "en" to "en-US",
        "hi" to "hi-IN",
        "es" to "es-ES",
        "ar" to "ar-SA",
        "fr" to "fr-FR",
        "de" to "de-DE",
        "ja" to "ja-JP",
        "zh" to "zh-CN",
        "ur" to "ur-PK",
        "pt" to "pt-BR",
        "ru" to "ru-RU",
        "id" to "id-ID",
        "it" to "it-IT",
        "ko" to "ko-KR",
        "tr" to "tr-TR",
        "vi" to "vi-VN",
        "th" to "th-TH",
        "ta" to "ta-IN",
        "te" to "te-IN",
        "mr" to "mr-IN",
        "gu" to "gu-IN",
        "pa" to "pa-IN",
        "ms" to "ms-MY",
        "nl" to "nl-NL",
        "pl" to "pl-PL",
        "sv" to "sv-SE",
        "fa" to "fa-IR",
        "el" to "el-GR",
        "he" to "he-IL",
        "da" to "da-DK",
        "fi" to "fi-FI",
        "no" to "nb-NO",
        "cs" to "cs-CZ",
        "ro" to "ro-RO",
        "hu" to "hu-HU",
        "uk" to "uk-UA",
        "sw" to "sw-KE",
        "ne" to "ne-NP",
        "si" to "si-LK",
        "my" to "my-MM",
        "km" to "km-KH",
        "fil" to "fil-PH"
    )

    /** True when a preference value can be sent to the speech recognizer. */
    fun isRealLanguage(code: String?): Boolean {
        val value = code?.trim().orEmpty()
        return value.isNotEmpty() &&
            !value.equals("auto", ignoreCase = true) &&
            !value.equals("original", ignoreCase = true)
    }

    /** Language subtag without region, e.g. `bn-BD` -> `bn`. */
    fun languageCode(tag: String): String =
        tag.trim().substringBefore('-').substringBefore('_').lowercase()

    /**
     * Converts an app language code to a recognizer tag. Already-regional tags
     * are returned unchanged so callers can pass a device locale directly.
     */
    fun toSpeechTag(code: String): String {
        val value = code.trim()
        if (!isRealLanguage(value)) return "bn-BD"
        if (value.contains('-') || value.contains('_')) return value
        return SPEECH_TAGS[value.lowercase()] ?: value
    }

    /**
     * Resolution order from new_plan.md §6.10:
     * 1. last-used real live language
     * 2. the user's explicit spoken language
     * 3. device locale
     * 4. Bangla (bn-BD) as the app's primary market default
     *
     * `auto` / `original` are never returned.
     */
    fun resolveLanguageTag(
        liveLanguage: String?,
        sourceLanguage: String?,
        deviceLanguageTag: String?
    ): String {
        if (isRealLanguage(liveLanguage)) return toSpeechTag(liveLanguage!!)
        if (isRealLanguage(sourceLanguage)) return toSpeechTag(sourceLanguage!!)

        val deviceTag = deviceLanguageTag?.trim().orEmpty()
        if (isRealLanguage(deviceTag)) {
            return if (deviceTag.contains('-') || deviceTag.contains('_')) deviceTag
            else toSpeechTag(deviceTag)
        }
        return "bn-BD"
    }

    /** CJK scripts do not use a space between dictated segments. */
    fun separatorFor(languageTag: String): String =
        if (languageCode(languageTag) in CJK_LANGUAGE_CODES) "" else " "

    /** Feature flag + explicit mode selection must both allow LIVE. */
    fun isLiveModeEnabled(preferences: KothonPreferences): Boolean =
        preferences.liveDictationEnabled &&
            preferences.dictationMode == KothonPreferences.MODE_LIVE

    fun buildSpeechConfig(
        preferences: KothonPreferences,
        languageTag: String
    ): SpeechConfig = SpeechConfig(
        languageTag = languageTag,
        preferOnDevice = preferences.livePreferOnDevice,
        partialResults = true,
        maxSessionMs = preferences.liveSessionMaxMs
    )
}
