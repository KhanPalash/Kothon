package com.whisperflow.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.whisperflow.model.LanguageCatalog
import com.whisperflow.settings.KothonPreferences
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class TranscriptionResult(
    val rawTranscript: String = "",
    val refinedText: String,
    val detectedLanguage: String = "Auto",
    val targetLanguage: String = "English",
    val tone: String = KothonPreferences.TONE_PROFESSIONAL
)

class TranscriptionClient(private val context: Context) {

    private val preferences = KothonPreferences(context)

    // Allowlists for input validation
    private val validProviders = setOf(
        KothonPreferences.PROVIDER_GEMINI,
        KothonPreferences.PROVIDER_OPENAI,
        KothonPreferences.PROVIDER_GROQ,
        KothonPreferences.PROVIDER_NVIDIA
    )
    private val validTones = KothonPreferences.TONES.toSet()
    private val validLangCodes = LanguageCatalog.ALL_LANGUAGES.map { it.code }.toSet() + setOf("auto", "original")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .certificatePinner(
            CertificatePinner.Builder()
                .add("generativelanguage.googleapis.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                .add("api.groq.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                .add("api.openai.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                .add("integrate.api.nvidia.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                .build()
        )
        .build()

    private fun validateProvider(provider: String): String =
        if (validProviders.contains(provider)) provider else KothonPreferences.PROVIDER_GEMINI

    private fun validateTone(tone: String): String =
        if (validTones.contains(tone)) tone else KothonPreferences.TONE_PROFESSIONAL

    private fun validateLangCode(code: String): String =
        if (validLangCodes.contains(code)) code else "auto"

    // Gemini active models with fallback chain
    val geminiFallbackModels = listOf(
        "gemini-3.8-flash",
        "gemini-3.5-flash",
        "gemini-3.5-flash-lite",
        "gemini-flash-latest",
        "gemini-flash-lite-latest"
    )

    fun isOnline(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Test connection to a specific provider with an API key
     */
    suspend fun testProviderConnection(provider: String, apiKey: String): Pair<Boolean, String> {
        if (!isOnline()) {
            return Pair(false, "Device is offline. Check your internet connection.")
        }
        return try {
            when (provider) {
                KothonPreferences.PROVIDER_GEMINI -> {
                    val key = apiKey
                    if (key.isBlank()) {
                        return Pair(false, "Gemini API key is empty.")
                    }
                    var success = false
                    var lastMsg = ""
                    for (model in geminiFallbackModels) {
                        try {
                            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model"
                            val request = Request.Builder()
                                .url(url)
                                .addHeader("x-goog-api-key", key)
                                .get()
                                .build()
                            httpClient.newCall(request).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    success = true
                                    lastMsg = "Gemini ($model) connected successfully! ✓"
                                    return@use
                                } else {
                                    lastMsg = "Gemini ($model): ${resp.code}"
                                }
                            }
                            if (success) return Pair(true, lastMsg)
                        } catch (e: Exception) {
                            lastMsg = e.message ?: "Failed"
                        }
                    }
                    Pair(success, lastMsg)
                }
                KothonPreferences.PROVIDER_NVIDIA -> {
                    if (apiKey.isBlank()) return Pair(false, "NVIDIA API key is empty.")
                    val request = Request.Builder()
                        .url("https://integrate.api.nvidia.com/v1/models")
                        .header("Authorization", "Bearer $apiKey")
                        .get()
                        .build()
                    httpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) Pair(true, "NVIDIA Nemotron connected successfully! ✓")
                        else Pair(false, "NVIDIA error: ${resp.code}")
                    }
                }
                KothonPreferences.PROVIDER_GROQ -> {
                    if (apiKey.isBlank()) return Pair(false, "Groq API key is empty.")
                    val request = Request.Builder()
                        .url("https://api.groq.com/openai/v1/models")
                        .header("Authorization", "Bearer $apiKey")
                        .get()
                        .build()
                    httpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) Pair(true, "Groq connected successfully! ✓")
                        else Pair(false, "Groq error: ${resp.code}")
                    }
                }
                KothonPreferences.PROVIDER_OPENAI -> {
                    if (apiKey.isBlank()) return Pair(false, "OpenAI API key is empty.")
                    val request = Request.Builder()
                        .url("https://api.openai.com/v1/models")
                        .header("Authorization", "Bearer $apiKey")
                        .get()
                        .build()
                    httpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) Pair(true, "OpenAI connected successfully! ✓")
                        else Pair(false, "OpenAI error: ${resp.code}")
                    }
                }
                else -> Pair(false, "Unknown provider")
            }
        } catch (e: Exception) {
            Pair(false, e.localizedMessage ?: "Connection error")
        }
    }

    @Throws(IOException::class)
    suspend fun transcribeAndRewrite(
        audioFile: File,
        sourceLanguageCode: String = preferences.sourceLanguage,
        targetLanguageCode: String = preferences.targetLanguage,
        tone: String = preferences.tone,
        removeFillers: Boolean = preferences.removeFillers,
        fixGrammar: Boolean = preferences.fixGrammar
    ): String {
        val result = processAudio(audioFile, sourceLanguageCode, targetLanguageCode, tone, removeFillers, fixGrammar)
        return result.refinedText
    }

    @Throws(IOException::class)
    suspend fun refineText(
        rawText: String,
        sourceLanguageCode: String = preferences.sourceLanguage,
        targetLanguageCode: String = preferences.targetLanguage,
        tone: String = preferences.tone,
        removeFillers: Boolean = preferences.removeFillers,
        fixGrammar: Boolean = preferences.fixGrammar
    ): String {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return ""
        if (!isOnline()) {
            return trimmed
        }

        val provider = validateProvider(preferences.provider)
        val customKey = preferences.getApiKeyForProvider(provider).trim()
        val sourceLangCode = validateLangCode(sourceLanguageCode)
        val targetLangCode = validateLangCode(targetLanguageCode)
        val validatedTone = validateTone(tone)
        val sourceLangObj = LanguageCatalog.findByCode(sourceLangCode)
        val targetLangObj = LanguageCatalog.findByCode(targetLangCode)

        return try {
            when (provider) {
                KothonPreferences.PROVIDER_NVIDIA -> {
                    if (customKey.isBlank()) throw IOException("NVIDIA API key is missing.")
                    refineWithNvidiaNim(trimmed, customKey, sourceLangObj.name, targetLangObj.name, targetLanguageCode, tone, removeFillers, fixGrammar).refinedText
                }
                KothonPreferences.PROVIDER_GROQ -> {
                    if (customKey.isBlank()) throw IOException("Groq API key is missing.")
                    refineWithGroq(trimmed, customKey, sourceLangObj.name, targetLangObj.name, targetLanguageCode, tone, removeFillers, fixGrammar).refinedText
                }
                KothonPreferences.PROVIDER_OPENAI -> {
                    if (customKey.isBlank()) throw IOException("OpenAI API key is missing.")
                    refineWithOpenAi(trimmed, customKey, sourceLangObj.name, targetLangObj.name, targetLanguageCode, tone, removeFillers, fixGrammar).refinedText
                }
                else -> {
                    refineWithGemini(trimmed, customKey, sourceLangObj.name, targetLangObj.name, targetLanguageCode, tone, removeFillers, fixGrammar).refinedText
                }
            }
        } catch (e: Exception) {
            Log.w("TranscriptionClient", "Text refinement failed via $provider: ${e.message}")
            if (provider != KothonPreferences.PROVIDER_GEMINI) {
                try {
                    refineWithGemini(trimmed, "", sourceLangObj.name, targetLangObj.name, targetLanguageCode, tone, removeFillers, fixGrammar).refinedText
                } catch (fallbackEx: Exception) {
                    throw IOException("$provider error: ${e.message}. (Gemini fallback: ${fallbackEx.message})", e)
                }
            } else {
                throw (e as? IOException) ?: IOException(e.message ?: "Refinement error", e)
            }
        }
    }

    @Throws(IOException::class)
    suspend fun processAudio(
        audioFile: File,
        sourceLanguageCode: String = preferences.sourceLanguage,
        targetLanguageCode: String = preferences.targetLanguage,
        tone: String = preferences.tone,
        removeFillers: Boolean = preferences.removeFillers,
        fixGrammar: Boolean = preferences.fixGrammar
    ): TranscriptionResult {
        if (!isOnline()) {
            throw IOException("Device is offline. Check your internet connection or use keyboard fallback.")
        }

        if (!audioFile.exists() || audioFile.length() < 2000L) {
            throw IOException("Audio recording was too short or empty. Please hold the mic and speak clearly.")
        }

        val provider = validateProvider(preferences.provider)
        val customKey = preferences.getApiKeyForProvider(provider).trim()
        val sourceLangCode = validateLangCode(sourceLanguageCode)
        val targetLangCode = validateLangCode(targetLanguageCode)
        val validatedTone = validateTone(tone)
        val sourceLangObj = LanguageCatalog.findByCode(sourceLangCode)
        val targetLangObj = LanguageCatalog.findByCode(targetLangCode)

        return try {
            when (provider) {
                KothonPreferences.PROVIDER_NVIDIA -> {
                    if (customKey.isBlank()) {
                        throw IOException("NVIDIA API key is missing. Please enter your NVIDIA NIM API key in Settings (gear icon) or switch to Gemini (Built-in).")
                    }
                    callNvidiaNemotron(audioFile, customKey, sourceLangObj.name, targetLangObj.name, targetLangCode, validatedTone, removeFillers, fixGrammar)
                }
                KothonPreferences.PROVIDER_GROQ -> {
                    if (customKey.isBlank()) {
                        throw IOException("Groq API key is missing. Please enter your Groq API key in Settings (gear icon) or switch to Gemini (Built-in).")
                    }
                    callGroq(audioFile, customKey, sourceLangObj.name, targetLangObj.name, targetLangCode, validatedTone, removeFillers, fixGrammar)
                }
                KothonPreferences.PROVIDER_OPENAI -> {
                    if (customKey.isBlank()) {
                        throw IOException("OpenAI API key is missing. Please enter your OpenAI API key in Settings (gear icon) or switch to Gemini (Built-in).")
                    }
                    callOpenAi(audioFile, customKey, sourceLangObj.name, targetLangObj.name, targetLangCode, validatedTone, removeFillers, fixGrammar)
                }
                else -> {
                    callGeminiWithFallbacks(audioFile, customKey, sourceLangObj.name, targetLangObj.name, targetLangCode, validatedTone, removeFillers, fixGrammar)
                }
            }
        } catch (e: Exception) {
            Log.w("TranscriptionClient", "$provider failed: ${e.message}", e)
            val msg = e.message ?: ""
            if (provider != KothonPreferences.PROVIDER_GEMINI) {
                if (msg.contains("API key is missing", ignoreCase = true)) {
                    throw (e as? IOException) ?: IOException(msg, e)
                }
                // If custom provider had a service or network error, attempt smooth Gemini fallback
                try {
                    callGeminiWithFallbacks(audioFile, "", sourceLangObj.name, targetLangObj.name, targetLanguageCode, tone, removeFillers, fixGrammar)
                } catch (fallbackEx: Exception) {
                    throw IOException("$provider error: ${e.message}. (Gemini fallback: ${fallbackEx.message})", e)
                }
            } else {
                throw (e as? IOException) ?: IOException(msg.ifBlank { "Transcription error" }, e)
            }
        }
    }

    /**
     * Gemini Multi-Model Fallback Chain
     */
    private fun callGeminiWithFallbacks(
        audioFile: File,
        customKey: String,
        sourceLangName: String,
        targetLangName: String,
        targetLangCode: String,
        tone: String,
        removeFillers: Boolean,
        fixGrammar: Boolean
    ): TranscriptionResult {
        val apiKey = customKey
        if (apiKey.isEmpty()) {
            throw IOException("Gemini API key is not configured. Please enter your API key in Settings.")
        }

        val audioBytes = audioFile.readBytes()
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val translationInstruction = if (targetLangCode == "original") {
            "Keep the output in the same detected language as spoken. Do NOT translate."
        } else {
            "Translate the refined speech into $targetLangName."
        }

        val validatedTone = validateTone(tone)
        val systemInstructionText = """
            You are Kothon AI, a voice transcription and professional refinement engine.
            Your task is strictly to process the provided audio file.
            RULES:
            1. Listen to the human voice in the audio and transcribe what was said.
            2. If the audio is silent, noisy, or contains NO spoken human words, return strictly:
               {"detected_language": "None", "raw_transcript": "", "refined_text": ""}
            3. NEVER transcribe or echo instructions, prompts, or system messages.
            4. Remove stuttering, false starts, and filler words (e.g. 'um', 'uh', 'mane', 'ar ki', 'you know') if requested.
            5. Correct grammar, punctuation, and refine tone into $validatedTone.
            6. $translationInstruction
            7. Return ONLY valid JSON with keys: "detected_language", "raw_transcript", "refined_text".
               Do not return nested language arrays. Output the final refined sentence directly in "refined_text".
        """.trimIndent()

        val userPrompt = "Transcribe the attached audio. Expected language: $sourceLangName. Target language: $targetLangName. Tone: $validatedTone."

        val jsonBody = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstructionText)
                    })
                })
            })
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    val parts = JSONArray().apply {
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "audio/mp4")
                                put("data", base64Audio)
                            })
                        })
                        put(JSONObject().apply {
                            put("text", userPrompt)
                        })
                    }
                    put("parts", parts)
                })
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 1200)
            })
        }

        var lastException: Exception? = null

        for (model in geminiFallbackModels) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", apiKey)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.w("TranscriptionClient", "Gemini $model failed: ${response.code} $responseBody. Trying next fallback...")
                        throw IOException("Gemini API error (${response.code}) on $model: $responseBody")
                    }

                    val respJson = JSONObject(responseBody)
                    val candidates = respJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        val sb = java.lang.StringBuilder()
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.optBoolean("thought", false)) continue
                            val text = part.optString("text", "")
                            if (text.isNotEmpty()) {
                                sb.append(text)
                            }
                        }
                        val resultText = sb.toString().trim()
                        if (resultText.isNotEmpty()) {
                            val parsed = parseUniversalOutput(resultText, targetLangName, targetLangCode, tone)
                            if (parsed.rawTranscript.contains("Kothon AI", ignoreCase = true) ||
                                parsed.rawTranscript.contains("WhisperFlow", ignoreCase = true) ||
                                parsed.refinedText.isBlank()) {
                                throw IOException("No speech detected. Please hold the mic and speak clearly.")
                            }
                            return parsed
                        }
                    }
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        throw (lastException as? IOException) ?: IOException("Gemini transcription failed across all fallback models: ${lastException?.message}", lastException)
    }

    private fun refineWithGemini(
        rawText: String,
        customKey: String,
        sourceLangName: String,
        targetLangName: String,
        targetLangCode: String,
        tone: String,
        removeFillers: Boolean,
        fixGrammar: Boolean
    ): TranscriptionResult {
        val apiKey = customKey
        if (apiKey.isEmpty()) {
            throw IOException("Gemini API key is not configured. Please enter your API key in Settings.")
        }

        val translationInstruction = if (targetLangCode == "original") {
            "Keep the output in the same detected language as spoken. Do NOT translate."
        } else {
            "Translate the refined speech into $targetLangName."
        }

        val validatedTone = validateTone(tone)
        val systemInstructionText = """
            You are Kothon AI, a precision voice transcription and professional refinement engine.
            Your task is strictly to polish the provided raw spoken text transcript.
            RULES:
            1. Punctuate, capitalize, and polish into tone: $validatedTone.
            2. If Remove Fillers is true, strip stuttering and filler words (e.g. 'um', 'uh', 'mane', 'ar ki', 'you know').
            3. If Fix Grammar is true, correct grammar, spelling, and tense agreement.
            4. $translationInstruction
            5. Return strictly JSON: {"detected_language": "<Language>", "refined_text": "<Output text>"}
               Do not return nested language arrays. Output the final refined sentence directly in "refined_text".
        """.trimIndent()

        val userPrompt = "Refine the transcript: \"$rawText\". Expected language: $sourceLangName. Target language: $targetLangName. Tone: $validatedTone. Remove fillers: $removeFillers. Fix grammar: $fixGrammar."

        val jsonBody = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstructionText)
                    })
                })
            })
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    val parts = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", userPrompt)
                        })
                    }
                    put("parts", parts)
                })
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.15)
                put("maxOutputTokens", 1000)
            })
        }

        var lastException: Exception? = null

        for (model in geminiFallbackModels) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", apiKey)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val errMsg = try {
                            JSONObject(responseBody).getJSONObject("error").getString("message")
                        } catch (e: Exception) {
                            responseBody
                        }
                        if (response.code == 404 || response.code == 429 || response.code == 503) {
                            Log.w("TranscriptionClient", "Gemini text model $model returned ${response.code}, falling back...")
                            return@use
                        }
                        throw IOException("Gemini error (${response.code}): $errMsg")
                    }

                    val jsonResponse = JSONObject(responseBody)
                    val candidates = jsonResponse.optJSONArray("candidates") ?: JSONArray()
                    if (candidates.length() > 0) {
                        val contentObj = candidates.getJSONObject(0).getJSONObject("content")
                        val parts = contentObj.getJSONArray("parts")
                        val sb = StringBuilder()
                        for (p in 0 until parts.length()) {
                            val part = parts.getJSONObject(p)
                            if (part.optBoolean("thought", false)) continue
                            val textPart = part.optString("text", "")
                            if (textPart.isNotBlank()) {
                                sb.append(textPart)
                            }
                        }
                        val resultText = sb.toString().trim()
                        if (resultText.isNotEmpty()) {
                            return parseUniversalOutput(resultText, targetLangName, targetLangCode, tone).copy(rawTranscript = rawText)
                        }
                    }
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        throw (lastException as? IOException) ?: IOException("Gemini text refinement failed across fallback models: ${lastException?.message}", lastException)
    }

    /**
     * NVIDIA Nemotron-3.5-Transcribe-30B-A4B Integration
     */
    private fun callNvidiaNemotron(
        audioFile: File,
        apiKey: String,
        sourceLangName: String,
        targetLangName: String,
        targetLangCode: String,
        tone: String,
        removeFillers: Boolean,
        fixGrammar: Boolean
    ): TranscriptionResult {
        if (apiKey.isEmpty()) {
            throw IOException("NVIDIA API key is missing. Please enter your NVIDIA NIM API key in Settings or switch to Gemini.")
        }

        val nemotronModels = listOf(
            "nvidia/NVIDIA-Nemotron-3.5-Transcribe-30B-A4B",
            "nvidia/nemotron-3.5-transcribe-30b-a4b"
        )

        var rawTranscript = ""
        var lastNvidiaEx: Exception? = null

        // 1. Try NVIDIA Nemotron Speech-to-Text API
        for (modelName in nemotronModels) {
            try {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "audio.m4a", audioFile.asRequestBody("audio/m4a".toMediaType()))
                    .addFormDataPart("model", modelName)
                    .addFormDataPart("prompt", "Transcribe the audio speech accurately in $targetLangName with professional tone.")
                    .build()

                val request = Request.Builder()
                    .url("https://integrate.api.nvidia.com/v1/audio/transcriptions")
                    .header("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        rawTranscript = JSONObject(body).optString("text", "").trim()
                        if (rawTranscript.isNotBlank()) return@use
                    } else {
                        Log.w("TranscriptionClient", "NVIDIA Nemotron $modelName transcriptions endpoint returned ${response.code}: $body")
                    }
                }
                if (rawTranscript.isNotBlank()) break
            } catch (e: Exception) {
                lastNvidiaEx = e
            }
        }

        // If audio transcription endpoint returned error or needs LLM refinement:
        if (rawTranscript.isBlank()) {
            // Use Gemini fallback for audio transcription, then refine with NVIDIA Nemotron LLM
            val geminiResult = callGeminiWithFallbacks(audioFile, "", sourceLangName, targetLangName, "original", tone, false, false)
            rawTranscript = geminiResult.rawTranscript.ifBlank { geminiResult.refinedText }
        }

        if (rawTranscript.isBlank()) {
            throw IOException("No speech detected. Please speak into the mic.")
        }

        // 2. Refine & Translate using NVIDIA NIM Chat Completion
        return refineWithNvidiaNim(rawTranscript, apiKey, sourceLangName, targetLangName, targetLangCode, tone, removeFillers, fixGrammar)
    }

    private fun refineWithNvidiaNim(
        rawTranscript: String,
        apiKey: String,
        sourceLangName: String,
        targetLangName: String,
        targetLangCode: String,
        tone: String,
        removeFillers: Boolean,
        fixGrammar: Boolean
    ): TranscriptionResult {
        val translationPrompt = if (targetLangCode == "original") {
            "Keep the output in the same language as spoken."
        } else {
            "Translate into $targetLangName."
        }

        val prompt = """
            You are Kothon AI powered by NVIDIA Nemotron.
            Refine this transcript into clean, professional text ('shikrito' style).
            Tone: $tone.
            Remove filler words: $removeFillers. Fix grammar: $fixGrammar.
            $translationPrompt
            Raw Transcript: "$rawTranscript"
            Return strictly JSON: {"detected_language": "<Language>", "refined_text": "<Output text>"}
            DO NOT return language arrays like {"Bengali": ["..."]}. Return a single string in "refined_text".
        """.trimIndent()

        val chatJson = JSONObject().apply {
            put("model", "meta/llama-3.3-70b-instruct")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.15)
        }

        val chatRequest = Request.Builder()
            .url("https://integrate.api.nvidia.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(chatJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return httpClient.newCall(chatRequest).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errMsg = try { JSONObject(body).getJSONObject("error").getString("message") } catch (e: Exception) { body }
                throw IOException("NVIDIA NIM failed (${response.code}): $errMsg")
            }
            val choices = JSONObject(body).getJSONArray("choices")
            val content = choices.getJSONObject(0).getJSONObject("message").getString("content").trim()
            parseUniversalOutput(content, targetLangName, targetLangCode, tone).copy(rawTranscript = rawTranscript)
        }
    }

    private fun callOpenAi(
        audioFile: File,
        apiKey: String,
        sourceLangName: String,
        targetLangName: String,
        targetLangCode: String,
        tone: String,
        removeFillers: Boolean,
        fixGrammar: Boolean
    ): TranscriptionResult {
        if (apiKey.isEmpty()) {
            throw IOException("OpenAI API key is missing. Please enter your API key in Settings or switch to Gemini.")
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.m4a", audioFile.asRequestBody("audio/m4a".toMediaType()))
            .addFormDataPart("model", "whisper-1")
            .build()

        val whisperRequest = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        val rawTranscript = httpClient.newCall(whisperRequest).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errMsg = try { JSONObject(body).getJSONObject("error").getString("message") } catch (e: Exception) { body }
                throw IOException("OpenAI Whisper failed (${response.code}): $errMsg")
            }
            JSONObject(body).optString("text", "").trim()
        }

        if (rawTranscript.isBlank()) {
            throw IOException("No speech detected by OpenAI Whisper. Please speak clearly into the mic.")
        }

        return refineWithOpenAi(rawTranscript, apiKey, sourceLangName, targetLangName, targetLangCode, tone, removeFillers, fixGrammar)
    }

    private fun refineWithOpenAi(
        rawTranscript: String,
        apiKey: String,
        sourceLangName: String,
        targetLangName: String,
        targetLangCode: String,
        tone: String,
        removeFillers: Boolean,
        fixGrammar: Boolean
    ): TranscriptionResult {
        val translationPrompt = if (targetLangCode == "original") {
            "Keep the output in the same language as spoken."
        } else {
            "Translate into $targetLangName."
        }

        val prompt = """
            Refine this transcript into clean, professional text ('shikrito' style).
            Tone: $tone.
            Remove filler words: $removeFillers. Fix grammar: $fixGrammar.
            $translationPrompt
            Raw Transcript: "$rawTranscript"
            Return strictly JSON: {"detected_language": "<Language>", "refined_text": "<Output text>"}
            DO NOT return language arrays like {"English": ["..."]}. Return single string in "refined_text".
        """.trimIndent()

        val chatJson = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.15)
        }

        val chatRequest = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(chatJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return httpClient.newCall(chatRequest).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errMsg = try { JSONObject(body).getJSONObject("error").getString("message") } catch (e: Exception) { body }
                throw IOException("OpenAI GPT refinement failed (${response.code}): $errMsg")
            }
            val choices = JSONObject(body).getJSONArray("choices")
            val content = choices.getJSONObject(0).getJSONObject("message").getString("content").trim()
            parseUniversalOutput(content, targetLangName, targetLangCode, tone).copy(rawTranscript = rawTranscript)
        }
    }

    private fun callGroq(
        audioFile: File,
        apiKey: String,
        sourceLangName: String,
        targetLangName: String,
        targetLangCode: String,
        tone: String,
        removeFillers: Boolean,
        fixGrammar: Boolean
    ): TranscriptionResult {
        if (apiKey.isEmpty()) {
            throw IOException("Groq API key is missing. Please enter your Groq API key in Settings or switch to Gemini.")
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.m4a", audioFile.asRequestBody("audio/m4a".toMediaType()))
            .addFormDataPart("model", "whisper-large-v3")
            .build()

        val whisperRequest = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        val rawTranscript = httpClient.newCall(whisperRequest).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errMsg = try { JSONObject(body).getJSONObject("error").getString("message") } catch (e: Exception) { body }
                throw IOException("Groq Whisper failed (${response.code}): $errMsg")
            }
            JSONObject(body).optString("text", "").trim()
        }

        if (rawTranscript.isBlank()) {
            throw IOException("No speech detected by Groq Whisper. Please speak clearly into the mic.")
        }

        return refineWithGroq(rawTranscript, apiKey, sourceLangName, targetLangName, targetLangCode, tone, removeFillers, fixGrammar)
    }

    private fun refineWithGroq(
        rawTranscript: String,
        apiKey: String,
        sourceLangName: String,
        targetLangName: String,
        targetLangCode: String,
        tone: String,
        removeFillers: Boolean,
        fixGrammar: Boolean
    ): TranscriptionResult {
        val translationPrompt = if (targetLangCode == "original") {
            "Keep the output in the same language as spoken."
        } else {
            "Translate into $targetLangName."
        }

        val prompt = """
            Refine this transcript into clean, professional text ('shikrito' style).
            Tone: $tone.
            Remove filler words: $removeFillers. Fix grammar: $fixGrammar.
            $translationPrompt
            Raw Transcript: "$rawTranscript"
            Return strictly JSON: {"detected_language": "<Language>", "refined_text": "<Output text>"}
            DO NOT return language arrays like {"English": ["..."]}. Return single string in "refined_text".
        """.trimIndent()

        val chatJson = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.15)
        }

        val chatRequest = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(chatJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return httpClient.newCall(chatRequest).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errMsg = try { JSONObject(body).getJSONObject("error").getString("message") } catch (e: Exception) { body }
                throw IOException("Groq refinement failed (${response.code}): $errMsg")
            }
            val choices = JSONObject(body).getJSONArray("choices")
            val content = choices.getJSONObject(0).getJSONObject("message").getString("content").trim()
            parseUniversalOutput(content, targetLangName, targetLangCode, tone).copy(rawTranscript = rawTranscript)
        }
    }

    /**
     * Universal Parser: handles any JSON format:
     * - {"refined_text": "..."}
     * - {"Bengali": ["..."], "English": ["..."]}
     * - {"English": "...", "Bengali": "..."}
     * - Plain string with or without quotes
     */
    fun parseUniversalOutput(
        rawOutput: String,
        targetLangName: String,
        targetLangCode: String,
        tone: String
    ): TranscriptionResult {
        val clean = rawOutput.replace("```json", "").replace("```", "").trim()

        try {
            if (clean.startsWith("{") && clean.endsWith("}")) {
                val json = JSONObject(clean)

                var detected = json.optString("detected_language", "Auto")
                var raw = json.optString("raw_transcript", "")
                var refined = ""

                // 1. Check if user wants English
                val isEnglishTarget = targetLangCode.equals("en", ignoreCase = true) || targetLangName.contains("English", ignoreCase = true)
                val isBengaliTarget = targetLangCode.equals("bn", ignoreCase = true) || targetLangName.contains("Bengali", ignoreCase = true)

                if (isEnglishTarget) {
                    refined = extractStringOrArray(json, "English")
                    if (refined.isBlank()) refined = extractStringOrArray(json, "english")
                } else if (isBengaliTarget) {
                    refined = extractStringOrArray(json, "Bengali")
                    if (refined.isBlank()) refined = extractStringOrArray(json, "bengali")
                    if (refined.isBlank()) refined = extractStringOrArray(json, "Bangla")
                }

                if (refined.isBlank()) {
                    refined = extractStringOrArray(json, "refined_text")
                }
                if (refined.isBlank()) {
                    refined = extractStringOrArray(json, "text")
                }
                if (refined.isBlank()) {
                    refined = extractStringOrArray(json, "translation")
                }

                // If still empty, check any key's value that isn't detected_language / raw_transcript
                if (refined.isBlank()) {
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key != "detected_language" && key != "raw_transcript") {
                            val candidate = extractStringOrArray(json, key)
                            if (candidate.isNotBlank()) {
                                refined = candidate
                                break
                            }
                        }
                    }
                }

                if (refined.isNotBlank()) {
                    return TranscriptionResult(
                        rawTranscript = raw,
                        refinedText = refined,
                        detectedLanguage = detected,
                        targetLanguage = targetLangName,
                        tone = tone
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("TranscriptionClient", "JSON parse error, falling back to regex clean", e)
        }

        // Regex fallback: if text contains {"English": ["..."]}
        val englishMatch = Regex(""""English"\s*:\s*\[\s*"([^"]+)"\s*\]""").find(clean)
        val bengaliMatch = Regex(""""Bengali"\s*:\s*\[\s*"([^"]+)"\s*\]""").find(clean)

        val isEnglishTarget = targetLangCode.equals("en", ignoreCase = true) || targetLangName.contains("English", ignoreCase = true)
        if (isEnglishTarget && englishMatch != null) {
            return TranscriptionResult(
                refinedText = englishMatch.groupValues[1],
                detectedLanguage = "Bengali",
                targetLanguage = "English",
                tone = tone
            )
        }
        if (!isEnglishTarget && bengaliMatch != null) {
            return TranscriptionResult(
                refinedText = bengaliMatch.groupValues[1],
                detectedLanguage = "Bengali",
                targetLanguage = targetLangName,
                tone = tone
            )
        }

        // Clean any lingering JSON syntax
        val plainText = clean
            .replace(Regex("""^\{.*?"refined_text"\s*:\s*""""), "")
            .replace(Regex("""^\{.*?"(English|Bengali)"\s*:\s*\[?"?"""), "")
            .replace(Regex("""["\]\}]+$"""), "")
            .trim()

        return TranscriptionResult(
            rawTranscript = "",
            refinedText = if (plainText.isNotBlank()) plainText else clean,
            detectedLanguage = "Detected",
            targetLanguage = targetLangName,
            tone = tone
        )
    }

    private fun extractStringOrArray(json: JSONObject, key: String): String {
        if (!json.has(key)) return ""
        val obj = json.get(key)
        return when (obj) {
            is JSONArray -> {
                val list = mutableListOf<String>()
                for (i in 0 until obj.length()) {
                    list.add(obj.optString(i, ""))
                }
                list.filter { it.isNotBlank() }.joinToString(" ")
            }
            is String -> obj.trim()
            else -> obj.toString().trim()
        }
    }
}
