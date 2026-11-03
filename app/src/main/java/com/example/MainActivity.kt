package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.KothonAmber
import com.example.ui.theme.KothonBorder
import com.example.ui.theme.KothonCyan
import com.example.ui.theme.KothonDarkBg
import com.example.ui.theme.KothonDarkCard
import com.example.ui.theme.KothonEmerald
import com.example.ui.theme.KothonIndigo
import com.example.ui.theme.KothonRose
import com.example.ui.theme.KothonTextPrimary
import com.example.ui.theme.KothonTextSecondary
import com.example.ui.theme.MyApplicationTheme
import com.whisperflow.api.TranscriptionClient
import com.whisperflow.api.TranscriptionResult
import com.whisperflow.audio.AndroidSpeechSession
import com.whisperflow.audio.DeviceSpikeValidator
import com.whisperflow.audio.DictationState
import com.whisperflow.audio.LiveDictationController
import com.whisperflow.audio.LiveDictationSupport
import com.whisperflow.audio.PermissionHelper
import com.whisperflow.audio.Recorder
import com.whisperflow.audio.StudioEditorAdapter
import com.whisperflow.model.LanguageCatalog
import com.whisperflow.settings.KothonPreferences
import com.whisperflow.ui.BrandFooter
import com.whisperflow.ui.KothonCard
import com.whisperflow.ui.KothonChip
import com.whisperflow.ui.KothonGlassBackground
import com.whisperflow.ui.KothonLanguagePill
import com.whisperflow.ui.KothonModeSegment
import com.whisperflow.ui.KothonNote
import com.whisperflow.ui.KothonSectionLabel
import com.whisperflow.ui.KothonSettingSwitchRow
import com.whisperflow.ui.KothonStatusPill
import com.whisperflow.ui.LanguageSelectorBottomSheet
import com.whisperflow.ui.MicrophonePermissionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class RefinedStudioNote(
    val id: Long = System.currentTimeMillis(),
    val rawText: String,
    val refinedText: String,
    val detectedLanguage: String,
    val targetLanguage: String,
    val tone: String,
    val timestamp: String
)

private enum class HomeLanguageSheet { SOURCE, TARGET, LIVE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
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

    // Provider & API Key States (auto-synced with KothonPreferences)
    var selectedProvider by remember { mutableStateOf(preferences.provider) }
    var apiKeyText by remember { mutableStateOf(preferences.getApiKeyForProvider(preferences.provider)) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var isTestingKey by remember { mutableStateOf(false) }
    var keyTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // Language & Translation States
    var sourceLangCode by remember { mutableStateOf(preferences.sourceLanguage) }
    var targetLangCode by remember { mutableStateOf(preferences.targetLanguage) }
    var translateEnabled by remember { mutableStateOf(preferences.isTranslateEnabled) }
    var activeLanguageSheet by remember { mutableStateOf<HomeLanguageSheet?>(null) }

    // Refinement & Live Dictation States (merged from SettingsScreen)
    var currentTone by remember { mutableStateOf(preferences.tone) }
    var removeFillers by remember { mutableStateOf(preferences.removeFillers) }
    var fixGrammar by remember { mutableStateOf(preferences.fixGrammar) }
    var liveEnabled by remember { mutableStateOf(preferences.liveDictationEnabled) }
    var dictationMode by remember { mutableStateOf(preferences.dictationMode) }
    var autoRefine by remember { mutableStateOf(preferences.liveAutoRefine) }
    var preferOnDevice by remember { mutableStateOf(preferences.livePreferOnDevice) }
    var wifiOnly by remember { mutableStateOf(preferences.liveModelDownloadWifiOnly) }
    var liveLangCode by remember {
        mutableStateOf(
            when {
                LiveDictationSupport.isRealLanguage(preferences.liveLanguage) -> preferences.liveLanguage
                LiveDictationSupport.isRealLanguage(preferences.sourceLanguage) -> preferences.sourceLanguage
                else -> "bn"
            }
        )
    }

    val currentSourceLang = remember(sourceLangCode) { LanguageCatalog.findByCode(sourceLangCode) }
    val currentTargetLang = remember(targetLangCode) { LanguageCatalog.findByCode(targetLangCode) }
    val currentLiveLang = remember(liveLangCode) { LanguageCatalog.findByCode(liveLangCode) }
    val liveExcludedCodes = remember { setOf("auto", "original") }

    // Audio & Dictation state
    val studioEditor = remember { StudioEditorAdapter() }
    val speechSession = remember { AndroidSpeechSession(context) }
    val liveController = remember {
        LiveDictationController(
            speechSession = speechSession,
            editor = studioEditor,
            scope = scope,
            autoRefine = preferences.liveAutoRefine,
            textRefiner = { rawText ->
                transcriptionClient.refineText(
                    rawText = rawText,
                    sourceLanguageCode = preferences.sourceLanguage,
                    targetLanguageCode = preferences.targetLanguage,
                    tone = preferences.tone,
                    removeFillers = preferences.removeFillers,
                    fixGrammar = preferences.fixGrammar
                )
            }
        )
    }
    val liveUi by liveController.uiState.collectAsState()

    var liveStreamingText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var amplitude by remember { mutableFloatStateOf(0f) }
    var amplitudeJob by remember { mutableStateOf<Job?>(null) }
    var lastResult by remember { mutableStateOf<TranscriptionResult?>(null) }
    var studioError by remember { mutableStateOf<String?>(null) }
    var testInputText by remember { mutableStateOf("") }
    var showRawComparison by remember { mutableStateOf(false) }
    var refinementExpanded by remember { mutableStateOf(true) }
    var modelStatus by remember { mutableStateOf<String?>(null) }
    var checkingModel by remember { mutableStateOf(false) }
    var lastSavedSessionId by remember { mutableStateOf(0L) }
    var activeStudioMode by remember { mutableStateOf(KothonPreferences.MODE_ACCURATE) }
    val liveModeActive = liveEnabled && dictationMode == KothonPreferences.MODE_LIVE
    val livePreviewText = if (liveModeActive) liveUi.liveText else liveStreamingText

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

    DisposableEffect(Unit) {
        onDispose {
            liveController.cancelSession()
            speechSession.destroy()
            if (recorder.isRecording) {
                recorder.cancel()
            }
            amplitudeJob?.cancel()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshImeStatus()
                hasMicPermission = PermissionHelper.hasAudioPermission(context)
                liveEnabled = preferences.liveDictationEnabled
                dictationMode = preferences.dictationMode
                autoRefine = preferences.liveAutoRefine
                preferOnDevice = preferences.livePreferOnDevice
                wifiOnly = preferences.liveModelDownloadWifiOnly
                sourceLangCode = preferences.sourceLanguage
                targetLangCode = preferences.targetLanguage
                translateEnabled = preferences.isTranslateEnabled
                currentTone = preferences.tone
                removeFillers = preferences.removeFillers
                fixGrammar = preferences.fixGrammar
                selectedProvider = preferences.provider
                apiKeyText = preferences.getApiKeyForProvider(preferences.provider)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(liveUi.state, liveUi.sessionId) {
        when (liveUi.state) {
            DictationState.LISTENING -> {
                isRecording = true
                isProcessing = false
                liveStreamingText = liveUi.liveText
            }
            DictationState.STOPPING -> {
                isRecording = false
                isProcessing = true
            }
            DictationState.REFINING -> {
                isRecording = false
                isProcessing = true
                amplitude = 0f
                liveStreamingText = liveUi.liveText
            }
            DictationState.DONE -> {
                isRecording = false
                isProcessing = false
                amplitude = 0f
                liveStreamingText = liveUi.liveText
                val finalText = liveUi.liveText.ifBlank { studioEditor.text }.trim()
                if (finalText.isNotBlank()) {
                    val rawText = liveController.lastRawText.ifBlank { finalText }.trim()
                    val result = TranscriptionResult(
                        rawTranscript = rawText,
                        refinedText = finalText,
                        detectedLanguage = LanguageCatalog.findByCode(sourceLangCode).name,
                        targetLanguage = LanguageCatalog.findByCode(targetLangCode).name,
                        tone = currentTone
                    )
                    lastResult = result
                    if (liveUi.sessionId != 0L && liveUi.sessionId != lastSavedSessionId) {
                        lastSavedSessionId = liveUi.sessionId
                        recentNotes.add(
                            0,
                            RefinedStudioNote(
                                rawText = rawText,
                                refinedText = finalText,
                                detectedLanguage = result.detectedLanguage,
                                targetLanguage = result.targetLanguage,
                                tone = currentTone,
                                timestamp = "Just now"
                            )
                        )
                    }
                } else if (liveUi.statusMessage.isNotBlank() &&
                    liveUi.statusMessage != "Done."
                ) {
                    studioError = liveUi.statusMessage
                }
            }
            DictationState.ERROR -> {
                isRecording = false
                isProcessing = false
                amplitude = 0f
                studioError = liveUi.statusMessage
            }
            DictationState.IDLE -> {
                if (!isRecording && !isProcessing) amplitude = 0f
            }
        }
    }

    fun cancelStudioDictation() {
        liveController.cancelSession()
        if (recorder.isRecording) recorder.cancel()
        amplitudeJob?.cancel()
        amplitudeJob = null
        isRecording = false
        isProcessing = false
        amplitude = 0f
        liveStreamingText = ""
        studioEditor.reset()
    }

    fun selectDictationMode(mode: String) {
        cancelStudioDictation()
        if (mode == KothonPreferences.MODE_LIVE && !liveEnabled) {
            liveEnabled = true
            preferences.liveDictationEnabled = true
        }
        dictationMode = mode
        preferences.dictationMode = mode
        lastResult = null
        studioError = null
        modelStatus = null
    }

    fun undoStudioLive() {
        val undone = liveController.undo()
        if (undone) {
            val text = studioEditor.text.trim()
            liveStreamingText = text
            lastResult = lastResult?.copy(refinedText = text)
        } else {
            studioError = "Undo unavailable. The text may have been edited."
        }
    }

    fun refineStudioLive() {
        liveController.refineNow()
    }

    fun checkStudioModelSupport() {
        if (checkingModel) return
        val languageTag = LiveDictationSupport.resolveLanguageTag(
            liveLanguage = liveLangCode,
            sourceLanguage = sourceLangCode,
            deviceLanguageTag = Locale.getDefault().toLanguageTag()
        )
        if (android.os.Build.VERSION.SDK_INT < 33) {
            modelStatus = "On-device model check requires Android 13 (API 33) or newer."
            return
        }
        checkingModel = true
        modelStatus = "Checking on-device model for $languageTag…"
        scope.launch {
            val support = DeviceSpikeValidator.checkLanguageSupport(context, languageTag)
            checkingModel = false
            if (support == null) {
                modelStatus = "Could not check model support on this device."
                return@launch
            }
            modelStatus = when {
                support.installedOnDevice -> "On-device model installed for $languageTag. ✓"
                support.supportedOnDevice -> {
                    val triggered = DeviceSpikeValidator.triggerModelDownload(context, languageTag)
                    if (triggered) {
                        "Download requested for $languageTag. Follow the system prompt."
                    } else {
                        "Model download is not available for $languageTag."
                    }
                }
                support.supportedOnline -> "No on-device model for $languageTag. Online recognition is available."
                else -> "Speech model not available for $languageTag on this device."
            }
        }
    }

    fun openProviderApiKeyPage(provider: String) {
        val url = TranscriptionClient.getProviderApiUrl(provider)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Open: $url", Toast.LENGTH_LONG).show()
        }
    }

    fun startStudioAmplitudePolling() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            while (isActive && recorder.isRecording) {
                amplitude = (recorder.getMaxAmplitude() / 32767f).coerceIn(0f, 1f)
                delay(100)
            }
            amplitude = 0f
        }
    }

    fun stopDictation() {
        if (!isRecording) return

        if (activeStudioMode == KothonPreferences.MODE_LIVE) {
            isRecording = false
            isProcessing = true
            liveController.stop()
            return
        }

        isRecording = false
        amplitudeJob?.cancel()
        amplitudeJob = null
        amplitude = 0f

        val file = recorder.stop()
        if (file == null || !file.exists() || file.length() < 2000L) {
            file?.delete()
            studioError = "No speech detected. Tap the mic, speak, then tap again."
            return
        }

        isProcessing = true
        studioError = null

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    transcriptionClient.processAudio(
                        audioFile = file,
                        sourceLanguageCode = sourceLangCode,
                        targetLanguageCode = if (translateEnabled) targetLangCode else "original",
                        tone = currentTone,
                        removeFillers = removeFillers,
                        fixGrammar = fixGrammar
                    )
                }
                lastResult = result
                recentNotes.add(
                    0,
                    RefinedStudioNote(
                        rawText = result.rawTranscript,
                        refinedText = result.refinedText,
                        detectedLanguage = result.detectedLanguage,
                        targetLanguage = result.targetLanguage,
                        tone = result.tone,
                        timestamp = "Just now"
                    )
                )
                liveStreamingText = result.refinedText
            } catch (e: Exception) {
                studioError = e.message ?: "Transcription failed. Check your $selectedProvider API key or network."
            } finally {
                file.delete()
                isProcessing = false
            }
        }
    }

    fun startDictation() {
        if (!hasMicPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (isRecording || isProcessing) return

        studioError = null
        lastResult = null
        liveStreamingText = ""
        studioEditor.reset()
        activeStudioMode = if (liveModeActive) {
            KothonPreferences.MODE_LIVE
        } else {
            KothonPreferences.MODE_ACCURATE
        }

        if (activeStudioMode == KothonPreferences.MODE_LIVE) {
            if (!DeviceSpikeValidator.isRecognitionAvailable(context)) {
                studioError = "Speech recognition is not available on this device."
                return
            }
            val languageTag = LiveDictationSupport.resolveLanguageTag(
                liveLanguage = liveLangCode,
                sourceLanguage = sourceLangCode,
                deviceLanguageTag = Locale.getDefault().toLanguageTag()
            )
            liveController.autoRefine = autoRefine
            isRecording = true
            liveController.start(
                LiveDictationSupport.buildSpeechConfig(preferences, languageTag),
                LiveDictationSupport.separatorFor(languageTag)
            )
        } else {
            try {
                recorder.start(onTimeout = { stopDictation() })
                isRecording = true
                startStudioAmplitudePolling()
            } catch (e: Exception) {
                isRecording = false
                studioError = "Failed to start audio recording: ${e.message}"
            }
        }
    }

    KothonGlassBackground {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(KothonCyan, KothonIndigo)
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Kothon logo",
                                tint = Color(0xFF090D16),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Kothon",
                                color = KothonTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp
                            )
                            Text(
                                text = "$selectedProvider · ${if (liveModeActive) "Live Mode" else "Accurate Mode"}",
                                color = KothonCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    KothonStatusPill(
                        text = when {
                            isKeyboardSelected -> "Keyboard Active"
                            isKeyboardEnabled -> "Keyboard Ready"
                            else -> "Setup Keyboard"
                        },
                        color = when {
                            isKeyboardSelected -> KothonEmerald
                            isKeyboardEnabled -> KothonCyan
                            else -> KothonAmber
                        }
                    )
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ---------------------------------------------------------------
                // 1. Dictation Studio Glass Card
                // ---------------------------------------------------------------
                item {
                    KothonCard(
                        modifier = Modifier.testTag("voice_dictation_studio_card"),
                        accentBorderColor = if (isRecording) KothonRose else KothonCyan
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Voice Dictation Studio",
                                    color = KothonTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${currentSourceLang.name}  →  ${if (translateEnabled) currentTargetLang.name else "Keep Original"}",
                                    color = KothonTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.07f))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                                    .selectableGroup()
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                KothonModeSegment(
                                    label = "Accurate",
                                    selected = !liveModeActive,
                                    onClick = { selectDictationMode(KothonPreferences.MODE_ACCURATE) },
                                    modifier = Modifier.testTag("studio_mode_accurate")
                                )
                                KothonModeSegment(
                                    label = "Live",
                                    selected = liveModeActive,
                                    enabled = true,
                                    selectedColor = KothonIndigo,
                                    onClick = { selectDictationMode(KothonPreferences.MODE_LIVE) },
                                    modifier = Modifier.testTag("studio_mode_live")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Microphone action
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(86.dp)
                            ) {
                                if (isRecording) {
                                    Box(
                                        modifier = Modifier
                                            .size(78.dp)
                                            .scale(pulseScale)
                                            .clip(CircleShape)
                                            .background(KothonRose.copy(alpha = 0.24f))
                                    )
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isRecording -> Brush.linearGradient(listOf(KothonRose, Color(0xFFEF4444)))
                                                isProcessing -> Brush.linearGradient(listOf(KothonIndigo, KothonCyan))
                                                else -> Brush.linearGradient(listOf(KothonCyan, Color(0xFF0EA5E9)))
                                            }
                                        )
                                        .border(1.2.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = if (isRecording) "Stop dictation" else "Start dictation"
                                        ) {
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
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                            contentDescription = if (isRecording) "Stop dictation" else "Start dictation",
                                            tint = if (isRecording) Color.White else Color(0xFF090D16),
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = when {
                                    isRecording -> "Listening — tap to stop & refine with $selectedProvider"
                                    isProcessing -> "Refining with $selectedProvider…"
                                    liveModeActive -> "Tap to stream live speech ($selectedProvider)"
                                    else -> "Tap to record audio & refine with $selectedProvider"
                                },
                                color = when {
                                    isRecording -> KothonRose
                                    isProcessing -> KothonIndigo
                                    else -> KothonTextSecondary
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite }
                            )
                        }

                        // Live / preview transcript
                        if ((isRecording || isProcessing) && livePreviewText.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .border(0.8.dp, KothonCyan.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                                    .testTag("live_speech_stream_card")
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (liveModeActive) KothonCyan else KothonRose)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (liveModeActive) "LIVE TRANSCRIPT" else "PREVIEW",
                                            color = KothonCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp
                                        )
                                        Text(
                                            text = livePreviewText,
                                            color = KothonTextPrimary,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                            maxLines = 5,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Manual undo / refine after a live session
                        if (liveModeActive && (liveUi.canUndo || liveUi.canRefine)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (liveUi.canUndo) {
                                    TextButton(
                                        onClick = { undoStudioLive() },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .testTag("studio_undo_button")
                                    ) {
                                        Text("Undo AI", fontSize = 11.sp)
                                    }
                                }
                                if (liveUi.canRefine) {
                                    Button(
                                        onClick = { refineStudioLive() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = KothonAmber,
                                            contentColor = Color(0xFF090D16)
                                        ),
                                        shape = RoundedCornerShape(9.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .testTag("studio_refine_button")
                                    ) {
                                        Text("Refine with AI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Error banner
                        if (studioError != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(KothonRose.copy(alpha = 0.12f))
                                    .border(0.8.dp, KothonRose.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Column {
                                    Text(
                                        text = studioError ?: "",
                                        color = KothonRose,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                    Row(
                                        modifier = Modifier.padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        TextButton(
                                            onClick = { openProviderApiKeyPage(selectedProvider) },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text(
                                                "Get $selectedProvider API Key",
                                                color = KothonCyan,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (selectedProvider == KothonPreferences.PROVIDER_NVIDIA && !liveModeActive) {
                                            TextButton(
                                                onClick = {
                                                    selectDictationMode(KothonPreferences.MODE_LIVE)
                                                    studioError = null
                                                },
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text(
                                                    "Switch to Live Mode",
                                                    color = KothonEmerald,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------------------------------------------------------------
                // 2. Latest Refined Result Glass Card
                // ---------------------------------------------------------------
                if (lastResult != null) {
                    item {
                        val res = lastResult!!
                        KothonCard(
                            modifier = Modifier.testTag("studio_result_card"),
                            accentBorderColor = KothonEmerald
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(KothonCyan.copy(alpha = 0.16f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = res.detectedLanguage,
                                            color = KothonCyan,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "  →  ${res.targetLanguage}",
                                        color = KothonTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Kothon Refined", res.refinedText))
                                            Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy refined text",
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
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share refined text",
                                            tint = KothonTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = res.refinedText,
                                color = KothonTextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )

                            if (res.rawTranscript.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showRawComparison = !showRawComparison }
                                        .padding(vertical = 4.dp, horizontal = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (showRawComparison) "Hide original speech" else "Show original speech",
                                        color = KothonAmber,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (showRawComparison) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = "\"${res.rawTranscript}\"",
                                            color = KothonTextSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------------------------------------------------------------
                // 3. AI Provider & "Get API" Glass Card
                // ---------------------------------------------------------------
                item {
                    KothonCard(modifier = Modifier.testTag("ai_provider_card")) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                KothonSectionLabel("AI MODEL PROVIDER")
                                Text(
                                    text = when (selectedProvider) {
                                        KothonPreferences.PROVIDER_GEMINI -> "Gemini 2.5 / 3.8 Flash · Multimodal Audio + Text"
                                        KothonPreferences.PROVIDER_GROQ -> "Whisper Large-v3 Turbo + Llama 3.3 70B · Ultra Fast"
                                        KothonPreferences.PROVIDER_OPENAI -> "Whisper-1 + GPT-4o-mini · High Precision"
                                        KothonPreferences.PROVIDER_NVIDIA -> "NVIDIA NIM + Llama 3.3 Nemotron · High Fidelity"
                                        else -> ""
                                    },
                                    color = KothonTextSecondary,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            KothonStatusPill(
                                text = if (apiKeyText.isNotBlank()) "Key Saved" else "Key Needed",
                                color = if (apiKeyText.isNotBlank()) KothonEmerald else KothonAmber
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup()
                        ) {
                            KothonPreferences.PROVIDERS.forEach { provider ->
                                KothonChip(
                                    label = provider,
                                    selected = selectedProvider == provider,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        selectedProvider = provider
                                        preferences.provider = provider
                                        apiKeyText = preferences.getApiKeyForProvider(provider)
                                        keyTestResult = null
                                        studioError = null
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // API Key Field + Get API button header row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = KothonCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "$selectedProvider API Key",
                                    color = KothonTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // "Get API" Button that opens selected provider's official API portal
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                KothonCyan.copy(alpha = 0.22f),
                                                KothonIndigo.copy(alpha = 0.22f)
                                            )
                                        )
                                    )
                                    .border(0.8.dp, KothonCyan.copy(alpha = 0.50f), RoundedCornerShape(999.dp))
                                    .clickable(role = Role.Button) {
                                        openProviderApiKeyPage(selectedProvider)
                                    }
                                    .padding(horizontal = 11.dp, vertical = 5.dp)
                                    .testTag("get_api_key_button"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Get API",
                                    color = KothonCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Get $selectedProvider API Key",
                                    tint = KothonCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = apiKeyText,
                            onValueChange = { newValue ->
                                apiKeyText = newValue
                                preferences.setApiKeyForProvider(selectedProvider, newValue)
                                keyTestResult = null
                            },
                            placeholder = {
                                Text(
                                    text = when (selectedProvider) {
                                        KothonPreferences.PROVIDER_GROQ -> "Paste Groq API key (gsk_…)"
                                        KothonPreferences.PROVIDER_OPENAI -> "Paste OpenAI API key (sk-…)"
                                        KothonPreferences.PROVIDER_NVIDIA -> "Paste NVIDIA NIM API key (nvapi-…)"
                                        else -> "Paste Gemini API key (AIza…)"
                                    },
                                    color = KothonTextSecondary,
                                    fontSize = 12.sp
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(
                                        imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (apiKeyVisible) "Hide key" else "Show key",
                                        tint = KothonTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("provider_api_key_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = KothonCyan,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                                focusedTextColor = KothonTextPrimary,
                                unfocusedTextColor = KothonTextPrimary,
                                focusedContainerColor = Color(0xFF090D16).copy(alpha = 0.55f),
                                unfocusedContainerColor = Color(0xFF090D16).copy(alpha = 0.45f)
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isTestingKey = true
                                        val res = withContext(Dispatchers.IO) {
                                            transcriptionClient.testProviderConnection(selectedProvider, apiKeyText)
                                        }
                                        keyTestResult = res
                                        isTestingKey = false
                                    }
                                },
                                enabled = !isTestingKey && apiKeyText.isNotBlank(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                if (isTestingKey) {
                                    CircularProgressIndicator(
                                        color = KothonCyan,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Text(
                                        text = "Test Connection",
                                        color = KothonTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    preferences.provider = selectedProvider
                                    preferences.setApiKeyForProvider(selectedProvider, apiKeyText)
                                    Toast.makeText(context, "$selectedProvider key saved!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KothonCyan,
                                    contentColor = Color(0xFF090D16)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Text("Save Key", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (keyTestResult != null) {
                            val (ok, msg) = keyTestResult!!
                            Text(
                                text = msg,
                                color = if (ok) KothonEmerald else KothonRose,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }

                // ---------------------------------------------------------------
                // 4. Languages & Translation Glass Card
                // ---------------------------------------------------------------
                item {
                    KothonCard(modifier = Modifier.testTag("language_selection_card")) {
                        KothonSectionLabel(
                            text = "LANGUAGES & TRANSLATION",
                            trailing = {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = KothonCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            KothonLanguagePill(
                                label = "SPOKEN INPUT",
                                flag = currentSourceLang.flagEmoji,
                                name = currentSourceLang.name,
                                accent = false,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("source_language_selector_button"),
                                onClick = { activeLanguageSheet = HomeLanguageSheet.SOURCE }
                            )

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
                                    .padding(horizontal = 6.dp)
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                                    .testTag("swap_languages_button")
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
                                flag = currentTargetLang.flagEmoji,
                                name = if (!translateEnabled || targetLangCode == "original") "Keep Original" else currentTargetLang.name,
                                accent = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("target_language_selector_button"),
                                onClick = { activeLanguageSheet = HomeLanguageSheet.TARGET }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        KothonSettingSwitchRow(
                            title = "Translate output",
                            subtitle = "Automatically translate refined speech into the output language",
                            checked = translateEnabled,
                            onCheckedChange = {
                                translateEnabled = it
                                preferences.isTranslateEnabled = it
                            }
                        )
                    }
                }

                // ---------------------------------------------------------------
                // 5. Merged Refinement & Live Dictation Glass Card
                // ---------------------------------------------------------------
                item {
                    KothonCard(modifier = Modifier.testTag("advanced_refinement_card")) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = if (refinementExpanded) "Collapse refinement options" else "Expand refinement options"
                                ) { refinementExpanded = !refinementExpanded }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = KothonAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "AI Tone, Refinement & Live Engine",
                                        color = KothonTextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "$currentTone · Fillers ${if (removeFillers) "On" else "Off"} · Live ${if (liveEnabled) "On" else "Off"}",
                                        color = KothonTextSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (refinementExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = KothonTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        AnimatedVisibility(visible = refinementExpanded) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(
                                    text = "AI TONE",
                                    color = KothonTextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectableGroup(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(KothonPreferences.TONES) { toneName ->
                                        KothonChip(
                                            label = toneName,
                                            selected = currentTone == toneName,
                                            selectedColor = KothonAmber,
                                            onClick = {
                                                currentTone = toneName
                                                preferences.tone = toneName
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                KothonSettingSwitchRow(
                                    title = "Remove vocal fillers",
                                    subtitle = "Drops “um”, “uh”, “mane” and stutters",
                                    checked = removeFillers,
                                    onCheckedChange = {
                                        removeFillers = it
                                        preferences.removeFillers = it
                                    }
                                )
                                KothonSettingSwitchRow(
                                    title = "Polish grammar & punctuation",
                                    subtitle = "Fixes sentence structure, casing and agreement",
                                    checked = fixGrammar,
                                    onCheckedChange = {
                                        fixGrammar = it
                                        preferences.fixGrammar = it
                                    }
                                )

                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )

                                KothonSectionLabel("LIVE DICTATION ENGINE")
                                Spacer(modifier = Modifier.height(4.dp))

                                KothonSettingSwitchRow(
                                    title = "Enable live streaming dictation",
                                    subtitle = "Real-time word-by-word streaming recognition while speaking",
                                    checked = liveEnabled,
                                    tint = KothonEmerald,
                                    onCheckedChange = {
                                        liveEnabled = it
                                        preferences.liveDictationEnabled = it
                                        if (!it && dictationMode == KothonPreferences.MODE_LIVE) {
                                            dictationMode = KothonPreferences.MODE_ACCURATE
                                            preferences.dictationMode = KothonPreferences.MODE_ACCURATE
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                KothonLanguagePill(
                                    label = "LIVE SPEECH RECOGNITION LANGUAGE",
                                    flag = currentLiveLang.flagEmoji,
                                    name = currentLiveLang.name,
                                    accent = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { activeLanguageSheet = HomeLanguageSheet.LIVE }
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                KothonSettingSwitchRow(
                                    title = "Auto-refine live speech with AI",
                                    subtitle = "Polish live transcript with $selectedProvider when you stop",
                                    checked = autoRefine,
                                    tint = KothonIndigo,
                                    modifier = Modifier.testTag("studio_auto_refine_switch"),
                                    onCheckedChange = {
                                        autoRefine = it
                                        preferences.liveAutoRefine = it
                                        liveController.autoRefine = it
                                    }
                                )
                                KothonSettingSwitchRow(
                                    title = "Prefer on-device recognition",
                                    subtitle = "Use local speech model when installed",
                                    checked = preferOnDevice,
                                    tint = KothonEmerald,
                                    onCheckedChange = {
                                        preferOnDevice = it
                                        preferences.livePreferOnDevice = it
                                    }
                                )
                                KothonSettingSwitchRow(
                                    title = "Download models over Wi-Fi only",
                                    subtitle = "Avoid mobile data when fetching offline speech packs",
                                    checked = wifiOnly,
                                    tint = KothonAmber,
                                    onCheckedChange = {
                                        wifiOnly = it
                                        preferences.liveModelDownloadWifiOnly = it
                                    }
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                                        .clickable(enabled = !checkingModel) { checkStudioModelSupport() }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .testTag("studio_check_model_button"),
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
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = "Check on-device speech model",
                                        color = KothonTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                modelStatus?.let { msg ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    KothonNote(
                                        text = msg,
                                        color = KothonCyan
                                    )
                                }
                            }
                        }
                    }
                }

                // ---------------------------------------------------------------
                // 6. System Keyboard Setup & Playground Glass Card
                // ---------------------------------------------------------------
                item {
                    KothonCard(modifier = Modifier.testTag("keyboard_setup_card")) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Keyboard,
                                    contentDescription = null,
                                    tint = KothonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "System Keyboard & Gboard-Style Typing",
                                    color = KothonTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            KothonStatusPill(
                                text = when {
                                    isKeyboardSelected -> "Active"
                                    isKeyboardEnabled -> "Enabled"
                                    else -> "Off"
                                },
                                color = when {
                                    isKeyboardSelected -> KothonEmerald
                                    isKeyboardEnabled -> KothonCyan
                                    else -> KothonAmber
                                }
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isKeyboardEnabled) KothonDarkCard.copy(alpha = 0.7f) else KothonCyan,
                                    contentColor = if (isKeyboardEnabled) KothonTextPrimary else Color(0xFF090D16)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Text(
                                    text = if (isKeyboardEnabled) "1 · Enabled ✓" else "1 · Enable IME",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Button(
                                onClick = {
                                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                    imm?.showInputMethodPicker()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isKeyboardSelected) KothonDarkCard.copy(alpha = 0.7f) else KothonIndigo,
                                    contentColor = if (isKeyboardSelected) KothonEmerald else Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Text(
                                    text = if (isKeyboardSelected) "2 · Selected ✓" else "2 · Select IME",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        OutlinedTextField(
                            value = testInputText,
                            onValueChange = { testInputText = it },
                            placeholder = {
                                Text(
                                    text = "Tap here to test Voice AI & Gboard-style keyboard…",
                                    color = KothonTextSecondary,
                                    fontSize = 12.sp
                                )
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .testTag("playground_text_field"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = KothonCyan,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                                focusedTextColor = KothonTextPrimary,
                                unfocusedTextColor = KothonTextPrimary,
                                focusedContainerColor = Color(0xFF090D16).copy(alpha = 0.55f),
                                unfocusedContainerColor = Color(0xFF090D16).copy(alpha = 0.45f)
                            ),
                            minLines = 1,
                            maxLines = 3
                        )
                    }
                }

                // ---------------------------------------------------------------
                // 7. History
                // ---------------------------------------------------------------
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 2.dp, end = 2.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Dictations",
                            color = KothonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${recentNotes.size} item${if (recentNotes.size == 1) "" else "s"}",
                            color = KothonTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                items(recentNotes, key = { it.id }) { note ->
                    KothonCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KothonCyan.copy(alpha = 0.14f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${note.detectedLanguage} → ${note.targetLanguage}",
                                        color = KothonCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = note.tone,
                                    color = KothonAmber,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = note.timestamp,
                                color = KothonTextSecondary,
                                fontSize = 9.sp
                            )
                        }

                        Text(
                            text = note.refinedText,
                            color = KothonTextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                        )

                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Kothon Note", note.refinedText))
                                    Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = KothonCyan,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Copy",
                                color = KothonCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BrandFooter()
                    }
                }
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

    // Unified Language Selection Bottom Sheet (Source, Target, or Live)
    if (activeLanguageSheet != null) {
        val sheetTitle = when (activeLanguageSheet) {
            HomeLanguageSheet.SOURCE -> "Select Spoken Input Language"
            HomeLanguageSheet.TARGET -> "Select Output / Translation Language"
            HomeLanguageSheet.LIVE -> "Select Live Recognition Language"
            null -> ""
        }
        val currentCode = when (activeLanguageSheet) {
            HomeLanguageSheet.SOURCE -> sourceLangCode
            HomeLanguageSheet.TARGET -> targetLangCode
            HomeLanguageSheet.LIVE -> liveLangCode
            null -> ""
        }
        LanguageSelectorBottomSheet(
            title = sheetTitle,
            currentSelectedCode = currentCode,
            isTargetLanguage = activeLanguageSheet == HomeLanguageSheet.TARGET,
            recentLanguageCodes = preferences.getRecentLanguages(),
            excludeCodes = if (activeLanguageSheet == HomeLanguageSheet.LIVE) liveExcludedCodes else emptySet(),
            onLanguageSelected = { lang ->
                when (activeLanguageSheet) {
                    HomeLanguageSheet.SOURCE -> {
                        sourceLangCode = lang.code
                        preferences.sourceLanguage = lang.code
                        if (lang.code != "auto" && lang.code != "original") {
                            liveLangCode = lang.code
                            preferences.liveLanguage = lang.code
                        }
                        preferences.addRecentLanguage(lang.code)
                    }
                    HomeLanguageSheet.TARGET -> {
                        targetLangCode = lang.code
                        preferences.targetLanguage = lang.code
                        preferences.addRecentLanguage(lang.code)
                    }
                    HomeLanguageSheet.LIVE -> {
                        liveLangCode = lang.code
                        preferences.liveLanguage = lang.code
                    }
                    null -> Unit
                }
                activeLanguageSheet = null
            },
            onDismissRequest = {
                activeLanguageSheet = null
            }
        )
    }
}
