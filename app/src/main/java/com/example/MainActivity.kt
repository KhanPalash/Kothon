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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.whisperflow.audio.AndroidSpeechSession
import com.whisperflow.audio.DeviceSpikeValidator
import com.whisperflow.audio.DictationState
import com.whisperflow.audio.LiveDictationController
import com.whisperflow.audio.LiveDictationSupport
import com.whisperflow.audio.PermissionHelper
import com.whisperflow.audio.Recorder
import com.whisperflow.audio.StudioEditorAdapter
import com.whisperflow.model.Language
import com.whisperflow.model.LanguageCatalog
import com.whisperflow.settings.KothonPreferences
import com.whisperflow.settings.SettingsActivity
import com.whisperflow.ui.LanguageSelectorBottomSheet
import com.whisperflow.ui.BrandFooter
import com.whisperflow.ui.KothonCard
import com.whisperflow.ui.KothonChip
import com.whisperflow.ui.KothonLanguagePill
import com.whisperflow.ui.KothonModeSegment
import com.whisperflow.ui.KothonSettingSwitchRow
import com.whisperflow.ui.KothonStatusPill
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
    var refinementExpanded by remember { mutableStateOf(false) }
    var liveEnabled by remember { mutableStateOf(preferences.liveDictationEnabled) }
    var dictationMode by remember { mutableStateOf(preferences.dictationMode) }
    var autoRefine by remember { mutableStateOf(preferences.liveAutoRefine) }
    var modelStatus by remember { mutableStateOf<String?>(null) }
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
                liveEnabled = preferences.liveDictationEnabled
                dictationMode = preferences.dictationMode
                autoRefine = preferences.liveAutoRefine
                sourceLangCode = preferences.sourceLanguage
                targetLangCode = preferences.targetLanguage
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
        if (mode == KothonPreferences.MODE_LIVE && !liveEnabled) return
        cancelStudioDictation()
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
        val languageTag = LiveDictationSupport.resolveLanguageTag(
            liveLanguage = preferences.liveLanguage,
            sourceLanguage = sourceLangCode,
            deviceLanguageTag = Locale.getDefault().toLanguageTag()
        )
        if (android.os.Build.VERSION.SDK_INT < 33) {
            modelStatus = "On-device model check requires Android 13 (API 33) or newer."
            return
        }
        modelStatus = "Checking on-device model for $languageTag…"
        scope.launch {
            val support = DeviceSpikeValidator.checkLanguageSupport(context, languageTag)
            if (support == null) {
                modelStatus = "Could not check model support on this device."
                return@launch
            }
            modelStatus = when {
                support.installedOnDevice -> "On-device model installed for $languageTag."
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

    fun startStudioAmplitudePolling() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            while (isActive && recorder.isRecording) {
                amplitude = (recorder.getMaxAmplitude() / 32767f).coerceIn(0f, 1f)
                delay(80)
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
                studioError = e.message ?: "Transcription failed. Check your API key or network connection."
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
                liveLanguage = preferences.liveLanguage,
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
                .fillMaxHeight(0.5f)
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(KothonCyan),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Kothon logo",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Kothon",
                            color = KothonTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        )
                        Text(
                            text = when {
                                isKeyboardSelected -> "Keyboard active"
                                isKeyboardEnabled -> "Keyboard enabled"
                                else -> "Voice AI studio"
                            },
                            color = when {
                                isKeyboardSelected -> KothonEmerald
                                isKeyboardEnabled -> KothonCyan
                                else -> KothonTextSecondary
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(
                    onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(KothonDarkCard)
                        .testTag("open_settings_header_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = KothonTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ---------------------------------------------------------------
            // 1. Dictation studio (primary action)
            // ---------------------------------------------------------------
            item {
                KothonCard(modifier = Modifier.testTag("voice_dictation_studio_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dictation",
                                color = KothonTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${currentSourceLang.name}  →  ${currentTargetLang.name}",
                                color = KothonTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Compact mode segmented control
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(KothonDarkCard)
                                .selectableGroup()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
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
                                enabled = liveEnabled,
                                selectedColor = KothonIndigo,
                                onClick = { selectDictationMode(KothonPreferences.MODE_LIVE) },
                                modifier = Modifier.testTag("studio_mode_live")
                            )
                        }
                    }

                    if (!liveEnabled) {
                        Text(
                            text = "Live mode is off — enable it in Settings",
                            color = KothonAmber,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Microphone action
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(84.dp)
                        ) {
                            if (isRecording) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(KothonRose.copy(alpha = 0.22f))
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(66.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isRecording -> KothonRose
                                            isProcessing -> KothonIndigo
                                            else -> KothonCyan
                                        }
                                    )
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
                                        tint = if (isRecording) Color.White else Color(0xFF0F172A),
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = when {
                                isRecording -> "Listening — tap to stop & refine"
                                isProcessing -> "Refining with $selectedProvider…"
                                liveModeActive -> "Tap to start live dictation"
                                else -> "Tap to record, then refine with AI"
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(KothonDarkCard)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
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
                                        contentColor = Color(0xFF0F172A)
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(KothonRose.copy(alpha = 0.10f))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = studioError ?: "",
                                    color = KothonRose,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                                if (selectedProvider != KothonPreferences.PROVIDER_GEMINI) {
                                    TextButton(
                                        onClick = {
                                            selectedProvider = KothonPreferences.PROVIDER_GEMINI
                                            preferences.provider = KothonPreferences.PROVIDER_GEMINI
                                            studioError = null
                                            Toast.makeText(context, "Switched to Gemini (Built-in)", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Switch to Gemini (Built-in)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------
            // 2. Latest refined result
            // ---------------------------------------------------------------
            if (lastResult != null) {
                item {
                    val res = lastResult!!
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_result_card"),
                        colors = CardDefaults.cardColors(containerColor = KothonDarkCard),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, KothonCyan.copy(alpha = 0.35f))
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
                                            .background(KothonCyan.copy(alpha = 0.14f))
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
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
                                        modifier = Modifier.size(34.dp)
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
                                        modifier = Modifier.size(34.dp)
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
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(KothonDarkBg)
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
            }

            // ---------------------------------------------------------------
            // 3. Languages
            // ---------------------------------------------------------------
            item {
                KothonCard(modifier = Modifier.testTag("language_selection_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Language",
                            color = KothonTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            tint = KothonTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KothonLanguagePill(
                            label = "SPEAK",
                            flag = currentSourceLang.flagEmoji,
                            name = currentSourceLang.name,
                            accent = false,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("source_language_selector_button"),
                            onClick = { showLanguageSheetForSource = true }
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
                                .padding(horizontal = 4.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(KothonDarkBg)
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
                            name = currentTargetLang.name,
                            accent = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("target_language_selector_button"),
                            onClick = { showLanguageSheetForTarget = true }
                        )
                    }
                }
            }

            // ---------------------------------------------------------------
            // 4. AI provider
            // ---------------------------------------------------------------
            item {
                KothonCard(modifier = Modifier.testTag("ai_provider_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI provider",
                            color = KothonTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                        KothonStatusPill(
                            text = when {
                                selectedProvider == KothonPreferences.PROVIDER_GEMINI -> "Built-in"
                                apiKeyText.isNotBlank() -> "Key saved"
                                else -> "Key required"
                            },
                            color = if (selectedProvider == KothonPreferences.PROVIDER_GEMINI || apiKeyText.isNotBlank()) KothonEmerald else KothonAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup()
                    ) {
                        items(KothonPreferences.PROVIDERS) { provider ->
                            KothonChip(
                                label = if (provider == KothonPreferences.PROVIDER_GEMINI) "Gemini" else provider,
                                selected = selectedProvider == provider,
                                onClick = {
                                    selectedProvider = provider
                                    preferences.provider = provider
                                    apiKeyText = preferences.getApiKeyForProvider(provider)
                                    keyTestResult = null
                                }
                            )
                        }
                    }

                    Text(
                        text = when (selectedProvider) {
                            KothonPreferences.PROVIDER_GEMINI -> "Gemini 3.8 Flash · built-in key ready"
                            KothonPreferences.PROVIDER_GROQ -> "Whisper large-v3 + Llama 3.3 70B · fastest"
                            KothonPreferences.PROVIDER_OPENAI -> "Whisper-1 + GPT-4o-mini · high accuracy"
                            KothonPreferences.PROVIDER_NVIDIA -> "Nemotron 30B transcribe · high fidelity"
                            else -> ""
                        },
                        color = KothonTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    if (selectedProvider != KothonPreferences.PROVIDER_GEMINI || showApiKeyInput) {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = apiKeyText,
                            onValueChange = {
                                apiKeyText = it
                                preferences.setApiKeyForProvider(selectedProvider, it.trim())
                                keyTestResult = null
                            },
                            placeholder = {
                                Text(
                                    text = when (selectedProvider) {
                                        KothonPreferences.PROVIDER_GROQ -> "Groq key (gsk_…)"
                                        KothonPreferences.PROVIDER_OPENAI -> "OpenAI key (sk-…)"
                                        KothonPreferences.PROVIDER_NVIDIA -> "NVIDIA key (nvapi-…)"
                                        else -> "Custom Gemini key"
                                    },
                                    color = KothonTextSecondary,
                                    fontSize = 12.sp
                                )
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
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
                                            transcriptionClient.testProviderConnection(selectedProvider, apiKeyText.trim())
                                        }
                                        keyTestResult = res
                                        isTestingKey = false
                                    }
                                },
                                enabled = !isTestingKey && apiKeyText.isNotBlank(),
                                shape = RoundedCornerShape(9.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                if (isTestingKey) {
                                    CircularProgressIndicator(
                                        color = KothonIndigo,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Text("Test key", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }

                            Button(
                                onClick = {
                                    preferences.setApiKeyForProvider(selectedProvider, apiKeyText.trim())
                                    Toast.makeText(context, "$selectedProvider key saved!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KothonCyan,
                                    contentColor = Color(0xFF0F172A)
                                ),
                                shape = RoundedCornerShape(9.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Text("Save key", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    } else {
                        Row(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showApiKeyInput = true }
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = KothonTextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Use a custom Gemini key",
                                color = KothonTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // ---------------------------------------------------------------
            // 5. Refinement (collapsible)
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
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Refinement",
                                    color = KothonTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = buildString {
                                        append(currentTone)
                                        append(" · Fillers ")
                                        append(if (removeFillers) "on" else "off")
                                        append(" · Grammar ")
                                        append(if (fixGrammar) "on" else "off")
                                    },
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
                                text = "TONE",
                                color = KothonTextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val popularTones = listOf(
                                    KothonPreferences.TONE_PROFESSIONAL,
                                    KothonPreferences.TONE_EXECUTIVE,
                                    KothonPreferences.TONE_CONCISE
                                )
                                popularTones.forEach { toneName ->
                                    KothonChip(
                                        label = toneName.substringBefore(" "),
                                        selected = currentTone == toneName,
                                        selectedColor = KothonIndigo,
                                        onClick = {
                                            currentTone = toneName
                                            preferences.tone = toneName
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

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
                                subtitle = "Fixes structure, casing and agreement",
                                checked = fixGrammar,
                                onCheckedChange = {
                                    fixGrammar = it
                                    preferences.fixGrammar = it
                                }
                            )

                            if (liveModeActive) {
                                HorizontalDivider(
                                    color = KothonBorder.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                                KothonSettingSwitchRow(
                                    title = "Auto-refine live speech",
                                    subtitle = "Off keeps the raw transcript",
                                    checked = autoRefine,
                                    tint = KothonIndigo,
                                    modifier = Modifier.testTag("studio_auto_refine_switch"),
                                    onCheckedChange = {
                                        autoRefine = it
                                        preferences.liveAutoRefine = it
                                        liveController.autoRefine = it
                                    }
                                )
                                TextButton(
                                    onClick = { checkStudioModelSupport() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .testTag("studio_check_model_button")
                                ) {
                                    Text("Check on-device model", fontSize = 11.sp)
                                }
                                if (modelStatus != null) {
                                    Text(
                                        text = modelStatus ?: "",
                                        color = KothonTextSecondary,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------
            // 6. Keyboard setup & playground
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
                                tint = KothonTextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "System keyboard",
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
                                containerColor = if (isKeyboardEnabled) KothonDarkCard else KothonCyan,
                                contentColor = if (isKeyboardEnabled) KothonTextPrimary else Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(9.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                        ) {
                            Text(
                                text = if (isKeyboardEnabled) "1 · Enabled" else "1 · Enable",
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
                                containerColor = if (isKeyboardSelected) KothonDarkCard else KothonIndigo,
                                contentColor = if (isKeyboardSelected) KothonEmerald else Color.White
                            ),
                            shape = RoundedCornerShape(9.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                        ) {
                            Text(
                                text = if (isKeyboardSelected) "2 · Selected" else "2 · Select",
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
                                text = "Try the keyboard here…",
                                color = KothonTextSecondary,
                                fontSize = 12.sp
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
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
                        .padding(start = 2.dp, end = 2.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "History",
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

            if (recentNotes.isEmpty()) {
                item {
                    KothonCard {
                        Text(
                            text = "Your refined dictations will appear here.",
                            color = KothonTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            items(recentNotes, key = { it.id }) { note ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = KothonDarkSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, KothonBorder.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
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
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(KothonCyan.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
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
                if (lang.code != "auto" && lang.code != "original") {
                    preferences.liveLanguage = lang.code
                }
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
