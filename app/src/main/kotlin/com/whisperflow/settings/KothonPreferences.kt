package com.whisperflow.settings

import android.content.Context
import android.content.SharedPreferences

class KothonPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var provider: String
        get() = prefs.getString(KEY_PROVIDER, PROVIDER_GEMINI) ?: PROVIDER_GEMINI
        set(value) = prefs.edit().putString(KEY_PROVIDER, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var isTranslateEnabled: Boolean
        get() = prefs.getBoolean(KEY_TRANSLATE, true)
        set(value) = prefs.edit().putBoolean(KEY_TRANSLATE, value).apply()

    var sourceLanguage: String
        get() = prefs.getString(KEY_SOURCE_LANG, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_SOURCE_LANG, value).apply()

    var targetLanguage: String
        get() = prefs.getString(KEY_TARGET_LANG, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_TARGET_LANG, value).apply()

    var tone: String
        get() = prefs.getString(KEY_TONE, TONE_PROFESSIONAL) ?: TONE_PROFESSIONAL
        set(value) = prefs.edit().putString(KEY_TONE, value).apply()

    var removeFillers: Boolean
        get() = prefs.getBoolean(KEY_REMOVE_FILLERS, true)
        set(value) = prefs.edit().putBoolean(KEY_REMOVE_FILLERS, value).apply()

    var fixGrammar: Boolean
        get() = prefs.getBoolean(KEY_FIX_GRAMMAR, true)
        set(value) = prefs.edit().putBoolean(KEY_FIX_GRAMMAR, value).apply()

    fun getRecentLanguages(): List<String> {
        val raw = prefs.getString(KEY_RECENT_LANGS, "auto,bn,en,hi,es") ?: "auto,bn,en,hi,es"
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun addRecentLanguage(code: String) {
        val current = getRecentLanguages().toMutableList()
        current.remove(code)
        current.add(0, code)
        val capped = current.take(6)
        prefs.edit().putString(KEY_RECENT_LANGS, capped.joinToString(",")).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "kothon_prefs"
        const val KEY_PROVIDER = "key_provider"
        const val KEY_API_KEY = "key_api_key"
        const val KEY_TRANSLATE = "key_translate"
        const val KEY_SOURCE_LANG = "key_source_lang"
        const val KEY_TARGET_LANG = "key_target_lang"
        const val KEY_RECENT_LANGS = "key_recent_langs"
        const val KEY_TONE = "key_tone"
        const val KEY_REMOVE_FILLERS = "key_remove_fillers"
        const val KEY_FIX_GRAMMAR = "key_fix_grammar"

        const val PROVIDER_GEMINI = "Gemini"
        const val PROVIDER_OPENAI = "OpenAI"
        const val PROVIDER_GROQ = "Groq"
        const val PROVIDER_NVIDIA = "NVIDIA"

        val PROVIDERS = arrayOf(PROVIDER_GEMINI, PROVIDER_OPENAI, PROVIDER_GROQ, PROVIDER_NVIDIA)

        const val TONE_PROFESSIONAL = "Professional Clarity"
        const val TONE_EXECUTIVE = "Executive & Formal"
        const val TONE_CASUAL = "Casual & Friendly"
        const val TONE_CONCISE = "Concise & Direct"
        const val TONE_BULLETS = "Structured Bullet Points"

        val TONES = arrayOf(TONE_PROFESSIONAL, TONE_EXECUTIVE, TONE_CASUAL, TONE_CONCISE, TONE_BULLETS)
    }
}
