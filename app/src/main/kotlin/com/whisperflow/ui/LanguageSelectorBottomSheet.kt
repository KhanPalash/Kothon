package com.whisperflow.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.KothonAmber
import com.example.ui.theme.KothonBorder
import com.example.ui.theme.KothonCyan
import com.example.ui.theme.KothonDarkBg
import com.example.ui.theme.KothonDarkCard
import com.example.ui.theme.KothonDarkSurface
import com.example.ui.theme.KothonTextMuted
import com.example.ui.theme.KothonTextPrimary
import com.example.ui.theme.KothonTextSecondary
import com.whisperflow.model.Language
import com.whisperflow.model.LanguageCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectorBottomSheet(
    title: String,
    currentSelectedCode: String,
    isTargetLanguage: Boolean,
    recentLanguageCodes: List<String>,
    onLanguageSelected: (Language) -> Unit,
    onDismissRequest: () -> Unit,
    excludeCodes: Set<String> = emptySet(),
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredLanguages = remember(searchQuery, isTargetLanguage, excludeCodes) {
        LanguageCatalog.search(searchQuery, forTarget = isTargetLanguage)
            .filter { it.code.lowercase() !in excludeCodes }
    }

    val recentLanguages = remember(recentLanguageCodes, isTargetLanguage, excludeCodes) {
        recentLanguageCodes
            .filter { it.lowercase() !in excludeCodes }
            .map { LanguageCatalog.findByCode(it) }
            .filter { lang -> if (isTargetLanguage) lang.code != "auto" else true }
            .distinctBy { it.code }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = KothonDarkSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(KothonBorder)
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Brand watermark behind the sheet content
            Image(
                painter = painterResource(R.drawable.brand_logo),
                contentDescription = null,
                alpha = 0.30f,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 60.dp, vertical = 24.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("language_selector_sheet")
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = KothonTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = KothonTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search language or country…",
                            color = KothonTextMuted,
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = KothonCyan,
                            modifier = Modifier.size(17.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = KothonTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("language_search_input"),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = KothonTextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KothonCyan,
                        unfocusedBorderColor = KothonBorder.copy(alpha = 0.7f),
                        focusedContainerColor = KothonDarkBg,
                        unfocusedContainerColor = KothonDarkBg,
                        cursorColor = KothonCyan
                    ),
                    singleLine = true
                )

                // Recently used
                if (searchQuery.isEmpty() && recentLanguages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Recent",
                            tint = KothonAmber,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "RECENTLY USED",
                            color = KothonTextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recentLanguages, key = { it.code }) { lang ->
                            val isSelected = lang.code.equals(currentSelectedCode, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isSelected) KothonCyan else KothonDarkCard)
                                    .clickable { onLanguageSelected(lang) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("recent_lang_${lang.code}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = lang.flagEmoji, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = lang.name,
                                    color = if (isSelected) KothonDarkBg else KothonTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "ALL LANGUAGES (${filteredLanguages.size})",
                    color = KothonTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Language list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(326.dp)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(filteredLanguages, key = { it.code }) { lang ->
                        val isSelected = lang.code.equals(currentSelectedCode, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) KothonDarkCard else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { onLanguageSelected(lang) }
                                .padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(KothonDarkBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = lang.flagEmoji, fontSize = 15.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = lang.name,
                                        color = if (isSelected) KothonCyan else KothonTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = lang.nativeName,
                                        color = KothonTextMuted,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = KothonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
