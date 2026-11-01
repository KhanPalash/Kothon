package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.KothonAmber
import com.example.ui.theme.KothonBorder
import com.example.ui.theme.KothonCyan
import com.example.ui.theme.KothonDarkBg
import com.example.ui.theme.KothonDarkCard
import com.example.ui.theme.KothonDarkSurface
import com.example.ui.theme.KothonEmerald
import com.example.ui.theme.KothonIndigo
import com.example.ui.theme.KothonRose
import com.example.ui.theme.KothonTextMuted
import com.example.ui.theme.KothonTextPrimary
import com.example.ui.theme.KothonTextSecondary
import com.example.ui.theme.MyApplicationTheme
import com.whisperflow.api.TranscriptionClient
import com.whisperflow.api.TranscriptionResult
import com.whisperflow.audio.PermissionHelper
import com.whisperflow.audio.Recorder
import com.whisperflow.model.Language
import com.whisperflow.model.LanguageCatalog
import com.whisperflow.settings.KothonPreferences
import com.whisperflow.settings.SettingsActivity
import com.whisperflow.ui.LanguageSelectorBottomSheet
import com.whisperflow.ui.MicrophonePermissionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RefinedStudioNote(
    val id: Long = System.currentTimeMillis(),
    val rawText: String,
    val refinedText: String,
    val detectedLanguage: String,
    val targetLanguage: String,
    val tone: String,
    val timestamp: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                KothonAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KothonAppScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { KothonPreferences(context) }
    val recorder = remember { Recorder(context) }
    val transcriptionClient = remember { TranscriptionClient(context) }

    // Permission states
    var hasMicPermission by remember {
        mutableStateOf(PermissionHelper.hasAudioPermission(context))
    }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            showPermissionDialog = false
            Toast.makeText(context, "Microphone permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            showPermissionDialog = true
        }
    }

    // Keyboard IME Status
    var isKeyboardEnabled by remember { mutableStateOf(false) }
    var isKeyboardSelected by remember { mutableStateOf(false) }

    fun refreshImeStatus() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val enabledList = imm?.enabledInputMethodList ?: emptyList()
        isKeyboardEnabled = enabledList.any { it.packageName == context.packageName }

        val defaultIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        isKeyboardSelected = defaultIme?.contains(context.packageName) == true
    }

    LaunchedEffect(Unit) {
        refreshImeStatus()
    }

    // Provider & API Key States
    var selectedProvider by remember { mutableStateOf(preferences.provider) }
    var apiKeyText by remember { mutableStateOf(preferences.apiKey) }
    var isTestingKey by remember { mutableStateOf(false) }
    var keyTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showApiKeyInput by remember { mutableStateOf(false) }

    // Language Selection States
    var sourceLangCode by remember { mutableStateOf(preferences.sourceLanguage) }
    var targetLangCode by remember { mutableStateOf(preferences.targetLanguage) }
    var showLanguageSheetForSource by remember { mutableStateOf(false) }
    var showLanguageSheetForTarget by remember { mutableStateOf(false) }

    val currentSourceLang = remember(sourceLangCode) { LanguageCatalog.findByCode(sourceLangCode) }
    val currentTargetLang = remember(targetLangCode) { LanguageCatalog.findByCode(targetLangCode) }

    // Advanced Refinement Options
    var currentTone by remember { mutableStateOf(preferences.tone) }
    var removeFillers by remember { mutableStateOf(preferences.removeFillers) }
    var fixGrammar by remember { mutableStateOf(preferences.fixGrammar) }

    // Audio & Dictation state
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var amplitude by remember { mutableFloatStateOf(0f) }
    var lastResult by remember { mutableStateOf<TranscriptionResult?>(null) }
    var studioError by remember { mutableStateOf<String?>(null) }
    var testInputText by remember { mutableStateOf("") }
    var showRawComparison by remember { mutableStateOf(false) }

    val recentNotes = remember {
        mutableStateListOf(
            RefinedStudioNote(
                rawText = "Ami ekhon kaj ta shuru korchi ar bikel er moddhe shob update diye dibo",
                refinedText = "I am beginning the task now and will provide all updates by this afternoon.",
                detectedLanguage = "Bengali",
                targetLanguage = "English",
                tone = "Professional Clarity",
                timestamp = "Sample"
            )
        )
    }

    // Waveform scale animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    fun startDictation() {
        if (!hasMicPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        studioError = null
        try {
            val audioFile = recorder.start()
            isRecording = true

            // Amplitude visualizer loop
            scope.launch {
                while (isActive && recorder.isRecording) {
                    val maxAmp = recorder.getMaxAmplitude()
                    amplitude = (maxAmp / 32767f).coerceIn(0f, 1f)
                    delay(80)
                }
                amplitude = 0f
            }
        } catch (e: Exception) {
            studioError = "Failed to start recorder: ${e.message}"
            isRecording = false
        }
    }

    fun stopDictation() {
        if (!isRecording) return
        isRecording = false
        val audioFile = recorder.stop()

        if (audioFile == null || !audioFile.exists() || audioFile.length() < 2500L) {
            studioError = "Recording was too short. Tap the mic, speak your full sentence, then tap again to finish."
            return
        }

        isProcessing = true
        studioError = null

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    transcriptionClient.processAudio(
                        audioFile = audioFile,
                        sourceLanguageCode = sourceLangCode,
                        targetLanguageCode = targetLangCode,
                        tone = currentTone,
                        removeFillers = removeFillers,
                        fixGrammar = fixGrammar
                    )
                }
                lastResult = result
                recentNotes.add(
                    0,
                    RefinedStudioNote(
                        rawText = result.rawTranscript.ifBlank { "Spoken audio (${audioFile.length() / 1024} KB)" },
                        refinedText = result.refinedText,
                        detectedLanguage = result.detectedLanguage,
                        targetLanguage = result.targetLanguage,
                        tone = currentTone,
                        timestamp = "Just now"
                    )
                )
            } catch (e: Exception) {
                studioError = e.message ?: "Transcription failed. Check your API key or network connection."
            } finally {
                isProcessing = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = KothonDarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(KothonCyan),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Kothon Logo",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Kothon",
                            color = KothonTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "WhisperFlow Voice AI",
                            color = KothonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(
                    onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(KothonDarkCard)
                        .testTag("open_settings_header_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = KothonTextSecondary
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI Provider Selection & Key Status Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_provider_card"),
                    colors = CardDefaults.cardColors(containerColor = KothonDarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(KothonBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AI Provider & Model",
                                color = KothonTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (selectedProvider == KothonPreferences.PROVIDER_GEMINI || apiKeyText.isNotBlank()) KothonEmerald else KothonAmber)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (selectedProvider == KothonPreferences.PROVIDER_GEMINI) "Built-in Active" else if (apiKeyText.isNotBlank()) "Key Saved" else "Key Required",
                                    color = if (selectedProvider == KothonPreferences.PROVIDER_GEMINI || apiKeyText.isNotBlank()) KothonEmerald else KothonAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Provider Chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(KothonPreferences.PROVIDERS) { provider ->
                                val isSelected = selectedProvider == provider
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedProvider = provider
                                        preferences.provider = provider
                                        keyTestResult = null
                                    },
                                    label = {
                                        Text(
                                            text = if (provider == KothonPreferences.PROVIDER_GEMINI) "Gemini (Built-in)" else provider,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = KothonCyan,
                                        selectedLabelColor = Color(0xFF0F172A),
                                        containerColor = KothonDarkCard,
                                        labelColor = KothonTextSecondary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Provider Details & Key input
                        when (selectedProvider) {
                            KothonPreferences.PROVIDER_GEMINI -> {
                                Text(
                                    text = "Google Gemini 2.5 Flash • Multimodal Audio Intelligence • Pre-configured with built-in AI Studio key",
                                    color = KothonEmerald,
                                    fontSize = 12.sp
                                )
                            }
                            KothonPreferences.PROVIDER_GROQ -> {
                                Text(
                                    text = "Groq Cloud • Whisper-large-v3 + Llama 3.3 70B • Blazing fast inference",
                                    color = KothonCyan,
                                    fontSize = 12.sp
                                )
                            }
                            KothonPreferences.PROVIDER_OPENAI -> {
                                Text(
                                    text = "OpenAI • Whisper-1 + GPT-4o-mini • High accuracy transcription",
                                    color = KothonCyan,
                                    fontSize = 12.sp
                                )
                            }
                            KothonPreferences.PROVIDER_NVIDIA -> {
                                Text(
                                    text = "NVIDIA NIM • Llama 3.3 70B • Enterprise speech refinement",
                                    color = KothonAmber,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // API Key input field for non-Gemini or custom Gemini key
                        if (selectedProvider != KothonPreferences.PROVIDER_GEMINI || showApiKeyInput) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = apiKeyText,
                                onValueChange = {
                                    apiKeyText = it
                                    preferences.apiKey = it.trim()
                                    keyTestResult = null
                                },
                                placeholder = {
                                    Text(
                                        when (selectedProvider) {
                                            KothonPreferences.PROVIDER_GROQ -> "Enter Groq key (gsk_...)"
                                            KothonPreferences.PROVIDER_OPENAI -> "Enter OpenAI key (sk-...)"
                                            KothonPreferences.PROVIDER_NVIDIA -> "Enter NVIDIA key (nvapi-...)"
                                            else -> "Enter custom Gemini key"
                                        },
                                        color = KothonTextMuted,
                                        fontSize = 13.sp
                                    )
                                },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("provider_api_key_input"),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = KothonCyan,
                                    unfocusedBorderColor = KothonBorder,
                                    focusedTextColor = KothonTextPrimary,
                                    unfocusedTextColor = KothonTextPrimary,
                                    focusedContainerColor = KothonDarkCard,
                                    unfocusedContainerColor = KothonDarkCard
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isTestingKey = true
                                            val res = withContext(Dispatchers.IO) {
                                                transcriptionClient.testProviderConnection(selectedProvider, apiKeyText.trim())
                                            }
                                            keyTestResult = res
                                            isTestingKey = false
                                        }
                                    },
                                    enabled = !isTestingKey && apiKeyText.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = KothonIndigo,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isTestingKey) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text("Test Key", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        preferences.apiKey = apiKeyText.trim()
                                        Toast.makeText(context, "$selectedProvider Key saved!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = KothonCyan,
                                        contentColor = Color(0xFF0F172A)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Save Key", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Key Test Result feedback
                            if (keyTestResult != null) {
                                val (ok, msg) = keyTestResult!!
                                Text(
                                    text = msg,
                                    color = if (ok) KothonEmerald else KothonRose,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .clickable { showApiKeyInput = true }
                                    .padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "Custom Key",
                                    tint = KothonTextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Use custom Gemini API key",
                                    color = KothonTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Source & Target Language Selector Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("language_selection_card"),
                    colors = CardDefaults.cardColors(containerColor = KothonDarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(KothonBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Language & Real-Time Translation",
                                color = KothonTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Translation",
                                tint = KothonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "Choose spoken input language and target translation language. Detects speech automatically.",
                            color = KothonTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        // Interactive Source & Target Selector with Swap
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Source Language Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(KothonDarkCard)
                                    .clickable { showLanguageSheetForSource = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("source_language_selector_button"),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Column {
                                    Text(
                                        text = "SPEAK IN (SOURCE)",
                                        color = KothonTextMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = currentSourceLang.flagEmoji, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = currentSourceLang.name,
                                            color = KothonTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // Swap Languages Button
                            IconButton(
                                onClick = {
                                    if (sourceLangCode != "auto") {
                                        val temp = sourceLangCode
                                        sourceLangCode = if (targetLangCode == "original") "en" else targetLangCode
                                        targetLangCode = temp
                                        preferences.sourceLanguage = sourceLangCode
                                        preferences.targetLanguage = targetLangCode
                                        Toast.makeText(context, "Languages swapped", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Cannot swap when Source is Auto Detect", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF334155))
                                    .testTag("swap_languages_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "Swap Languages",
                                    tint = KothonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Target Language Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(KothonDarkCard)
                                    .clickable { showLanguageSheetForTarget = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("target_language_selector_button"),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Column {
                                    Text(
                                        text = "OUTPUT (TRANSLATE TO)",
                                        color = KothonTextMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = currentTargetLang.flagEmoji, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = currentTargetLang.name,
                                            color = KothonCyan,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Advanced Text Refinement & Tone Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("advanced_refinement_card"),
                    colors = CardDefaults.cardColors(containerColor = KothonDarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(KothonBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Advanced Shikrito Refinement",
                                color = KothonTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Refinement",
                                tint = KothonAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "Intelligently rephrases transcription to preserve intent while polishing grammar and tone.",
                            color = KothonTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        // Tone Selector Chips
                        Text(
                            text = "Refinement Tone Target",
                            color = KothonTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val popularTones = listOf(
                                KothonPreferences.TONE_PROFESSIONAL,
                                KothonPreferences.TONE_EXECUTIVE,
                                KothonPreferences.TONE_CONCISE
                            )
                            popularTones.forEach { toneName ->
                                val isSelected = currentTone == toneName
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        currentTone = toneName
                                        preferences.tone = toneName
                                    },
                                    label = {
                                        Text(
                                            text = toneName,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = KothonIndigo,
                                        selectedLabelColor = Color.White,
                                        containerColor = KothonDarkCard,
                                        labelColor = KothonTextSecondary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Toggle Switches for Refinement
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Eliminate Vocal Fillers",
                                    color = KothonTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Removes 'um', 'uh', 'mane', 'ar ki', and stutters",
                                    color = KothonTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = removeFillers,
                                onCheckedChange = {
                                    removeFillers = it
                                    preferences.removeFillers = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = KothonCyan
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Grammar & Punctuation Polish",
                                    color = KothonTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Fixes structure, capitalization, and agreements",
                                    color = KothonTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = fixGrammar,
                                onCheckedChange = {
                                    fixGrammar = it
                                    preferences.fixGrammar = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = KothonCyan
                                )
                            )
                        }
                    }
                }
            }

            // In-App Dictation & AI Studio
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voice_dictation_studio_card"),
                    colors = CardDefaults.cardColors(containerColor = KothonDarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(KothonBorder))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Live Speech Dictation & Translation Studio",
                            color = KothonTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Speak naturally in ${currentSourceLang.name} — AI refines and translates instantaneously.",
                            color = KothonTextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )

                        // Waveform Animation while recording
                        if (isRecording) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(36.dp)
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (i in 0 until 11) {
                                    val factor = (Math.sin((i.toDouble() / 11) * Math.PI) * 0.85 + 0.15).toFloat()
                                    val barHeight = (6 + (amplitude * 28 * factor)).coerceIn(4f, 32f).dp
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(KothonCyan)
                                    )
                                }
                            }
                        }

                        // Big Mic Action Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(96.dp)
                                .padding(vertical = 8.dp)
                        ) {
                            if (isRecording) {
                                Box(
                                    modifier = Modifier
                                        .size(88.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(KothonRose.copy(alpha = 0.25f))
                                )
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(if (isRecording) KothonRose else KothonCyan)
                                    .clickable {
                                        if (isRecording) {
                                            stopDictation()
                                        } else {
                                            startDictation()
                                        }
                                    }
                                    .testTag("studio_mic_button")
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(34.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = if (isRecording) "Stop" else "Record",
                                        tint = if (isRecording) Color.White else Color(0xFF0F172A),
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (isRecording) "Listening... Tap to finish & refine"
                            else if (isProcessing) "Refining & translating with $selectedProvider..."
                            else "Tap mic to start speaking (Tap again to stop)",
                            color = if (isRecording) KothonRose else if (isProcessing) KothonIndigo else KothonTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // Error Banner with Fallback Action
                        if (studioError != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = studioError ?: "",
                                    color = KothonRose,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )

                                if (selectedProvider != KothonPreferences.PROVIDER_GEMINI) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            selectedProvider = KothonPreferences.PROVIDER_GEMINI
                                            preferences.provider = KothonPreferences.PROVIDER_GEMINI
                                            studioError = null
                                            Toast.makeText(context, "Switched to Gemini (Built-in)", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = KothonIndigo,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Switch to Gemini (Built-in) & Try Again", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // Refined Result Card with Language Detection Badge
                        if (lastResult != null) {
                            val res = lastResult!!
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                                    .testTag("studio_result_card"),
                                colors = CardDefaults.cardColors(containerColor = KothonDarkCard),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF065F46))
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = "Detected: ${res.detectedLanguage}",
                                                    color = Color(0xFFA7F3D0),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "→ ${res.targetLanguage}",
                                                color = KothonCyan,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Row {
                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("Kothon Refined", res.refinedText))
                                                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy",
                                                    tint = KothonCyan,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, res.refinedText)
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, "Share refined text"))
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = "Share",
                                                    tint = KothonTextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Refined Output
                                    Text(
                                        text = res.refinedText,
                                        color = KothonTextPrimary,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                        fontWeight = FontWeight.Normal,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )

                                    // Optional verbatim raw transcript toggle
                                    if (res.rawTranscript.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier
                                                .clickable { showRawComparison = !showRawComparison }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (showRawComparison) "Hide original raw speech" else "View original raw speech",
                                                color = KothonAmber,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        if (showRawComparison) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF0F172A))
                                                    .padding(10.dp)
                                            ) {
                                                Text(
                                                    text = "\"${res.rawTranscript}\"",
                                                    color = KothonTextMuted,
                                                    fontSize = 12.sp,
                                                    lineHeight = 17.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Keyboard Setup & Playground Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("keyboard_setup_card"),
                    colors = CardDefaults.cardColors(containerColor = KothonDarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(KothonBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "System Keyboard & Test Playground",
                                color = KothonTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isKeyboardSelected) KothonEmerald else KothonAmber)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isKeyboardSelected) "Active" else if (isKeyboardEnabled) "Enabled" else "Not Enabled",
                                    color = if (isKeyboardSelected) KothonEmerald else KothonAmber,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            text = "Test Kothon keyboard directly in the playground field below, or switch system keyboards.",
                            color = KothonTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isKeyboardEnabled) KothonDarkCard else KothonCyan,
                                    contentColor = if (isKeyboardEnabled) KothonTextPrimary else Color(0xFF0F172A)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (isKeyboardEnabled) "1. Enabled ✓" else "1. Enable Keyboard",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Button(
                                onClick = {
                                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                    imm?.showInputMethodPicker()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isKeyboardSelected) KothonEmerald else KothonIndigo,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (isKeyboardSelected) "2. Selected ✓" else "2. Select Kothon",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = testInputText,
                            onValueChange = { testInputText = it },
                            placeholder = { Text("Tap here to open Kothon keyboard & dictate...", color = KothonTextMuted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("playground_text_field"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = KothonCyan,
                                unfocusedBorderColor = KothonBorder,
                                focusedTextColor = KothonTextPrimary,
                                unfocusedTextColor = KothonTextPrimary,
                                focusedContainerColor = KothonDarkCard,
                                unfocusedContainerColor = KothonDarkCard
                            ),
                            maxLines = 4
                        )
                    }
                }
            }

            // Recent Refined Transcriptions History
            item {
                Text(
                    text = "Refinement History & Logs",
                    color = KothonTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(recentNotes) { note ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = KothonDarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(KothonBorder))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KothonDarkCard)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${note.detectedLanguage} → ${note.targetLanguage}",
                                        color = KothonCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = note.tone,
                                    color = KothonAmber,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = note.timestamp,
                                color = KothonTextMuted,
                                fontSize = 11.sp
                            )
                        }

                        Text(
                            text = note.refinedText,
                            color = KothonTextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Copy text",
                                color = KothonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Kothon Note", note.refinedText))
                                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Microphone Permission Dialog
    if (showPermissionDialog) {
        MicrophonePermissionDialog(
            onGrantClick = {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onOpenSettingsClick = {
                context.startActivity(PermissionHelper.createPermissionIntent(context))
            },
            onDismissRequest = {
                showPermissionDialog = false
            }
        )
    }

    // Source Language Selection Sheet
    if (showLanguageSheetForSource) {
        LanguageSelectorBottomSheet(
            title = "Select Spoken Input Language",
            currentSelectedCode = sourceLangCode,
            isTargetLanguage = false,
            recentLanguageCodes = preferences.getRecentLanguages(),
            onLanguageSelected = { lang ->
                sourceLangCode = lang.code
                preferences.sourceLanguage = lang.code
                preferences.addRecentLanguage(lang.code)
                showLanguageSheetForSource = false
            },
            onDismissRequest = {
                showLanguageSheetForSource = false
            }
        )
    }

    // Target Language Selection Sheet
    if (showLanguageSheetForTarget) {
        LanguageSelectorBottomSheet(
            title = "Select Output / Translation Language",
            currentSelectedCode = targetLangCode,
            isTargetLanguage = true,
            recentLanguageCodes = preferences.getRecentLanguages(),
            onLanguageSelected = { lang ->
                targetLangCode = lang.code
                preferences.targetLanguage = lang.code
                preferences.addRecentLanguage(lang.code)
                showLanguageSheetForTarget = false
            },
            onDismissRequest = {
                showLanguageSheetForTarget = false
            }
        )
    }
}
