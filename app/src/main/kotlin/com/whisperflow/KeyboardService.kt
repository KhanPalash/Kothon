package com.whisperflow

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.whisperflow.api.TranscriptionClient
import com.whisperflow.audio.AndroidSpeechSession
import com.whisperflow.audio.DeviceSpikeValidator
import com.whisperflow.audio.DictationState
import com.whisperflow.audio.ImeDictationEditor
import com.whisperflow.audio.LiveDictationController
import com.whisperflow.audio.LiveDictationSupport
import com.whisperflow.audio.PasswordFieldDetector
import com.whisperflow.audio.PermissionHelper
import com.whisperflow.audio.Recorder
import com.whisperflow.settings.KothonPreferences
import com.whisperflow.settings.SettingsActivity
import com.whisperflow.ui.StandardKeyboardView
import com.whisperflow.ui.VoiceButton
import com.whisperflow.ui.VoiceButtonState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class KeyboardService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var amplitudeJob: Job? = null

    private lateinit var preferences: KothonPreferences
    private lateinit var recorder: Recorder
    private lateinit var transcriptionClient: TranscriptionClient
    private lateinit var speechSession: AndroidSpeechSession
    private lateinit var dictationEditor: ImeDictationEditor
    private lateinit var liveController: LiveDictationController

    private var editorToken = 0L
    private var currentEditorInfo: EditorInfo? = null
    private var activeDictationMode: String = KothonPreferences.MODE_ACCURATE

    // Keyboard UI Mode: false = Voice Mode, true = Standard QWERTY Fallback
    private val _isFallbackKeyboard = MutableStateFlow(false)
    val isFallbackKeyboard = _isFallbackKeyboard.asStateFlow()

    private val _buttonState = MutableStateFlow(VoiceButtonState.IDLE)
    val buttonState = _buttonState.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude = _amplitude.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage = _statusMessage.asStateFlow()

    private val _voiceDisabled = MutableStateFlow(false)
    val voiceDisabled = _voiceDisabled.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _canRefine = MutableStateFlow(false)
    val canRefine = _canRefine.asStateFlow()

    private val _modeLabel = MutableStateFlow("ACCURATE")
    val modeLabel = _modeLabel.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        preferences = KothonPreferences(this)
        recorder = Recorder(this)
        transcriptionClient = TranscriptionClient(this)
        speechSession = AndroidSpeechSession(this)
        dictationEditor = ImeDictationEditor(
            connection = { currentInputConnection },
            editorInfo = { currentEditorInfo },
            token = { editorToken }
        )
        liveController = LiveDictationController(
            speechSession = speechSession,
            editor = dictationEditor,
            scope = serviceScope,
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

        serviceScope.launch {
            liveController.uiState.collect { ui ->
                when (ui.state) {
                    DictationState.LISTENING -> {
                        _buttonState.value = VoiceButtonState.RECORDING
                        _statusMessage.value = "Listening…"
                    }
                    DictationState.STOPPING -> {
                        _amplitude.value = 0f
                        _buttonState.value = VoiceButtonState.PROCESSING
                        _statusMessage.value = "Finishing…"
                    }
                    DictationState.REFINING -> {
                        _amplitude.value = 0f
                        _buttonState.value = VoiceButtonState.PROCESSING
                        _statusMessage.value = "Polishing with AI…"
                    }
                    DictationState.DONE -> {
                        _amplitude.value = 0f
                        _canUndo.value = ui.canUndo
                        _canRefine.value = ui.canRefine
                        _buttonState.value =
                            if (ui.liveText.isBlank() && !ui.canUndo && !ui.canRefine) {
                                VoiceButtonState.IDLE
                            } else {
                                VoiceButtonState.SUCCESS
                            }
                        _statusMessage.value = ui.statusMessage
                    }
                    DictationState.ERROR -> {
                        _amplitude.value = 0f
                        _canUndo.value = false
                        _canRefine.value = false
                        _buttonState.value = VoiceButtonState.ERROR
                        _statusMessage.value = ui.statusMessage
                    }
                    DictationState.IDLE -> {
                        _amplitude.value = 0f
                        _canUndo.value = false
                        _canRefine.value = false
                        if (!_voiceDisabled.value &&
                            _buttonState.value != VoiceButtonState.PERMISSION_REQUIRED
                        ) {
                            _buttonState.value = VoiceButtonState.IDLE
                            _statusMessage.value = ""
                        }
                    }
                }
            }
        }
    }

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        installViewTreeOwners(win.decorView)
    }

    private fun installViewTreeOwners(view: View?) {
        var current: View? = view
        while (current != null) {
            current.setViewTreeLifecycleOwner(this)
            current.setViewTreeSavedStateRegistryOwner(this)
            current.setViewTreeViewModelStoreOwner(this)
            val p = current.parent
            current = if (p is View) p else null
        }
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        window?.window?.decorView?.let { installViewTreeOwners(it) }

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(this@KeyboardService)
            setViewTreeSavedStateRegistryOwner(this@KeyboardService)
            setViewTreeViewModelStoreOwner(this@KeyboardService)

            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    installViewTreeOwners(v)
                    window?.window?.decorView?.let { installViewTreeOwners(it) }
                }
                override fun onViewDetachedFromWindow(v: View) {}
            })

            setContent {
                val fallback by _isFallbackKeyboard.collectAsState()
                val state by _buttonState.collectAsState()
                val amp by _amplitude.collectAsState()
                val msg by _statusMessage.collectAsState()
                val disabled by _voiceDisabled.collectAsState()
                val undoAvailable by _canUndo.collectAsState()
                val refineAvailable by _canRefine.collectAsState()
                val mode by _modeLabel.collectAsState()

                Box(modifier = Modifier.fillMaxWidth()) {
                    if (fallback) {
                        StandardKeyboardView(
                            onKeyChar = { char ->
                                commitTextToEditor(char)
                            },
                            onBackspace = {
                                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                            },
                            onEnter = {
                                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                            },
                            onSwitchToVoice = {
                                _isFallbackKeyboard.value = false
                                checkAndRefreshPermission()
                            },
                            onSwitchIme = {
                                handleSwitchIme()
                            }
                        )
                    } else {
                        VoiceButton(
                            state = state,
                            amplitude = amp,
                            statusMessage = msg,
                            sourceLanguageCode = preferences.sourceLanguage,
                            targetLanguageCode = preferences.targetLanguage,
                            tone = preferences.tone,
                            modeLabel = mode,
                            voiceDisabled = disabled,
                            canUndo = undoAvailable,
                            canRefine = refineAvailable,
                            onRecordStart = {
                                startVoiceRecording()
                            },
                            onRecordStop = {
                                stopVoiceRecordingAndProcess()
                            },
                            onUndo = {
                                undoLiveReplacement()
                            },
                            onRefine = {
                                refineLiveNow()
                            },
                            onToggleLanguages = {
                                // Fast toggle between English and Original (or Bengali)
                                val newTarget = if (preferences.targetLanguage == "en") "original" else "en"
                                preferences.targetLanguage = newTarget
                                _statusMessage.value = "Target: ${if (newTarget == "en") "English" else "Original Language"}"
                            },
                            onCycleTone = {
                                val tones = KothonPreferences.TONES
                                val idx = (tones.indexOf(preferences.tone) + 1) % tones.size
                                val nextTone = tones[idx]
                                preferences.tone = nextTone
                                _statusMessage.value = "Tone: $nextTone"
                            },
                            onSwitchToKeyboard = {
                                _isFallbackKeyboard.value = true
                            },
                            onOpenSettings = {
                                openSettings()
                            },
                            onRequestPermission = {
                                requestAudioPermission()
                            }
                        )
                    }
                }
            }
        }
        return composeView
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        editorToken++
        liveController.onFieldChanged(editorToken)
        if (recorder.isRecording) recorder.cancel()
        stopAmplitudePolling()
        _canUndo.value = false
        _canRefine.value = false
        refreshEditorState(info)
        checkAndRefreshPermission()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        refreshEditorState(info)
        checkAndRefreshPermission()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        cancelActiveDictation()
        if (!_voiceDisabled.value) {
            _buttonState.value = VoiceButtonState.IDLE
            _statusMessage.value = ""
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        editorToken++
        cancelActiveDictation()
        currentEditorInfo = null
    }

    private fun refreshEditorState(info: EditorInfo?) {
        currentEditorInfo = info
        val passwordField = PasswordFieldDetector.shouldDisableVoice(info)
        _voiceDisabled.value = passwordField
        _modeLabel.value =
            if (LiveDictationSupport.isLiveModeEnabled(preferences)) "LIVE" else "ACCURATE"

        if (passwordField) {
            cancelActiveDictation()
            _buttonState.value = VoiceButtonState.IDLE
            _statusMessage.value = "Voice disabled for password fields."
        }
    }

    private fun cancelActiveDictation() {
        liveController.cancelSession()
        if (recorder.isRecording) recorder.cancel()
        stopAmplitudePolling()
        _canUndo.value = false
        _canRefine.value = false
    }

    fun checkAudioPermission(): Boolean {
        return PermissionHelper.hasAudioPermission(this)
    }

    fun requestAudioPermission() {
        try {
            val intent = PermissionHelper.createPermissionIntent(this)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("KeyboardService", "Could not launch permission settings", e)
        }
    }

    private fun checkAndRefreshPermission() {
        if (_voiceDisabled.value) {
            _buttonState.value = VoiceButtonState.IDLE
            _statusMessage.value = "Voice disabled for password fields."
            return
        }
        if (!checkAudioPermission()) {
            _buttonState.value = VoiceButtonState.PERMISSION_REQUIRED
            _statusMessage.value = "Microphone permission required."
        } else if (_buttonState.value == VoiceButtonState.PERMISSION_REQUIRED) {
            _buttonState.value = VoiceButtonState.IDLE
            _statusMessage.value = ""
        }
    }

    fun startVoiceRecording() {
        if (_voiceDisabled.value) {
            _statusMessage.value = "Voice disabled for password fields."
            return
        }
        if (!checkAudioPermission()) {
            _buttonState.value = VoiceButtonState.PERMISSION_REQUIRED
            _statusMessage.value = "Please grant mic permission to record."
            requestAudioPermission()
            return
        }
        if (currentInputConnection == null) {
            _buttonState.value = VoiceButtonState.IDLE
            _statusMessage.value = "No active text field."
            return
        }

        _canUndo.value = false
        _canRefine.value = false
        activeDictationMode =
            if (LiveDictationSupport.isLiveModeEnabled(preferences)) {
                KothonPreferences.MODE_LIVE
            } else {
                KothonPreferences.MODE_ACCURATE
            }

        if (activeDictationMode == KothonPreferences.MODE_LIVE) {
            startLiveDictation()
        } else {
            startAccurateDictation()
        }
    }

    private fun startLiveDictation() {
        if (!DeviceSpikeValidator.isRecognitionAvailable(this)) {
            _buttonState.value = VoiceButtonState.ERROR
            _statusMessage.value = "Speech recognition is not available on this device."
            return
        }

        val languageTag = LiveDictationSupport.resolveLanguageTag(
            liveLanguage = preferences.liveLanguage,
            sourceLanguage = preferences.sourceLanguage,
            deviceLanguageTag = Locale.getDefault().toLanguageTag()
        )
        _buttonState.value = VoiceButtonState.RECORDING
        _statusMessage.value = "Listening…"
        liveController.autoRefine = preferences.liveAutoRefine
        liveController.start(
            LiveDictationSupport.buildSpeechConfig(preferences, languageTag),
            LiveDictationSupport.separatorFor(languageTag)
        )
    }

    private fun startAccurateDictation() {
        try {
            recorder.start(onTimeout = { stopVoiceRecordingAndProcess() })
            _buttonState.value = VoiceButtonState.RECORDING
            _statusMessage.value = "Recording… tap to stop."
            startAmplitudePolling()
        } catch (e: Exception) {
            Log.e("KeyboardService", "Failed to start audio recording", e)
            _buttonState.value = VoiceButtonState.ERROR
            _statusMessage.value = "Could not start recording. Check microphone access."
        }
    }

    fun stopVoiceRecordingAndProcess() {
        if (_voiceDisabled.value) return

        if (activeDictationMode == KothonPreferences.MODE_LIVE) {
            if (liveController.isActive) {
                liveController.stop()
            }
            return
        }

        if (!recorder.isRecording) return
        val file = recorder.stop()
        stopAmplitudePolling()
        _amplitude.value = 0f

        if (file == null || !file.exists() || file.length() < 2000L) {
            file?.delete()
            _buttonState.value = VoiceButtonState.IDLE
            _statusMessage.value = "No speech detected. Tap mic to speak."
            return
        }

        processAccurateAudio(file)
    }

    private fun processAccurateAudio(file: File) {
        _buttonState.value = VoiceButtonState.PROCESSING
        _statusMessage.value = "Transcribing with AI…"

        serviceScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    transcriptionClient.processAudio(
                        audioFile = file,
                        sourceLanguageCode = preferences.sourceLanguage,
                        targetLanguageCode = preferences.targetLanguage,
                        tone = preferences.tone,
                        removeFillers = preferences.removeFillers,
                        fixGrammar = preferences.fixGrammar
                    )
                }
                val text = result.refinedText.trim()
                val conn = currentInputConnection
                if (text.isNotBlank() && conn != null) {
                    conn.commitText(text, 1)
                    _buttonState.value = VoiceButtonState.SUCCESS
                    _statusMessage.value = "Inserted refined text."
                } else {
                    _buttonState.value = VoiceButtonState.IDLE
                    _statusMessage.value = "No text returned."
                }
            } catch (e: Exception) {
                Log.w("KeyboardService", "ACCURATE transcription failed: ${e.message}")
                _buttonState.value = VoiceButtonState.ERROR
                _statusMessage.value = "Transcription failed. Check API key or network."
            } finally {
                file.delete()
                delay(2500)
                if (_buttonState.value == VoiceButtonState.SUCCESS ||
                    _buttonState.value == VoiceButtonState.ERROR
                ) {
                    _buttonState.value = VoiceButtonState.IDLE
                    _statusMessage.value = ""
                }
            }
        }
    }

    fun undoLiveReplacement() {
        val undone = liveController.undo()
        _canUndo.value = false
        _statusMessage.value = if (undone) "Reverted to raw text." else "Undo unavailable."
    }

    fun refineLiveNow() {
        liveController.refineNow()
    }

    private fun startAmplitudePolling() {
        amplitudeJob?.cancel()
        amplitudeJob = serviceScope.launch {
            while (isActive && recorder.isRecording) {
                val maxAmp = recorder.getMaxAmplitude()
                _amplitude.value = (maxAmp / 32767f).coerceIn(0f, 1f)
                delay(80)
            }
            _amplitude.value = 0f
        }
    }

    private fun stopAmplitudePolling() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        _amplitude.value = 0f
    }

    fun switchToFallbackKeyboard() {
        _isFallbackKeyboard.value = true
    }

    fun commitTextToEditor(text: String) {
        val inputConn = currentInputConnection
        if (inputConn == null) {
            Log.w("KeyboardService", "No active editor inputConnection to commit text to.")
            return
        }
        inputConn.commitText(text, 1)
    }

    private fun openSettings() {
        try {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("KeyboardService", "Could not start SettingsActivity", e)
        }
    }

    fun handleSwitchIme() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val switched = super.switchToPreviousInputMethod()
                if (!switched) {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showInputMethodPicker()
                }
            } else {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showInputMethodPicker()
            }
        } catch (e: Exception) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        }
    }

    override fun onDestroy() {
        liveController.cancelSession()
        speechSession.destroy()
        recorder.cancel()
        stopAmplitudePolling()
        serviceScope.cancel()
        store.clear()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
