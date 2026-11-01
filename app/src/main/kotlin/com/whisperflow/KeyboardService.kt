package com.whisperflow

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import android.view.Window
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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

    // Keyboard UI Mode: false = Voice Mode, true = Standard QWERTY Fallback
    private val _isFallbackKeyboard = MutableStateFlow(false)
    val isFallbackKeyboard = _isFallbackKeyboard.asStateFlow()

    private val _buttonState = MutableStateFlow(VoiceButtonState.IDLE)
    val buttonState = _buttonState.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude = _amplitude.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage = _statusMessage.asStateFlow()

    private var currentRecordingFile: File? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        preferences = KothonPreferences(this)
        recorder = Recorder(this)
        transcriptionClient = TranscriptionClient(this)
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
                            onRecordStart = {
                                startVoiceRecording()
                            },
                            onRecordStop = {
                                stopVoiceRecordingAndProcess()
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

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        checkAndRefreshPermission()
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
        if (!checkAudioPermission()) {
            _buttonState.value = VoiceButtonState.PERMISSION_REQUIRED
            _statusMessage.value = "Microphone permission required."
        } else if (_buttonState.value == VoiceButtonState.PERMISSION_REQUIRED) {
            _buttonState.value = VoiceButtonState.IDLE
            _statusMessage.value = ""
        }
    }

    fun startVoiceRecording() {
        if (!checkAudioPermission()) {
            _buttonState.value = VoiceButtonState.PERMISSION_REQUIRED
            _statusMessage.value = "Please grant mic permission to record."
            requestAudioPermission()
            return
        }

        try {
            val file = recorder.start(onTimeout = {
                stopVoiceRecordingAndProcess()
            })
            currentRecordingFile = file
            _buttonState.value = VoiceButtonState.RECORDING
            _statusMessage.value = "Listening... Speak in Bangla or English"

            // Start amplitude polling
            startAmplitudePolling()
        } catch (e: Exception) {
            Log.e("KeyboardService", "Failed to start audio recording", e)
            _buttonState.value = VoiceButtonState.ERROR
            _statusMessage.value = "Recording error: ${e.message}"
            // Fallback to keyboard
            switchToFallbackKeyboard()
        }
    }

    fun stopVoiceRecordingAndProcess() {
        stopAmplitudePolling()

        if (_buttonState.value != VoiceButtonState.RECORDING) {
            return
        }

        _buttonState.value = VoiceButtonState.PROCESSING
        _statusMessage.value = "Refining with AI & translating..."

        val recordedAudio = recorder.stop() ?: currentRecordingFile
        if (recordedAudio == null || !recordedAudio.exists() || recordedAudio.length() < 2500L) {
            _buttonState.value = VoiceButtonState.IDLE
            _statusMessage.value = "Speech too short. Tap to speak, then tap to stop."
            return
        }

        serviceScope.launch {
            try {
                val processedText = withContext(Dispatchers.IO) {
                    transcriptionClient.transcribeAndRewrite(
                        audioFile = recordedAudio,
                        sourceLanguageCode = preferences.sourceLanguage,
                        targetLanguageCode = preferences.targetLanguage,
                        tone = preferences.tone,
                        removeFillers = preferences.removeFillers,
                        fixGrammar = preferences.fixGrammar
                    )
                }

                if (processedText.isNotBlank()) {
                    commitTextToEditor(processedText)
                    _buttonState.value = VoiceButtonState.SUCCESS
                    _statusMessage.value = "Inserted: \"$processedText\""

                    // Auto-reset to IDLE after 2 seconds
                    delay(2000)
                    if (_buttonState.value == VoiceButtonState.SUCCESS) {
                        _buttonState.value = VoiceButtonState.IDLE
                        _statusMessage.value = ""
                    }
                } else {
                    _buttonState.value = VoiceButtonState.ERROR
                    _statusMessage.value = "Refinement produced empty text."
                }
            } catch (e: IOException) {
                Log.w("KeyboardService", "Transcription failed, falling back to keyboard", e)
                _buttonState.value = VoiceButtonState.ERROR
                _statusMessage.value = e.message ?: "Failed. Falling back to keyboard."
                // Graceful fallback to standard keyboard layout
                delay(1200)
                switchToFallbackKeyboard()
            } catch (e: Exception) {
                Log.e("KeyboardService", "Unexpected processing error", e)
                _buttonState.value = VoiceButtonState.ERROR
                _statusMessage.value = "Error: ${e.localizedMessage}"
                switchToFallbackKeyboard()
            }
        }
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

    private fun startAmplitudePolling() {
        amplitudeJob?.cancel()
        amplitudeJob = serviceScope.launch {
            while (isActive && recorder.isRecording) {
                val maxAmp = recorder.getMaxAmplitude()
                // Normalize 0..32767 to 0..1
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
        recorder.cancel()
        serviceScope.cancel()
        store.clear()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
