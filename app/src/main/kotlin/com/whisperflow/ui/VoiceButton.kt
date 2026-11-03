package com.whisperflow.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.KothonAmber
import com.example.ui.theme.KothonCyan
import com.example.ui.theme.KothonDarkBg
import com.example.ui.theme.KothonDarkCard
import com.example.ui.theme.KothonEmerald
import com.example.ui.theme.KothonIndigo
import com.example.ui.theme.KothonRose
import com.example.ui.theme.KothonTextPrimary
import com.example.ui.theme.KothonTextSecondary
import com.whisperflow.model.LanguageCatalog

enum class VoiceButtonState {
    IDLE,
    RECORDING,
    PROCESSING,
    SUCCESS,
    ERROR,
    PERMISSION_REQUIRED
}

@Composable
fun VoiceButton(
    state: VoiceButtonState,
    amplitude: Float = 0f,
    statusMessage: String = "",
    sourceLanguageCode: String = "auto",
    targetLanguageCode: String = "en",
    tone: String = "Professional Clarity",
    modeLabel: String = "",
    voiceDisabled: Boolean = false,
    canUndo: Boolean = false,
    canRefine: Boolean = false,
    onRecordStart: () -> Unit = {},
    onRecordStop: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRefine: () -> Unit = {},
    onToggleLanguages: () -> Unit = {},
    onCycleTone: () -> Unit = {},
    onSwitchToKeyboard: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onRequestPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == VoiceButtonState.RECORDING) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val buttonBgColor by animateColorAsState(
        targetValue = when (state) {
            VoiceButtonState.IDLE -> KothonCyan
            VoiceButtonState.RECORDING -> KothonRose
            VoiceButtonState.PROCESSING -> KothonIndigo
            VoiceButtonState.SUCCESS -> KothonEmerald
            VoiceButtonState.ERROR -> Color(0xFFFB923C)
            VoiceButtonState.PERMISSION_REQUIRED -> KothonAmber
        }.let { if (voiceDisabled) Color(0xFF475569) else it },
        label = "bgColor"
    )

    val sourceLang = remember(sourceLanguageCode) { LanguageCatalog.findByCode(sourceLanguageCode) }
    val targetLang = remember(targetLanguageCode) { LanguageCatalog.findByCode(targetLanguageCode) }

    // Transparent surface so the IME background watermark stays visible.
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_button_container"),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quick top action bar: language pair, tone, keyboard switch, settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(KothonDarkCard)
                        .clickable { onToggleLanguages() }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                        .testTag("translate_toggle_chip")
                        .semantics {
                            role = Role.Button
                            contentDescription = "Language pair: ${sourceLang.name} to ${targetLang.name}. Tap to change."
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "",
                        tint = KothonCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "${sourceLang.code.uppercase()} → ${targetLang.code.uppercase()}",
                        color = KothonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(KothonDarkCard)
                        .clickable { onCycleTone() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("cycle_tone_chip")
                        .semantics {
                            role = Role.Button
                            contentDescription = "AI tone: $tone. Tap to cycle."
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "",
                        tint = KothonAmber,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = tone,
                        color = KothonTextPrimary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onSwitchToKeyboard,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("switch_to_keyboard_button")
                            .semantics {
                                role = Role.Button
                                contentDescription = "Switch to QWERTY keyboard"
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "",
                            tint = KothonTextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("open_settings_button")
                            .semantics {
                                role = Role.Button
                                contentDescription = "Open Kothon settings"
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "",
                            tint = KothonTextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // Real-time status / transcription hint
            Text(
                text = when {
                    voiceDisabled -> "Voice disabled for password fields."
                    state == VoiceButtonState.IDLE -> {
                        val modePrefix = if (modeLabel.isBlank()) "" else "$modeLabel • "
                        "${modePrefix}Speak in ${sourceLang.name} → Output ${targetLang.name}"
                    }
                    state == VoiceButtonState.RECORDING -> "Listening... Speak naturally"
                    state == VoiceButtonState.PROCESSING -> "Detecting language, refining & translating..."
                    state == VoiceButtonState.SUCCESS -> statusMessage.ifEmpty { "Transcribed, refined and inserted!" }
                    state == VoiceButtonState.ERROR -> statusMessage.ifEmpty { "Failed. Tap to retry or use keyboard." }
                    else -> "Microphone permission needed. Tap to allow."
                },
                color = when (state) {
                    VoiceButtonState.RECORDING -> KothonRose
                    VoiceButtonState.PROCESSING -> Color(0xFFC084FC)
                    VoiceButtonState.SUCCESS -> KothonEmerald
                    VoiceButtonState.ERROR -> Color(0xFFFB923C)
                    VoiceButtonState.PERMISSION_REQUIRED -> Color(0xFFFACC15)
                    else -> KothonTextSecondary
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .testTag("voice_button_status_text")
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                    }
            )

            // Undo / manual refine actions after a completed live session
            if (canUndo || canRefine) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (canUndo) {
                        Text(
                            text = "Undo",
                            color = KothonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(KothonDarkCard)
                                .clickable { onUndo() }
                                .padding(horizontal = 11.dp, vertical = 8.dp)
                                .testTag("live_undo_button")
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Undo last transcription"
                                }
                        )
                    }
                    if (canRefine) {
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "Refine with AI",
                            color = KothonAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(KothonDarkCard)
                                .clickable { onRefine() }
                                .padding(horizontal = 11.dp, vertical = 8.dp)
                                .testTag("live_refine_button")
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Refine transcription with AI"
                                }
                        )
                    }
                }
            }

            // Dynamic waveform visualizer while recording
            if (state == VoiceButtonState.RECORDING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(24.dp)
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bars = 9
                    for (i in 0 until bars) {
                        val barHeight = remember(amplitude, i) {
                            val factor = (Math.sin((i.toDouble() / bars) * Math.PI) * 0.8 + 0.2).toFloat()
                            (6 + (amplitude * 22 * factor)).coerceIn(4f, 24f).dp
                        }
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(2.dp))
                                .background(KothonCyan)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Main floating voice action button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(70.dp)
                    .padding(3.dp)
            ) {
                if (state == VoiceButtonState.RECORDING) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(KothonRose.copy(alpha = 0.25f))
                    )
                }

                var pressStartTime by remember { mutableStateOf<Long>(0L) }

                val mainButtonContentDescription = when {
                    voiceDisabled -> "Voice disabled"
                    state == VoiceButtonState.RECORDING -> "Stop recording"
                    state == VoiceButtonState.PROCESSING -> "Processing speech"
                    state == VoiceButtonState.PERMISSION_REQUIRED -> "Permission required, tap to request"
                    state == VoiceButtonState.ERROR -> "Error, tap to retry"
                    else -> "Record voice"
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(buttonBgColor)
                        .pointerInput(state, voiceDisabled) {
                            detectTapGestures(
                                onPress = {
                                    if (voiceDisabled) {
                                        return@detectTapGestures
                                    }
                                    if (state == VoiceButtonState.PERMISSION_REQUIRED) {
                                        onRequestPermission()
                                        return@detectTapGestures
                                    }
                                    if (state == VoiceButtonState.PROCESSING) {
                                        return@detectTapGestures
                                    }
                                    if (state == VoiceButtonState.RECORDING) {
                                        // Tap to stop
                                        tryAwaitRelease()
                                        onRecordStop()
                                    } else {
                                        // Tap or hold to start
                                        pressStartTime = System.currentTimeMillis()
                                        onRecordStart()
                                        val released = tryAwaitRelease()
                                        val duration = System.currentTimeMillis() - pressStartTime
                                        if (duration > 500L) {
                                            // User held for > 500ms and released -> stop recording
                                            onRecordStop()
                                        }
                                        // If <= 500ms, user tapped to start recording -> keep recording!
                                    }
                                }
                            )
                        }
                        .testTag("main_voice_action_button")
                        .semantics {
                            role = Role.Button
                            contentDescription = mainButtonContentDescription
                        }
                ) {
                    when {
                        voiceDisabled -> {
                            Icon(
                                imageVector = Icons.Default.MicOff,
                                contentDescription = "",
                                tint = KothonTextPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        state == VoiceButtonState.RECORDING -> {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        state == VoiceButtonState.PROCESSING -> {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        state == VoiceButtonState.PERMISSION_REQUIRED -> {
                            Icon(
                                imageVector = Icons.Default.MicOff,
                                contentDescription = "",
                                tint = KothonDarkBg,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        state == VoiceButtonState.ERROR -> {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "",
                                tint = KothonDarkBg,
                                modifier = Modifier
                                    .size(30.dp)
                                    .graphicsLayer { alpha = if (state == VoiceButtonState.IDLE) 1f else 0.5f }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            BrandFooter(fontSize = 9.sp)
        }
    }
}
