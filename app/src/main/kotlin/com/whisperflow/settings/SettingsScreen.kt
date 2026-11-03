package com.whisperflow.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.KothonAmber
import com.example.ui.theme.KothonBorder
import com.example.ui.theme.KothonCyan
import com.example.ui.theme.KothonDarkBg
import com.example.ui.theme.KothonDarkCard
import com.example.ui.theme.KothonEmerald
import com.example.ui.theme.KothonRose
import com.example.ui.theme.KothonTextMuted
import com.example.ui.theme.KothonTextPrimary
import com.example.ui.theme.KothonTextSecondary
import com.whisperflow.audio.DeviceSpikeValidator
import com.whisperflow.audio.LiveDictationSupport
import com.whisperflow.model.Language
import com.whisperflow.model.LanguageCatalog
import com.whisperflow.ui.BrandFooter
import com.whisperflow.ui.KothonCard
import com.whisperflow.ui.KothonChip
import com.whisperflow.ui.KothonLanguagePill
import com.whisperflow.ui.KothonModeSegment
import com.whisperflow.ui.KothonNote
import com.whisperflow.ui.KothonSectionLabel
import com.whisperflow.ui.KothonSettingSwitchRow
import com.whisperflow.ui.LanguageSelectorBottomSheet
import kotlinx.coroutines.launch

private enum class LanguageTarget { SOURCE, TARGET, LIVE }

/**
 * Compact Compose port of the legacy XML settings screen. Every preference that
 * the old activity persisted is still written in exactly the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: KothonPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val supportedLanguages = remember { LanguageCatalog.ALL_LANGUAGES }
    val liveLanguages = remember {
        supportedLanguages.filter { it.code != "auto" && it.code != "original" }
    }
    val liveExcluded = remember { setOf("auto", "original") }

    var provider by remember { mutableStateOf(preferences.provider) }
    var apiKey by remember { mutableStateOf(preferences.getApiKeyForProvider(preferences.provider)) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var sourceCode by remember { mutableStateOf(preferences.sourceLanguage) }
    var targetCode by remember { mutableStateOf(preferences.targetLanguage) }
    var translateEnabled by remember { mutableStateOf(preferences.isTranslateEnabled) }
    var tone by remember { mutableStateOf(preferences.tone) }
    var liveEnabled by remember { mutableStateOf(preferences.liveDictationEnabled) }
    var dictationMode by remember { mutableStateOf(preferences.dictationMode) }
    var autoRefine by remember { mutableStateOf(preferences.liveAutoRefine) }
    var preferOnDevice by remember { mutableStateOf(preferences.livePreferOnDevice) }
    var wifiOnly by remember { mutableStateOf(preferences.liveModelDownloadWifiOnly) }
    var liveCode by remember {
        mutableStateOf(
            when {
                LiveDictationSupport.isRealLanguage(preferences.liveLanguage) -> preferences.liveLanguage
                LiveDictationSupport.isRealLanguage(preferences.sourceLanguage) -> preferences.sourceLanguage
                else -> "bn"
            }
        )
    }
    var openSheetFor by remember { mutableStateOf<LanguageTarget?>(null) }
    var modelStatus by remember { mutableStateOf<String?>(null) }
    var modelStatusColor by remember { mutableStateOf(KothonTextSecondary) }
    var checkingModel by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf<String?>(null) }

    fun checkModelSupport() {
        if (checkingModel) return
        val selected = liveLanguages.find { it.code.equals(liveCode, ignoreCase = true) } ?: return
        val languageTag = LiveDictationSupport.toSpeechTag(selected.code)

        if (Build.VERSION.SDK_INT < 33) {
            modelStatus = "On-device model check requires Android 13 (API 33) or newer."
            modelStatusColor = KothonAmber
            return
        }

        checkingModel = true
        modelStatus = "Checking on-device model for $languageTag…"
        modelStatusColor = KothonTextSecondary
        scope.launch {
            val support = DeviceSpikeValidator.checkLanguageSupport(context, languageTag)
            checkingModel = false
            if (support == null) {
                modelStatus = "Could not check model support on this device."
                modelStatusColor = KothonRose
                return@launch
            }

            when {
                support.installedOnDevice -> {
                    modelStatus = "On-device model installed for $languageTag."
                    modelStatusColor = KothonEmerald
                }
                support.supportedOnDevice -> {
                    val triggered = DeviceSpikeValidator.triggerModelDownload(context, languageTag)
                    modelStatus = if (triggered) {
                        "Download requested for $languageTag. Follow the system prompt."
                    } else {
                        "Model download is not available for $languageTag."
                    }
                    modelStatusColor = if (triggered) KothonCyan else KothonAmber
                }
                support.supportedOnline -> {
                    modelStatus = "No on-device model for $languageTag. Online recognition is available."
                    modelStatusColor = KothonAmber
                }
                else -> {
                    modelStatus = "Speech model not available for $languageTag on this device."
                    modelStatusColor = KothonRose
                }
            }
        }
    }

    fun save() {
        preferences.provider = provider
        preferences.setApiKeyForProvider(provider, apiKey.trim())

        preferences.sourceLanguage = sourceCode
        preferences.addRecentLanguage(sourceCode)
        preferences.targetLanguage = targetCode
        preferences.addRecentLanguage(targetCode)

        preferences.isTranslateEnabled = translateEnabled
        preferences.tone = tone

        preferences.liveDictationEnabled = liveEnabled
        preferences.dictationMode = when {
            !liveEnabled -> KothonPreferences.MODE_ACCURATE
            dictationMode == KothonPreferences.MODE_LIVE -> KothonPreferences.MODE_LIVE
            else -> KothonPreferences.MODE_ACCURATE
        }
        preferences.liveAutoRefine = autoRefine
        preferences.livePreferOnDevice = preferOnDevice
        preferences.liveModelDownloadWifiOnly = wifiOnly
        preferences.liveLanguage = liveCode

        saveStatus = "Settings applied successfully!"
        Toast.makeText(context, "Settings saved successfully", Toast.LENGTH_SHORT).show()
    }

    val sourceLanguage = remember(sourceCode) { LanguageCatalog.findByCode(sourceCode) }
    val targetLanguage = remember(targetCode) { LanguageCatalog.findByCode(targetCode) }
    val liveLanguage = remember(liveCode) { LanguageCatalog.findByCode(liveCode) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KothonDarkBg)
    ) {
        Image(
            painter = painterResource(R.drawable.brand_logo),
            contentDescription = null,
            alpha = 0.30f,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.66f)
                .height(360.dp)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = KothonTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.padding(start = 2.dp)) {
                    Text(
                        text = "Kothon Settings",
                        color = KothonTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Providers, languages and live dictation",
                        color = KothonTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("settings_screen"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 4.dp,
                    bottom = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ---------------------------------------------------- provider
                item {
                    KothonCard {
                        KothonSectionLabel("MODEL PROVIDER")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            KothonPreferences.PROVIDERS.forEach { name ->
                                KothonChip(
                                    label = name,
                                    selected = provider == name,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        provider = name
                                        apiKey = preferences.getApiKeyForProvider(name)
                                        saveStatus = null
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "API KEY",
                            color = KothonTextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = {
                                apiKey = it
                                saveStatus = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_api_key_input"),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 12.sp,
                                color = KothonTextPrimary
                            ),
                            placeholder = {
                                Text(
                                    text = "Paste your $provider API key",
                                    color = KothonTextMuted,
                                    fontSize = 11.sp
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(
                                        imageVector = if (apiKeyVisible) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = if (apiKeyVisible) "Hide key" else "Show key",
                                        tint = KothonTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (apiKeyVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = KothonCyan,
                                unfocusedBorderColor = KothonBorder.copy(alpha = 0.7f),
                                focusedContainerColor = KothonDarkBg,
                                unfocusedContainerColor = KothonDarkBg,
                                cursorColor = KothonCyan
                            )
                        )
                    }
                }

                // ---------------------------------------------------- language
                item {
                    KothonCard {
                        KothonSectionLabel("LANGUAGE")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            KothonLanguagePill(
                                label = "SPOKEN INPUT",
                                flag = sourceLanguage.flagEmoji,
                                name = sourceLanguage.name,
                                accent = true,
                                modifier = Modifier.weight(1f),
                                onClick = { openSheetFor = LanguageTarget.SOURCE }
                            )

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(KothonDarkCard)
                                    .clickable {
                                        if (sourceCode == "auto") {
                                            Toast.makeText(
                                                context,
                                                "Cannot swap when Source is Auto Detect",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            val previousSource = sourceCode
                                            sourceCode = if (targetCode == "original") "en" else targetCode
                                            targetCode = previousSource
                                        }
                                    }
                                    .testTag("settings_swap_languages"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "Swap languages",
                                    tint = KothonCyan,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            KothonLanguagePill(
                                label = "OUTPUT",
                                flag = targetLanguage.flagEmoji,
                                name = if (targetCode == "original") "Keep Original" else targetLanguage.name,
                                accent = false,
                                modifier = Modifier.weight(1f),
                                onClick = { openSheetFor = LanguageTarget.TARGET }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        KothonSettingSwitchRow(
                            title = "Translate output",
                            subtitle = "Translate the refined text into the output language",
                            checked = translateEnabled,
                            onCheckedChange = { translateEnabled = it }
                        )
                    }
                }

                // ---------------------------------------------------- tone
                item {
                    KothonCard {
                        KothonSectionLabel("AI TONE")
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(KothonPreferences.TONES) { name ->
                                KothonChip(
                                    label = name,
                                    selected = tone == name,
                                    selectedColor = KothonAmber,
                                    onClick = { tone = name }
                                )
                            }
                        }
                    }
                }

                // ---------------------------------------------------- live
                item {
                    KothonCard {
                        KothonSectionLabel(
                            text = "LIVE DICTATION",
                            trailing = {
                                if (liveEnabled) {
                                    Text(
                                        text = "BETA",
                                        color = KothonAmber,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.6.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(KothonAmber.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        KothonSettingSwitchRow(
                            title = "Enable live dictation",
                            subtitle = "Real-time streaming recognition while you speak",
                            checked = liveEnabled,
                            tint = KothonEmerald,
                            onCheckedChange = {
                                liveEnabled = it
                                if (!it) dictationMode = KothonPreferences.MODE_ACCURATE
                            }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(KothonDarkBg)
                                .selectableGroup()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            KothonModeSegment(
                                label = "Accurate (Audio + AI)",
                                selected = dictationMode != KothonPreferences.MODE_LIVE,
                                enabled = liveEnabled,
                                modifier = Modifier.weight(1f),
                                onClick = { dictationMode = KothonPreferences.MODE_ACCURATE }
                            )
                            KothonModeSegment(
                                label = "Live (Real-time)",
                                selected = dictationMode == KothonPreferences.MODE_LIVE,
                                enabled = liveEnabled,
                                selectedColor = KothonIndigoAccent,
                                modifier = Modifier.weight(1f),
                                onClick = { dictationMode = KothonPreferences.MODE_LIVE }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "LIVE LANGUAGE",
                            color = KothonTextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        KothonLanguagePill(
                            label = "ON-DEVICE / SPEECH",
                            flag = liveLanguage.flagEmoji,
                            name = liveLanguage.name,
                            accent = true,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { openSheetFor = LanguageTarget.LIVE }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        KothonSettingSwitchRow(
                            title = "Auto-refine transcript",
                            subtitle = "Polish the live transcript with AI when you stop",
                            checked = autoRefine,
                            onCheckedChange = { autoRefine = it }
                        )
                        KothonSettingSwitchRow(
                            title = "Prefer on-device recognition",
                            subtitle = "Use the local speech model when it is available",
                            checked = preferOnDevice,
                            tint = KothonEmerald,
                            onCheckedChange = { preferOnDevice = it }
                        )
                        KothonSettingSwitchRow(
                            title = "Download models over Wi-Fi only",
                            subtitle = "Avoid mobile data while fetching speech models",
                            checked = wifiOnly,
                            tint = KothonAmber,
                            onCheckedChange = { wifiOnly = it }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(KothonDarkCard)
                                    .clickable(enabled = !checkingModel) { checkModelSupport() }
                                    .padding(horizontal = 11.dp, vertical = 8.dp)
                                    .testTag("settings_check_model_button"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (checkingModel) {
                                    CircularProgressIndicator(
                                        color = KothonCyan,
                                        strokeWidth = 1.8.dp,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = KothonCyan,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                }
                                Text(
                                    text = "Check on-device model",
                                    color = KothonTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        modelStatus?.let { message ->
                            Spacer(modifier = Modifier.height(6.dp))
                            KothonNote(
                                text = message,
                                color = modelStatusColor,
                                modifier = Modifier.testTag("settings_model_status")
                            )
                        }
                    }
                }

                // ---------------------------------------------------- apply
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(KothonCyan)
                                .clickable { save() }
                                .testTag("settings_apply_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Apply settings",
                                color = Color(0xFF0B0F19),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp
                            )
                        }

                        saveStatus?.let { message ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(KothonEmerald.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                                    .testTag("settings_status_text"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = message,
                                    color = KothonEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BrandFooter()
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------- language sheets
        val sheetTitle = when (openSheetFor) {
            LanguageTarget.SOURCE -> "Select Spoken Input Language"
            LanguageTarget.TARGET -> "Select Output / Translation Language"
            LanguageTarget.LIVE -> "Select Live Dictation Language"
            null -> null
        }
        val sheetSelected = when (openSheetFor) {
            LanguageTarget.SOURCE -> sourceCode
            LanguageTarget.TARGET -> targetCode
            LanguageTarget.LIVE -> liveCode
            null -> ""
        }
        val sheetForTarget = openSheetFor == LanguageTarget.TARGET
        val sheetExcluded = if (openSheetFor == LanguageTarget.LIVE) liveExcluded else emptySet()

        if (sheetTitle != null) {
            LanguageSelectorBottomSheet(
                title = sheetTitle,
                currentSelectedCode = sheetSelected,
                isTargetLanguage = sheetForTarget,
                recentLanguageCodes = preferences.getRecentLanguages(),
                excludeCodes = sheetExcluded,
                onLanguageSelected = { language: Language ->
                    when (openSheetFor) {
                        LanguageTarget.SOURCE -> sourceCode = language.code
                        LanguageTarget.TARGET -> targetCode = language.code
                        LanguageTarget.LIVE -> liveCode = language.code
                        null -> Unit
                    }
                    openSheetFor = null
                },
                onDismissRequest = { openSheetFor = null }
            )
        }
    }
}

/** Indigo used for the "Live" segment so it reads as a secondary, beta action. */
private val KothonIndigoAccent = Color(0xFF818CF8)
