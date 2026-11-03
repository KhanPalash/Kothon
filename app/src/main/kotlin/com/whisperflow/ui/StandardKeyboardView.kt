package com.whisperflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StandardKeyboardView(
    onKeyChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onSwitchToVoice: () -> Unit,
    onSwitchIme: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isShifted by remember { mutableStateOf(false) }
    var isSymbols by remember { mutableStateOf(false) }

    val row1 = if (isSymbols) listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    else listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")

    val row2 = if (isSymbols) listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/")
    else listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")

    val row3 = if (isSymbols) listOf("*", "\"", "'", ":", ";", "!", "?")
    else listOf("z", "x", "c", "v", "b", "n", "m")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("standard_keyboard_view"),
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Quick top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kothon Keyboard",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF38BDF8).copy(alpha = 0.2f))
                        .clickable { onSwitchToVoice() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("switch_to_voice_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Mode",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.height(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Voice Refinement",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row1.forEach { char ->
                    KeyButton(
                        text = if (isShifted) char.uppercase() else char,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val out = if (isShifted) char.uppercase() else char
                            onKeyChar(out)
                        }
                    )
                }
            }

            // Row 2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row2.forEach { char ->
                    KeyButton(
                        text = if (isShifted) char.uppercase() else char,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val out = if (isShifted) char.uppercase() else char
                            onKeyChar(out)
                        }
                    )
                }
            }

            // Row 3 (Shift, characters, Backspace)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shift key
                KeySpecialButton(
                    text = if (isShifted) "▲" else "⇧",
                    modifier = Modifier.weight(1.4f),
                    active = isShifted,
                    onClick = { isShifted = !isShifted }
                )

                row3.forEach { char ->
                    KeyButton(
                        text = if (isShifted) char.uppercase() else char,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val out = if (isShifted) char.uppercase() else char
                            onKeyChar(out)
                        }
                    )
                }

                // Backspace
                KeySpecialButton(
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = Color.White,
                            modifier = Modifier.height(18.dp)
                        )
                    },
                    modifier = Modifier.weight(1.4f),
                    onClick = onBackspace
                )
            }

            // Row 4 (123/ABC, Switch IME, Space, Enter)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ?123 toggle
                KeySpecialButton(
                    text = if (isSymbols) "ABC" else "?123",
                    modifier = Modifier.weight(1.5f),
                    onClick = { isSymbols = !isSymbols }
                )

                // IME switch button
                KeySpecialButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Switch Input Method",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.height(18.dp)
                        )
                    },
                    modifier = Modifier.weight(1.2f),
                    onClick = onSwitchIme
                )

                // Space bar
                Box(
                    modifier = Modifier
                        .weight(4.5f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF334155))
                        .clickable { onKeyChar(" ") }
                        .testTag("key_space"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "space", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }

                // Enter key
                KeySpecialButton(
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                            contentDescription = "Enter",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.height(20.dp)
                        )
                    },
                    bgColor = Color(0xFF38BDF8),
                    modifier = Modifier.weight(1.8f),
                    onClick = onEnter
                )
            }
        }
    }
}

@Composable
private fun KeyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1E293B))
            .clickable(onClick = onClick)
            .testTag("key_$text"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun KeySpecialButton(
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: (@Composable () -> Unit)? = null,
    active: Boolean = false,
    bgColor: Color = if (active) Color(0xFF475569) else Color(0xFF1E293B),
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (text != null) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        } else if (icon != null) {
            icon()
        }
    }
}
