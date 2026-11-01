package com.whisperflow.settings

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.whisperflow.audio.DeviceSpikeValidator
import com.whisperflow.audio.LiveDictationSupport
import com.whisperflow.model.LanguageCatalog
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    private lateinit var preferences: KothonPreferences

    private lateinit var providerSpinner: Spinner
    private lateinit var apiKeyInput: EditText
    private lateinit var sourceLangSpinner: Spinner
    private lateinit var targetLangSpinner: Spinner
    private lateinit var translateSwitch: Switch
    private lateinit var toneSpinner: Spinner
    private lateinit var liveEnabledSwitch: Switch
    private lateinit var dictationModeSpinner: Spinner
    private lateinit var autoRefineSwitch: Switch
    private lateinit var preferOnDeviceSwitch: Switch
    private lateinit var wifiOnlySwitch: Switch
    private lateinit var liveLanguageSpinner: Spinner
    private lateinit var checkModelButton: Button
    private lateinit var modelStatusText: TextView
    private lateinit var applyButton: Button
    private lateinit var statusText: TextView

    private val supportedLanguages = LanguageCatalog.ALL_LANGUAGES
    private val languageDisplayNames by lazy {
        supportedLanguages.map { "${it.flagEmoji} ${it.name} (${it.nativeName})" }
    }
    private val liveLanguages = LanguageCatalog.ALL_LANGUAGES.filter {
        it.code != "auto" && it.code != "original"
    }
    private val liveLanguageDisplayNames by lazy {
        liveLanguages.map { "${it.flagEmoji} ${it.name} (${it.nativeName})" }
    }
    private val dictationModeLabels = arrayOf(
        "Accurate (Audio + AI)",
        "Live (Real-time, Beta)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        preferences = KothonPreferences(this)

        providerSpinner = findViewById(R.id.provider_spinner)
        apiKeyInput = findViewById(R.id.api_key_input)
        sourceLangSpinner = findViewById(R.id.source_lang_spinner)
        targetLangSpinner = findViewById(R.id.target_lang_spinner)
        translateSwitch = findViewById(R.id.translate_switch)
        toneSpinner = findViewById(R.id.tone_spinner)
        liveEnabledSwitch = findViewById(R.id.live_enabled_switch)
        dictationModeSpinner = findViewById(R.id.dictation_mode_spinner)
        autoRefineSwitch = findViewById(R.id.auto_refine_switch)
        preferOnDeviceSwitch = findViewById(R.id.prefer_on_device_switch)
        wifiOnlySwitch = findViewById(R.id.wifi_only_switch)
        liveLanguageSpinner = findViewById(R.id.live_language_spinner)
        checkModelButton = findViewById(R.id.check_model_button)
        modelStatusText = findViewById(R.id.model_status_text)
        applyButton = findViewById(R.id.apply_button)
        statusText = findViewById(R.id.status_text)

        setupSpinners()
        loadPreferences()

        liveEnabledSwitch.setOnCheckedChangeListener { _, checked ->
            dictationModeSpinner.isEnabled = checked
            if (!checked) dictationModeSpinner.setSelection(0)
        }
        checkModelButton.setOnClickListener {
            checkModelSupport()
        }
        applyButton.setOnClickListener {
            savePreferences()
        }
    }

    private fun setupSpinners() {
        val providerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            KothonPreferences.PROVIDERS
        )
        providerSpinner.adapter = providerAdapter
        providerSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val prov = KothonPreferences.PROVIDERS.getOrNull(position) ?: KothonPreferences.PROVIDER_GEMINI
                apiKeyInput.setText(preferences.getApiKeyForProvider(prov))
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val langAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            languageDisplayNames
        )
        sourceLangSpinner.adapter = langAdapter
        targetLangSpinner.adapter = langAdapter

        val toneAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            KothonPreferences.TONES
        )
        toneSpinner.adapter = toneAdapter

        dictationModeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            dictationModeLabels
        )

        liveLanguageSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            liveLanguageDisplayNames
        )
    }

    private fun loadPreferences() {
        val currentProvider = preferences.provider
        val providerIndex = KothonPreferences.PROVIDERS.indexOf(currentProvider)
        if (providerIndex >= 0) {
            providerSpinner.setSelection(providerIndex)
        }

        apiKeyInput.setText(preferences.getApiKeyForProvider(currentProvider))

        val sourceIndex = supportedLanguages.indexOfFirst { it.code.equals(preferences.sourceLanguage, ignoreCase = true) }
        if (sourceIndex >= 0) {
            sourceLangSpinner.setSelection(sourceIndex)
        }

        val targetIndex = supportedLanguages.indexOfFirst { it.code.equals(preferences.targetLanguage, ignoreCase = true) }
        if (targetIndex >= 0) {
            targetLangSpinner.setSelection(targetIndex)
        }

        translateSwitch.isChecked = preferences.isTranslateEnabled

        val currentTone = preferences.tone
        val toneIndex = KothonPreferences.TONES.indexOf(currentTone)
        if (toneIndex >= 0) {
            toneSpinner.setSelection(toneIndex)
        }

        liveEnabledSwitch.isChecked = preferences.liveDictationEnabled
        dictationModeSpinner.setSelection(
            if (preferences.dictationMode == KothonPreferences.MODE_LIVE) 1 else 0
        )
        dictationModeSpinner.isEnabled = preferences.liveDictationEnabled
        autoRefineSwitch.isChecked = preferences.liveAutoRefine
        preferOnDeviceSwitch.isChecked = preferences.livePreferOnDevice
        wifiOnlySwitch.isChecked = preferences.liveModelDownloadWifiOnly

        val preferredLiveLanguage = when {
            LiveDictationSupport.isRealLanguage(preferences.liveLanguage) -> preferences.liveLanguage
            LiveDictationSupport.isRealLanguage(preferences.sourceLanguage) -> preferences.sourceLanguage
            else -> "bn"
        }
        val liveLanguageIndex = liveLanguages.indexOfFirst {
            it.code.equals(preferredLiveLanguage, ignoreCase = true)
        }
        if (liveLanguageIndex >= 0) {
            liveLanguageSpinner.setSelection(liveLanguageIndex)
        }
    }

    private fun checkModelSupport() {
        val selected = liveLanguages.getOrNull(liveLanguageSpinner.selectedItemPosition) ?: return
        val languageTag = LiveDictationSupport.toSpeechTag(selected.code)

        if (Build.VERSION.SDK_INT < 33) {
            showModelStatus("On-device model check requires Android 13 (API 33) or newer.")
            return
        }

        showModelStatus("Checking on-device model for $languageTag…")
        lifecycleScope.launch {
            val support = DeviceSpikeValidator.checkLanguageSupport(this@SettingsActivity, languageTag)
            if (support == null) {
                showModelStatus("Could not check model support on this device.")
                return@launch
            }

            when {
                support.installedOnDevice -> {
                    showModelStatus("On-device model installed for $languageTag.")
                }
                support.supportedOnDevice -> {
                    val triggered = DeviceSpikeValidator.triggerModelDownload(
                        this@SettingsActivity,
                        languageTag
                    )
                    showModelStatus(
                        if (triggered) {
                            "Download requested for $languageTag. Follow the system prompt."
                        } else {
                            "Model download is not available for $languageTag."
                        }
                    )
                }
                support.supportedOnline -> {
                    showModelStatus(
                        "No on-device model for $languageTag. Online recognition is available."
                    )
                }
                else -> {
                    showModelStatus("Speech model not available for $languageTag on this device.")
                }
            }
        }
    }

    private fun showModelStatus(message: String) {
        modelStatusText.text = message
        modelStatusText.visibility = View.VISIBLE
    }

    private fun savePreferences() {
        val selectedProvider = providerSpinner.selectedItem as? String ?: KothonPreferences.PROVIDER_GEMINI
        val enteredKey = apiKeyInput.text.toString().trim()
        val sourceIndex = sourceLangSpinner.selectedItemPosition
        val targetIndex = targetLangSpinner.selectedItemPosition
        val isTranslate = translateSwitch.isChecked
        val selectedTone = toneSpinner.selectedItem as? String ?: KothonPreferences.TONE_PROFESSIONAL

        preferences.provider = selectedProvider
        preferences.setApiKeyForProvider(selectedProvider, enteredKey)

        if (sourceIndex in supportedLanguages.indices) {
            preferences.sourceLanguage = supportedLanguages[sourceIndex].code
            preferences.addRecentLanguage(supportedLanguages[sourceIndex].code)
        }

        if (targetIndex in supportedLanguages.indices) {
            preferences.targetLanguage = supportedLanguages[targetIndex].code
            preferences.addRecentLanguage(supportedLanguages[targetIndex].code)
        }

        preferences.isTranslateEnabled = isTranslate
        preferences.tone = selectedTone

        val liveEnabled = liveEnabledSwitch.isChecked
        preferences.liveDictationEnabled = liveEnabled
        preferences.dictationMode = when {
            !liveEnabled -> KothonPreferences.MODE_ACCURATE
            dictationModeSpinner.selectedItemPosition == 1 -> KothonPreferences.MODE_LIVE
            else -> KothonPreferences.MODE_ACCURATE
        }
        preferences.liveAutoRefine = autoRefineSwitch.isChecked
        preferences.livePreferOnDevice = preferOnDeviceSwitch.isChecked
        preferences.liveModelDownloadWifiOnly = wifiOnlySwitch.isChecked

        liveLanguages.getOrNull(liveLanguageSpinner.selectedItemPosition)?.let { language ->
            preferences.liveLanguage = language.code
        }

        statusText.text = "Settings applied successfully!"
        statusText.visibility = View.VISIBLE
        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
    }
}
