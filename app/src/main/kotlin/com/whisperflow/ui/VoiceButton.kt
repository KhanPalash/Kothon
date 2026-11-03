package com.whisperflow.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
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
    providerName: String = "Gemini",
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_button_container"),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0F172A).copy(alpha = 0.78f),
                            Color(0xFF090D16).copy(alpha = 0.90f)
                        )
                    )
                )
                .border(
                    width = 0.8.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.14f),
                            KothonCyan.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.14f)
                        )
                    ),
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Frosted glass top action bar: language pair, tone, keyboard switch, home/settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(0.8.dp, KothonCyan.copy(alpha = 0.35f), RoundedCornerShape(11.dp))
                        .clickable { onToggleLanguages() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("translate_toggle_chip")
                        .semantics {
                            role = Role.Button
                            contentDescription = "Language pair: ${sourceLang.name} to ${targetLang.name}. Tap to change."
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
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
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(11.dp))
                        .clickable { onCycleTone() }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                        .testTag("cycle_tone_chip")
                        .semantics {
                            role = Role.Button
                            contentDescription = "AI tone: $tone. Tap to cycle."
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
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
                            .size(48.dp)
                            .testTag("switch_to_keyboard_button")
                            .semantics {
                                role = Role.Button
                                contentDescription = "Switch to QWERTY keyboard"
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = KothonTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("open_settings_button")
                            .semantics {
                                role = Role.Button
                                contentDescription = "Open Kothon Home & Settings"
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = KothonTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Real-time status / transcription hint
            Text(
                text = when {
                    voiceDisabled -> "Voice disabled for password fields."
                    state == VoiceButtonState.IDLE -> {
                        val modePrefix = if (modeLabel.isBlank()) "$providerName • " else "$providerName ($modeLabel) • "
                        "${modePrefix}Speak ${sourceLang.name} → ${targetLang.name}"
                    }
                    state == VoiceButtonState.RECORDING -> statusMessage.ifEmpty { "Listening... Speak naturally" }
                    state == VoiceButtonState.PROCESSING -> statusMessage.ifEmpty { "Refining with $providerName..." }
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(0.8.dp, KothonCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .clickable { onUndo() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(0.8.dp, KothonAmber.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .clickable { onRefine() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
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
                    .size(72.dp)
                    .padding(3.dp)
            ) {
                if (state == VoiceButtonState.RECORDING) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(KothonRose.copy(alpha = 0.26f))
                    )
                }

                var pressStartTime by remember { mutableStateOf(0L) }

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
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(buttonBgColor)
                        .border(1.2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
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
                                        tryAwaitRelease()
                                        onRecordStop()
                                    } else {
                                        pressStartTime = System.currentTimeMillis()
                                        onRecordStart()
                                        tryAwaitRelease()
                                        val duration = System.currentTimeMillis() - pressStartTime
                                        if (duration > 500L) {
                                            onRecordStop()
                                        }
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
                                contentDescription = null,
                                tint = KothonTextPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        state == VoiceButtonState.RECORDING -> {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null,
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
                                contentDescription = null,
                                tint = KothonDarkBg,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        state == VoiceButtonState.ERROR -> {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
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
