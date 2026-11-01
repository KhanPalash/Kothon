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
}
