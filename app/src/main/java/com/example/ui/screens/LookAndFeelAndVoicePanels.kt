package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.MotionPhotosOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AccentColorPreset
import com.example.data.model.AppSettings
import com.example.data.model.GeminiLiveVoice
import com.example.data.model.HomeCharacterStyle
import com.example.data.model.ImageRegionsConfig
import com.example.data.model.PersonalityPreset
import com.example.data.model.ResponseStyle
import com.example.data.model.ShailaCatalog
import com.example.data.model.VoiceProviderType
import com.example.ui.components.CroppedImage
import com.example.ui.components.GlassCard
import com.example.ui.theme.AccentVioletBright
import com.example.ui.theme.AccentVioletPrimary
import com.example.ui.theme.AccentVioletSecondary
import com.example.ui.theme.LocalShailaColors
import com.example.ui.theme.accentPair

@Composable
fun LookAndFeelSection(
    settings: AppSettings,
    imageRegions: ImageRegionsConfig,
    isBn: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onSelectAccent: (AccentColorPreset) -> Unit,
    onSelectHomeCharacterStyle: (HomeCharacterStyle) -> Unit,
    onSelectCustomCharacterUri: (String) -> Unit,
    onToggleShowHerEverywhere: (Boolean) -> Unit,
    onToggleReduceMotion: (Boolean) -> Unit
) {
    val ext = LocalShailaColors.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSelectCustomCharacterUri(uri.toString())
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1: Home Character & Visual Presence (flat cards, no nested popups)
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, contentPadding = 16.dp) {
            Text(
                text = if (isBn) "হোম ও লাইভ কল ক্যারেক্টার" else "Home & Live Call Character",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isBn) {
                    "হোম ও লাইভ কলে যাকে দেখতে চান সরাসরি এখান থেকেই বাছাই করুন।"
                } else {
                    "Pick how your companion appears on Home and Live Call."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val portraitSelected = settings.homeCharacterStyle == HomeCharacterStyle.HALF_BODY_SHAILA
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelectHomeCharacterStyle(HomeCharacterStyle.HALF_BODY_SHAILA) }
                        .testTag("char_option_portrait"),
                    shape = RoundedCornerShape(18.dp),
                    color = if (portraitSelected) AccentVioletPrimary.copy(alpha = 0.24f) else ext.glassFill,
                    border = BorderStroke(
                        if (portraitSelected) 2.dp else 1.dp,
                        if (portraitSelected) AccentVioletBright else ext.glassBorder
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.shaila_character_portrait),
                            contentDescription = "Shaila Portrait",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(132.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isBn) "শাইলা পোর্ট্রেট" else "Shaila Portrait",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (portraitSelected) AccentVioletBright else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isBn) "অর্ধেক বডি" else "Half-body",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val orbSelected = settings.homeCharacterStyle == HomeCharacterStyle.CLASSIC_ORB
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelectHomeCharacterStyle(HomeCharacterStyle.CLASSIC_ORB) }
                        .testTag("char_option_orb"),
                    shape = RoundedCornerShape(18.dp),
                    color = if (orbSelected) AccentVioletPrimary.copy(alpha = 0.24f) else ext.glassFill,
                    border = BorderStroke(
                        if (orbSelected) 2.dp else 1.dp,
                        if (orbSelected) AccentVioletBright else ext.glassBorder
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(132.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF140D2B)),
                            contentAlignment = Alignment.Center
                        ) {
                            CroppedImage(
                                region = imageRegions.avatarFace,
                                contentDescription = "Classic Orb",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, AccentVioletSecondary, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isBn) "ক্লাসিক অবতার" else "Classic Orb",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (orbSelected) AccentVioletBright else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isBn) "নিয়ন রিং" else "Glowing ring",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val customSelected = settings.homeCharacterStyle == HomeCharacterStyle.CUSTOM_GALLERY
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .testTag("char_option_gallery"),
                shape = RoundedCornerShape(16.dp),
                color = if (customSelected) AccentVioletPrimary.copy(alpha = 0.22f) else ext.glassFill,
                border = BorderStroke(
                    if (customSelected) 1.5.dp else 1.dp,
                    if (customSelected) AccentVioletBright else ext.glassBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (settings.customCharacterUri.isNotBlank()) {
                        AsyncImage(
                            model = Uri.parse(settings.customCharacterUri),
                            contentDescription = "Custom photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentVioletPrimary.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddPhotoAlternate,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBn) "গ্যালারি থেকে নিজের ছবি দিন" else "Pick Custom Gallery Photo",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (customSelected && settings.customCharacterUri.isNotBlank()) {
                                if (isBn) "কাস্টম ছবি সক্রিয় — বদলাতে ট্যাপ করুন" else "Active — tap to choose another"
                            } else {
                                if (isBn) "ফোন থেকে যেকোনো পোর্ট্রেট ছবি বাছাই করুন" else "Choose any portrait photo from your phone"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = ext.glassBorder)
            Spacer(modifier = Modifier.height(6.dp))

            PanelSwitchRow(
                icon = Icons.Filled.Visibility,
                title = if (isBn) "সব জায়গায় শাইলাকে দেখান" else "Show Her Everywhere",
                subtitle = if (isBn) {
                    "মাইক বাটন ও মিনি কলেও তার ছবি দেখাবে"
                } else {
                    "Show her portrait inside the mic button & mini pill"
                },
                checked = settings.showHerEverywhere,
                modifier = Modifier.testTag("show_her_everywhere_switch"),
                onCheckedChange = onToggleShowHerEverywhere
            )

            PanelSwitchRow(
                icon = Icons.Filled.MotionPhotosOff,
                title = if (isBn) "নড়াচড়া কমান (Reduce Motion)" else "Reduce Motion",
                subtitle = if (isBn) {
                    "হাত, মাথা ও শরীরের নড়াচড়া শান্ত রাখুন"
                } else {
                    "Keep body, head, and gesture motion gentle and minimal"
                },
                checked = settings.reduceMotion,
                modifier = Modifier.testTag("reduce_motion_switch"),
                onCheckedChange = onToggleReduceMotion
            )
        }

        // Card 2: Theme & Accent Color
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, contentPadding = 16.dp) {
            Text(
                text = if (isBn) "থিম ও রঙ (Theme & Color)" else "Theme & Color",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            PanelSwitchRow(
                icon = Icons.Filled.DarkMode,
                title = if (isBn) "ডার্ক মোড (Dark Theme)" else "Dark Theme",
                subtitle = if (isBn) "ডিপ নেভি-টু-ভায়োলেট গ্রেডিয়েন্ট" else "Deep navy-to-violet gradient",
                checked = settings.darkTheme,
                onCheckedChange = onToggleDarkTheme
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isBn) "অ্যাকসেন্ট রঙ (Accent Color)" else "Accent Color",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentColorPreset.entries.forEach { preset ->
                    val (c1, c2) = accentPair(preset)
                    val isSelected = settings.accentPreset == preset
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onSelectAccent(preset) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(c1, c2)))
                                .then(
                                    if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = preset.labelEn,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceAndPersonalitySection(
    settings: AppSettings,
    isBn: Boolean,
    voicePreviewStatus: String?,
    isLoadingElevenVoices: Boolean,
    onSetAssistantName: (String) -> Unit,
    onSelectPersonality: (PersonalityPreset) -> Unit,
    onSaveCustomPersonality: (String, String) -> Unit,
    onSelectResponseStyle: (ResponseStyle) -> Unit,
    onToggleListenOnAppOpen: (Boolean) -> Unit,
    onSelectGeminiVoice: (GeminiLiveVoice) -> Unit,
    onSelectVoiceProvider: (VoiceProviderType) -> Unit,
    onSelectVoice: (VoiceProviderType, String) -> Unit,
    onPreviewVoice: (VoiceProviderType, String) -> Unit,
    onLoadMyElevenLabsVoices: () -> Unit,
    onAddCustomElevenLabsVoice: (String, String) -> Unit,
    onChangeVoiceSpeed: (Float) -> Unit,
    onChangeStability: (Float) -> Unit,
    onChangeStyle: (Float) -> Unit
) {
    val ext = LocalShailaColors.current
    var nameInput by rememberSaveable(settings.assistantName) { mutableStateOf(settings.assistantName) }
    var showCustomEditor by remember(settings.personalityPreset) {
        mutableStateOf(settings.personalityPreset == PersonalityPreset.CUSTOM)
    }
    var customName by rememberSaveable(settings.customPersonalityName) {
        mutableStateOf(settings.customPersonalityName)
    }
    var customPrompt by rememberSaveable(settings.customPersonalityPrompt) {
        mutableStateOf(settings.customPersonalityPrompt)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1: Assistant Name & Response Style
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, contentPadding = 16.dp) {
            Text(
                text = if (isBn) "নাম ও কথা বলার ধরন" else "Name & Conversation Style",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    label = { Text(if (isBn) "সহকারীর নাম" else "Assistant Name") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("assistant_name_input")
                )
                FilledTonalButton(
                    onClick = { onSetAssistantName(nameInput) },
                    enabled = nameInput.isNotBlank() && nameInput.trim() != settings.assistantName,
                    modifier = Modifier.height(52.dp)
                ) {
                    Text(if (isBn) "সেভ" else "Save")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isBn) "উত্তরের দৈর্ঘ্য ও ধরন (Response Style)" else "Response Style",
                style = MaterialTheme.typography.labelLarge,
                color = AccentVioletSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResponseStyle.entries.forEach { style ->
                    FilterChip(
                        selected = settings.responseStyle == style,
                        onClick = { onSelectResponseStyle(style) },
                        label = { Text(if (isBn) style.titleBn else style.titleEn) }
                    )
                }
            }
        }

        // Card 2: Inline Personality Picker (zero nested popups!)
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, contentPadding = 16.dp) {
            Text(
                text = if (isBn) "ব্যক্তিত্ব ও মেজাজ (Personality)" else "Personality",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isBn) "শাইলা কোন মেজাজে কথা বলবে সরাসরি নির্বাচন করুন:" else "Choose directly how Shaila speaks with you:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PersonalityPreset.entries.forEach { preset ->
                    val selected = settings.personalityPreset == preset
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (preset == PersonalityPreset.CUSTOM) {
                                    showCustomEditor = true
                                } else {
                                    showCustomEditor = false
                                    onSelectPersonality(preset)
                                }
                            }
                            .testTag("personality_${preset.id}"),
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) AccentVioletPrimary.copy(alpha = 0.22f) else ext.glassFill,
                        border = BorderStroke(
                            if (selected) 1.5.dp else 1.dp,
                            if (selected) AccentVioletBright else ext.glassBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isBn) preset.titleBn else preset.titleEn,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) AccentVioletBright else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isBn) preset.descBn else preset.descEn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = AccentVioletBright,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                if (showCustomEditor) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        singleLine = true,
                        label = { Text(if (isBn) "মেজাজের নাম" else "Style Name") },
                        placeholder = { Text(if (isBn) "যেমন: মিষ্টি ও কেয়ারিং" else "e.g. Sweet & caring") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customPrompt,
                        onValueChange = { customPrompt = it },
                        minLines = 2,
                        maxLines = 4,
                        label = { Text(if (isBn) "সে কীভাবে কথা বলবে" else "How she should talk") },
                        placeholder = {
                            Text(
                                if (isBn) "যেমন: সব সময় আদুরে, আন্তরিক ও হাসিখুশিভাবে কথা বলো..."
                                else "e.g. Always speak affectionately, gently, and enthusiastically..."
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_personality_prompt_input")
                    )
                    Button(
                        onClick = {
                            onSaveCustomPersonality(
                                customName.ifBlank { if (isBn) "কাস্টম মেজাজ" else "Custom" },
                                customPrompt
                            )
                        },
                        enabled = customPrompt.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isBn) "কাস্টম ব্যক্তিত্ব চালু করুন" else "Apply Custom Personality")
                    }
                }
            }
        }

        // Card 3: Gemini Live Natural Voice
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, contentPadding = 16.dp) {
            Text(
                text = if (isBn) "Gemini Live কণ্ঠ (লাইভ কল ও আসল নারী কণ্ঠ)" else "Gemini Live Voice (Natural Female)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isBn) {
                    "লাইভ কল ও হোম-চ্যাটের উত্তরে এই স্বাভাবিক নারী কণ্ঠে কথা বলবে (Gemini Key থাকলেই চলবে)।"
                } else {
                    "Used for Live Calls and natural replies whenever a Gemini API key is saved."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            GeminiLiveVoice.entries.forEach { gVoice ->
                val isSelected = settings.geminiLiveVoice == gVoice
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onSelectGeminiVoice(gVoice) }
                        .testTag("gemini_voice_${gVoice.voiceName}")
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectGeminiVoice(gVoice) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBn) gVoice.titleBn else gVoice.titleEn,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (isBn) gVoice.descriptionBn else gVoice.descriptionEn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Card 4: Chat & Read-Aloud TTS Voice Provider & Speed
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, contentPadding = 16.dp) {
            Text(
                text = if (isBn) "চ্যাট ও রিড-আলাউড ভয়েস (TTS)" else "Chat & Read-Aloud Voice (TTS)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            VoiceProviderType.entries.forEach { provider ->
                val selected = settings.voiceProvider == provider
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onSelectVoiceProvider(provider) }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onSelectVoiceProvider(provider) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBn) provider.titleBn else provider.titleEn,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = provider.subtitleBn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isBn) "ভয়েস তালিকা (${settings.voiceProvider.titleEn})" else "Voices (${settings.voiceProvider.titleEn})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            if (voicePreviewStatus != null) {
                Text(
                    text = voicePreviewStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val voices = if (settings.voiceProvider == VoiceProviderType.ELEVENLABS) {
                settings.allElevenLabsVoices()
            } else {
                ShailaCatalog.voicesFor(settings.voiceProvider)
            }
            val selectedVoiceId = settings.currentVoiceId()
            voices.forEach { voice ->
                val isSelected = voice.voiceId == selectedVoiceId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onSelectVoice(settings.voiceProvider, voice.voiceId) }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectVoice(settings.voiceProvider, voice.voiceId) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (voice.isCustom) "${voice.name} ★" else voice.name,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (isBn) voice.descriptionBn else voice.descriptionEn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { onPreviewVoice(settings.voiceProvider, voice.voiceId) },
                        modifier = Modifier.testTag("preview_voice_${voice.voiceId}")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isBn) "শুনে দেখো" else "Preview")
                    }
                }
            }

            if (settings.voiceProvider == VoiceProviderType.ELEVENLABS) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onLoadMyElevenLabsVoices,
                    enabled = !isLoadingElevenVoices,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("load_elevenlabs_voices_button")
                ) {
                    if (isLoadingElevenVoices) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(if (isBn) "আমার ElevenLabs ভয়েস লোড করুন" else "Load My ElevenLabs Voices")
                }

                var customId by rememberSaveable { mutableStateOf("") }
                var customName by rememberSaveable { mutableStateOf("") }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ext.glassFill,
                    border = BorderStroke(1.dp, ext.glassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isBn) "যেকোনো সুন্দর ফিমেল Voice ID বসান" else "Paste Any Custom Female Voice ID",
                            style = MaterialTheme.typography.labelLarge
                        )
                        OutlinedTextField(
                            value = customId,
                            onValueChange = { customId = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_voice_id_input"),
                            label = { Text("Voice ID") },
                            placeholder = { Text("e.g. EXAVITQu4vr4xnSDxMaL") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (isBn) "ভয়েসের নাম (ঐচ্ছিক)" else "Voice Name (optional)") },
                            placeholder = { Text(if (isBn) "যেমন: মিষ্টি ফিমেল কণ্ঠ" else "e.g. Sweet Bengali Female") },
                            singleLine = true
                        )
                        FilledTonalButton(
                            onClick = {
                                if (customId.isNotBlank()) {
                                    onAddCustomElevenLabsVoice(customId, customName)
                                    customId = ""
                                    customName = ""
                                }
                            },
                            enabled = customId.isNotBlank(),
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("save_custom_voice_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isBn) "ভয়েস যোগ করুন" else "Add Voice")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (isBn) "কথা বলার গতি (Speed): ${"%.2fx".format(settings.voiceSpeed)}"
                else "Speech Speed: ${"%.2fx".format(settings.voiceSpeed)}",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = settings.voiceSpeed,
                onValueChange = onChangeVoiceSpeed,
                valueRange = 0.7f..1.5f
            )

            if (settings.voiceProvider == VoiceProviderType.ELEVENLABS) {
                Text(
                    text = "Stability: ${"%.2f".format(settings.elevenLabsStability)}",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = settings.elevenLabsStability,
                    onValueChange = onChangeStability,
                    valueRange = 0f..1f
                )
                Text(
                    text = "Style: ${"%.2f".format(settings.elevenLabsStyle)}",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = settings.elevenLabsStyle,
                    onValueChange = onChangeStyle,
                    valueRange = 0f..1f
                )
            }
        }
    }
}

@Composable
private fun PanelSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentVioletSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
