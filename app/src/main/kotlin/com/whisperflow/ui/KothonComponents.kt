package com.whisperflow.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.KothonCyan
import com.example.ui.theme.KothonDarkBg
import com.example.ui.theme.KothonDarkCard
import com.example.ui.theme.KothonEmerald
import com.example.ui.theme.KothonIndigo
import com.example.ui.theme.KothonTextMuted
import com.example.ui.theme.KothonTextPrimary
import com.example.ui.theme.KothonTextSecondary

val KothonCardShape = RoundedCornerShape(16.dp)
val KothonInnerShape = RoundedCornerShape(10.dp)

val GlassSurfaceBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.09f),
        Color(0xFF151F32).copy(alpha = 0.52f),
        Color(0xFF0B111E).copy(alpha = 0.68f)
    )
)

val GlassBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.24f),
        KothonCyan.copy(alpha = 0.14f),
        Color.White.copy(alpha = 0.05f)
    )
)

val GlassPillBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.08f),
        Color(0xFF0F172A).copy(alpha = 0.58f)
    )
)

/**
 * Ambient atmospheric background with soft radial glass glow orbs + subtle logo watermark.
 */
@Composable
fun KothonGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF050811),
                        Color(0xFF0A101D),
                        Color(0xFF070B14)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        KothonCyan.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.15f, size.height * 0.10f),
                    radius = size.width * 0.60f
                ),
                center = Offset(size.width * 0.15f, size.height * 0.10f),
                radius = size.width * 0.60f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        KothonIndigo.copy(alpha = 0.13f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.36f),
                    radius = size.width * 0.65f
                ),
                center = Offset(size.width * 0.88f, size.height * 0.36f),
                radius = size.width * 0.65f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        KothonEmerald.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.25f, size.height * 0.84f),
                    radius = size.width * 0.55f
                ),
                center = Offset(size.width * 0.25f, size.height * 0.84f),
                radius = size.width * 0.55f
            )
        }

        Image(
            painter = painterResource(R.drawable.brand_logo),
            contentDescription = null,
            alpha = 0.14f,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.58f)
                .fillMaxHeight(0.40f)
        )

        content()
    }
}

@Composable
fun KothonCard(
    modifier: Modifier = Modifier,
    accentBorderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderBrush = if (accentBorderColor != null) {
        Brush.linearGradient(
            colors = listOf(
                accentBorderColor.copy(alpha = 0.50f),
                Color.White.copy(alpha = 0.20f),
                accentBorderColor.copy(alpha = 0.14f)
            )
        )
    } else {
        GlassBorderBrush
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B1120).copy(alpha = 0.58f)
        ),
        shape = KothonCardShape,
        border = BorderStroke(0.8.dp, borderBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassSurfaceBrush)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            content = content
        )
    }
}

@Composable
fun KothonSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = KothonTextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        trailing?.invoke()
    }
}

@Composable
fun KothonStatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.13f))
            .border(
                width = 0.7.dp,
                color = color.copy(alpha = 0.36f),
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp
        )
    }
}

@Composable
fun KothonChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedColor: Color = KothonCyan
) {
    val shape = RoundedCornerShape(9.dp)
    val backgroundBrush = when {
        !enabled -> Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.03f), Color.White.copy(alpha = 0.02f))
        )
        selected -> Brush.linearGradient(
            listOf(
                selectedColor.copy(alpha = 0.92f),
                selectedColor.copy(alpha = 0.74f)
            )
        )
        else -> GlassPillBrush
    }
    val borderColor = when {
        !enabled -> Color.White.copy(alpha = 0.05f)
        selected -> Color.White.copy(alpha = 0.42f)
        else -> Color.White.copy(alpha = 0.13f)
    }
    val contentColor = when {
        !enabled -> KothonTextMuted
        selected -> if (selectedColor == KothonIndigo) Color.White else Color(0xFF070B14)
        else -> KothonTextPrimary.copy(alpha = 0.88f)
    }

    Box(
        modifier = modifier
            .height(30.dp)
            .clip(shape)
            .background(backgroundBrush)
            .border(0.7.dp, borderColor, shape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun KothonModeSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedColor: Color = KothonCyan
) {
    val shape = RoundedCornerShape(8.dp)
    val background = if (enabled && selected) selectedColor else Color.Transparent
    val contentColor = when {
        !enabled -> KothonTextMuted.copy(alpha = 0.6f)
        selected -> if (selectedColor == KothonIndigo) Color.White else Color(0xFF070B14)
        else -> KothonTextSecondary
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .then(
                if (enabled && selected) {
                    Modifier.border(0.7.dp, Color.White.copy(alpha = 0.35f), shape)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun KothonSettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = KothonCyan
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = title,
                color = KothonTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = KothonTextSecondary,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = tint,
                uncheckedThumbColor = KothonTextSecondary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.22f)
            ),
            modifier = Modifier
                .scale(0.76f)
                .height(28.dp)
        )
    }
}

@Composable
fun KothonLanguagePill(
    label: String,
    flag: String,
    name: String,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor = if (accent) KothonCyan.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.14f)
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(GlassPillBrush)
            .border(0.8.dp, borderColor, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(text = flag, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                color = if (accent) KothonCyan else KothonTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = KothonTextMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
        )
    }
}

@Composable
fun BrandFooter(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 9.sp
) {
    Text(
        text = "Powered by KhansCreations",
        color = KothonTextSecondary.copy(alpha = 0.65f),
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
        modifier = modifier
    )
}

@Composable
fun KothonActionPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = KothonCyan,
    background: Color = KothonDarkCard
) {
    val shape = RoundedCornerShape(9.dp)
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(shape)
            .background(background.copy(alpha = 0.65f))
            .border(0.7.dp, color.copy(alpha = 0.35f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
fun KothonNote(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = KothonTextSecondary,
    background: Color = KothonDarkBg
) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background.copy(alpha = 0.55f))
            .border(0.7.dp, color.copy(alpha = 0.25f), shape)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            lineHeight = 13.sp
        )
    }
}
