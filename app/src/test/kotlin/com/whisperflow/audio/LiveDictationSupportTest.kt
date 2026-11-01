package com.whisperflow.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.whisperflow.settings.KothonPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveDictationSupportTest {

    private lateinit var context: Context
    private lateinit var preferences: KothonPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = KothonPreferences(context)
        preferences.clear()
    }

    @Test
    fun `sentinel language codes are not real languages`() {
        assertFalse(LiveDictationSupport.isRealLanguage(null))
        assertFalse(LiveDictationSupport.isRealLanguage(""))
        assertFalse(LiveDictationSupport.isRealLanguage("   "))
        assertFalse(LiveDictationSupport.isRealLanguage("auto"))
        assertFalse(LiveDictationSupport.isRealLanguage("AUTO"))
        assertFalse(LiveDictationSupport.isRealLanguage("original"))
        assertTrue(LiveDictationSupport.isRealLanguage("bn"))
        assertTrue(LiveDictationSupport.isRealLanguage("en-US"))
    }

    @Test
    fun `language code strips region and normalizes case`() {
        assertEquals("bn", LiveDictationSupport.languageCode("bn-BD"))
        assertEquals("en", LiveDictationSupport.languageCode("EN_us"))
        assertEquals("ja", LiveDictationSupport.languageCode(" ja-JP "))
    }

    @Test
    fun `toSpeechTag maps short codes and preserves regional tags`() {
        assertEquals("bn-BD", LiveDictationSupport.toSpeechTag("bn"))
        assertEquals("en-US", LiveDictationSupport.toSpeechTag("en"))
        assertEquals("hi-IN", LiveDictationSupport.toSpeechTag("hi"))
        assertEquals("en-GB", LiveDictationSupport.toSpeechTag("en-GB"))
        assertEquals("xx", LiveDictationSupport.toSpeechTag("xx"))
    }

    @Test
    fun `toSpeechTag never returns sentinel values`() {
        assertEquals("bn-BD", LiveDictationSupport.toSpeechTag("auto"))
        assertEquals("bn-BD", LiveDictationSupport.toSpeechTag("original"))
        assertEquals("bn-BD", LiveDictationSupport.toSpeechTag(""))
    }

    @Test
    fun `resolve prefers last live language over source and device`() {
        assertEquals(
            "es-ES",
            LiveDictationSupport.resolveLanguageTag(
                liveLanguage = "es",
                sourceLanguage = "bn",
                deviceLanguageTag = "en-US"
            )
        )
    }

    @Test
    fun `resolve falls back to explicit source language`() {
        assertEquals(
            "hi-IN",
            LiveDictationSupport.resolveLanguageTag(
                liveLanguage = "",
                sourceLanguage = "hi",
                deviceLanguageTag = "en-US"
            )
        )
    }

    @Test
    fun `resolve skips sentinels and uses device locale`() {
        assertEquals(
            "fr-FR",
            LiveDictationSupport.resolveLanguageTag(
                liveLanguage = "auto",
                sourceLanguage = "original",
                deviceLanguageTag = "fr-FR"
            )
        )
    }

    @Test
    fun `resolve defaults to Bangla when nothing usable is configured`() {
        assertEquals(
            "bn-BD",
            LiveDictationSupport.resolveLanguageTag(
                liveLanguage = "auto",
                sourceLanguage = "original",
                deviceLanguageTag = "auto"
            )
        )
        assertEquals(
            "bn-BD",
            LiveDictationSupport.resolveLanguageTag(
                liveLanguage = null,
                sourceLanguage = null,
                deviceLanguageTag = null
            )
        )
    }

    @Test
    fun `CJK languages use no separator`() {
        assertEquals("", LiveDictationSupport.separatorFor("zh-CN"))
        assertEquals("", LiveDictationSupport.separatorFor("ja-JP"))
        assertEquals("", LiveDictationSupport.separatorFor("ko-KR"))
        assertEquals(" ", LiveDictationSupport.separatorFor("en-US"))
        assertEquals(" ", LiveDictationSupport.separatorFor("bn-BD"))
    }

    @Test
    fun `live mode requires both feature flag and mode selection`() {
        preferences.liveDictationEnabled = false
        preferences.dictationMode = KothonPreferences.MODE_LIVE
        assertFalse(LiveDictationSupport.isLiveModeEnabled(preferences))

        preferences.liveDictationEnabled = true
        preferences.dictationMode = KothonPreferences.MODE_ACCURATE
        assertFalse(LiveDictationSupport.isLiveModeEnabled(preferences))

        preferences.dictationMode = KothonPreferences.MODE_LIVE
        assertTrue(LiveDictationSupport.isLiveModeEnabled(preferences))
    }

    @Test
    fun `speech config reflects live preferences`() {
        preferences.livePreferOnDevice = false
        preferences.liveSessionMaxMs = 42_000L

        val config = LiveDictationSupport.buildSpeechConfig(preferences, "bn-BD")

        assertEquals("bn-BD", config.languageTag)
        assertFalse(config.preferOnDevice)
        assertTrue(config.partialResults)
        assertEquals(42_000L, config.maxSessionMs)
    }
}
