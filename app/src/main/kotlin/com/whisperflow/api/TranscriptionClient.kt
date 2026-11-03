package com.whisperflow.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.whisperflow.model.LanguageCatalog
import com.whisperflow.settings.KothonPreferences
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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    private fun validateProvider(provider: String): String =
        if (validProviders.contains(provider)) provider else KothonPreferences.PROVIDER_GEMINI

    private fun validateTone(tone: String): String =
        if (validTones.contains(tone)) tone else KothonPreferences.TONE_PROFESSIONAL

    private fun validateLangCode(code: String): String =
        if (validLangCodes.contains(code)) code else "auto"

    fun sanitizeApiKey(rawKey: String): String {
        return rawKey
            .trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()
            .trim('"', '\'', ' ', '\n', '\r', '\t')
    }

    // Gemini active models with fallback chain
    val geminiFallbackModels = listOf(
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash",
        "gemini-flash-latest",
        "gemini-flash-lite-latest",
        "gemini-3.8-flash",
        "gemini-3.5-flash",
        "gemini-3.5-flash-lite"
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
        val cleanKey = sanitizeApiKey(apiKey)
        return try {
            when (provider) {
                KothonPreferences.PROVIDER_GEMINI -> {
                    if (cleanKey.isBlank()) {
                        return Pair(false, "Gemini API key is empty.")
                    }
                    var success = false
                    var lastMsg = ""
                    for (model in geminiFallbackModels) {
                        try {
                            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model"
                            val request = Request.Builder()
                                .url(url)
                                .addHeader("x-goog-api-key", cleanKey)
                                .get()
                                .build()
                            httpClient.newCall(request).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    success = true
                                    lastMsg = "Gemini ($model) connected! ✓"
                                    return@use
                                } else {
                                    lastMsg = "Gemini ($model): HTTP ${resp.code}"
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
                    if (cleanKey.isBlank()) return Pair(false, "NVIDIA API key is empty.")
                    val request = Request.Builder()
                        .url("https://integrate.api.nvidia.com/v1/models")
                        .header("Authorization", "Bearer $cleanKey")
                        .get()
                        .build()
                    httpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) Pair(true, "NVIDIA NIM connected! ✓")
                        else Pair(false, "NVIDIA error: HTTP ${resp.code}")
                    }
                }
                KothonPreferences.PROVIDER_GROQ -> {
                    if (cleanKey.isBlank()) return Pair(false, "Groq API key is empty.")
                    val request = Request.Builder()
                        .url("https://api.groq.com/openai/v1/models")
                        .header("Authorization", "Bearer $cleanKey")
                        .get()
                        .build()
                    httpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) Pair(true, "Groq Whisper + Llama connected! ✓")
                        else Pair(false, "Groq error: HTTP ${resp.code}")
                    }
                }
                KothonPreferences.PROVIDER_OPENAI -> {
                    if (cleanKey.isBlank()) return Pair(false, "OpenAI API key is empty.")
                    val request = Request.Builder()
                        .url("https://api.openai.com/v1/models")
                        .header("Authorization", "Bearer $cleanKey")
                        .get()
                        .build()
                    httpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) Pair(true, "OpenAI Whisper + GPT connected! ✓")
                        else Pair(false, "OpenAI error: HTTP ${resp.code}")
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
        val customKey = sanitizeApiKey(preferences.getApiKeyForProvider(provider))
        val sourceLangCode = validateLangCode(sourceLanguageCode)
        val effectiveTargetCode = if (!preferences.isTranslateEnabled) "original" else validateLangCode(targetLanguageCode)
        val validatedTone = validateTone(tone)
        val sourceLangObj = LanguageCatalog.findByCode(sourceLangCode)
        val targetLangObj = LanguageCatalog.findByCode(effectiveTargetCode)

        return try {
            when (provider) {
                KothonPreferences.PROVIDER_NVIDIA -> {
                    if (customKey.isBlank()) throw IOException("NVIDIA API key is missing. Tap 'Get API' or enter your key.")
                    refineWithNvidiaNim(trimmed, customKey, sourceLangObj.name, targetLangObj.name, effectiveTargetCode, validatedTone, removeFillers, fixGrammar).refinedText
                }
                KothonPreferences.PROVIDER_GROQ -> {
                    if (customKey.isBlank()) throw IOException("Groq API key is missing. Tap 'Get API' or enter your key.")
                    refineWithGroq(trimmed, customKey, sourceLangObj.name, targetLangObj.name, effectiveTargetCode, validatedTone, removeFillers, fixGrammar).refinedText
                }
                KothonPreferences.PROVIDER_OPENAI -> {
                    if (customKey.isBlank()) throw IOException("OpenAI API key is missing. Tap 'Get API' or enter your key.")
                    refineWithOpenAi(trimmed, customKey, sourceLangObj.name, targetLangObj.name, effectiveTargetCode, validatedTone, removeFillers, fixGrammar).refinedText
                }
                else -> {
                    refineWithGemini(trimmed, customKey, sourceLangObj.name, targetLangObj.name, effectiveTargetCode, validatedTone, removeFillers, fixGrammar).refinedText
                }
            }
        } catch (e: Exception) {
            Log.w("TranscriptionClient", "Text refinement failed via $provider: ${e.message}")
            val geminiBackupKey = sanitizeApiKey(preferences.getApiKeyForProvider(KothonPreferences.PROVIDER_GEMINI))
            if (provider != KothonPreferences.PROVIDER_GEMINI && geminiBackupKey.isNotBlank()) {
                try {
                    refineWithGemini(trimmed, geminiBackupKey, sourceLangObj.name, targetLangObj.name, effectiveTargetCode, validatedTone, removeFillers, fixGrammar).refinedText
                } catch (_: Exception) {
                    throw (e as? IOException) ?: IOException("$provider error: ${e.message}", e)
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
        val customKey = sanitizeApiKey(preferences.getApiKeyForProvider(provider))
        val sourceLangCode = validateLangCode(sourceLanguageCode)
        val effectiveTargetCode = if (!preferences.isTranslateEnabled) "original" else validateLangCode(targetLanguageCode)
        val validatedTone = validateTone(tone)
        val sourceLangObj = LanguageCatalog.findByCode(sourceLangCode)
        val targetLangObj = LanguageCatalog.findByCode(effectiveTargetCode)

        return try {
            when (provider) {
                KothonPreferences.PROVIDER_NVIDIA -> {
                    if (customKey.isBlank()) {
                        throw IOException("NVIDIA API key is missing. Tap 'Get API' on the Home screen to get a key.")
                    }
                    callNvidiaNemotron(audioFile, customKey, sourceLangCode, sourceLangObj.name, targetLangObj.name, effectiveTargetCode, validatedTone, removeFillers, fixGrammar)
                }
                KothonPreferences.PROVIDER_GROQ -> {
                    if (customKey.isBlank()) {
                        throw IOException("Groq API key is missing. Tap 'Get API' on the Home screen to get a key.")
                    }
                    callGroq(audioFile, customKey, sourceLangCode, sourceLangObj.name, targetLangObj.name, effectiveTargetCode, validatedTone, removeFillers, fixGrammar)
                }
                KothonPreferences.PROVIDER_OPENAI -> {
                    if (customKey.isBlank()) {
                        throw IOException("OpenAI API key is missing. Tap 'Get API' on the Home screen to get a key.")
                    }
                    callOpenAi(audioFile, customKey, sourceLangCode, sourceLangObj.name, targetLangObj.name, effectiveTargetCode, validatedTone, removeFillers, fixGrammar)
                }
                else -> {
                    callGeminiWithFallbacks(audioFile, customKey, sourceLangObj.name, targetLangObj.name, effectiveTargetCode, validatedTone, removeFillers, fixGrammar)
                }
            }
        } catch (e: Exception) {
            Log.w("TranscriptionClient", "$provider failed: ${e.message}", e)
            val msg = e.message ?: ""
            val geminiBackupKey = sanitizeApiKey(preferences.getApiKeyForProvider(KothonPreferences.PROVIDER_GEMINI))
            if (provider != KothonPreferences.PROVIDER_GEMINI &&
                !msg.contains("API key is missing", ignoreCase = true) &&
                geminiBackupKey.isNotBlank()
            ) {
                try {
                    callGeminiWithFallbacks(audioFile, geminiBackupKey, sourceLangObj.name, targetLangObj.name, effectiveTargetCode, validatedTone, removeFillers, fixGrammar)
                } catch (_: Exception) {
                    throw (e as? IOException) ?: IOException("$provider error: ${e.message}", e)
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
        val apiKey = sanitizeApiKey(customKey.ifBlank {
            preferences.getApiKeyForProvider(KothonPreferences.PROVIDER_GEMINI)
        })
        if (apiKey.isEmpty()) {
            throw IOException("Gemini API key is not configured. Tap 'Get API' and paste your Gemini API key.")
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
            4. Remove stuttering, false starts, and filler words (e.g. 'um', 'uh', 'mane', 'ar ki', 'you know') if removeFillers=$removeFillers.
            5. Correct grammar, punctuation, and refine tone into $validatedTone (fixGrammar=$fixGrammar).
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
                                parsed.refinedText.isBlank()
                            ) {
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

        throw (lastException as? IOException)
            ?: IOException("Gemini transcription failed across all fallback models: ${lastException?.message}", lastException)
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
        val apiKey = sanitizeApiKey(customKey.ifBlank {
            preferences.getApiKeyForProvider(KothonPreferences.PROVIDER_GEMINI)
        })
        if (apiKey.isEmpty()) {
            throw IOException("Gemini API key is not configured. Tap 'Get API' and paste your Gemini API key.")
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
            2. If Remove Fillers is true ($removeFillers), strip stuttering and filler words (e.g. 'um', 'uh', 'mane', 'ar ki', 'you know').
            3. If Fix Grammar is true ($fixGrammar), correct grammar, spelling, and tense agreement.
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
                        } catch (_: Exception) {
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

        throw (lastException as? IOException)
            ?: IOException("Gemini text refinement failed across fallback models: ${lastException?.message}", lastException)
    }

    /**
     * NVIDIA NIM Multi-Stage Audio + Chat Integration
     */
    private fun callNvidiaNemotron(
        audioFile: File,
        apiKey: String,
        sourceLangCode: String,
        sourceLangName: String,
        targetLangName: String,
        targetLangCode: String,
        tone: String,
        removeFillers: Boolean,
        fixGrammar: Boolean
    ): TranscriptionResult {
        val cleanKey = sanitizeApiKey(apiKey)
        if (cleanKey.isEmpty()) {
            throw IOException("NVIDIA API key is missing. Tap 'Get API' to configure your NVIDIA NIM key.")
        }

        var rawTranscript = ""

        // Stage 1: Try NVIDIA NIM Audio Transcriptions endpoint (Whisper / Parakeet / Nemotron)
        val audioModels = listOf(
            "openai/whisper-large-v3",
            "nvidia/parakeet-ctc-1.1b",
            "nvidia/NVIDIA-Nemotron-3.5-Transcribe-30B-A4B",
            "nvidia/nemotron-3.5-transcribe-30b-a4b"
        )
        for (modelName in audioModels) {
            try {
                val builder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "audio.m4a", audioFile.asRequestBody("audio/mp4".toMediaType()))
                    .addFormDataPart("model", modelName)
                if (sourceLangCode.length == 2 && sourceLangCode != "auto") {
                    builder.addFormDataPart("language", sourceLangCode.lowercase())
                }

                val request = Request.Builder()
                    .url("https://integrate.api.nvidia.com/v1/audio/transcriptions")
                    .header("Authorization", "Bearer $cleanKey")
                    .post(builder.build())
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        rawTranscript = JSONObject(body).optString("text", "").trim()
                    }
                }
                if (rawTranscript.isNotBlank()) break
            } catch (e: Exception) {
                Log.w("TranscriptionClient", "NVIDIA audio endpoint $modelName error: ${e.message}")
            }
        }

        // Stage 2: Try NVIDIA NIM Multimodal Chat Completions with inline base64 audio
        if (rawTranscript.isBlank()) {
            val base64Audio = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)
            val multimodalModels = listOf(
                "google/gemma-3n-e4b-it",
                "microsoft/phi-4-multimodal-instruct"
            )
            for (mmModel in multimodalModels) {
                try {
                    val chatJson = JSONObject().apply {
                        put("model", mmModel)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("type", "text")
                                        put(
                                            "text",
                                            "Transcribe the spoken words in this audio accurately (expected language: $sourceLangName). Output only the spoken transcript."
                                        )
                                    })
                                    put(JSONObject().apply {
                                        put("type", "audio_url")
                                        put("audio_url", JSONObject().apply {
                                            put("url", "data:audio/mp4;base64,$base64Audio")
                                        })
                                    })
                                })
                            })
                        })
                        put("temperature", 0.1)
                        put("max_tokens", 800)
                    }

                    val mmRequest = Request.Builder()
                        .url("https://integrate.api.nvidia.com/v1/chat/completions")
                        .header("Authorization", "Bearer $cleanKey")
                        .post(chatJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    httpClient.newCall(mmRequest).execute().use { response ->
                        val body = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            val choices = JSONObject(body).optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val text = choices.getJSONObject(0)
                                    .getJSONObject("message")
                                    .optString("content", "")
                                    .trim()
                                if (text.isNotBlank()) {
                                    rawTranscript = text
                                }
                            }
                        }
                    }
                    if (rawTranscript.isNotBlank()) break
                } catch (e: Exception) {
                    Log.w("TranscriptionClient", "NVIDIA multimodal $mmModel failed: ${e.message}")
                }
            }
        }

        // Stage 3: If NVIDIA audio endpoints are unavailable for this key, check configured backup STT keys (Groq / OpenAI / Gemini)
        if (rawTranscript.isBlank()) {
            val groqKey = sanitizeApiKey(preferences.getApiKeyForProvider(KothonPreferences.PROVIDER_GROQ))
            val openAiKey = sanitizeApiKey(preferences.getApiKeyForProvider(KothonPreferences.PROVIDER_OPENAI))
            val geminiKey = sanitizeApiKey(preferences.getApiKeyForProvider(KothonPreferences.PROVIDER_GEMINI))

            if (groqKey.isNotBlank()) {
                try {
                    rawTranscript = transcribeAudioWithGroqWhisper(audioFile, groqKey, sourceLangCode)
                } catch (_: Exception) {}
            }
            if (rawTranscript.isBlank() && openAiKey.isNotBlank()) {
                try {
                    rawTranscript = transcribeAudioWithOpenAiWhisper(audioFile, openAiKey, sourceLangCode)
                } catch (_: Exception) {}
            }
            if (rawTranscript.isBlank() && geminiKey.isNotBlank()) {
                try {
                    val geminiResult = callGeminiWithFallbacks(
                        audioFile,
                        geminiKey,
                        sourceLangName,
                        targetLangName,
                        "original",
                        tone,
                        false,
                        false
                    )
                    rawTranscript = geminiResult.rawTranscript.ifBlank { geminiResult.refinedText }
                } catch (_: Exception) {}
            }
        }

        if (rawTranscript.isBlank()) {
            throw IOException("NVIDIA audio transcription requires Live Dictation mode or a secondary STT key (Groq/Gemini). Switch mode to 'Live' to stream speech and refine with NVIDIA NIM!")
        }

        return try {
            refineWithNvidiaNim(rawTranscript, cleanKey, sourceLangName, targetLangName, targetLangCode, tone, removeFillers, fixGrammar)
        } catch (e: Exception) {
            Log.w("TranscriptionClient", "NVIDIA NIM refinement failed, returning raw transcript: ${e.message}")
            TranscriptionResult(
                rawTranscript = rawTranscript,
                refinedText = rawTranscript,
                detectedLanguage = sourceLangName,
                targetLanguage = targetLangName,
                tone = tone
            )
        }
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
        val cleanKey = sanitizeApiKey(apiKey)
        val translationPrompt = if (targetLangCode == "original") {
            "Keep the output in the same language as spoken. Do NOT translate."
        } else {
            "Translate the refined text into $targetLangName."
        }

        val systemPrompt = """
            You are Kothon AI powered by NVIDIA NIM.
            Refine spoken transcripts into clean, natural text.
            Tone: $tone.
            Remove filler words: $removeFillers. Fix grammar: $fixGrammar.
            $translationPrompt
            Return strictly JSON: {"detected_language": "<Language>", "refined_text": "<Output text>"}
            DO NOT return language arrays. Return a single string in "refined_text".
        """.trimIndent()

        val nvidiaChatModels = listOf(
            "meta/llama-3.3-70b-instruct",
            "nvidia/llama-3.1-nemotron-70b-instruct",
            "meta/llama-3.1-70b-instruct",
            "meta/llama-3.1-8b-instruct"
        )

        var lastException: Exception? = null

        for (modelName in nvidiaChatModels) {
            try {
                val chatJson = JSONObject().apply {
                    put("model", modelName)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", rawTranscript)
                        })
                    })
                    put("temperature", 0.15)
                    put("max_tokens", 1000)
                }

                val chatRequest = Request.Builder()
                    .url("https://integrate.api.nvidia.com/v1/chat/completions")
                    .header("Authorization", "Bearer $cleanKey")
                    .post(chatJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(chatRequest).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val errMsg = try {
                            JSONObject(body).getJSONObject("error").getString("message")
                        } catch (_: Exception) {
                            body
                        }
                        throw IOException("NVIDIA NIM ($modelName) failed (${response.code}): $errMsg")
                    }
                    val choices = JSONObject(body).getJSONArray("choices")
                    val content = choices.getJSONObject(0).getJSONObject("message").getString("content").trim()
                    if (content.isNotBlank()) {
                        return parseUniversalOutput(content, targetLangName, targetLangCode, tone).copy(rawTranscript = rawTranscript)
                    }
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        throw (lastException as? IOException) ?: IOException("NVIDIA NIM refinement failed: ${lastException?.message}", lastException)
    }

    private fun transcribeAudioWithOpenAiWhisper(
        audioFile: File,
        cleanKey: String,
        sourceLangCode: String
    ): String {
        val models = listOf("whisper-1", "gpt-4o-mini-transcribe")
        var lastEx: Exception? = null

        for (modelName in models) {
            val includeLangAttempts = if (sourceLangCode.length == 2 && sourceLangCode != "auto") {
                listOf(true, false)
            } else {
                listOf(false)
            }
            for (withLang in includeLangAttempts) {
                try {
                    val builder = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", "audio.m4a", audioFile.asRequestBody("audio/mp4".toMediaType()))
                        .addFormDataPart("model", modelName)
                    if (withLang) {
                        builder.addFormDataPart("language", sourceLangCode.lowercase())
                    }

                    val whisperRequest = Request.Builder()
                        .url("https://api.openai.com/v1/audio/transcriptions")
                        .header("Authorization", "Bearer $cleanKey")
                        .post(builder.build())
                        .build()

                    httpClient.newCall(whisperRequest).execute().use { response ->
                        val body = response.body?.string() ?: ""
                        if (!response.isSuccessful) {
                            val errMsg = try {
                                JSONObject(body).getJSONObject("error").getString("message")
                            } catch (_: Exception) {
                                body
                            }
                            throw IOException("OpenAI Whisper (${response.code}): $errMsg")
                        }
                        val transcript = JSONObject(body).optString("text", "").trim()
                        if (transcript.isNotBlank()) {
                            return transcript
                        }
                    }
                } catch (e: Exception) {
                    lastEx = e
                }
            }
        }
        throw (lastEx as? IOException) ?: IOException("OpenAI Whisper failed: ${lastEx?.message}", lastEx)
    }

    private fun callOpenAi(
        audioFile: File,
        apiKey: String,
        sourceLangCode: String,
        sourceLangName: String,
        targetLangName: String,
        targetLangCode: String,
        tone: String,
        removeFillers: Boolean,
        fixGrammar: Boolean
    ): TranscriptionResult {
        val cleanKey = sanitizeApiKey(apiKey)
        if (cleanKey.isEmpty()) {
            throw IOException("OpenAI API key is missing. Tap 'Get API' to configure your key.")
        }

        val rawTranscript = transcribeAudioWithOpenAiWhisper(audioFile, cleanKey, sourceLangCode)
        if (rawTranscript.isBlank()) {
            throw IOException("No speech detected by OpenAI Whisper. Please speak clearly into the mic.")
        }

        return try {
            refineWithOpenAi(rawTranscript, cleanKey, sourceLangName, targetLangName, targetLangCode, tone, removeFillers, fixGrammar)
        } catch (e: Exception) {
            Log.w("TranscriptionClient", "OpenAI GPT refinement failed, returning Whisper transcript: ${e.message}")
            TranscriptionResult(
                rawTranscript = rawTranscript,
                refinedText = rawTranscript,
                detectedLanguage = sourceLangName,
                targetLanguage = targetLangName,
                tone = tone
            )
        }
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
        val cleanKey = sanitizeApiKey(apiKey)
        val translationPrompt = if (targetLangCode == "original") {
            "Keep the output in the same language as spoken. Do NOT translate."
        } else {
            "Translate the refined text into $targetLangName."
        }

        val systemPrompt = """
            You are Kothon AI. Refine the user's spoken transcript into clean, polished text.
            Tone: $tone.
            Remove filler words: $removeFillers. Fix grammar: $fixGrammar.
            $translationPrompt
            Return strictly JSON: {"detected_language": "<Language>", "refined_text": "<Output text>"}
            DO NOT return language arrays. Return a single string in "refined_text".
        """.trimIndent()

        val models = listOf("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo")
        var lastEx: Exception? = null

        for (modelName in models) {
            try {
                val chatJson = JSONObject().apply {
                    put("model", modelName)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", rawTranscript)
                        })
                    })
                    put("temperature", 0.15)
                }

                val chatRequest = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .header("Authorization", "Bearer $cleanKey")
                    .post(chatJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(chatRequest).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val errMsg = try {
                            JSONObject(body).getJSONObject("error").getString("message")
                        } catch (_: Exception) {
                            body
                        }
                        throw IOException("OpenAI GPT ($modelName) failed (${response.code}): $errMsg")
                    }
                    val choices = JSONObject(body).getJSONArray("choices")
                    val content = choices.getJSONObject(0).getJSONObject("message").getString("content").trim()
                    if (content.isNotBlank()) {
                        return parseUniversalOutput(content, targetLangName, targetLangCode, tone).copy(rawTranscript = rawTranscript)
                    }
                }
            } catch (e: Exception) {
                lastEx = e
            }
        }

        throw (lastEx as? IOException) ?: IOException("OpenAI refinement failed: ${lastEx?.message}", lastEx)
    }

    private fun transcribeAudioWithGroqWhisper(
        audioFile: File,
        cleanKey: String,
        sourceLangCode: String
    ): String {
        val whisperModels = listOf("whisper-large-v3-turbo", "whisper-large-v3")
        var lastEx: Exception? = null

        for (modelName in whisperModels) {
            val includeLangAttempts = if (sourceLangCode.length == 2 && sourceLangCode != "auto") {
                listOf(true, false)
            } else {
                listOf(false)
            }
            for (withLang in includeLangAttempts) {
                try {
                    val builder = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", "audio.m4a", audioFile.asRequestBody("audio/mp4".toMediaType()))
                        .addFormDataPart("model", modelName)
                        .addFormDataPart("response_format", "json")
                    if (withLang) {
                        builder.addFormDataPart("language", sourceLangCode.lowercase())
                    }

                    val whisperRequest = Request.Builder()
                        .url("https://api.groq.com/openai/v1/audio/transcriptions")
                        .header("Authorization", "Bearer $cleanKey")
                        .post(builder.build())
                        .build()

                    httpClient.newCall(whisperRequest).execute().use { response ->
                        val body = response.body?.string() ?: ""
                        if (!response.isSuccessful) {
                            val errMsg = try {
                                JSONObject(body).getJSONObject("error").getString("message")
                            } catch (_: Exception) {
                                body
                            }
                            throw IOException("Groq Whisper $modelName (${response.code}): $errMsg")
                        }
                        val transcript = JSONObject(body).optString("text", "").trim()
                        if (transcript.isNotBlank()) {
                            return transcript
                        }
                    }
                } catch (e: Exception) {
                    lastEx = e
                }
            }
        }

        throw (lastEx as? IOException) ?: IOException("Groq Whisper failed: ${lastEx?.message}", lastEx)
    }

    private fun callGroq(
        audioFile: File,
        apiKey: String,
        sourceLangCode: String,
        sourceLangName: String,
        targetLangName: String,
        targetLangCode: String,
        tone: String,
        removeFillers: Boolean,
        fixGrammar: Boolean
    ): TranscriptionResult {
        val cleanKey = sanitizeApiKey(apiKey)
        if (cleanKey.isEmpty()) {
            throw IOException("Groq API key is missing. Tap 'Get API' to configure your Groq key.")
        }

        val rawTranscript = transcribeAudioWithGroqWhisper(audioFile, cleanKey, sourceLangCode)

        if (rawTranscript.isBlank()) {
            throw IOException("No speech detected by Groq Whisper. Please speak clearly into the mic.")
        }

        return try {
            refineWithGroq(rawTranscript, cleanKey, sourceLangName, targetLangName, targetLangCode, tone, removeFillers, fixGrammar)
        } catch (e: Exception) {
            Log.w("TranscriptionClient", "Groq Llama refinement failed, returning Whisper transcript: ${e.message}")
            TranscriptionResult(
                rawTranscript = rawTranscript,
                refinedText = rawTranscript,
                detectedLanguage = sourceLangName,
                targetLanguage = targetLangName,
                tone = tone
            )
        }
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
        val cleanKey = sanitizeApiKey(apiKey)
        val translationPrompt = if (targetLangCode == "original") {
            "Keep the output in the same language as spoken. Do NOT translate."
        } else {
            "Translate the refined text into $targetLangName."
        }

        val systemPrompt = """
            You are Kothon AI. Refine the user's spoken transcript into clean, polished text.
            Tone: $tone.
            Remove filler words: $removeFillers. Fix grammar: $fixGrammar.
            $translationPrompt
            Return strictly JSON: {"detected_language": "<Language>", "refined_text": "<Output text>"}
            DO NOT return language arrays. Return a single string in "refined_text".
        """.trimIndent()

        val groqChatModels = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "gemma2-9b-it"
        )
        var lastEx: Exception? = null

        for (modelName in groqChatModels) {
            try {
                val chatJson = JSONObject().apply {
                    put("model", modelName)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", rawTranscript)
                        })
                    })
                    put("temperature", 0.15)
                }

                val chatRequest = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .header("Authorization", "Bearer $cleanKey")
                    .post(chatJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(chatRequest).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val errMsg = try {
                            JSONObject(body).getJSONObject("error").getString("message")
                        } catch (_: Exception) {
                            body
                        }
                        throw IOException("Groq refinement ($modelName) failed (${response.code}): $errMsg")
                    }
                    val choices = JSONObject(body).getJSONArray("choices")
                    val content = choices.getJSONObject(0).getJSONObject("message").getString("content").trim()
                    if (content.isNotBlank()) {
                        return parseUniversalOutput(content, targetLangName, targetLangCode, tone).copy(rawTranscript = rawTranscript)
                    }
                }
            } catch (e: Exception) {
                lastEx = e
            }
        }

        throw (lastEx as? IOException) ?: IOException("Groq refinement failed: ${lastEx?.message}", lastEx)
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
            val jsonCandidate = if (clean.startsWith("{") && clean.endsWith("}")) {
                clean
            } else {
                val firstBrace = clean.indexOf('{')
                val lastBrace = clean.lastIndexOf('}')
                if (firstBrace >= 0 && lastBrace > firstBrace) {
                    clean.substring(firstBrace, lastBrace + 1)
                } else {
                    ""
                }
            }

            if (jsonCandidate.isNotEmpty()) {
                val json = JSONObject(jsonCandidate)

                val detected = json.optString("detected_language", "Auto")
                val raw = json.optString("raw_transcript", "")
                var refined = ""

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

        val plainText = clean
            .replace(Regex("""^\{.*?"refined_text"\s*:\s*""""), "")
            .replace(Regex(""""\s*\}$"""), "")
            .trim()
            .removeSurrounding("\"")

        return TranscriptionResult(
            rawTranscript = "",
            refinedText = plainText.ifBlank { clean },
            detectedLanguage = "Auto",
            targetLanguage = targetLangName,
            tone = tone
        )
    }

    private fun extractStringOrArray(json: JSONObject, key: String): String {
        if (!json.has(key)) return ""
        val arr = json.optJSONArray(key)
        if (arr != null && arr.length() > 0) {
            val sb = StringBuilder()
            for (i in 0 until arr.length()) {
                val item = arr.optString(i, "").trim()
                if (item.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append(" ")
                    sb.append(item)
                }
            }
            return sb.toString()
        }
        return json.optString(key, "").trim()
    }

    companion object {
        fun getProviderApiUrl(provider: String): String {
            return when (provider) {
                KothonPreferences.PROVIDER_GEMINI -> "https://aistudio.google.com/app/apikey"
                KothonPreferences.PROVIDER_GROQ -> "https://console.groq.com/keys"
                KothonPreferences.PROVIDER_OPENAI -> "https://platform.openai.com/api-keys"
                KothonPreferences.PROVIDER_NVIDIA -> "https://build.nvidia.com/settings/api-keys"
                else -> "https://aistudio.google.com/app/apikey"
            }
        }
    }
}
