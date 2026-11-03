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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.KothonAmber
import com.example.ui.theme.KothonCyan
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

    // Provider & API Key States
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

    // Refinement & Live Dictation States
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
    var refinementExpanded by remember { mutableStateOf(false) }
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
        targetValue = if (isRecording) 1.22f else 1.04f,
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
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(KothonCyan, KothonIndigo)
                                    )
                                )
                                .border(0.8.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Kothon logo",
                                tint = Color(0xFF070B14),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Kothon",
                                color = KothonTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp
                            )
                            Text(
                                text = "$selectedProvider · ${if (liveModeActive) "Live Stream" else "Accurate AI"}",
                                color = KothonCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    KothonStatusPill(
                        text = when {
                            isKeyboardSelected -> "IME Active"
                            isKeyboardEnabled -> "IME Ready"
                            else -> "Setup IME"
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
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ---------------------------------------------------------------
                // 1. Sleek Horizontal Dictation Studio Pod (Compact Hero Card)
                // ---------------------------------------------------------------
                item {
                    KothonCard(
                        modifier = Modifier.testTag("voice_dictation_studio_card"),
                        accentBorderColor = if (isRecording) KothonRose else KothonCyan
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: Compact Glowing Glass Mic Button
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(54.dp)
                            ) {
                                if (isRecording) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .scale(pulseScale)
                                            .clip(CircleShape)
                                            .background(KothonRose.copy(alpha = 0.24f))
                                    )
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isRecording -> Brush.linearGradient(listOf(KothonRose, Color(0xFFEF4444)))
                                                isProcessing -> Brush.linearGradient(listOf(KothonIndigo, KothonCyan))
                                                else -> Brush.linearGradient(listOf(KothonCyan, Color(0xFF0EA5E9)))
                                            }
                                        )
                                        .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
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
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                            contentDescription = if (isRecording) "Stop dictation" else "Start dictation",
                                            tint = if (isRecording) Color.White else Color(0xFF070B14),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Center: Title + Dynamic Status / Language Pair
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Voice Studio",
                                    color = KothonTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        isRecording -> "Listening… tap mic to finish"
                                        isProcessing -> "Refining via $selectedProvider…"
                                        else -> "${currentSourceLang.name} → ${if (translateEnabled) currentTargetLang.name else "Original"}"
                                    },
                                    color = when {
                                        isRecording -> KothonRose
                                        isProcessing -> KothonIndigo
                                        else -> KothonTextSecondary
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Right: Accurate | Live Segmented Pill
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .border(0.7.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                                    .selectableGroup()
                                    .padding(2.dp),
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
                                    enabled = true,
                                    selectedColor = KothonIndigo,
                                    onClick = { selectDictationMode(KothonPreferences.MODE_LIVE) },
                                    modifier = Modifier.testTag("studio_mode_live")
                                )
                            }
                        }

                        // Live / preview transcript box (only visible when active)
                        if ((isRecording || isProcessing) && livePreviewText.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(0.7.dp, KothonCyan.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("live_speech_stream_card")
                            ) {
                                Text(
                                    text = livePreviewText,
                                    color = KothonTextPrimary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Manual undo / refine after a live session
                        if (liveModeActive && (liveUi.canUndo || liveUi.canRefine)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (liveUi.canUndo) {
                                    TextButton(
                                        onClick = { undoStudioLive() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .height(28.dp)
                                            .testTag("studio_undo_button")
                                    ) {
                                        Text("Undo AI", fontSize = 10.sp)
                                    }
                                }
                                if (liveUi.canRefine) {
                                    Button(
                                        onClick = { refineStudioLive() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = KothonAmber,
                                            contentColor = Color(0xFF070B14)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .height(28.dp)
                                            .testTag("studio_refine_button")
                                    ) {
                                        Text("Refine with AI", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Compact Error Banner
                        if (studioError != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 7.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(KothonRose.copy(alpha = 0.12f))
                                    .border(0.7.dp, KothonRose.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = studioError ?: "",
                                    color = KothonRose,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Get API ↗",
                                    color = KothonCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { openProviderApiKeyPage(selectedProvider) }
                                )
                            }
                        }
                    }
                }

                // ---------------------------------------------------------------
                // 2. Latest Refined Result Glass Card (when available)
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
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(KothonCyan.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${res.detectedLanguage} → ${res.targetLanguage}",
                                            color = KothonCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (res.rawTranscript.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (showRawComparison) "Hide raw" else "Raw speech",
                                            color = KothonAmber,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clickable { showRawComparison = !showRawComparison }
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Kothon Refined", res.refinedText))
                                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy refined text",
                                            tint = KothonCyan,
                                            modifier = Modifier.size(14.dp)
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
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share refined text",
                                            tint = KothonTextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = res.refinedText,
                                color = KothonTextPrimary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            if (showRawComparison && res.rawTranscript.isNotBlank()) {
                                Text(
                                    text = "\"${res.rawTranscript}\"",
                                    color = KothonTextSecondary,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                // ---------------------------------------------------------------
                // 3. Compact AI Provider & "Get API" Glass Card
                // ---------------------------------------------------------------
                item {
                    KothonCard(modifier = Modifier.testTag("ai_provider_card")) {
                        // Single compact header row with Title, Key Status, and "Get API" pill
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "AI PROVIDER",
                                    color = KothonTextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                KothonStatusPill(
                                    text = if (apiKeyText.isNotBlank()) "Key Saved" else "Key Needed",
                                    color = if (apiKeyText.isNotBlank()) KothonEmerald else KothonAmber
                                )
                            }

                            // "Get API" Pill Button
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
                                    .border(0.8.dp, KothonCyan.copy(alpha = 0.48f), RoundedCornerShape(999.dp))
                                    .clickable(role = Role.Button) {
                                        openProviderApiKeyPage(selectedProvider)
                                    }
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                                    .testTag("get_api_key_button"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Get API",
                                    color = KothonCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Get $selectedProvider API Key",
                                    tint = KothonCyan,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(7.dp))

                        // Compact 30.dp Provider Chips Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
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

                        Spacer(modifier = Modifier.height(7.dp))

                        // Sleek 36.dp Inline Glass Key Bar (Key Input + Visibility + Test + Save in one row!)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF070B14).copy(alpha = 0.62f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .padding(start = 9.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = KothonCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("provider_api_key_input"),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (apiKeyText.isEmpty()) {
                                    Text(
                                        text = when (selectedProvider) {
                                            KothonPreferences.PROVIDER_GROQ -> "Groq key (gsk_…)"
                                            KothonPreferences.PROVIDER_OPENAI -> "OpenAI key (sk-…)"
                                            KothonPreferences.PROVIDER_NVIDIA -> "NVIDIA key (nvapi-…)"
                                            else -> "Gemini key (AIza…)"
                                        },
                                        color = KothonTextMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = apiKeyText,
                                    onValueChange = { newValue ->
                                        apiKeyText = newValue
                                        preferences.setApiKeyForProvider(selectedProvider, newValue)
                                        keyTestResult = null
                                    },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = KothonTextPrimary,
                                        fontSize = 11.sp
                                    ),
                                    cursorBrush = SolidColor(KothonCyan),
                                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            IconButton(
                                onClick = { apiKeyVisible = !apiKeyVisible },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (apiKeyVisible) "Hide key" else "Show key",
                                    tint = KothonTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            // Inline Test Button
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(0.6.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(7.dp))
                                    .clickable(enabled = !isTestingKey && apiKeyText.isNotBlank()) {
                                        scope.launch {
                                            isTestingKey = true
                                            val res = withContext(Dispatchers.IO) {
                                                transcriptionClient.testProviderConnection(selectedProvider, apiKeyText)
                                            }
                                            keyTestResult = res
                                            isTestingKey = false
                                        }
                                    }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isTestingKey) {
                                    CircularProgressIndicator(
                                        color = KothonCyan,
                                        strokeWidth = 1.5.dp,
                                        modifier = Modifier.size(11.dp)
                                    )
                                } else {
                                    Text(
                                        text = "Test",
                                        color = if (apiKeyText.isNotBlank()) KothonTextPrimary else KothonTextMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Inline Save Button
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(KothonCyan)
                                    .clickable {
                                        preferences.provider = selectedProvider
                                        preferences.setApiKeyForProvider(selectedProvider, apiKeyText)
                                        Toast.makeText(context, "$selectedProvider key saved!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Save",
                                    color = Color(0xFF070B14),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (keyTestResult != null) {
                            val (ok, msg) = keyTestResult!!
                            Text(
                                text = msg,
                                color = if (ok) KothonEmerald else KothonRose,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // ---------------------------------------------------------------
                // 4. Compact Languages & Translation Glass Card (~68dp tall)
                // ---------------------------------------------------------------
                item {
                    KothonCard(modifier = Modifier.testTag("language_selection_card")) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LANGUAGE & TRANSLATION",
                                color = KothonTextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )

                            // Compact inline Translate toggle pill
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (translateEnabled) KothonCyan.copy(alpha = 0.16f)
                                        else Color.White.copy(alpha = 0.06f)
                                    )
                                    .border(
                                        0.7.dp,
                                        if (translateEnabled) KothonCyan.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.14f),
                                        RoundedCornerShape(999.dp)
                                    )
                                    .clickable {
                                        translateEnabled = !translateEnabled
                                        preferences.isTranslateEnabled = translateEnabled
                                    }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = if (translateEnabled) KothonCyan else KothonTextSecondary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (translateEnabled) "Translate: ON" else "Translate: OFF",
                                    color = if (translateEnabled) KothonCyan else KothonTextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            KothonLanguagePill(
                                label = "IN",
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
                                    .padding(horizontal = 5.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.07f))
                                    .border(0.7.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                    .testTag("swap_languages_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "Swap languages",
                                    tint = KothonCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            KothonLanguagePill(
                                label = "OUT",
                                flag = currentTargetLang.flagEmoji,
                                name = if (!translateEnabled || targetLangCode == "original") "Original" else currentTargetLang.name,
                                accent = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("target_language_selector_button"),
                                onClick = { activeLanguageSheet = HomeLanguageSheet.TARGET }
                            )
                        }
                    }
                }

                // ---------------------------------------------------------------
                // 5. Compact Refinement & Live Engine Glass Card (Collapsed by default)
                // ---------------------------------------------------------------
                item {
                    KothonCard(modifier = Modifier.testTag("advanced_refinement_card")) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = if (refinementExpanded) "Collapse refinement options" else "Expand refinement options"
                                ) { refinementExpanded = !refinementExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = KothonAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = "Tone & Engine",
                                    color = KothonTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "•  $currentTone",
                                    color = KothonAmber,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = if (refinementExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = KothonTextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        AnimatedVisibility(visible = refinementExpanded) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectableGroup(),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
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

                                Spacer(modifier = Modifier.height(6.dp))

                                KothonSettingSwitchRow(
                                    title = "Remove vocal fillers (“um”, “mane”)",
                                    subtitle = "",
                                    checked = removeFillers,
                                    onCheckedChange = {
                                        removeFillers = it
                                        preferences.removeFillers = it
                                    }
                                )
                                KothonSettingSwitchRow(
                                    title = "Polish grammar & punctuation",
                                    subtitle = "",
                                    checked = fixGrammar,
                                    onCheckedChange = {
                                        fixGrammar = it
                                        preferences.fixGrammar = it
                                    }
                                )

                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.10f),
                                    modifier = Modifier.padding(vertical = 5.dp)
                                )

                                KothonSettingSwitchRow(
                                    title = "Enable Live Streaming Mode",
                                    subtitle = "",
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
                                KothonSettingSwitchRow(
                                    title = "Auto-refine live speech with AI",
                                    subtitle = "",
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
                                    title = "Prefer on-device recognition (Wi-Fi model download)",
                                    subtitle = "",
                                    checked = preferOnDevice,
                                    tint = KothonEmerald,
                                    onCheckedChange = {
                                        preferOnDevice = it
                                        preferences.livePreferOnDevice = it
                                    }
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    KothonLanguagePill(
                                        label = "LIVE",
                                        flag = currentLiveLang.flagEmoji,
                                        name = currentLiveLang.name,
                                        accent = true,
                                        modifier = Modifier.weight(1f),
                                        onClick = { activeLanguageSheet = HomeLanguageSheet.LIVE }
                                    )

                                    Row(
                                        modifier = Modifier
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(0.7.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                                            .clickable(enabled = !checkingModel) { checkStudioModelSupport() }
                                            .padding(horizontal = 10.dp)
                                            .testTag("studio_check_model_button"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (checkingModel) {
                                            CircularProgressIndicator(
                                                color = KothonCyan,
                                                strokeWidth = 1.6.dp,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = KothonCyan,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = "Check Model",
                                            color = KothonTextPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                modelStatus?.let { msg ->
                                    Spacer(modifier = Modifier.height(5.dp))
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
                // 6. Compact System Keyboard & Playground Glass Card (~78dp tall)
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
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Keyboard",
                                    color = KothonTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(26.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isKeyboardEnabled) Color.White.copy(alpha = 0.08f) else KothonCyan
                                        )
                                        .border(
                                            0.7.dp,
                                            if (isKeyboardEnabled) Color.White.copy(alpha = 0.16f) else KothonCyan,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                        }
                                        .padding(horizontal = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isKeyboardEnabled) "1 · Enabled ✓" else "1 · Enable",
                                        color = if (isKeyboardEnabled) KothonTextPrimary else Color(0xFF070B14),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .height(26.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isKeyboardSelected) Color.White.copy(alpha = 0.08f) else KothonIndigo
                                        )
                                        .border(
                                            0.7.dp,
                                            if (isKeyboardSelected) KothonEmerald.copy(alpha = 0.4f) else KothonIndigo,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                            imm?.showInputMethodPicker()
                                        }
                                        .padding(horizontal = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isKeyboardSelected) "2 · Active ✓" else "2 · Select",
                                        color = if (isKeyboardSelected) KothonEmerald else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Sleek 36.dp Glass Playground Input Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF070B14).copy(alpha = 0.62f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp)
                                .testTag("playground_text_field"),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (testInputText.isEmpty()) {
                                Text(
                                    text = "Tap here to test Voice AI & Gboard-style keyboard…",
                                    color = KothonTextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = testInputText,
                                onValueChange = { testInputText = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = KothonTextPrimary,
                                    fontSize = 12.sp
                                ),
                                cursorBrush = SolidColor(KothonCyan),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // ---------------------------------------------------------------
                // 7. Compact Recent Dictations History
                // ---------------------------------------------------------------
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 2.dp, end = 2.dp, top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECENT DICTATIONS",
                            color = KothonTextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "${recentNotes.size} item${if (recentNotes.size == 1) "" else "s"}",
                            color = KothonTextMuted,
                            fontSize = 9.sp
                        )
                    }
                }

                items(recentNotes, key = { it.id }) { note ->
                    KothonCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${note.detectedLanguage} → ${note.targetLanguage}",
                                        color = KothonCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "  •  ${note.tone}  •  ${note.timestamp}",
                                        color = KothonTextMuted,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = note.refinedText,
                                    color = KothonTextPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.07f))
                                    .border(0.7.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Kothon Note", note.refinedText))
                                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = KothonCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp),
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
