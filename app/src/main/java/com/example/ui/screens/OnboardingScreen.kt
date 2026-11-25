package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.ApiKeyType
import com.example.data.local.ApiKeysSnapshot
import com.example.data.model.AppLanguage
import com.example.data.model.ImageRegionRect
import com.example.data.model.ImageRegionsConfig
import com.example.ui.components.CroppedImage
import com.example.ui.components.GlassCard
import com.example.ui.components.ShailaCosmicBackdrop
import com.example.ui.theme.AccentVioletBright
import com.example.ui.theme.AccentVioletPrimary
import com.example.ui.theme.AccentVioletSecondary
import com.example.ui.theme.LocalShailaColors
import com.example.ui.theme.ShailaScriptFont
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    appLanguage: AppLanguage,
    imageRegions: ImageRegionsConfig,
    apiKeys: ApiKeysSnapshot,
    onSaveApiKey: (ApiKeyType, String) -> Unit,
    onAddGalleryUris: (List<Uri>) -> Unit,
    onMicPermissionUpdated: (Boolean) -> Unit,
    onComplete: () -> Unit
) {
    // Page 0: Splash/Welcome screen (part D layout)
    // Page 1: Onboarding 1 (portraitFace)
    // Page 2: Onboarding 2 (fullBody)
    // Page 3: Permissions
    // Page 4: API Key Setup (skip for now)
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val ext = LocalShailaColors.current
    val isBn = appLanguage == AppLanguage.BENGALI

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    var mediaAddedCount by remember { mutableStateOf(0) }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        onMicPermissionUpdated(granted)
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            mediaAddedCount += uris.size
            onAddGalleryUris(uris)
        }
    }

    ShailaCosmicBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CroppedImage(
                        region = imageRegions.iconArt,
                        contentDescription = "Shaila logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Shaila",
                        fontFamily = ShailaScriptFont,
                        fontSize = 24.sp,
                        color = Color.White
                    )
                }
                TextButton(
                    onClick = onComplete,
                    modifier = Modifier.testTag("onboarding_skip_top")
                ) {
                    Text(if (isBn) "স্কিপ করো" else "Skip")
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 600.dp)
            ) { page ->
                when (page) {
                    0 -> SplashWelcomePage(
                        imageRegions = imageRegions,
                        onGetStarted = {
                            scope.launch { pagerState.animateScrollToPage(1) }
                        }
                    )
                    1 -> CharacterOnboardingSlide(
                        region = imageRegions.portraitFace,
                        title = if (isBn) "হ্যালো! আমি শাইলা" else "Hello! I'm Shaila",
                        subtitle = if (isBn) {
                            "আপনার ব্যক্তিগত বাংলা ও ইংরেজি ভয়েস সহকারী। ঠিক একজন কাছের মানুষের মতো কথা বলুন, প্রশ্ন করুন ও পরামর্শ নিন।"
                        } else {
                            "Your warm Bengali & English personal AI voice assistant. Talk naturally like speaking to a real friend."
                        },
                        isPortraitCrop = true
                    )
                    2 -> CharacterOnboardingSlide(
                        region = imageRegions.fullBody,
                        title = if (isBn) "ভিডিও ও কনটেন্ট ক্রিয়েটর স্টুডিও" else "Creator & Video Studio",
                        subtitle = if (isBn) {
                            "ফোনের ছবি ও ভিডিও বিশ্লেষণ করে টাইটেল, ডেসক্রিপশন, ট্যাগ, ফেসবুক-ইনস্টাগ্রাম ক্যাপশন এবং শর্ট স্ক্রিপ্ট তৈরি করুন।"
                        } else {
                            "Analyze your videos and photos to generate YouTube titles, tags, social captions, and short scripts in seconds."
                        },
                        isPortraitCrop = false
                    )
                    3 -> PermissionsSlide(
                        isBn = isBn,
                        micGranted = micGranted,
                        notifGranted = notifGranted,
                        mediaAddedCount = mediaAddedCount,
                        onRequestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onRequestNotif = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onPickMedia = {
                            mediaPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                    )
                    else -> ApiSetupSlide(
                        isBn = isBn,
                        apiKeys = apiKeys,
                        onSaveApiKey = onSaveApiKey,
                        onSkip = onComplete
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Page dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(5) { idx ->
                    val active = pagerState.currentPage == idx
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (active) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) AccentVioletBright else ext.glassBorder
                            )
                    )
                }
            }

            if (pagerState.currentPage > 0) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(if (isBn) "পেছনে" else "Back")
                    }

                    Button(
                        onClick = {
                            if (pagerState.currentPage < 4) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            } else {
                                onComplete()
                            }
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(52.dp)
                            .testTag("onboarding_next_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentVioletPrimary)
                    ) {
                        Text(
                            text = if (pagerState.currentPage == 4) {
                                if (isBn) "শুরু করুন" else "Get Started"
                            } else {
                                if (isBn) "পরবর্তী" else "Next"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashWelcomePage(
    imageRegions: ImageRegionsConfig,
    onGetStarted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Upper half: Shaila portrait with glowing neon blue-purple ring behind her
        Box(
            modifier = Modifier.size(260.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(246.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                AccentVioletSecondary.copy(alpha = 0.55f),
                                AccentVioletBright.copy(alpha = 0.30f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        width = 2.5.dp,
                        brush = Brush.linearGradient(listOf(AccentVioletSecondary, AccentVioletBright)),
                        shape = CircleShape
                    )
            )
            CroppedImage(
                region = imageRegions.portraitFace,
                contentDescription = "Shaila portrait",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(226.dp)
                    .clip(CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Shaila",
            fontFamily = ShailaScriptFont,
            fontSize = 54.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = 56.sp
        )
        Text(
            text = "শাইলা",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White.copy(alpha = 0.95f),
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your AI Voice\nPersonal Assistant",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Small pill: Developed by Amir (আমির)
        Surface(
            shape = CircleShape,
            color = LocalShailaColors.current.glassFill,
            border = BorderStroke(1.dp, LocalShailaColors.current.glassBorder)
        ) {
            Text(
                text = "Developed by Amir (আমির)",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Purple gradient "Get Started ->" button
        Box(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .height(54.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(AccentVioletPrimary, AccentVioletBright)
                    )
                )
        ) {
            Button(
                onClick = onGetStarted,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("splash_get_started_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                shape = CircleShape
            ) {
                Text(
                    text = "Get Started",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun CharacterOnboardingSlide(
    region: ImageRegionRect,
    title: String,
    subtitle: String,
    isPortraitCrop: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 28.dp,
            contentPadding = 12.dp
        ) {
            CroppedImage(
                region = region,
                contentDescription = title,
                contentScale = if (isPortraitCrop) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun PermissionsSlide(
    isBn: Boolean,
    micGranted: Boolean,
    notifGranted: Boolean,
    mediaAddedCount: Int,
    onRequestMic: () -> Unit,
    onRequestNotif: () -> Unit,
    onPickMedia: () -> Unit
) {
    val ext = LocalShailaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isBn) "প্রয়োজনীয় অনুমতিসমূহ" else "Helpful Permissions",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isBn) {
                "শাইলার সাথে ভয়েসে কথা বলতে এবং মিডিয়া বিশ্লেষণ করতে নিচের অনুমতিগুলো দিন।"
            } else {
                "Enable microphone and media access so Shaila can hear you and inspect selected videos or photos."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(18.dp))

        PermissionRowCard(
            icon = Icons.Filled.Mic,
            title = if (isBn) "মাইক্রোফোন (Microphone)" else "Microphone",
            description = if (isBn) "আপনার বাংলা ও ইংরেজি ভয়েস শুনতে প্রয়োজন।" else "Required to listen to your voice commands.",
            granted = micGranted,
            buttonLabel = if (micGranted) (if (isBn) "অনুমতি দেওয়া আছে" else "Granted") else (if (isBn) "অনুমতি দিন" else "Allow"),
            onClick = onRequestMic,
            successTint = ext.success
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionRowCard(
            icon = Icons.Filled.PhotoLibrary,
            title = if (isBn) "মিডিয়া লাইব্রেরি (গ্যালারি)" else "Media Library",
            description = if (isBn) "সুরক্ষিত অ্যান্ড্রয়েড ফটো পিকার দিয়ে ছবি ও ভিডিও বাছাই করুন।" else "Pick photos & videos privately via Android Photo Picker.",
            granted = mediaAddedCount > 0,
            buttonLabel = if (mediaAddedCount > 0) {
                if (isBn) "$mediaAddedCount টি ফাইল যুক্ত" else "$mediaAddedCount added"
            } else {
                if (isBn) "ছবি/ভিডিও বাছাই" else "Select Media"
            },
            onClick = onPickMedia,
            successTint = ext.success
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionRowCard(
            icon = Icons.Filled.Notifications,
            title = if (isBn) "নোটিফিকেশন" else "Notifications",
            description = if (isBn) "ভয়েস রিমাইন্ডার ও সহকারীর আপডেটের জন্য।" else "For helpful reminders and assistant alerts.",
            granted = notifGranted,
            buttonLabel = if (notifGranted) (if (isBn) "চালু আছে" else "Enabled") else (if (isBn) "অনুমতি দিন" else "Allow"),
            onClick = onRequestNotif,
            successTint = ext.success
        )
    }
}

@Composable
private fun PermissionRowCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    buttonLabel: String,
    onClick: () -> Unit,
    successTint: Color
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else icon,
                contentDescription = title,
                tint = if (granted) successTint else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(onClick = onClick) {
                Text(buttonLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ApiSetupSlide(
    isBn: Boolean,
    apiKeys: ApiKeysSnapshot,
    onSaveApiKey: (ApiKeyType, String) -> Unit,
    onSkip: () -> Unit
) {
    var geminiInput by rememberSaveable { mutableStateOf(apiKeys.geminiKey) }
    var groqInput by rememberSaveable { mutableStateOf(apiKeys.groqKey) }
    var claudeInput by rememberSaveable { mutableStateOf(apiKeys.claudeKey) }
    var elevenInput by rememberSaveable { mutableStateOf(apiKeys.elevenLabsKey) }
    var showGemini by remember { mutableStateOf(false) }
    var showGroq by remember { mutableStateOf(false) }
    var showClaude by remember { mutableStateOf(false) }
    var showEleven by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isBn) "API Key সেটআপ (ফ্রি লিংকসহ)" else "API Key Setup (With Direct Links)",
                style = MaterialTheme.typography.headlineSmall
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isBn) {
                "লাইভ ভয়েস ও আসল নারী কণ্ঠের জন্য নিচের লিংক থেকে একটি ফ্রি Gemini Key নিতে পারেন। সব কী আপনার ফোনে সুরক্ষিত থাকে।"
            } else {
                "Tap any direct link below to get a free Gemini or Groq key. All keys stay encrypted on-device."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = geminiInput,
            onValueChange = {
                geminiInput = it
                onSaveApiKey(ApiKeyType.GEMINI, it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_gemini_key"),
            label = { Text(if (isBn) "Voice - Gemini Key (ফ্রি ও প্রস্তাবিত)" else "Voice - Gemini Key (Free & Recommended)") },
            placeholder = { Text("AIza...") },
            singleLine = true,
            visualTransformation = if (showGemini) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showGemini = !showGemini }) {
                    Icon(
                        imageVector = if (showGemini) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = "Toggle key visibility"
                    )
                }
            }
        )
        OnboardingDirectKeyLink(
            label = if (isBn) "Google AI Studio থেকে ফ্রি কী নিন" else "Get a free key at Google AI Studio",
            onClick = { runCatching { uriHandler.openUri("https://aistudio.google.com/apikey") } }
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = groqInput,
            onValueChange = {
                groqInput = it
                onSaveApiKey(ApiKeyType.GROQ, it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_groq_key"),
            label = { Text(if (isBn) "Chat - Groq Key (দ্রুত ফ্রি চ্যাট)" else "Chat - Groq Key (Fast Free Chat)") },
            placeholder = { Text("gsk_...") },
            singleLine = true,
            visualTransformation = if (showGroq) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showGroq = !showGroq }) {
                    Icon(
                        imageVector = if (showGroq) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = "Toggle key visibility"
                    )
                }
            }
        )
        OnboardingDirectKeyLink(
            label = if (isBn) "Groq থেকে ফ্রি কী নিন" else "Get a free key at Groq",
            onClick = { runCatching { uriHandler.openUri("https://console.groq.com/keys") } }
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = elevenInput,
            onValueChange = {
                elevenInput = it
                onSaveApiKey(ApiKeyType.ELEVENLABS, it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_elevenlabs_key"),
            label = { Text("ElevenLabs API Key (Optional)") },
            placeholder = { Text("xi-...") },
            singleLine = true,
            visualTransformation = if (showEleven) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showEleven = !showEleven }) {
                    Icon(
                        imageVector = if (showEleven) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = "Toggle key visibility"
                    )
                }
            }
        )
        OnboardingDirectKeyLink(
            label = if (isBn) "ElevenLabs থেকে কী নিন" else "Get a key at ElevenLabs",
            onClick = { runCatching { uriHandler.openUri("https://elevenlabs.io/app/settings/api-keys") } }
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = claudeInput,
            onValueChange = {
                claudeInput = it
                onSaveApiKey(ApiKeyType.CLAUDE, it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_claude_key"),
            label = { Text("Anthropic Claude API Key (Optional)") },
            placeholder = { Text("sk-ant-...") },
            singleLine = true,
            visualTransformation = if (showClaude) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showClaude = !showClaude }) {
                    Icon(
                        imageVector = if (showClaude) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = "Toggle key visibility"
                    )
                }
            }
        )
        OnboardingDirectKeyLink(
            label = if (isBn) "Anthropic Console থেকে কী নিন" else "Get a key at Anthropic Console",
            onClick = { runCatching { uriHandler.openUri("https://console.anthropic.com/settings/keys") } }
        )

        Spacer(modifier = Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
            Text(
                text = "Developed by Amir (আমির)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = if (isBn) {
                    "ভয়েস, লাইভ কল ও কথা বলার জন্য অন্তত একটি API Key (যেমন ফ্রি Gemini Key) যোগ করা আবশ্যক—কোনো অফলাইন রোবটিক ভয়েস ব্যবহার হয় না।"
                } else {
                    "An API key (such as a free Gemini key) is required for Shaila to speak or start Live Calls—no offline robotic voice is used."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .testTag("onboarding_skip_for_now")
        ) {
            Text(if (isBn) "আপাতত স্কিপ করো (Skip for now)" else "Skip for now")
        }
    }
}

@Composable
private fun OnboardingDirectKeyLink(
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AccentVioletBright,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = label,
            tint = AccentVioletBright,
            modifier = Modifier.size(14.dp)
        )
    }
}
