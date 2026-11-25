package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.ApiKeyType
import com.example.data.local.ApiKeysSnapshot
import com.example.data.model.AccentColorPreset
import com.example.data.model.AppLanguage
import com.example.data.model.AppSettings
import com.example.data.model.DefaultImageRegions
import com.example.data.model.GeminiLiveVoice
import com.example.data.model.HomeCharacterStyle
import com.example.data.model.ImageRegionsConfig
import com.example.data.model.PersonalityPreset
import com.example.data.model.ResponseStyle
import com.example.data.model.ShailaCatalog
import com.example.data.model.SpeechLanguage
import com.example.data.model.VoiceProviderType
import com.example.data.remote.AppUpdateManager
import com.example.data.remote.DownloadProgressState
import com.example.ui.components.GlassCard
import com.example.ui.components.ShailaCharacterAvatar
import com.example.ui.theme.AccentVioletBright
import com.example.ui.theme.AccentVioletSecondary
import com.example.ui.theme.LocalShailaColors
import com.example.ui.viewmodel.ApkUpdateUiState
import com.example.ui.viewmodel.KeyTestStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    imageRegions: ImageRegionsConfig = DefaultImageRegions.defaultConfig,
    apiKeys: ApiKeysSnapshot,
    keyTestStatuses: Map<ApiKeyType, KeyTestStatus>,
    voicePreviewStatus: String?,
    onSaveApiKey: (ApiKeyType, String) -> Unit,
    onTestApiKey: (ApiKeyType, String) -> Unit,
    onSelectClaudeModel: (String) -> Unit,
    onSelectVoiceProvider: (VoiceProviderType) -> Unit,
    onSelectVoice: (VoiceProviderType, String) -> Unit,
    onPreviewVoice: (VoiceProviderType, String) -> Unit,
    onChangeVoiceSpeed: (Float) -> Unit,
    onChangeStability: (Float) -> Unit,
    onChangeStyle: (Float) -> Unit,
    onSelectAppLanguage: (AppLanguage) -> Unit,
    onSelectSpeechLanguage: (SpeechLanguage) -> Unit,
    onToggleContinuous: (Boolean) -> Unit,
    onToggleAutoSpeak: (Boolean) -> Unit,
    onToggleHoldToTalk: (Boolean) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onSelectAccent: (AccentColorPreset) -> Unit,
    onOpenMemory: () -> Unit,
    onOpenImageRegionDebug: () -> Unit = {},
    onClearChat: () -> Unit,
    onClearMemories: () -> Unit,
    apkUpdateState: ApkUpdateUiState = ApkUpdateUiState(),
    onSaveApkUpdateUrl: (String) -> Unit = {},
    onCheckLatestBuild: (String) -> Unit = {},
    onDownloadApk: (String) -> Unit = {},
    onCancelApkDownload: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    openApiKeysSignal: Int = 0,
    isLoadingElevenVoices: Boolean = false,
    onSelectGeminiVoice: (GeminiLiveVoice) -> Unit = {},
    onLoadMyElevenLabsVoices: () -> Unit = {},
    onAddCustomElevenLabsVoice: (String, String) -> Unit = { _, _ -> },
    onSetAssistantName: (String) -> Unit = {},
    onSelectPersonality: (PersonalityPreset) -> Unit = {},
    onSaveCustomPersonality: (String, String) -> Unit = { _, _ -> },
    onSelectResponseStyle: (ResponseStyle) -> Unit = {},
    onToggleListenOnAppOpen: (Boolean) -> Unit = {},
    onSelectHomeCharacterStyle: (HomeCharacterStyle) -> Unit = {},
    onSelectCustomCharacterUri: (String) -> Unit = {},
    onToggleShowHerEverywhere: (Boolean) -> Unit = {},
    onToggleReduceMotion: (Boolean) -> Unit = {},
    isAccessibilityEnabled: Boolean = false,
    onOpenAccessibilitySettings: () -> Unit = {}
) {
    val isBn = settings.appLanguage == AppLanguage.BENGALI
    val ext = LocalShailaColors.current

    var expandApiKeys by rememberSaveable { mutableStateOf(openApiKeysSignal > 0) }
    var expandVoice by rememberSaveable { mutableStateOf(false) }
    var expandLanguage by rememberSaveable { mutableStateOf(false) }
    var expandAppearance by rememberSaveable { mutableStateOf(false) }
    var expandPrivacy by rememberSaveable { mutableStateOf(false) }
    var expandDownloadApk by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openApiKeysSignal) {
        if (openApiKeysSignal > 0) {
            expandApiKeys = true
        }
    }

    var showClearChatConfirm by remember { mutableStateOf(false) }
    var showClearMemoriesConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header matching Mockup H
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(22.dp)
                )
                Text(
                    text = if (isBn) "Settings" else "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Top Profile Card matching Mockup H: avatarFace + Shaila (শাইলা) + AI Voice Assistant + Developed by Amir (আমির)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                contentPadding = 16.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShailaCharacterAvatar(
                        homeCharacterStyle = settings.homeCharacterStyle,
                        customCharacterUri = settings.customCharacterUri,
                        fallbackAvatarRegion = imageRegions.avatarFace,
                        contentDescription = "Shaila avatar",
                        modifier = Modifier.size(72.dp),
                        borderColor = AccentVioletSecondary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        val isDefaultShaila = settings.assistantName.equals("Shaila", ignoreCase = true) ||
                            settings.assistantName == "শাইলা"
                        Text(
                            text = if (isDefaultShaila) "Shaila (শাইলা)" else settings.assistantName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${settings.effectivePersonalityLabel(isBn)} • ${settings.geminiLiveVoice.voiceName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Developed by Amir (আমির)",
                            style = MaterialTheme.typography.labelLarge,
                            color = AccentVioletSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Settings Menu Card with Colored Round Icons matching Mockup H
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                contentPadding = 14.dp
            ) {
                // 1. API Keys (Connections with direct links)
                SettingsMenuRow(
                    icon = Icons.Filled.Key,
                    iconBg1 = Color(0xFF7C3AED),
                    iconBg2 = Color(0xFFA855F7),
                    title = "API Keys",
                    subtitle = if (isBn) {
                        "Gemini, Groq, ElevenLabs, Claude ও সরাসরি লিংক"
                    } else {
                        "Gemini, Groq, ElevenLabs, Claude + direct key links"
                    },
                    expanded = expandApiKeys,
                    onClick = { expandApiKeys = !expandApiKeys }
                )
                AnimatedVisibility(visible = expandApiKeys) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isBn) {
                                "আলাদা করে ওয়েবসাইট খুঁজতে হবে না—প্রতিটি কার্ডের নিচের লিংকে ট্যাপ করলেই সরাসরি ফ্রি বা অফিশিয়াল API Key পেজ খুলে যাবে।"
                            } else {
                                "No need to hunt for URLs—tap the link inside any card to open its official key page directly."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ConnectionApiKeyCard(
                            title = "Voice - Gemini key",
                            description = if (isBn) {
                                "লাইভ ভয়েস কল এবং মানুষের মতো বাস্তব ফিমেল কণ্ঠের জন্য (Google AI Studio থেকে সম্পূর্ণ ফ্রি)।"
                            } else {
                                "Powers real-time Live Voice call & natural human female voices (free at Google AI Studio)."
                            },
                            placeholder = "AIza...",
                            initialValue = apiKeys.geminiKey,
                            readyLabel = if (apiKeys.geminiKey.isNotBlank()) "Ready" else null,
                            linkLabel = if (isBn) "Google AI Studio থেকে ফ্রি কী নিন" else "Get a free key at Google AI Studio",
                            linkUrl = "https://aistudio.google.com/apikey",
                            status = keyTestStatuses[ApiKeyType.GEMINI],
                            testTagPrefix = "gemini",
                            isBn = isBn,
                            onSave = { onSaveApiKey(ApiKeyType.GEMINI, it) },
                            onTest = { onTestApiKey(ApiKeyType.GEMINI, it) }
                        )

                        val groqCount = apiKeys.groqKeysList().size
                        ConnectionApiKeyCard(
                            title = "Chat - Groq keys",
                            description = if (isBn) {
                                "দ্রুত ফ্রি চ্যাট ও উত্তর। কমা (,) দিয়ে একাধিক কী দিলে লিমিট শেষ হলে অটো বদলে নেবে।"
                            } else {
                                "Fast free typed/voice replies. Separate multiple keys with commas for auto rotation."
                            },
                            placeholder = "gsk_...",
                            initialValue = apiKeys.groqKey,
                            readyLabel = if (groqCount > 0) {
                                if (groqCount == 1) "1 key ready" else "$groqCount keys ready"
                            } else null,
                            linkLabel = if (isBn) "Groq থেকে ফ্রি কী নিন" else "Get a free key at Groq",
                            linkUrl = "https://console.groq.com/keys",
                            status = keyTestStatuses[ApiKeyType.GROQ],
                            testTagPrefix = "groq",
                            isBn = isBn,
                            onSave = { onSaveApiKey(ApiKeyType.GROQ, it) },
                            onTest = { onTestApiKey(ApiKeyType.GROQ, it) }
                        )

                        ConnectionApiKeyCard(
                            title = "Voice - ElevenLabs key (optional)",
                            description = if (isBn) {
                                "আপনার পছন্দের কাস্টম ফিমেল ভয়েস বা ElevenLabs-এর আসল কণ্ঠে শাইলাকে কথা বলাতে ব্যবহার করুন।"
                            } else {
                                "Use custom ElevenLabs female voices or your own cloned library voices."
                            },
                            placeholder = "xi-... / sk_...",
                            initialValue = apiKeys.elevenLabsKey,
                            readyLabel = if (apiKeys.elevenLabsKey.isNotBlank()) "Ready" else null,
                            linkLabel = if (isBn) "ElevenLabs থেকে কী নিন" else "Get a key at ElevenLabs",
                            linkUrl = "https://elevenlabs.io/app/settings/api-keys",
                            status = keyTestStatuses[ApiKeyType.ELEVENLABS],
                            testTagPrefix = "elevenlabs",
                            isBn = isBn,
                            onSave = { onSaveApiKey(ApiKeyType.ELEVENLABS, it) },
                            onTest = { onTestApiKey(ApiKeyType.ELEVENLABS, it) }
                        )

                        ConnectionApiKeyCard(
                            title = "Anthropic Claude Key (optional)",
                            description = if (isBn) {
                                "উচ্চমানের বাংলা লেখা, ভিডিও বিশ্লেষণ ও গভীর চ্যাটের জন্য ঐচ্ছিক ক্লড কী।"
                            } else {
                                "Optional Claude key for advanced Bengali writing and video/photo analysis."
                            },
                            placeholder = "sk-ant-...",
                            initialValue = apiKeys.claudeKey,
                            readyLabel = if (apiKeys.claudeKey.isNotBlank()) "Ready" else null,
                            linkLabel = if (isBn) "Anthropic Console থেকে কী নিন" else "Get a key at Anthropic Console",
                            linkUrl = "https://console.anthropic.com/settings/keys",
                            status = keyTestStatuses[ApiKeyType.CLAUDE],
                            testTagPrefix = "claude",
                            isBn = isBn,
                            onSave = { onSaveApiKey(ApiKeyType.CLAUDE, it) },
                            onTest = { onTestApiKey(ApiKeyType.CLAUDE, it) }
                        )

                        ConnectionApiKeyCard(
                            title = "OpenAI API Key (optional)",
                            description = if (isBn) {
                                "OpenAI Coral / Nova ফিমেল ভয়েসের জন্য ঐচ্ছিক কী।"
                            } else {
                                "Optional key for OpenAI Coral / Nova female TTS voices."
                            },
                            placeholder = "sk-...",
                            initialValue = apiKeys.openAiKey,
                            readyLabel = if (apiKeys.openAiKey.isNotBlank()) "Ready" else null,
                            linkLabel = if (isBn) "OpenAI Platform থেকে কী নিন" else "Get a key at OpenAI",
                            linkUrl = "https://platform.openai.com/api-keys",
                            status = keyTestStatuses[ApiKeyType.OPENAI],
                            testTagPrefix = "openai",
                            isBn = isBn,
                            onSave = { onSaveApiKey(ApiKeyType.OPENAI, it) },
                            onTest = { onTestApiKey(ApiKeyType.OPENAI, it) }
                        )

                        ConnectionApiKeyCard(
                            title = "xAI / Grok API Key (optional)",
                            description = if (isBn) {
                                "Grok / xAI Ara ফিমেল ভয়েসের জন্য ঐচ্ছিক কী।"
                            } else {
                                "Optional key for Grok / xAI Ara conversational voice."
                            },
                            placeholder = "xai-...",
                            initialValue = apiKeys.xaiKey,
                            readyLabel = if (apiKeys.xaiKey.isNotBlank()) "Ready" else null,
                            linkLabel = if (isBn) "xAI Console থেকে কী নিন" else "Get a key at xAI Console",
                            linkUrl = "https://console.xai.com/",
                            status = keyTestStatuses[ApiKeyType.XAI],
                            testTagPrefix = "xai",
                            isBn = isBn,
                            onSave = { onSaveApiKey(ApiKeyType.XAI, it) },
                            onTest = { onTestApiKey(ApiKeyType.XAI, it) }
                        )
                    }
                }

                HorizontalDivider(color = ext.glassBorder, modifier = Modifier.padding(vertical = 6.dp))

                // 2. Voice & Personality
                SettingsMenuRow(
                    icon = Icons.Filled.RecordVoiceOver,
                    iconBg1 = Color(0xFF6366F1),
                    iconBg2 = Color(0xFF8B5CF6),
                    title = if (isBn) "ভয়েস ও ব্যক্তিত্ব (Voice & Personality)" else "Voice & Personality",
                    subtitle = "${settings.assistantName} • ${settings.effectivePersonalityLabel(isBn)} • ${settings.geminiLiveVoice.voiceName}",
                    expanded = expandVoice,
                    onClick = { expandVoice = !expandVoice }
                )
                AnimatedVisibility(visible = expandVoice) {
                    VoiceAndPersonalitySection(
                        settings = settings,
                        isBn = isBn,
                        voicePreviewStatus = voicePreviewStatus,
                        isLoadingElevenVoices = isLoadingElevenVoices,
                        onSetAssistantName = onSetAssistantName,
                        onSelectPersonality = onSelectPersonality,
                        onSaveCustomPersonality = onSaveCustomPersonality,
                        onSelectResponseStyle = onSelectResponseStyle,
                        onToggleListenOnAppOpen = onToggleListenOnAppOpen,
                        onSelectGeminiVoice = onSelectGeminiVoice,
                        onSelectVoiceProvider = onSelectVoiceProvider,
                        onSelectVoice = onSelectVoice,
                        onPreviewVoice = onPreviewVoice,
                        onLoadMyElevenLabsVoices = onLoadMyElevenLabsVoices,
                        onAddCustomElevenLabsVoice = onAddCustomElevenLabsVoice,
                        onChangeVoiceSpeed = onChangeVoiceSpeed,
                        onChangeStability = onChangeStability,
                        onChangeStyle = onChangeStyle
                    )
                }

                HorizontalDivider(color = ext.glassBorder, modifier = Modifier.padding(vertical = 6.dp))

                // 3. Language & Claude Model
                SettingsMenuRow(
                    icon = Icons.Filled.Language,
                    iconBg1 = Color(0xFFEC4899),
                    iconBg2 = Color(0xFFF43F5E),
                    title = if (isBn) "Language" else "Language",
                    subtitle = if (settings.appLanguage == AppLanguage.BENGALI) "বাংলা (Bengali)" else "English",
                    expanded = expandLanguage,
                    onClick = { expandLanguage = !expandLanguage }
                )
                AnimatedVisibility(visible = expandLanguage) {
                    Column(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                        Text(
                            text = if (isBn) "অ্যাপের ভাষা" else "App Language",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = settings.appLanguage == lang,
                                    onClick = { onSelectAppLanguage(lang) },
                                    label = { Text(if (isBn) lang.displayBn else lang.displayEn) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isBn) "স্পিচ-টু-টেক্সট ভাষা" else "Speech Recognition Language",
                            style = MaterialTheme.typography.labelLarge
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SpeechLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = settings.speechLanguage == lang,
                                    onClick = { onSelectSpeechLanguage(lang) },
                                    label = { Text(if (isBn) lang.displayBn else lang.displayEn) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isBn) "Claude মডেল নির্বাচন" else "Claude Model",
                            style = MaterialTheme.typography.labelLarge
                        )
                        ShailaCatalog.claudeModels.forEach { model ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectClaudeModel(model.modelId) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.claudeModel == model.modelId,
                                    onClick = { onSelectClaudeModel(model.modelId) }
                                )
                                Column {
                                    Text(model.label, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        model.noteBn,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = ext.glassBorder, modifier = Modifier.padding(vertical = 6.dp))

                // 4. Look & Feel (Home Character, Show Everywhere, Reduce Motion, Theme & Accent)
                SettingsMenuRow(
                    icon = Icons.Filled.DarkMode,
                    iconBg1 = Color(0xFF3B82F6),
                    iconBg2 = Color(0xFF6366F1),
                    title = if (isBn) "লুক অ্যান্ড ফিল (Look & Feel)" else "Look & Feel",
                    subtitle = "${if (isBn) settings.homeCharacterStyle.titleBn else settings.homeCharacterStyle.titleEn} • ${if (settings.darkTheme) "Dark" else "Light"}",
                    expanded = expandAppearance,
                    onClick = { expandAppearance = !expandAppearance }
                )
                AnimatedVisibility(visible = expandAppearance) {
                    LookAndFeelSection(
                        settings = settings,
                        imageRegions = imageRegions,
                        isBn = isBn,
                        onToggleDarkTheme = onToggleDarkTheme,
                        onSelectAccent = onSelectAccent,
                        onSelectHomeCharacterStyle = onSelectHomeCharacterStyle,
                        onSelectCustomCharacterUri = onSelectCustomCharacterUri,
                        onToggleShowHerEverywhere = onToggleShowHerEverywhere,
                        onToggleReduceMotion = onToggleReduceMotion
                    )
                }

                HorizontalDivider(color = ext.glassBorder, modifier = Modifier.padding(vertical = 6.dp))

                // 5. Continuous Conversation toggle
                SettingsSwitchMenuRow(
                    icon = Icons.Filled.GraphicEq,
                    iconBg1 = Color(0xFF06B6D4),
                    iconBg2 = Color(0xFF3B82F6),
                    title = "Continuous Conversation",
                    subtitle = if (isBn) "উত্তর বলা শেষ হলেই আবার শুনবে" else "Auto-listen after Shaila finishes speaking",
                    checked = settings.continuousConversation,
                    onCheckedChange = onToggleContinuous
                )

                HorizontalDivider(color = ext.glassBorder, modifier = Modifier.padding(vertical = 6.dp))

                // 6. Auto-Speak Replies toggle
                SettingsSwitchMenuRow(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    iconBg1 = Color(0xFF0EA5E9),
                    iconBg2 = Color(0xFF38BDF8),
                    title = "Auto-Speak Replies",
                    subtitle = if (isBn) "শাইলা প্রতিটি উত্তর মুখে বলবে" else "Speak replies aloud automatically",
                    checked = settings.autoSpeakReplies,
                    onCheckedChange = onToggleAutoSpeak
                )

                HorizontalDivider(color = ext.glassBorder, modifier = Modifier.padding(vertical = 6.dp))

                // 7. Hold to Talk toggle
                SettingsSwitchMenuRow(
                    icon = Icons.Filled.PanTool,
                    iconBg1 = Color(0xFF8B5CF6),
                    iconBg2 = Color(0xFFC084FC),
                    title = "Hold to Talk",
                    subtitle = if (isBn) "মাইক বাটন চেপে ধরে কথা বলুন" else "Press and hold mic button to talk",
                    checked = settings.holdToTalkMode,
                    onCheckedChange = onToggleHoldToTalk
                )

                HorizontalDivider(color = ext.glassBorder, modifier = Modifier.padding(vertical = 6.dp))

                // 8. Phone & App Control (Accessibility)
                SettingsMenuRow(
                    icon = Icons.Filled.TouchApp,
                    iconBg1 = if (isAccessibilityEnabled) Color(0xFF10B981) else Color(0xFFF59E0B),
                    iconBg2 = if (isAccessibilityEnabled) Color(0xFF059669) else Color(0xFFEF4444),
                    title = if (isBn) "ফোন ও অ্যাপ কন্ট্রোল (Accessibility)" else "Phone & App Control",
                    subtitle = if (isAccessibilityEnabled) {
                        if (isBn) "চালু আছে • যেকোনো অ্যাপে টাইপ, ট্যাপ ও কাজ করবে" else "Enabled • Can type, tap & control any app"
                    } else {
                        if (isBn) "বন্ধ আছে • অ্যাপের ভিতরে টাইপ ও বাটন ট্যাপ করতে চালু করুন" else "Tap to enable Accessibility for typing & tapping inside apps"
                    },
                    expanded = false,
                    onClick = onOpenAccessibilitySettings,
                    modifier = Modifier.testTag("phone_control_accessibility_row")
                )

                HorizontalDivider(color = ext.glassBorder, modifier = Modifier.padding(vertical = 6.dp))

                // 9. Memory
                SettingsMenuRow(
                    icon = Icons.Filled.Psychology,
                    iconBg1 = Color(0xFFF97316),
                    iconBg2 = Color(0xFFFB923C),
                    title = "Memory",
                    subtitle = if (isBn) "Manage your memories (স্মৃতি পরিচালনা)" else "Manage your memories",
                    expanded = false,
                    onClick = onOpenMemory
                )

                HorizontalDivider(color = ext.glassBorder, modifier = Modifier.padding(vertical = 6.dp))

                // 9. Privacy
                SettingsMenuRow(
                    icon = Icons.Filled.Lock,
                    iconBg1 = Color(0xFF22C55E),
                    iconBg2 = Color(0xFF16A34A),
                    title = "Privacy",
                    subtitle = if (isBn) "Clear history & data" else "Clear history & data",
                    expanded = expandPrivacy,
                    onClick = { expandPrivacy = !expandPrivacy }
                )
                AnimatedVisibility(visible = expandPrivacy) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showClearChatConfirm = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Filled.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isBn) "চ্যাট মুছুন" else "Clear Chat", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showClearMemoriesConfirm = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Filled.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isBn) "স্মৃতি মুছুন" else "Clear Memory", maxLines = 1)
                        }
                    }
                }

                HorizontalDivider(color = ext.glassBorder, modifier = Modifier.padding(vertical = 6.dp))

                // 10. Download APK
                val buildInfo = apkUpdateState.buildInfo
                val downloadSubtitle = when {
                    apkUpdateState.downloadState is DownloadProgressState.Downloading -> {
                        val p = apkUpdateState.downloadState.progressPercent
                        if (isBn) "ডাউনলোড হচ্ছে ($p%)..." else "Downloading ($p%)..."
                    }
                    buildInfo.isUpdateAvailable && buildInfo.latestVersionName != null -> {
                        if (isBn) "নতুন ভার্সন v${buildInfo.latestVersionName} পাওয়া গেছে" else "New build v${buildInfo.latestVersionName} available"
                    }
                    else -> {
                        if (isBn) {
                            "বর্তমান বিল্ড v${buildInfo.currentVersionName} (${buildInfo.currentVersionCode})"
                        } else {
                            "Current build v${buildInfo.currentVersionName} (${buildInfo.currentVersionCode})"
                        }
                    }
                }
                SettingsMenuRow(
                    icon = Icons.Filled.Download,
                    iconBg1 = Color(0xFF0EA5E9),
                    iconBg2 = Color(0xFF6366F1),
                    title = "Download APK",
                    subtitle = downloadSubtitle,
                    expanded = expandDownloadApk,
                    onClick = { expandDownloadApk = !expandDownloadApk },
                    modifier = Modifier.testTag("download_apk_menu_row")
                )
                AnimatedVisibility(visible = expandDownloadApk) {
                    DownloadApkPanel(
                        isBn = isBn,
                        savedUrl = settings.apkUpdateUrl,
                        apkUpdateState = apkUpdateState,
                        onSaveUrl = onSaveApkUpdateUrl,
                        onCheckLatest = onCheckLatestBuild,
                        onDownloadApk = onDownloadApk,
                        onCancelDownload = onCancelApkDownload,
                        onOpenDownloads = onOpenDownloads
                    )
                }
            }

            // About Section (5 taps on version opens hidden Image Region Debug page)
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
                var versionTapCount by remember { mutableStateOf(0) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBn) "অ্যাপ সম্পর্কে (About)" else "About",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Shaila (শাইলা) • ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "v1.0",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentVioletBright,
                        modifier = Modifier
                            .testTag("about_version_tap")
                            .clickable {
                                versionTapCount++
                                if (versionTapCount >= 5) {
                                    versionTapCount = 0
                                    onOpenImageRegionDebug()
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Shaila is an AI-generated character. Developed by Amir (আমির).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ext.orbSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }

    if (showClearChatConfirm) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirm = false },
            title = { Text(if (isBn) "চ্যাট ইতিহাস মুছবেন?" else "Clear Chat History?") },
            text = {
                Text(if (isBn) "সব কথোপকথন স্থায়ীভাবে মুছে যাবে। নিশ্চিত?" else "All conversation messages will be deleted permanently.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearChatConfirm = false
                        onClearChat()
                    }
                ) { Text(if (isBn) "হ্যাঁ, মুছুন" else "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatConfirm = false }) {
                    Text(if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }

    if (showClearMemoriesConfirm) {
        AlertDialog(
            onDismissRequest = { showClearMemoriesConfirm = false },
            title = { Text(if (isBn) "সব স্মৃতি মুছবেন?" else "Clear All Memories?") },
            text = {
                Text(if (isBn) "শাইলার সংরক্ষিত সব স্মৃতি স্থায়ীভাবে মুছে যাবে। নিশ্চিত?" else "All saved facts in Memory will be permanently deleted.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearMemoriesConfirm = false
                        onClearMemories()
                    }
                ) { Text(if (isBn) "হ্যাঁ, মুছুন" else "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showClearMemoriesConfirm = false }) {
                    Text(if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
    iconBg1: Color,
    iconBg2: Color,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(iconBg1, iconBg2))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSwitchMenuRow(
    icon: ImageVector,
    iconBg1: Color,
    iconBg2: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(iconBg1, iconBg2))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ConnectionApiKeyCard(
    title: String,
    description: String,
    placeholder: String,
    initialValue: String,
    readyLabel: String?,
    linkLabel: String,
    linkUrl: String,
    status: KeyTestStatus?,
    testTagPrefix: String,
    isBn: Boolean,
    onSave: (String) -> Unit,
    onTest: (String) -> Unit
) {
    var value by rememberSaveable(title, initialValue) { mutableStateOf(initialValue) }
    var visible by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val ext = LocalShailaColors.current

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ext.glassFill,
        border = BorderStroke(1.dp, ext.glassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val isReady = readyLabel != null
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isReady) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (isReady) ext.success else Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = readyLabel ?: (if (isBn) "কী যোগ করুন" else "Add a key"),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isReady) ext.success else Color(0xFFF59E0B),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    onSave(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("key_input_$testTagPrefix"),
                placeholder = { Text(placeholder) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (visible) "Hide key" else "Show key"
                        )
                    }
                }
            )

            Button(
                onClick = {
                    onSave(value)
                    if (value.isNotBlank()) onTest(value)
                },
                enabled = status?.testing != true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("test_key_$testTagPrefix")
            ) {
                if (status?.testing == true) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    if (isBn) "সেভ ও যাচাই করুন (Save & Test)" else "Save & Test Key",
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (status != null && status.message.isNotBlank()) {
                Text(
                    text = status.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (status.success) {
                        true -> ext.success
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        runCatching { uriHandler.openUri(linkUrl) }
                    }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
                    .testTag("key_link_$testTagPrefix"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = linkLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentVioletBright,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = linkLabel,
                    tint = AccentVioletBright,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DownloadApkPanel(
    isBn: Boolean,
    savedUrl: String,
    apkUpdateState: ApkUpdateUiState,
    onSaveUrl: (String) -> Unit,
    onCheckLatest: (String) -> Unit,
    onDownloadApk: (String) -> Unit,
    onCancelDownload: () -> Unit,
    onOpenDownloads: () -> Unit
) {
    val context = LocalContext.current
    var urlInput by rememberSaveable(savedUrl) { mutableStateOf(savedUrl) }
    val buildInfo = apkUpdateState.buildInfo
    val ext = LocalShailaColors.current

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onDownloadApk(urlInput)
    }

    fun triggerDownloadWithNotificationCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        onDownloadApk(urlInput)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBn) "ইনস্টলড ভার্সন" else "Installed Build",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "v${buildInfo.currentVersionName} (Build ${buildInfo.currentVersionCode})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("current_apk_version_text")
                    )
                }

                if (apkUpdateState.lastCheckedAtMillis != null && buildInfo.latestVersionName != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBn) "সর্বশেষ ভার্সন" else "Latest Build",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val sizeSuffix = AppUpdateManager.formatFileSize(buildInfo.fileSizeBytes)
                            ?.let { " • $it" }
                            .orEmpty()
                        Text(
                            text = "v${buildInfo.latestVersionName} (Build ${buildInfo.latestVersionCode ?: buildInfo.currentVersionCode})$sizeSuffix",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (buildInfo.isUpdateAvailable) AccentVioletBright else ext.success,
                            modifier = Modifier.testTag("latest_apk_version_text")
                        )
                    }

                    Text(
                        text = if (buildInfo.isUpdateAvailable) {
                            if (isBn) "নতুন আপডেট ডাউনলোডের জন্য প্রস্তুত।" else "A newer build is ready to download."
                        } else {
                            if (isBn) "আপনি সর্বশেষ পরিচিত বিল্ড ব্যবহার করছেন।" else "You are on the latest checked build."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (buildInfo.isUpdateAvailable) AccentVioletBright else ext.success
                    )

                    if (!buildInfo.releaseNotes.isNullOrBlank()) {
                        Text(
                            text = buildInfo.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = urlInput,
            onValueChange = {
                urlInput = it
                onSaveUrl(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apk_update_url_input"),
            label = {
                Text(if (isBn) "APK বা Release JSON URL" else "APK or Release JSON URL")
            },
            placeholder = {
                Text("https://.../releases/latest or .../Shaila.apk")
            },
            supportingText = {
                Text(
                    if (isBn) {
                        "সরাসরি .apk লিংক অথবা GitHub Releases / release.json লিংক দিন। DownloadManager দিয়ে ডাউনলোড ও স্ট্যাটাস নোটিফিকেশন চলবে।"
                    } else {
                        "Enter a direct .apk URL or GitHub Releases / release.json URL. Uses Android DownloadManager with status notifications."
                    }
                )
            },
            singleLine = true
        )

        if (!apkUpdateState.checkError.isNullOrBlank()) {
            Text(
                text = apkUpdateState.checkError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { onCheckLatest(urlInput) },
                enabled = urlInput.isNotBlank() && !apkUpdateState.isChecking,
                modifier = Modifier
                    .weight(1f)
                    .testTag("check_apk_version_button")
            ) {
                if (apkUpdateState.isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isBn) "ভার্সন চেক" else "Check Version",
                    maxLines = 1
                )
            }

            Button(
                onClick = { triggerDownloadWithNotificationCheck() },
                enabled = (urlInput.isNotBlank() || !buildInfo.resolvedApkUrl.isNullOrBlank()) &&
                    !apkUpdateState.isChecking &&
                    apkUpdateState.downloadState !is DownloadProgressState.Downloading,
                modifier = Modifier
                    .weight(1f)
                    .testTag("download_apk_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isBn) "APK ডাউনলোড" else "Download APK",
                    maxLines = 1
                )
            }
        }

        when (val dlState = apkUpdateState.downloadState) {
            DownloadProgressState.Idle -> Unit
            is DownloadProgressState.Downloading -> {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                val statusTitle = when {
                                    dlState.isPending -> if (isBn) "ডাউনলোড অপেক্ষমাণ..." else "Download queued..."
                                    dlState.isPaused -> if (isBn) "ডাউনলোড বিরতিতে আছে" else "Download paused"
                                    else -> if (isBn) "APK ডাউনলোড হচ্ছে..." else "Downloading APK..."
                                }
                                Text(
                                    text = statusTitle,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            TextButton(
                                onClick = onCancelDownload,
                                modifier = Modifier.testTag("cancel_apk_download_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = if (isBn) "বাতিল" else "Cancel",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(if (isBn) "বাতিল" else "Cancel")
                            }
                        }
                        if (dlState.totalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = { (dlState.progressPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("apk_download_progress_bar")
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("apk_download_progress_bar")
                            )
                        }
                        val dlSize = AppUpdateManager.formatFileSize(dlState.bytesDownloaded) ?: "0 KB"
                        val totalSize = AppUpdateManager.formatFileSize(dlState.totalBytes)
                        val progressLine = if (totalSize != null) {
                            "${dlState.progressPercent}% ($dlSize / $totalSize)"
                        } else {
                            dlSize
                        }
                        Text(
                            text = progressLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is DownloadProgressState.Completed -> {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = ext.success.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isBn) {
                                "ডাউনলোড সম্পন্ন হয়েছে: ${dlState.fileName}"
                            } else {
                                "Download complete: ${dlState.fileName}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.success,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedButton(
                            onClick = onOpenDownloads,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_downloads_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isBn) "Downloads ফোল্ডার খুলুন" else "Open Downloads")
                        }
                    }
                }
            }
            is DownloadProgressState.Failed -> {
                Text(
                    text = dlState.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
