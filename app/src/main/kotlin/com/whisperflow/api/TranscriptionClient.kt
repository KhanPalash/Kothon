package com.whisperflow.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
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

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

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
                    val key = if (apiKey.isNotBlank()) apiKey else BuildConfig.GEMINI_API_KEY
                    if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
                        return Pair(false, "Gemini API key is empty.")
                    }
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash?key=$key"
                    val request = Request.Builder().url(url).get().build()
                    httpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) Pair(true, "Gemini connected successfully!")
                        else Pair(false, "Gemini error: ${resp.code} ${resp.message}")
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
                        if (resp.isSuccessful) Pair(true, "Groq connected successfully!")
                        else Pair(false, "Groq error: ${resp.code} (Check your key)")
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
                        if (resp.isSuccessful) Pair(true, "OpenAI connected successfully!")
                        else Pair(false, "OpenAI error: ${resp.code} (Check key or credits)")
                    }
                }
                KothonPreferences.PROVIDER_NVIDIA -> {
                    if (apiKey.isBlank()) return Pair(false, "NVIDIA API key is empty.")
                    val request = Request.Builder()
                        .url("https://integrate.api.nvidia.com/v1/models")
                        .header("Authorization", "Bearer $apiKey")
                        .get()
                        .build()
                    httpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) Pair(true, "NVIDIA NIM connected successfully!")
                        else Pair(false, "NVIDIA error: ${resp.code}")
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

        val provider = preferences.provider
        val customKey = preferences.apiKey.trim()

        val sourceLangObj = LanguageCatalog.findByCode(sourceLanguageCode)
        val targetLangObj = LanguageCatalog.findByCode(targetLanguageCode)

        return when (provider) {
            KothonPreferences.PROVIDER_OPENAI -> {
                callOpenAi(audioFile, customKey, sourceLangObj.name, targetLangObj.name, targetLanguageCode, tone, removeFillers, fixGrammar)
            }
            KothonPreferences.PROVIDER_GROQ -> {
                callGroq(audioFile, customKey, sourceLangObj.name, targetLangObj.name, targetLanguageCode, tone, removeFillers, fixGrammar)
            }
            KothonPreferences.PROVIDER_NVIDIA -> {
                callNvidia(audioFile, customKey, sourceLangObj.name, targetLangObj.name, targetLanguageCode, tone, removeFillers, fixGrammar)
            }
            else -> {
                callGemini(audioFile, customKey, sourceLangObj.name, targetLangObj.name, targetLanguageCode, tone, removeFillers, fixGrammar)
            }
        }
    }

    private fun callGemini(
        audioFile: File,
        customKey: String,
        sourceLangName: String,
        targetLangName: String,
        targetLangCode: String,
        tone: String,
        removeFillers: Boolean,
        fixGrammar: Boolean
    ): TranscriptionResult {
        val apiKey = if (customKey.isNotEmpty()) customKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IOException("Gemini API key is not configured. Please enter your API key in Kothon Settings.")
        }

        val audioBytes = audioFile.readBytes()
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val translationInstruction = if (targetLangCode == "original") {
            "Keep the output in the same detected language as spoken. Do NOT translate."
        } else {
            "Translate the refined speech into $targetLangName."
        }

        val systemInstructionText = """
            You are Kothon AI, a voice transcription and professional refinement engine.
            Your task is strictly to process the provided audio file.
            RULES:
            1. Listen to the human voice in the audio and transcribe what was said.
            2. If the audio is silent, noisy, or contains NO spoken human words, you MUST return:
               {"detected_language": "None", "raw_transcript": "", "refined_text": ""}
            3. NEVER transcribe or echo instructions, prompts, or system messages.
            4. Remove stuttering, false starts, and filler words (e.g. 'um', 'uh', 'mane', 'ar ki', 'you know') if requested.
            5. Correct grammar, punctuation, and refine tone into $tone.
            6. $translationInstruction
            7. Return ONLY valid JSON with keys: "detected_language", "raw_transcript", "refined_text".
        """.trimIndent()

        val userPrompt = "Transcribe the attached audio. Expected language: $sourceLangName. Target language: $targetLangName. Tone: $tone."

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

        val models = listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-2.0-flash")
        var lastException: Exception? = null

        for (model in models) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.w("TranscriptionClient", "Gemini $model failed: ${response.code} $responseBody")
                        throw IOException("Gemini API error (${response.code}): $responseBody")
                    }

                    val respJson = JSONObject(responseBody)
                    val candidates = respJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        val resultText = parts.getJSONObject(0).getString("text").trim()
                        if (resultText.isNotEmpty()) {
                            val parsed = parseGeminiOutput(resultText, targetLangName, tone)
                            // Safety guard against prompt echoing
                            if (parsed.rawTranscript.contains("Kothon AI", ignoreCase = true) ||
                                parsed.rawTranscript.contains("WhisperFlow", ignoreCase = true) ||
                                parsed.refinedText.isBlank()) {
                                throw IOException("No speech detected. Please hold the mic and speak clearly.")
                            }
                            return parsed
                        }
                    }
                    throw IOException("Empty response from Gemini API")
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        throw (lastException as? IOException) ?: IOException("Gemini transcription failed: ${lastException?.message}", lastException)
    }

    private fun parseGeminiOutput(rawOutput: String, targetLangName: String, tone: String): TranscriptionResult {
        val clean = rawOutput.replace("```json", "").replace("```", "").trim()
        return try {
            val json = JSONObject(clean)
            TranscriptionResult(
                rawTranscript = json.optString("raw_transcript", ""),
                refinedText = json.optString("refined_text", clean),
                detectedLanguage = json.optString("detected_language", "Auto"),
                targetLanguage = targetLangName,
                tone = tone
            )
        } catch (e: Exception) {
            TranscriptionResult(
                rawTranscript = "",
                refinedText = clean,
                detectedLanguage = "Detected",
                targetLanguage = targetLangName,
                tone = tone
            )
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

        // 1. Whisper transcription
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

        val rawTranscript = try {
            httpClient.newCall(whisperRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = try { JSONObject(body).getJSONObject("error").getString("message") } catch (e: Exception) { body }
                    throw IOException("OpenAI Whisper failed (${response.code}): $errMsg")
                }
                JSONObject(body).optString("text", "").trim()
            }
        } catch (e: Exception) {
            Log.e("TranscriptionClient", "OpenAI Whisper error", e)
            throw (e as? IOException) ?: IOException("OpenAI transcription failed: ${e.message}", e)
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
            Return strictly JSON:
            {"detected_language": "<Language>", "refined_text": "<Output>"}
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
            parseGeminiOutput(content, targetLangName, tone).copy(rawTranscript = rawTranscript)
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

        val rawTranscript = try {
            httpClient.newCall(whisperRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = try { JSONObject(body).getJSONObject("error").getString("message") } catch (e: Exception) { body }
                    throw IOException("Groq Whisper failed (${response.code}): $errMsg")
                }
                JSONObject(body).optString("text", "").trim()
            }
        } catch (e: Exception) {
            Log.e("TranscriptionClient", "Groq Whisper error", e)
            throw (e as? IOException) ?: IOException("Groq transcription failed: ${e.message}", e)
        }

        if (rawTranscript.isBlank()) {
            throw IOException("No speech detected by Groq Whisper. Please speak clearly into the mic.")
        }

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
            Return strictly JSON: {"detected_language": "<Language>", "refined_text": "<Output>"}
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
            parseGeminiOutput(content, targetLangName, tone).copy(rawTranscript = rawTranscript)
        }
    }

    private fun callNvidia(
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

        // NVIDIA NIM is an LLM inference API. First transcribe audio with Gemini, then refine with NVIDIA NIM!
        val geminiTranscript = callGemini(audioFile, "", sourceLangName, targetLangName, "original", tone, false, false)
        val rawTranscript = geminiTranscript.rawTranscript.ifBlank { geminiTranscript.refinedText }

        val translationPrompt = if (targetLangCode == "original") {
            "Keep the output in the same language as spoken."
        } else {
            "Translate into $targetLangName."
        }

        val prompt = """
            You are Kothon AI powered by NVIDIA NIM.
            Refine this transcript into clean, professional text ('shikrito' style).
            Tone: $tone.
            Remove filler words: $removeFillers. Fix grammar: $fixGrammar.
            $translationPrompt
            Raw Transcript: "$rawTranscript"
            Return strictly JSON: {"detected_language": "<Language>", "refined_text": "<Output>"}
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
            parseGeminiOutput(content, targetLangName, tone).copy(rawTranscript = rawTranscript)
        }
    }
}
