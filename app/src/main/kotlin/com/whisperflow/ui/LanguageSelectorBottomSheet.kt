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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredLanguages = remember(searchQuery, isTargetLanguage) {
        LanguageCatalog.search(searchQuery, forTarget = isTargetLanguage)
    }

    val recentLanguages = remember(recentLanguageCodes, isTargetLanguage) {
        recentLanguageCodes.map { LanguageCatalog.findByCode(it) }
            .filter { lang ->
                if (isTargetLanguage) lang.code != "auto" else true
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF334155))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("language_selector_sheet")
        ) {
            // Header with title and close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color(0xFFF8FAFC),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search language or country...", color = Color(0xFF64748B), fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF38BDF8)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("language_search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color(0xFFF8FAFC),
                    unfocusedTextColor = Color(0xFFF8FAFC),
                    focusedContainerColor = Color(0xFF1E293B),
                    unfocusedContainerColor = Color(0xFF1E293B)
                ),
                singleLine = true
            )

            // Recently Used Languages Row (only if not searching)
            if (searchQuery.isEmpty() && recentLanguages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Recent",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Recently Used",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(recentLanguages) { lang ->
                        val isSelected = lang.code.equals(currentSelectedCode, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF0369A1) else Color(0xFF1E293B))
                                .clickable {
                                    onLanguageSelected(lang)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("recent_lang_${lang.code}")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = lang.flagEmoji, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = lang.name,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "All Supported Languages (${filteredLanguages.size})",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Language List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredLanguages) { lang ->
                    val isSelected = lang.code.equals(currentSelectedCode, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF1E293B) else Color.Transparent)
                            .clickable {
                                onLanguageSelected(lang)
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF334155)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = lang.flagEmoji, fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = lang.name,
                                    color = if (isSelected) Color(0xFF38BDF8) else Color(0xFFF8FAFC),
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = lang.nativeName,
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
