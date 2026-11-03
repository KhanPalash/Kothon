package com.whisperflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.KothonCyan
import com.example.ui.theme.KothonIndigo
import com.example.ui.theme.KothonTextPrimary
import com.example.ui.theme.KothonTextSecondary

// Shift states like Gboard: 0 = lowercase, 1 = single shift, 2 = caps lock
private const val SHIFT_OFF = 0
private const val SHIFT_ONCE = 1
private const val SHIFT_CAPS = 2

@Composable
fun StandardKeyboardView(
    onKeyChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onSwitchToVoice: () -> Unit,
    onSwitchIme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var shiftState by remember { mutableIntStateOf(SHIFT_OFF) }
    var isSymbols by remember { mutableStateOf(false) }
    var isSecondarySymbols by remember { mutableStateOf(false) }

    val isShifted = shiftState != SHIFT_OFF

    val numberRow = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    val row1 = when {
        !isSymbols -> listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        !isSecondarySymbols -> listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        else -> listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆")
    }

    val row2 = when {
        !isSymbols -> listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        !isSecondarySymbols -> listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/")
        else -> listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\")
    }

    val row3 = when {
        !isSymbols -> listOf("z", "x", "c", "v", "b", "n", "m")
        !isSecondarySymbols -> listOf("*", "\"", "'", ":", ";", "!", "?")
        else -> listOf("%", "©", "®", "™", "✓", "[", "]")
    }

    val quickChips = listOf("!", "?", "@", "'", "-", "/")

    fun emitChar(raw: String) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val out = if (!isSymbols && isShifted) raw.uppercase() else raw
        onKeyChar(out)
        if (!isSymbols && shiftState == SHIFT_ONCE) {
            shiftState = SHIFT_OFF
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("standard_keyboard_view"),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F172A).copy(alpha = 0.86f),
                            Color(0xFF090D16).copy(alpha = 0.94f)
                        )
                    )
                )
                .border(
                    width = 0.8.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            KothonCyan.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.12f)
                        )
                    ),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                .padding(horizontal = 5.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // 1. Gboard-style Suggestion & Quick Action Toolbar Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(0.6.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Voice AI Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    KothonCyan.copy(alpha = 0.22f),
                                    KothonIndigo.copy(alpha = 0.20f)
                                )
                            )
                        )
                        .border(0.8.dp, KothonCyan.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSwitchToVoice()
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("switch_to_voice_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Mode",
                        tint = KothonCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Voice AI",
                        color = KothonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Center Gboard suggestion strip (punctuation shortcuts)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    quickChips.forEach { sym ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { emitChar(sym) }
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sym,
                                color = KothonTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Right Gboard Mic Icon shortcut
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSwitchToVoice()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Dictation",
                        tint = KothonTextPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // 2. Dedicated Gboard Number Row (shown in letter mode)
            if (!isSymbols) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    numberRow.forEach { num ->
                        GboardKeyButton(
                            text = num,
                            height = 35.dp,
                            fontSize = 14,
                            isNumberRow = true,
                            modifier = Modifier.weight(1f),
                            onClick = { emitChar(num) }
                        )
                    }
                }
            }

            // 3. QWERTY Row 1 (with top-right number hint in letter mode, like Gboard)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row1.forEachIndexed { idx, char ->
                    val display = if (!isSymbols && isShifted) char.uppercase() else char
                    val hint = if (!isSymbols && idx < numberRow.size) numberRow[idx] else null
                    GboardKeyButton(
                        text = display,
                        topRightHint = hint,
                        modifier = Modifier.weight(1f),
                        onClick = { emitChar(char) }
                    )
                }
            }

            // 4. QWERTY Row 2 (Gboard half-key horizontal inset for 9-letter row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (!isSymbols) 16.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row2.forEach { char ->
                    val display = if (!isSymbols && isShifted) char.uppercase() else char
                    GboardKeyButton(
                        text = display,
                        modifier = Modifier.weight(1f),
                        onClick = { emitChar(char) }
                    )
                }
            }

            // 5. QWERTY Row 3 (Shift / Symbol Page toggle, 7 keys, Backspace)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isSymbols) {
                    val shiftDesc = when (shiftState) {
                        SHIFT_CAPS -> "Caps lock on"
                        SHIFT_ONCE -> "Shift active"
                        else -> "Shift off"
                    }
                    GboardSpecialKey(
                        modifier = Modifier
                            .weight(1.45f)
                            .semantics {
                                role = Role.Button
                                contentDescription = shiftDesc
                            },
                        active = shiftState != SHIFT_OFF,
                        accentColor = if (shiftState == SHIFT_CAPS) KothonCyan else Color.White.copy(alpha = 0.22f),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            shiftState = when (shiftState) {
                                SHIFT_OFF -> SHIFT_ONCE
                                SHIFT_ONCE -> SHIFT_CAPS
                                else -> SHIFT_OFF
                            }
                        },
                        icon = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardCapslock,
                                    contentDescription = null,
                                    tint = if (shiftState != SHIFT_OFF) KothonCyan else KothonTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                if (shiftState == SHIFT_CAPS) {
                                    Box(
                                        modifier = Modifier
                                            .width(10.dp)
                                            .height(2.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(KothonCyan)
                                    )
                                }
                            }
                        }
                    )
                } else {
                    GboardSpecialKey(
                        text = if (isSecondarySymbols) "123" else "=\\<",
                        modifier = Modifier.weight(1.45f),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isSecondarySymbols = !isSecondarySymbols
                        }
                    )
                }

                row3.forEach { char ->
                    val display = if (!isSymbols && isShifted) char.uppercase() else char
                    GboardKeyButton(
                        text = display,
                        modifier = Modifier.weight(1f),
                        onClick = { emitChar(char) }
                    )
                }

                GboardSpecialKey(
                    modifier = Modifier
                        .weight(1.45f)
                        .testTag("key_backspace"),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBackspace()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = KothonTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            // 6. Gboard Bottom Row: ?123 | , | Globe | Spacebar | . | Enter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ?123 / ABC pill
                GboardSpecialKey(
                    text = if (isSymbols) "ABC" else "?123",
                    modifier = Modifier.weight(1.35f),
                    isPill = true,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isSymbols = !isSymbols
                        isSecondarySymbols = false
                    }
                )

                // Comma key (Gboard left of spacebar)
                GboardKeyButton(
                    text = ",",
                    modifier = Modifier.weight(0.9f),
                    onClick = { emitChar(",") }
                )

                // Globe / Switch IME key
                GboardSpecialKey(
                    modifier = Modifier.weight(0.95f),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSwitchIme()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Switch Input Method",
                            tint = KothonTextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                )

                // Gboard Spacebar
                Box(
                    modifier = Modifier
                        .weight(3.9f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.14f),
                                    Color.White.copy(alpha = 0.08f)
                                )
                            )
                        )
                        .border(0.8.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(21.dp))
                        .clickable { emitChar(" ") }
                        .testTag("key_space"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "English • Kothon",
                        color = KothonTextSecondary.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp
                    )
                }

                // Period key (Gboard right of spacebar)
                GboardKeyButton(
                    text = ".",
                    modifier = Modifier.weight(0.9f),
                    onClick = { emitChar(".") }
                )

                // Gboard Accented Enter / Return pill button
                Box(
                    modifier = Modifier
                        .weight(1.45f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(KothonCyan, KothonIndigo)
                            )
                        )
                        .border(0.8.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(21.dp))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onEnter()
                        }
                        .testTag("key_enter"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                        contentDescription = "Enter",
                        tint = Color(0xFF090D16),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GboardKeyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp,
    fontSize: Int = 16,
    topRightHint: String? = null,
    isNumberRow: Boolean = false
) {
    val shape = RoundedCornerShape(8.dp)
    val bgBrush = if (isNumberRow) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.04f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.08f)
            )
        )
    }

    Box(
        modifier = modifier
            .height(height)
            .shadow(elevation = 1.dp, shape = shape, clip = false)
            .clip(shape)
            .background(bgBrush)
            .border(0.7.dp, Color.White.copy(alpha = 0.15f), shape)
            .clickable(onClick = onClick)
            .testTag("key_$text"),
        contentAlignment = Alignment.Center
    ) {
        if (topRightHint != null) {
            Text(
                text = topRightHint,
                color = KothonTextSecondary.copy(alpha = 0.65f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp)
            )
        }
        Text(
            text = text,
            color = KothonTextPrimary,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun GboardSpecialKey(
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: (@Composable () -> Unit)? = null,
    active: Boolean = false,
    isPill: Boolean = false,
    accentColor: Color = Color.White.copy(alpha = 0.10f),
    onClick: () -> Unit
) {
    val shape = if (isPill) RoundedCornerShape(21.dp) else RoundedCornerShape(8.dp)
    val bgColor = if (active) accentColor else Color(0xFF1E293B).copy(alpha = 0.72f)
    val borderColor = if (active) KothonCyan.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(shape)
            .background(bgColor)
            .border(0.7.dp, borderColor, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (text != null) {
            Text(
                text = text,
                color = KothonTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else if (icon != null) {
            icon()
        }
    }
}
