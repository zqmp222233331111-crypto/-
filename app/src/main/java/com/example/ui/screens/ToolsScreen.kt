package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.ContentToolType
import com.example.data.model.ToolLength
import com.example.data.model.ToolTone
import com.example.data.model.TranslationDirection
import com.example.ui.components.GlassCard
import com.example.ui.theme.AccentVioletBright
import com.example.ui.theme.AccentVioletPrimary
import com.example.ui.theme.GoldCrown
import com.example.ui.theme.LocalShailaColors
import com.example.ui.viewmodel.ToolUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ToolsScreen(
    appLanguage: AppLanguage,
    toolState: ToolUiState,
    onGenerate: (ContentToolType, String, ToolTone, ToolLength, TranslationDirection) -> Unit,
    onResetToolState: () -> Unit,
    onSpeakText: (String) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val isBn = appLanguage == AppLanguage.BENGALI
    val ext = LocalShailaColors.current
    val clipboard = LocalClipboardManager.current
    var activeTool by remember { mutableStateOf<ContentToolType?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header matching Mockup G: Wrench/Build left, "Tools" center, Settings right
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp)
                .padding(top = 8.dp, bottom = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Build,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .size(22.dp)
            )
            Text(
                text = if (isBn) "Tools" else "Tools",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = if (isBn) "সেটিংস" else "Settings",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(ContentToolType.entries) { tool ->
                val (badgeBg1, badgeBg2) = toolBadgeColors(tool)
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tool_card_${tool.id}"),
                    cornerRadius = 20.dp,
                    contentPadding = 16.dp,
                    onClick = {
                        onResetToolState()
                        activeTool = tool
                    }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(badgeBg1, badgeBg2))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = toolIcon(tool),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (tool == ContentToolType.TRANSLATOR) {
                                "Translator\n(Bangla ⇄ English)"
                            } else {
                                if (isBn) tool.titleBn else tool.titleEn
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            minLines = 2
                        )
                    }
                }
            }

            // Full-width purple gradient banner at the bottom matching Mockup G
            item(span = { GridItemSpan(2) }) {
                Surface(
                    onClick = {
                        onResetToolState()
                        activeTool = ContentToolType.SHORT_SCRIPT
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, ext.glassBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(AccentVioletPrimary, AccentVioletBright)
                                )
                            )
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lightbulb,
                                contentDescription = null,
                                tint = GoldCrown,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Create Amazing Content with Shaila",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    val currentTool = activeTool
    if (currentTool != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var topic by rememberSaveable(currentTool.id) { mutableStateOf("") }
        var tone by rememberSaveable(currentTool.id) { mutableStateOf(ToolTone.FRIENDLY) }
        var length by rememberSaveable(currentTool.id) { mutableStateOf(ToolLength.MEDIUM) }
        var direction by rememberSaveable(currentTool.id) { mutableStateOf(TranslationDirection.BN_TO_EN) }
        var copied by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { activeTool = null },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBn) currentTool.titleBn else currentTool.titleEn,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { activeTool = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tool_topic_input"),
                    label = {
                        Text(if (isBn) "বিষয় / মূল লেখা" else "Topic / Source Text")
                    },
                    placeholder = {
                        Text(if (isBn) currentTool.placeholderBn else currentTool.placeholderEn)
                    },
                    minLines = 3,
                    maxLines = 5
                )

                if (currentTool == ContentToolType.TRANSLATOR) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TranslationDirection.entries.forEach { dir ->
                            FilterChip(
                                selected = direction == dir,
                                onClick = { direction = dir },
                                label = { Text(if (isBn) dir.labelBn else dir.labelEn) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isBn) "টোন (Tone)" else "Tone",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ToolTone.entries.forEach { item ->
                        FilterChip(
                            selected = tone == item,
                            onClick = { tone = item },
                            label = { Text(if (isBn) item.labelBn else item.labelEn) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isBn) "দৈর্ঘ্য (Length)" else "Length",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolLength.entries.forEach { item ->
                        FilterChip(
                            selected = length == item,
                            onClick = { length = item },
                            label = { Text(if (isBn) item.labelBn else item.labelEn) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        copied = false
                        onGenerate(currentTool, topic, tone, length, direction)
                    },
                    enabled = topic.isNotBlank() && !toolState.isGenerating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("tool_generate_button")
                ) {
                    if (toolState.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isBn) "তৈরি হচ্ছে..." else "Generating...")
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isBn) "তৈরি করুন" else "Generate")
                    }
                }

                if (toolState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = toolState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (toolState.resultText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = toolState.resultText,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (copied) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isBn) "ক্লিপবোর্ডে কপি হয়েছে!" else "Copied to clipboard!",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(toolState.resultText))
                                    copied = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isBn) "কপি" else "Copy", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onSpeakText(toolState.resultText) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isBn) "শুনুন" else "Speak", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    copied = false
                                    onGenerate(currentTool, topic, tone, length, direction)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isBn) "আবার" else "Retry", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun toolIcon(tool: ContentToolType): ImageVector = when (tool) {
    ContentToolType.YOUTUBE_TITLE -> Icons.Filled.SmartDisplay
    ContentToolType.DESCRIPTION_TAGS -> Icons.Filled.Description
    ContentToolType.FACEBOOK_CAPTION -> Icons.Filled.Public
    ContentToolType.INSTAGRAM_CAPTION -> Icons.Filled.CameraAlt
    ContentToolType.SHORT_SCRIPT -> Icons.Filled.Movie
    ContentToolType.REPLY_WRITER -> Icons.AutoMirrored.Filled.Reply
    ContentToolType.TRANSLATOR -> Icons.Filled.Translate
}

private fun toolBadgeColors(tool: ContentToolType): Pair<Color, Color> = when (tool) {
    ContentToolType.YOUTUBE_TITLE -> Color(0xFFEF4444) to Color(0xFFDC2626)
    ContentToolType.DESCRIPTION_TAGS -> Color(0xFF3B82F6) to Color(0xFF2563EB)
    ContentToolType.FACEBOOK_CAPTION -> Color(0xFF1D4ED8) to Color(0xFF3B82F6)
    ContentToolType.INSTAGRAM_CAPTION -> Color(0xFFEC4899) to Color(0xFFF59E0B)
    ContentToolType.SHORT_SCRIPT -> Color(0xFF8B5CF6) to Color(0xFF6D28D9)
    ContentToolType.REPLY_WRITER -> Color(0xFF22C55E) to Color(0xFF16A34A)
    ContentToolType.TRANSLATOR -> Color(0xFF14B8A6) to Color(0xFF0D9488)
}
