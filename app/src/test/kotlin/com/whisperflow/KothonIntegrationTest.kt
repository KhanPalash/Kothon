package com.whisperflow

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.whisperflow.api.TranscriptionClient
import com.whisperflow.model.LanguageCatalog
import com.whisperflow.settings.KothonPreferences
import com.whisperflow.ui.VoiceButtonState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KothonIntegrationTest {

    private lateinit var context: Context
    private lateinit var preferences: KothonPreferences
    private lateinit var transcriptionClient: TranscriptionClient

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        preferences = KothonPreferences(context)
        preferences.clear()
        transcriptionClient = TranscriptionClient(context)
    }

    @Test
    fun `preferences default values are correct`() {
        assertEquals(KothonPreferences.PROVIDER_GEMINI, preferences.provider)
        assertEquals("", preferences.apiKey)
        assertTrue(preferences.isTranslateEnabled)
        assertEquals("auto", preferences.sourceLanguage)
        assertEquals("en", preferences.targetLanguage)
        assertEquals(KothonPreferences.TONE_PROFESSIONAL, preferences.tone)
        assertTrue(preferences.removeFillers)
        assertTrue(preferences.fixGrammar)
    }

    @Test
    fun `preferences can update languages and recent history`() {
        preferences.sourceLanguage = "bn"
        preferences.targetLanguage = "es"
        preferences.addRecentLanguage("bn")
        preferences.addRecentLanguage("es")

        assertEquals("bn", preferences.sourceLanguage)
        assertEquals("es", preferences.targetLanguage)

        val recents = preferences.getRecentLanguages()
        assertTrue(recents.contains("bn"))
        assertTrue(recents.contains("es"))
    }

    @Test
    fun `language catalog search finds matching languages`() {
        val resultsBengali = LanguageCatalog.search("Bengali")
        assertTrue(resultsBengali.any { it.code == "bn" })

        val resultsNative = LanguageCatalog.search("বাংলা")
        assertTrue(resultsNative.any { it.code == "bn" })

        val resultsSpanish = LanguageCatalog.search("es")
        assertTrue(resultsSpanish.any { it.code == "es" })

        val targetLanguages = LanguageCatalog.search("", forTarget = true)
        assertTrue(targetLanguages.any { it.code == "original" })
    }

    @Test
    fun `transcription client throws error on empty or missing audio file`() {
        val dummyFile = File(context.cacheDir, "non_existent.m4a")
        runBlocking {
            try {
                transcriptionClient.processAudio(dummyFile)
                fail("Expected IOException on missing audio file or offline state")
            } catch (e: IOException) {
                assertNotNull(e.message)
            }
        }
    }

    @Test
    fun `universal output parser unwraps language array json correctly`() {
        val sampleJson = """{"Bengali":["জেমিনির কয়েকটি মডেল ফলব্যাকে যোগ করুন।"],"English":["Add a few Gemini models to the fallback."]}"""
        val result = transcriptionClient.parseUniversalOutput(
            rawOutput = sampleJson,
            targetLangName = "English",
            targetLangCode = "en",
            tone = KothonPreferences.TONE_PROFESSIONAL
        )
        assertEquals("Add a few Gemini models to the fallback.", result.refinedText)
        assertFalse(result.refinedText.contains("{"))
        assertFalse(result.refinedText.contains("["))
    }

    @Test
    fun `gemini fallback models are populated with active models`() {
        assertTrue(transcriptionClient.geminiFallbackModels.contains("gemini-3.8-flash"))
        assertTrue(transcriptionClient.geminiFallbackModels.contains("gemini-3.5-flash"))
        assertTrue(transcriptionClient.geminiFallbackModels.contains("gemini-3.5-flash-lite"))
    }

    @Test
    fun `preferences store per provider api keys independently`() {
        preferences.setApiKeyForProvider(KothonPreferences.PROVIDER_NVIDIA, "nvapi-12345")
        preferences.setApiKeyForProvider(KothonPreferences.PROVIDER_GROQ, "gsk-67890")

        assertEquals("nvapi-12345", preferences.getApiKeyForProvider(KothonPreferences.PROVIDER_NVIDIA))
        assertEquals("gsk-67890", preferences.getApiKeyForProvider(KothonPreferences.PROVIDER_GROQ))
        // Verify no cross-provider key poisoning into Gemini or OpenAI
        assertEquals("", preferences.getApiKeyForProvider(KothonPreferences.PROVIDER_GEMINI))
        assertEquals("", preferences.getApiKeyForProvider(KothonPreferences.PROVIDER_OPENAI))
    }

    @Test
    fun `keystore helper encrypts and decrypts sensitive keys accurately`() {
        val rawKey = "sk-proj-test123456789"
        val encrypted = com.whisperflow.settings.KeyStoreHelper.encrypt(rawKey)
        assertTrue(encrypted.startsWith("ENC:"))
        assertFalse(encrypted.contains(rawKey))

        val decrypted = com.whisperflow.settings.KeyStoreHelper.decrypt(encrypted)
        assertEquals(rawKey, decrypted)
    }

    @Test
    fun `preferences store keys encrypted in raw sharedpreferences`() {
        val testKey = "nvapi-secret-key-abc"
        preferences.setApiKeyForProvider(KothonPreferences.PROVIDER_NVIDIA, testKey)

        // Verify transparent decryption via API
        assertEquals(testKey, preferences.getApiKeyForProvider(KothonPreferences.PROVIDER_NVIDIA))

        // Verify underlying storage on disk is encrypted with ENC: prefix
        val rawPrefs = context.getSharedPreferences(KothonPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val rawStoredValue = rawPrefs.getString("key_api_key_${KothonPreferences.PROVIDER_NVIDIA}", "") ?: ""
        assertTrue(rawStoredValue.startsWith("ENC:"))
        assertFalse(rawStoredValue.contains("nvapi-secret-key-abc"))
    }

    @Test
    fun `voice button state enum contains all expected states`() {
        val states = VoiceButtonState.values()
        assertTrue(states.contains(VoiceButtonState.IDLE))
        assertTrue(states.contains(VoiceButtonState.RECORDING))
        assertTrue(states.contains(VoiceButtonState.PROCESSING))
        assertTrue(states.contains(VoiceButtonState.SUCCESS))
        assertTrue(states.contains(VoiceButtonState.ERROR))
        assertTrue(states.contains(VoiceButtonState.PERMISSION_REQUIRED))
    }

    @Test
    fun `recorder cleans up stale recordings older than 3 minutes`() {
        val recorder = com.whisperflow.audio.Recorder(context)
        val staleFile = File(context.cacheDir, "kothon_audio_stale_test.m4a").apply {
            writeBytes(ByteArray(100))
            setLastModified(System.currentTimeMillis() - 300_000L) // 5 minutes ago
        }
        val freshFile = File(context.cacheDir, "kothon_audio_fresh_test.m4a").apply {
            writeBytes(ByteArray(100))
            setLastModified(System.currentTimeMillis())
        }

        assertTrue(staleFile.exists())
        assertTrue(freshFile.exists())

        recorder.cleanupStaleRecordings()

        assertFalse("Stale audio file should be purged", staleFile.exists())
        assertTrue("Fresh audio file should be preserved", freshFile.exists())

        // Cleanup
        freshFile.delete()
    }

    @Test
    fun `refineText returns empty string for blank input`() {
        runBlocking {
            val result = transcriptionClient.refineText("    \n\t")
            assertEquals("", result)
        }
    }

    @Test
    fun `refineText preserves raw text when offline`() {
        val sampleText = "Ami ekhon kaj ta korchi"
        runBlocking {
            // In unit test environment without active network, refineText preserves raw input
            val result = transcriptionClient.refineText(sampleText)
            assertEquals(sampleText, result)
        }
    }

    @Test
    fun `parseUniversalOutput parses plain non-json text directly`() {
        val plainText = "This is a direct polished sentence without any JSON formatting."
        val result = transcriptionClient.parseUniversalOutput(
            rawOutput = plainText,
            targetLangName = "English",
            targetLangCode = "en",
            tone = KothonPreferences.TONE_CASUAL
        )
        assertEquals(plainText, result.refinedText)
    }

    @Test
    fun `live dictation preferences default to ACCURATE and flag off`() {
        assertEquals(KothonPreferences.MODE_ACCURATE, preferences.dictationMode)
        assertTrue(preferences.liveAutoRefine)
        assertEquals("", preferences.liveLanguage)
        assertTrue(preferences.livePreferOnDevice)
        assertTrue(preferences.liveModelDownloadWifiOnly)
        assertEquals(KothonPreferences.DEFAULT_LIVE_SESSION_MAX_MS, preferences.liveSessionMaxMs)
        assertFalse(preferences.liveDictationEnabled)
    }

    @Test
    fun `live dictation preferences persist across instances`() {
        preferences.dictationMode = KothonPreferences.MODE_LIVE
        preferences.liveAutoRefine = false
        preferences.liveLanguage = "bn"
        preferences.livePreferOnDevice = false
        preferences.liveModelDownloadWifiOnly = false
        preferences.liveSessionMaxMs = 45_000L
        preferences.liveDictationEnabled = true

        val reloaded = KothonPreferences(context)
        assertEquals(KothonPreferences.MODE_LIVE, reloaded.dictationMode)
        assertFalse(reloaded.liveAutoRefine)
        assertEquals("bn", reloaded.liveLanguage)
        assertFalse(reloaded.livePreferOnDevice)
        assertFalse(reloaded.liveModelDownloadWifiOnly)
        assertEquals(45_000L, reloaded.liveSessionMaxMs)
        assertTrue(reloaded.liveDictationEnabled)
    }

    @Test
    fun `provider api key urls point to official portals for all providers`() {
        assertTrue(TranscriptionClient.getProviderApiUrl(KothonPreferences.PROVIDER_GEMINI).contains("aistudio.google.com"))
        assertTrue(TranscriptionClient.getProviderApiUrl(KothonPreferences.PROVIDER_GROQ).contains("console.groq.com"))
        assertTrue(TranscriptionClient.getProviderApiUrl(KothonPreferences.PROVIDER_OPENAI).contains("platform.openai.com"))
        assertTrue(TranscriptionClient.getProviderApiUrl(KothonPreferences.PROVIDER_NVIDIA).contains("build.nvidia.com"))
    }

    @Test
    fun `sanitizeApiKey strips Bearer prefix and quotes cleanly`() {
        assertEquals("gsk_12345", transcriptionClient.sanitizeApiKey("  Bearer gsk_12345 \n"))
        assertEquals("sk-abcde", transcriptionClient.sanitizeApiKey("\"sk-abcde\""))
        assertEquals("nvapi-xyz", transcriptionClient.sanitizeApiKey("bearer nvapi-xyz"))
    }
}
