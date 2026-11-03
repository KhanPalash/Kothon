package com.whisperflow.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.ui.theme.KothonBorder
import com.example.ui.theme.KothonCyan
import com.example.ui.theme.KothonDarkBg
import com.example.ui.theme.KothonDarkSurface
import com.example.ui.theme.KothonEmerald
import com.example.ui.theme.KothonTextPrimary
import com.example.ui.theme.KothonTextSecondary

@Composable
fun MicrophonePermissionDialog(
    onGrantClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .testTag("mic_permission_dialog"),
            color = KothonDarkSurface,
            border = BorderStroke(1.dp, KothonBorder.copy(alpha = 0.55f))
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Brand watermark behind the dialog content
                Image(
                    painter = painterResource(R.drawable.brand_logo),
                    contentDescription = null,
                    alpha = 0.30f,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(160.dp)
                        .height(160.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(KothonCyan.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Microphone",
                            tint = KothonCyan,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Microphone Access Required",
                        color = KothonTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Kothon needs microphone access to capture your speech, transcribe it in real time, and intelligently rephrase it into professional text.",
                        color = KothonTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(KothonDarkBg)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Privacy",
                            tint = KothonEmerald,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "Your audio is processed strictly for transcription and is never permanently stored.",
                            color = KothonTextSecondary,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onGrantClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("grant_mic_permission_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KothonCyan,
                            contentColor = Color(0xFF0B0F19)
                        ),
                        shape = RoundedCornerShape(11.dp)
                    ) {
                        Text(
                            text = "Allow Microphone",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = onOpenSettingsClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("open_app_settings_button"),
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, KothonBorder.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = "Open App Settings",
                            color = KothonTextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    BrandFooter(fontSize = 9.sp)
                }
            }
        }
    }
}
