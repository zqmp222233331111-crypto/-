package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.AppSettings
import com.example.data.model.ChatMessage
import com.example.data.model.HomeCharacterStyle
import com.example.data.model.ImageRegionRect
import com.example.data.model.ImageRegionsConfig
import com.example.data.model.SenderRole
import com.example.ui.components.AnimatedVoiceOrb
import com.example.ui.components.GlassCard
import com.example.ui.components.LivingHalfBodyCharacter
import com.example.ui.components.ShailaCharacterAvatar
import com.example.ui.components.SoundWaveBar
import com.example.ui.theme.AccentVioletBright
import com.example.ui.theme.AccentVioletPrimary
import com.example.ui.theme.AccentVioletSecondary
import com.example.ui.theme.GoldCrown
import com.example.ui.theme.LocalShailaColors
import com.example.ui.theme.ShailaScriptFont
import com.example.ui.viewmodel.OrbState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    settings: AppSettings,
    imageRegions: ImageRegionsConfig,
    messages: List<ChatMessage>,
    memoryCount: Int,
    orbState: OrbState,
    rmsLevel: Float,
    speakingLevel: Float = 0f,
    partialTranscript: String,
    statusMessage: String?,
    activeVoiceSource: String?,
    needsApiKeyBanner: Boolean,
    pendingSafetyPrompt: String?,
    greetingText: String,
    onUpdateMicPermission: (Boolean) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onStopSpeaking: () -> Unit,
    onStartLiveCall: () -> Unit = {},
    onSendMessage: (String) -> Unit,
    onSpeakMessage: (String) -> Unit,
    onSaveToMemory: (String) -> Unit,
    onToggleHoldToTalk: (Boolean) -> Unit,
    onToggleLanguage: (AppLanguage) -> Unit,
    onConfirmSafety: () -> Unit,
    onDismissSafety: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissStatus: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val ext = LocalShailaColors.current
    val isBn = settings.appLanguage == AppLanguage.BENGALI

    var textInput by rememberSaveable { mutableStateOf("") }
    var showMicPermissionDialog by remember { mutableStateOf(false) }
    var pendingLiveCallPermission by remember { mutableStateOf(false) }
    var showMenuDropdown by remember { mutableStateOf(false) }
    val textInputFocusRequester = remember { FocusRequester() }

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(micGranted) {
        onUpdateMicPermission(micGranted)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        onUpdateMicPermission(granted)
        if (granted) {
            if (pendingLiveCallPermission) {
                pendingLiveCallPermission = false
                onStartLiveCall()
            } else {
                onStartListening()
            }
        } else {
            pendingLiveCallPermission = false
            showMicPermissionDialog = true
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val triggerMicAction: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (!micGranted) {
            showMicPermissionDialog = true
        } else {
            when (orbState) {
                OrbState.LISTENING -> onStopListening()
                OrbState.SPEAKING -> {
                    onStopSpeaking()
                    onStartListening()
                }
                OrbState.IDLE -> onStartListening()
                OrbState.THINKING -> Unit
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val isDefaultShaila = settings.assistantName.equals("Shaila", ignoreCase = true) ||
            settings.assistantName == "শাইলা"

        // Top Bar: Menu left, "Shaila শাইলা" center, Gold Crown right
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .padding(top = 6.dp, bottom = 2.dp)
        ) {
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                IconButton(
                    onClick = { showMenuDropdown = true },
                    modifier = Modifier.testTag("home_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = if (isBn) "মেনু" else "Menu",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                DropdownMenu(
                    expanded = showMenuDropdown,
                    onDismissRequest = { showMenuDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(if (isBn) "স্মৃতি পরিচালনা ($memoryCount)" else "Manage Memory ($memoryCount)")
                        },
                        onClick = {
                            showMenuDropdown = false
                            onOpenMemory()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(if (isBn) "ভাষা: বাংলা ⇄ English" else "Switch Language: EN ⇄ বাংলা")
                        },
                        onClick = {
                            showMenuDropdown = false
                            onToggleLanguage(if (isBn) AppLanguage.ENGLISH else AppLanguage.BENGALI)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBn) "সেটিংস" else "Settings") },
                        onClick = {
                            showMenuDropdown = false
                            onOpenSettings()
                        }
                    )
                }
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = settings.assistantName,
                    fontFamily = ShailaScriptFont,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 28.sp
                )
                if (isDefaultShaila) {
                    Text(
                        text = "শাইলা",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onOpenMemory,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .testTag("home_memory_chip")
            ) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = stringResource(R.string.cd_memory_page),
                    tint = GoldCrown,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        val micCd = stringResource(R.string.cd_mic_button)
        val usePortraitCharacter = settings.homeCharacterStyle != HomeCharacterStyle.CLASSIC_ORB
        val bnDisplayName = if (isDefaultShaila) "শাইলা" else settings.assistantName

        val micControlsRow: @Composable () -> Unit = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onToggleHoldToTalk(!settings.holdToTalkMode) }
                        .padding(4.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (settings.holdToTalkMode) AccentVioletPrimary.copy(alpha = 0.45f) else ext.glassFill,
                        border = BorderStroke(
                            1.dp,
                            if (settings.holdToTalkMode) AccentVioletBright else ext.glassBorder
                        ),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.PanTool,
                                contentDescription = "Hold to Talk",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (isBn) "চেপে ধরুন" else "Hold to Talk",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .shadow(14.dp, CircleShape, ambientColor = AccentVioletBright, spotColor = AccentVioletSecondary)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = if (orbState == OrbState.LISTENING) {
                                    listOf(MaterialTheme.colorScheme.error, AccentVioletPrimary)
                                } else {
                                    listOf(AccentVioletPrimary, AccentVioletBright)
                                }
                            )
                        )
                        .border(2.dp, Color.White.copy(alpha = 0.42f), CircleShape)
                        .testTag("mic_button")
                        .semantics { contentDescription = micCd }
                        .pointerInput(settings.holdToTalkMode, micGranted, orbState) {
                            detectTapGestures(
                                onTap = {
                                    if (!settings.holdToTalkMode) {
                                        triggerMicAction()
                                    }
                                },
                                onPress = {
                                    if (settings.holdToTalkMode) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (!micGranted) {
                                            showMicPermissionDialog = true
                                        } else {
                                            onStartListening()
                                            tryAwaitRelease()
                                            onStopListening()
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (settings.showHerEverywhere) {
                        ShailaCharacterAvatar(
                            homeCharacterStyle = settings.homeCharacterStyle,
                            customCharacterUri = settings.customCharacterUri,
                            fallbackAvatarRegion = imageRegions.avatarFace,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            borderColor = Color.Transparent
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (orbState == OrbState.LISTENING) {
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.42f)
                                    } else {
                                        Color(0x55120A28)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (orbState == OrbState.LISTENING) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = micCd,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (orbState == OrbState.LISTENING) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = micCd,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (orbState == OrbState.SPEAKING) {
                                onStopSpeaking()
                            } else {
                                textInputFocusRequester.requestFocus()
                            }
                        }
                        .padding(4.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ext.glassFill,
                        border = BorderStroke(1.dp, ext.glassBorder),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (orbState == OrbState.SPEAKING) Icons.Filled.Stop else Icons.Filled.Keyboard,
                                contentDescription = "Type",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (orbState == OrbState.SPEAKING) {
                            if (isBn) "থামান" else "Stop"
                        } else {
                            if (isBn) "লিখুন" else "Type"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Center Character / Avatar + Sound wave + Greeting + Live Call CTA
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (usePortraitCharacter) {
                val heroHeight = if (messages.isEmpty()) 258.dp else 192.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LivingHalfBodyCharacter(
                        homeCharacterStyle = settings.homeCharacterStyle,
                        customCharacterUri = settings.customCharacterUri,
                        orbState = orbState,
                        rmsLevel = rmsLevel,
                        speakingLevel = speakingLevel,
                        reduceMotion = settings.reduceMotion,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 34.dp),
                        onClick = triggerMicAction
                    )
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        micControlsRow()
                    }
                }
            } else {
                AnimatedVoiceOrb(
                    orbState = orbState,
                    rmsLevel = rmsLevel,
                    speakingLevel = speakingLevel,
                    avatarRegion = imageRegions.avatarFace,
                    orbSize = if (messages.isEmpty()) 172.dp else 142.dp,
                    onClick = triggerMicAction
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            SoundWaveBar(
                orbState = orbState,
                rmsLevel = if (orbState == OrbState.SPEAKING) speakingLevel else rmsLevel
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = when (orbState) {
                    OrbState.LISTENING -> if (isBn) "মন দিয়ে শুনছি..." else "Listening..."
                    OrbState.THINKING -> if (isBn) "$bnDisplayName ভাবছে..." else "${settings.assistantName} is thinking..."
                    OrbState.SPEAKING -> if (isBn) "$bnDisplayName বলছে..." else "${settings.assistantName} is speaking..."
                    OrbState.IDLE -> greetingText
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (activeVoiceSource != null && orbState == OrbState.IDLE) {
                Text(
                    text = if (isBn) "কণ্ঠ: $activeVoiceSource" else "Voice: $activeVoiceSource",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Prominent Real-Time Live Voice Call pill
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                border = BorderStroke(
                    1.2.dp,
                    Brush.linearGradient(listOf(AccentVioletSecondary, AccentVioletBright))
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                AccentVioletPrimary.copy(alpha = 0.42f),
                                AccentVioletSecondary.copy(alpha = 0.28f)
                            )
                        )
                    )
                    .testTag("start_live_call_button")
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!micGranted) {
                            pendingLiveCallPermission = true
                            showMicPermissionDialog = true
                        } else {
                            onStartLiveCall()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = if (isBn) "লাইভ কল" else "Live Call",
                        tint = AccentVioletSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isBn) "${bnDisplayName}-এর সাথে লাইভ কথা বলুন" else "Live Call with ${settings.assistantName}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Live partial transcript pill
            AnimatedVisibility(visible = partialTranscript.isNotBlank()) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    cornerRadius = 16.dp,
                    contentPadding = 10.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = AccentVioletSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = partialTranscript,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (!usePortraitCharacter) {
                Spacer(modifier = Modifier.height(6.dp))
                micControlsRow()
            }
        }

        // Error / API key missing banner
        AnimatedVisibility(visible = statusMessage != null || needsApiKeyBanner) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .padding(top = 6.dp),
                cornerRadius = 16.dp,
                contentPadding = 10.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    if (needsApiKeyBanner) {
                        TextButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isBn) "API Key দিন" else "Set Key")
                        }
                    } else {
                        TextButton(onClick = onDismissStatus) {
                            Text(if (isBn) "ঠিক আছে" else "OK")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Scrollable Transcript / Chat Bubbles
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 680.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                    Text(
                        text = if (isBn) {
                            "মাইক্রোফোনে ট্যাপ করে কথা বলুন বা নিচের যেকোনো প্রশ্ন চেষ্টা করুন:"
                        } else {
                            "Tap the mic to talk or try one of these prompts:"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val suggestions = if (isBn) {
                        listOf(
                            "তোমার নাম কী?",
                            "তোমাকে কে বানিয়েছে?",
                            "আজকের আবহাওয়া কেমন?",
                            "একটি সুন্দর বাংলা ক্যাপশন লিখে দাও"
                        )
                    } else {
                        listOf(
                            "What is your name?",
                            "Who created you?",
                            "Give me 3 YouTube video ideas",
                            "Write a warm Instagram caption"
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestions.forEach { chipText ->
                            AssistChip(
                                onClick = { onSendMessage(chipText) },
                                label = { Text(chipText, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubbleItem(
                        message = msg,
                        settings = settings,
                        avatarRegion = imageRegions.avatarFace,
                        isBn = isBn,
                        onSpeak = { onSpeakMessage(msg.text) },
                        onCopy = { clipboard.setText(AnnotatedString(msg.text)) },
                        onSaveMemory = { onSaveToMemory(msg.text) }
                    )
                }
            }
        }

        // Bottom Input Bar: "Type a message..." with purple send button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .padding(top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(textInputFocusRequester)
                    .testTag("chat_text_input"),
                placeholder = {
                    Text(
                        if (isBn) "Type a message..." else "Type a message...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ext.glassFill,
                    unfocusedContainerColor = ext.glassFill,
                    focusedBorderColor = AccentVioletBright,
                    unfocusedBorderColor = ext.glassBorder
                ),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (textInput.isNotBlank()) {
                            onSendMessage(textInput)
                            textInput = ""
                        }
                    }
                )
            )

            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(AccentVioletPrimary, AccentVioletBright))
                    )
            ) {
                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onSendMessage(textInput)
                            textInput = ""
                        }
                    },
                    enabled = textInput.isNotBlank() && orbState != OrbState.THINKING,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("send_message_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.cd_send_message),
                        tint = Color.White
                    )
                }
            }
        }
    }

    // Friendly Microphone Permission Explanation Dialog
    if (showMicPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showMicPermissionDialog = false },
            title = {
                Text(if (isBn) "মাইক্রোফোন অনুমতি প্রয়োজন" else "Microphone Permission")
            },
            text = {
                Text(
                    if (isBn) {
                        "শাইলার সাথে সরাসরি বাংলা বা ইংরেজিতে কথা বলতে আপনার মাইক্রোফোন ব্যবহারের অনুমতি দিন। আপনি চাইলে নিচের টেক্সট বক্সে লিখেও কথা বলতে পারেন।"
                    } else {
                        "Shaila needs microphone access so you can speak naturally. You can also type messages anytime using the text bar below."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMicPermissionDialog = false
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                ) {
                    Text(if (isBn) "অনুমতি দিন" else "Grant Permission")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showMicPermissionDialog = false
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Text(if (isBn) "অ্যাপ সেটিংস" else "App Settings")
                    }
                    TextButton(onClick = { showMicPermissionDialog = false }) {
                        Text(if (isBn) "পরে" else "Later")
                    }
                }
            }
        )
    }

    // Safety Confirmation Dialog
    if (pendingSafetyPrompt != null) {
        AlertDialog(
            onDismissRequest = onDismissSafety,
            title = {
                Text(if (isBn) "নিরাপত্তা নিশ্চিতকরণ" else "Safety Confirmation")
            },
            text = {
                Text(
                    if (isBn) {
                        "এই অনুরোধটিতে টাকা, পাসওয়ার্ড বা ডেটা মুছে ফেলার মতো সংবেদনশীল বিষয় রয়েছে। আপনি কি নিশ্চিতভাবে এগিয়ে যেতে চান?\n\n\"$pendingSafetyPrompt\""
                    } else {
                        "This request involves sensitive topics such as money, passwords, or deleting data. Are you sure you want to proceed?\n\n\"$pendingSafetyPrompt\""
                    }
                )
            },
            confirmButton = {
                Button(onClick = onConfirmSafety) {
                    Text(if (isBn) "হ্যাঁ, নিশ্চিত" else "Yes, Confirm")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissSafety) {
                    Text(if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChatBubbleItem(
    message: ChatMessage,
    settings: AppSettings,
    avatarRegion: ImageRegionRect,
    isBn: Boolean,
    onSpeak: () -> Unit,
    onCopy: () -> Unit,
    onSaveMemory: () -> Unit
) {
    val ext = LocalShailaColors.current
    val isUser = message.role == SenderRole.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            ShailaCharacterAvatar(
                homeCharacterStyle = if (settings.showHerEverywhere || settings.homeCharacterStyle != HomeCharacterStyle.CLASSIC_ORB) {
                    settings.homeCharacterStyle
                } else {
                    HomeCharacterStyle.CLASSIC_ORB
                },
                customCharacterUri = settings.customCharacterUri,
                fallbackAvatarRegion = avatarRegion,
                contentDescription = "${settings.assistantName} avatar",
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            modifier = Modifier.widthIn(max = 285.dp),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp
            ),
            color = if (isUser) ext.userBubble else ext.assistantBubble,
            border = BorderStroke(1.dp, ext.glassBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (ext.isDark || isUser) Color.White else MaterialTheme.colorScheme.onSurface
                )
                if (!isUser) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isBn) "আবার শুনুন" else "Speak",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = if (isBn) "কপি করুন" else "Copy",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        IconButton(onClick = onSaveMemory, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Filled.BookmarkAdd,
                                contentDescription = if (isBn) "স্মৃতিতে সেভ করুন" else "Save to Memory",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}
