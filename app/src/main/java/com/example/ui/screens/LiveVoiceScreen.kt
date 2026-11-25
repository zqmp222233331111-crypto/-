package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppLanguage
import com.example.data.model.AppSettings
import com.example.data.model.HomeCharacterStyle
import com.example.data.model.ImageRegionRect
import com.example.data.remote.LiveCallSessionState
import com.example.data.remote.LiveCallStatus
import com.example.ui.components.AnimatedVoiceOrb
import com.example.ui.components.GlassCard
import com.example.ui.components.LivingHalfBodyCharacter
import com.example.ui.components.ShailaCharacterAvatar
import com.example.ui.components.ShailaCosmicBackdrop
import com.example.ui.components.SoundWaveBar
import com.example.ui.theme.AccentVioletBright
import com.example.ui.theme.AccentVioletPrimary
import com.example.ui.theme.AccentVioletSecondary
import com.example.ui.theme.LocalShailaColors
import com.example.ui.theme.ShailaScriptFont
import com.example.ui.viewmodel.OrbState

private val CoralEndCall = Color(0xFFE55365)

@Composable
fun LiveVoiceScreen(
    liveState: LiveCallSessionState,
    settings: AppSettings,
    avatarRegion: ImageRegionRect,
    speakingLevel: Float,
    micLevel: Float,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit = {},
    onMinimize: () -> Unit,
    onEndCall: () -> Unit,
    onRetry: () -> Unit,
    onOpenApiKeys: () -> Unit
) {
    val isBn = settings.appLanguage == AppLanguage.BENGALI
    val ext = LocalShailaColors.current
    val isDefaultShaila = settings.assistantName.equals("Shaila", ignoreCase = true) ||
        settings.assistantName == "শাইলা"
    val bnName = if (isDefaultShaila) "শাইলা" else settings.assistantName

    BackHandler {
        if (liveState.status == LiveCallStatus.ERROR) {
            onEndCall()
        } else {
            onMinimize()
        }
    }

    val mappedOrbState = when (liveState.status) {
        LiveCallStatus.CONNECTING -> OrbState.THINKING
        LiveCallStatus.LISTENING -> OrbState.LISTENING
        LiveCallStatus.SPEAKING -> OrbState.SPEAKING
        LiveCallStatus.IDLE, LiveCallStatus.ERROR -> OrbState.IDLE
    }

    ShailaCosmicBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header: Shaila title + Voice Mode Badge + Minimize chevron
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = settings.assistantName,
                            fontFamily = ShailaScriptFont,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDefaultShaila) "শাইলা • Live" else "Live",
                            style = MaterialTheme.typography.labelLarge,
                            color = AccentVioletSecondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ext.glassFill,
                        border = BorderStroke(1.dp, ext.glassBorder)
                    ) {
                        Text(
                            text = liveState.modeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = ext.glassFill,
                    border = BorderStroke(1.dp, ext.glassBorder),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .testTag("live_call_minimize_top")
                        .clickable {
                            if (liveState.status == LiveCallStatus.ERROR) onEndCall() else onMinimize()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isBn) "মিনিমাইজ করুন" else "Minimize call",
                            tint = Color.White
                        )
                    }
                }
            }

            // Center Living Character or Glowing Orb with real-time Lip-Sync & Gesture Motion
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (settings.homeCharacterStyle != HomeCharacterStyle.CLASSIC_ORB) {
                    LivingHalfBodyCharacter(
                        homeCharacterStyle = settings.homeCharacterStyle,
                        customCharacterUri = settings.customCharacterUri,
                        orbState = mappedOrbState,
                        rmsLevel = micLevel,
                        speakingLevel = speakingLevel,
                        reduceMotion = settings.reduceMotion,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(335.dp),
                        onClick = onToggleMute
                    )
                } else {
                    AnimatedVoiceOrb(
                        orbState = mappedOrbState,
                        rmsLevel = micLevel,
                        speakingLevel = speakingLevel,
                        avatarRegion = avatarRegion,
                        orbSize = 246.dp,
                        onClick = onToggleMute
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                SoundWaveBar(
                    orbState = mappedOrbState,
                    rmsLevel = if (mappedOrbState == OrbState.SPEAKING) speakingLevel else micLevel
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = when (liveState.status) {
                        LiveCallStatus.CONNECTING -> if (isBn) "${bnName}-এর সাথে সংযুক্ত হচ্ছে..." else "Connecting to ${settings.assistantName}..."
                        LiveCallStatus.LISTENING -> if (liveState.isMuted) {
                            if (isBn) "মাইক্রোফোন মিউট করা আছে" else "Microphone muted"
                        } else {
                            if (isBn) "$bnName শুনছে..." else "${settings.assistantName} is listening..."
                        }
                        LiveCallStatus.SPEAKING -> if (isBn) "$bnName কথা বলছে..." else "${settings.assistantName} is speaking..."
                        LiveCallStatus.ERROR -> if (isBn) "সংযোগে সমস্যা হয়েছে" else "Connection issue"
                        LiveCallStatus.IDLE -> if (isBn) "কল প্রস্তুত" else "Ready"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (liveState.status == LiveCallStatus.ERROR) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        cornerRadius = 18.dp,
                        contentPadding = 14.dp
                    ) {
                        Text(
                            text = liveState.errorMessage ?: if (isBn) "লাইভ কল চালু করা যায়নি।" else "Could not start live call.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                        ) {
                            FilledTonalButton(
                                onClick = onRetry,
                                modifier = Modifier.testTag("live_call_retry_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isBn) "আবার চেষ্টা" else "Retry")
                            }
                            Button(
                                onClick = onOpenApiKeys,
                                modifier = Modifier.testTag("live_call_open_keys_button")
                            ) {
                                Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isBn) "API Keys" else "API Keys")
                            }
                        }
                    }
                } else {
                    Text(
                        text = liveState.liveCaption.ifBlank {
                            if (isBn) "মিনিমাইজ করলেও ব্যাকগ্রাউন্ডে $bnName শুনতে থাকবে।" else "Keeps listening even if you minimize this screen."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // Bottom Call Controls: Mute | Speaker | Coral-Red End | Minimize
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = if (liveState.isMuted) AccentVioletPrimary.copy(alpha = 0.5f) else ext.glassFill,
                        border = BorderStroke(
                            1.5.dp,
                            if (liveState.isMuted) AccentVioletBright else ext.glassBorder
                        ),
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .testTag("live_call_mute_button")
                            .clickable(onClick = onToggleMute)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (liveState.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                contentDescription = if (liveState.isMuted) "Unmute" else "Mute",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (liveState.isMuted) {
                            if (isBn) "আনমিউট" else "Unmute"
                        } else {
                            if (isBn) "মিউট" else "Mute"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                // Speaker / Earpiece toggle button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = if (liveState.isSpeakerOn) AccentVioletPrimary.copy(alpha = 0.5f) else ext.glassFill,
                        border = BorderStroke(
                            1.5.dp,
                            if (liveState.isSpeakerOn) AccentVioletBright else ext.glassBorder
                        ),
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .testTag("live_call_speaker_button")
                            .clickable(onClick = onToggleSpeaker)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (liveState.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.PhoneInTalk,
                                contentDescription = if (liveState.isSpeakerOn) "Speaker" else "Earpiece",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (liveState.isSpeakerOn) {
                            if (isBn) "স্পিকার" else "Speaker"
                        } else {
                            if (isBn) "ইয়ারপিস" else "Earpiece"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                // End Call coral-red button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .shadow(16.dp, CircleShape, ambientColor = CoralEndCall, spotColor = CoralEndCall)
                            .clip(CircleShape)
                            .background(CoralEndCall)
                            .testTag("live_call_end_button")
                            .clickable(onClick = onEndCall),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CallEnd,
                            contentDescription = if (isBn) "কল শেষ করুন" else "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isBn) "শেষ করুন" else "End",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                // Minimize button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = ext.glassFill,
                        border = BorderStroke(1.5.dp, ext.glassBorder),
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .testTag("live_call_minimize_button")
                            .clickable {
                                if (liveState.status == LiveCallStatus.ERROR) onEndCall() else onMinimize()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (isBn) "মিনিমাইজ" else "Minimize",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isBn) "মিনিমাইজ" else "Minimize",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
fun MinimizedLiveCallPill(
    liveState: LiveCallSessionState,
    settings: AppSettings,
    avatarRegion: ImageRegionRect,
    onExpand: () -> Unit,
    onToggleMute: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBn = settings.appLanguage == AppLanguage.BENGALI
    val ext = LocalShailaColors.current
    val isDefaultShaila = settings.assistantName.equals("Shaila", ignoreCase = true) ||
        settings.assistantName == "শাইলা"
    val bnName = if (isDefaultShaila) "শাইলা" else settings.assistantName

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("live_call_mini_pill")
            .clickable(onClick = onExpand),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xEE1A1238),
        border = BorderStroke(1.2.dp, Brush.linearGradient(listOf(AccentVioletSecondary, AccentVioletBright))),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShailaCharacterAvatar(
                homeCharacterStyle = if (settings.showHerEverywhere || settings.homeCharacterStyle != HomeCharacterStyle.CLASSIC_ORB) {
                    settings.homeCharacterStyle
                } else {
                    HomeCharacterStyle.CLASSIC_ORB
                },
                customCharacterUri = settings.customCharacterUri,
                fallbackAvatarRegion = avatarRegion,
                contentDescription = "${settings.assistantName} live",
                modifier = Modifier.size(40.dp),
                borderColor = AccentVioletSecondary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isBn) "$bnName লাইভ কল" else "${settings.assistantName} Live Call",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when (liveState.status) {
                        LiveCallStatus.CONNECTING -> if (isBn) "সংযুক্ত হচ্ছে..." else "Connecting..."
                        LiveCallStatus.LISTENING -> if (liveState.isMuted) {
                            if (isBn) "মিউট" else "Muted"
                        } else {
                            if (isBn) "শুনছে... ট্যাপ করে বড় করুন" else "Listening • Tap to expand"
                        }
                        LiveCallStatus.SPEAKING -> if (isBn) "$bnName কথা বলছে..." else "${settings.assistantName} is speaking..."
                        else -> liveState.modeLabel
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.orbSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (liveState.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = if (liveState.isMuted) "Unmute" else "Mute",
                    tint = Color.White
                )
            }
            IconButton(
                onClick = onEndCall,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CoralEndCall)
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = if (isBn) "কল শেষ" else "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
