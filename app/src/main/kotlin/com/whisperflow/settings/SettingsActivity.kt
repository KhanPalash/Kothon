package com.whisperflow.settings

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
import com.example.R
import com.whisperflow.model.LanguageCatalog

class SettingsActivity : ComponentActivity() {

    private lateinit var preferences: KothonPreferences

    private lateinit var providerSpinner: Spinner
    private lateinit var apiKeyInput: EditText
    private lateinit var sourceLangSpinner: Spinner
    private lateinit var targetLangSpinner: Spinner
    private lateinit var translateSwitch: Switch
    private lateinit var toneSpinner: Spinner
    private lateinit var applyButton: Button
    private lateinit var statusText: TextView

    private val supportedLanguages = LanguageCatalog.ALL_LANGUAGES
    private val languageDisplayNames by lazy {
        supportedLanguages.map { "${it.flagEmoji} ${it.name} (${it.nativeName})" }
    }

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
        applyButton = findViewById(R.id.apply_button)
        statusText = findViewById(R.id.status_text)

        setupSpinners()
        loadPreferences()

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

        statusText.text = "Settings applied successfully!"
        statusText.visibility = View.VISIBLE
        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
    }
}
