package com.example

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.AppLanguage
import com.example.data.remote.DownloadProgressState
import com.example.data.remote.LiveCallStatus
import com.example.ui.components.ShailaBottomBar
import com.example.ui.components.ShailaCosmicBackdrop
import com.example.ui.components.ShailaRoutes
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageRegionDebugScreen
import com.example.ui.screens.LiveVoiceScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.MinimizedLiveCallPill
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.ShailaTheme
import com.example.ui.viewmodel.ShailaViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        enableEdgeToEdge()
        setContent {
            ShailaApp()
        }
    }
}

@Composable
fun ShailaApp(viewModel: ShailaViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val imageRegions by viewModel.imageRegions.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val galleryMedia by viewModel.galleryMedia.collectAsStateWithLifecycle()
    val apiKeys by viewModel.apiKeys.collectAsStateWithLifecycle()
    val orbState by viewModel.orbState.collectAsStateWithLifecycle()
    val rmsLevel by viewModel.rmsLevel.collectAsStateWithLifecycle()
    val speakingLevel by viewModel.speakingLevel.collectAsStateWithLifecycle()
    val liveMicLevel by viewModel.liveMicLevel.collectAsStateWithLifecycle()
    val liveCallState by viewModel.liveCallState.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val activeVoiceSource by viewModel.activeVoiceSource.collectAsStateWithLifecycle()
    val needsApiKeyBanner by viewModel.needsApiKeyBanner.collectAsStateWithLifecycle()
    val pendingSafetyPrompt by viewModel.pendingSafetyPrompt.collectAsStateWithLifecycle()
    val galleryState by viewModel.galleryState.collectAsStateWithLifecycle()
    val toolState by viewModel.toolState.collectAsStateWithLifecycle()
    val keyTestStatuses by viewModel.keyTestStatuses.collectAsStateWithLifecycle()
    val voicePreviewStatus by viewModel.voicePreviewStatus.collectAsStateWithLifecycle()
    val isLoadingElevenVoices by viewModel.isLoadingElevenVoices.collectAsStateWithLifecycle()
    val apkUpdateState by viewModel.apkUpdateState.collectAsStateWithLifecycle()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsStateWithLifecycle()
    val needsAccessibilityDialog by viewModel.needsAccessibilityDialog.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAccessibilityStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isBn = settings.appLanguage == AppLanguage.BENGALI
    var openApiKeysSignal by remember { mutableIntStateOf(0) }

    ShailaTheme(
        darkTheme = settings.darkTheme,
        accentPreset = settings.accentPreset
    ) {
        if (!settings.onboardingCompleted) {
            OnboardingScreen(
                appLanguage = settings.appLanguage,
                imageRegions = imageRegions,
                apiKeys = apiKeys,
                onSaveApiKey = viewModel::saveApiKey,
                onAddGalleryUris = viewModel::addPickedGalleryUris,
                onMicPermissionUpdated = viewModel::updateMicPermission,
                onComplete = {
                    viewModel.completeOnboarding()
                }
            )
        } else {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            val showBottomBar = currentRoute in listOf(
                ShailaRoutes.HOME,
                ShailaRoutes.GALLERY,
                ShailaRoutes.TOOLS,
                ShailaRoutes.SETTINGS
            )

            LaunchedEffect(settings.onboardingCompleted) {
                viewModel.triggerStartupGreetingIfNeeded()
            }

            var lastNotifiedDownloadId by remember { mutableLongStateOf(-1L) }
            LaunchedEffect(apkUpdateState.downloadState) {
                when (val dlState = apkUpdateState.downloadState) {
                    is DownloadProgressState.Downloading -> {
                        if (dlState.downloadId != lastNotifiedDownloadId) {
                            lastNotifiedDownloadId = dlState.downloadId
                            snackbarHostState.showSnackbar(
                                if (isBn) "APK ডাউনলোড শুরু হয়েছে..." else "APK download started..."
                            )
                        }
                    }
                    is DownloadProgressState.Completed -> {
                        val action = snackbarHostState.showSnackbar(
                            message = if (isBn) {
                                "ডাউনলোড সম্পন্ন হয়েছে: ${dlState.fileName}"
                            } else {
                                "Download complete: ${dlState.fileName}"
                            },
                            actionLabel = if (isBn) "খুলুন" else "Open"
                        )
                        if (action == SnackbarResult.ActionPerformed) {
                            viewModel.openSystemDownloads()
                        }
                    }
                    is DownloadProgressState.Failed -> {
                        snackbarHostState.showSnackbar(dlState.reason)
                    }
                    DownloadProgressState.Idle -> Unit
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                ShailaCosmicBackdrop {
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                        bottomBar = {
                            Column {
                                if (liveCallState.isActive && liveCallState.isMinimized) {
                                    MinimizedLiveCallPill(
                                        liveState = liveCallState,
                                        settings = settings,
                                        avatarRegion = imageRegions.avatarFace,
                                        onExpand = { viewModel.setLiveMinimized(false) },
                                        onToggleMute = viewModel::toggleLiveMute,
                                        onEndCall = viewModel::endLiveCall
                                    )
                                }
                                if (showBottomBar) {
                                    ShailaBottomBar(
                                        currentRoute = currentRoute,
                                        appLanguage = settings.appLanguage,
                                        onNavigate = { target ->
                                            if (target != currentRoute) {
                                                navController.navigate(target) {
                                                    popUpTo(ShailaRoutes.HOME) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = ShailaRoutes.HOME
                            ) {
                                composable(ShailaRoutes.HOME) {
                                    HomeScreen(
                                        settings = settings,
                                        imageRegions = imageRegions,
                                        messages = messages,
                                        memoryCount = memories.size,
                                        orbState = orbState,
                                        rmsLevel = rmsLevel,
                                        speakingLevel = speakingLevel,
                                        partialTranscript = partialTranscript,
                                        statusMessage = statusMessage,
                                        activeVoiceSource = activeVoiceSource,
                                        needsApiKeyBanner = needsApiKeyBanner,
                                        pendingSafetyPrompt = pendingSafetyPrompt,
                                        greetingText = viewModel.getCurrentGreeting(),
                                        onUpdateMicPermission = viewModel::updateMicPermission,
                                        onStartListening = { viewModel.startVoiceListening(null) },
                                        onStopListening = viewModel::stopVoiceListening,
                                        onStopSpeaking = viewModel::stopSpeaking,
                                        onStartLiveCall = viewModel::startLiveCall,
                                        onSendMessage = { text -> viewModel.submitUserMessage(text) },
                                        onSpeakMessage = { text -> viewModel.speakOut(text) },
                                        onSaveToMemory = { text ->
                                            viewModel.addMemory(text, if (isBn) "চ্যাট থেকে" else "From Chat")
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    if (isBn) "স্মৃতিতে সংরক্ষিত হয়েছে" else "Saved to Memory"
                                                )
                                            }
                                        },
                                        onToggleHoldToTalk = viewModel::setHoldToTalkMode,
                                        onToggleLanguage = viewModel::setAppLanguage,
                                        onConfirmSafety = viewModel::confirmSafetyPrompt,
                                        onDismissSafety = viewModel::dismissSafetyPrompt,
                                        onOpenMemory = { navController.navigate(ShailaRoutes.MEMORY) },
                                        onOpenSettings = {
                                            openApiKeysSignal++
                                            navController.navigate(ShailaRoutes.SETTINGS) {
                                                popUpTo(ShailaRoutes.HOME) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        onDismissStatus = viewModel::clearStatusMessage
                                    )
                                }

                            composable(ShailaRoutes.GALLERY) {
                                GalleryScreen(
                                    appLanguage = settings.appLanguage,
                                    mediaItems = galleryMedia,
                                    galleryState = galleryState,
                                    orbState = orbState,
                                    partialTranscript = partialTranscript,
                                    frameExtractor = viewModel.frameExtractor,
                                    onAddUris = viewModel::addPickedGalleryUris,
                                    onRemoveItem = viewModel::removeGalleryItem,
                                    onSelectItem = viewModel::selectGalleryItem,
                                    onClearSelection = viewModel::clearSelectedGalleryItem,
                                    onAnalyzeMedia = viewModel::analyzeSelectedGalleryMedia,
                                    onStartVoiceForGallery = { item -> viewModel.startVoiceListening(item) },
                                    onStopVoice = viewModel::stopVoiceListening,
                                    onSpeakText = { text -> viewModel.speakOut(text) },
                                    onOpenSettings = {
                                        navController.navigate(ShailaRoutes.SETTINGS) {
                                            popUpTo(ShailaRoutes.HOME) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }

                            composable(ShailaRoutes.TOOLS) {
                                ToolsScreen(
                                    appLanguage = settings.appLanguage,
                                    toolState = toolState,
                                    onGenerate = viewModel::generateToolOutput,
                                    onResetToolState = viewModel::resetToolState,
                                    onSpeakText = { text -> viewModel.speakOut(text) },
                                    onOpenSettings = {
                                        navController.navigate(ShailaRoutes.SETTINGS) {
                                            popUpTo(ShailaRoutes.HOME) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }

                            composable(ShailaRoutes.SETTINGS) {
                                SettingsScreen(
                                    settings = settings,
                                    imageRegions = imageRegions,
                                    apiKeys = apiKeys,
                                    keyTestStatuses = keyTestStatuses,
                                    voicePreviewStatus = voicePreviewStatus,
                                    onSaveApiKey = viewModel::saveApiKey,
                                    onTestApiKey = viewModel::testApiKey,
                                    onSelectClaudeModel = viewModel::setClaudeModel,
                                    onSelectVoiceProvider = viewModel::setVoiceProvider,
                                    onSelectVoice = viewModel::setVoiceForProvider,
                                    onPreviewVoice = viewModel::previewVoice,
                                    onChangeVoiceSpeed = viewModel::setVoiceSpeed,
                                    onChangeStability = viewModel::setElevenLabsStability,
                                    onChangeStyle = viewModel::setElevenLabsStyle,
                                    onSelectAppLanguage = viewModel::setAppLanguage,
                                    onSelectSpeechLanguage = viewModel::setSpeechLanguage,
                                    onToggleContinuous = viewModel::setContinuousConversation,
                                    onToggleAutoSpeak = viewModel::setAutoSpeakReplies,
                                    onToggleHoldToTalk = viewModel::setHoldToTalkMode,
                                    onToggleDarkTheme = viewModel::setDarkTheme,
                                    onSelectAccent = viewModel::setAccentPreset,
                                    onOpenMemory = { navController.navigate(ShailaRoutes.MEMORY) },
                                    onOpenImageRegionDebug = { navController.navigate(ShailaRoutes.IMAGE_REGION_DEBUG) },
                                    onClearChat = {
                                        viewModel.clearChatHistory()
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (isBn) "চ্যাট ইতিহাস মুছে ফেলা হয়েছে" else "Chat history cleared"
                                            )
                                        }
                                    },
                                    onClearMemories = {
                                        viewModel.clearAllMemories()
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (isBn) "সব স্মৃতি মুছে ফেলা হয়েছে" else "All memories cleared"
                                            )
                                        }
                                    },
                                    apkUpdateState = apkUpdateState,
                                    onSaveApkUpdateUrl = viewModel::setApkUpdateUrl,
                                    onCheckLatestBuild = viewModel::checkLatestBuild,
                                    onDownloadApk = viewModel::startApkDownload,
                                    onCancelApkDownload = viewModel::cancelApkDownload,
                                    onOpenDownloads = viewModel::openSystemDownloads,
                                    openApiKeysSignal = openApiKeysSignal,
                                    isLoadingElevenVoices = isLoadingElevenVoices,
                                    onSelectGeminiVoice = viewModel::setGeminiLiveVoice,
                                    onLoadMyElevenLabsVoices = viewModel::loadMyElevenLabsVoices,
                                    onAddCustomElevenLabsVoice = viewModel::addCustomElevenLabsVoice,
                                    onSetAssistantName = viewModel::setAssistantName,
                                    onSelectPersonality = viewModel::setPersonalityPreset,
                                    onSaveCustomPersonality = viewModel::setCustomPersonality,
                                    onSelectResponseStyle = viewModel::setResponseStyle,
                                    onToggleListenOnAppOpen = viewModel::setListenOnAppOpen,
                                    onSelectHomeCharacterStyle = viewModel::setHomeCharacterStyle,
                                    onSelectCustomCharacterUri = viewModel::setCustomCharacterUri,
                                    onToggleShowHerEverywhere = viewModel::setShowHerEverywhere,
                                    onToggleReduceMotion = viewModel::setReduceMotion,
                                    isAccessibilityEnabled = isAccessibilityEnabled,
                                    onOpenAccessibilitySettings = viewModel::openAccessibilitySettings
                                )
                            }

                            composable(ShailaRoutes.MEMORY) {
                                MemoryScreen(
                                    appLanguage = settings.appLanguage,
                                    memories = memories,
                                    onAddMemory = viewModel::addMemory,
                                    onDeleteMemory = viewModel::deleteMemory,
                                    onClearAll = viewModel::clearAllMemories,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(ShailaRoutes.IMAGE_REGION_DEBUG) {
                                ImageRegionDebugScreen(
                                    regions = imageRegions,
                                    onUpdateRegion = viewModel::updateImageRegion,
                                    onResetAll = viewModel::resetImageRegions,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
                }

                // Full-screen Real-Time Live Voice Call overlay (when active & not minimized)
                if ((liveCallState.isActive || liveCallState.status == LiveCallStatus.ERROR) && !liveCallState.isMinimized) {
                    LiveVoiceScreen(
                        liveState = liveCallState,
                        settings = settings,
                        avatarRegion = imageRegions.avatarFace,
                        speakingLevel = speakingLevel,
                        micLevel = liveMicLevel,
                        onToggleMute = viewModel::toggleLiveMute,
                        onToggleSpeaker = viewModel::toggleLiveSpeaker,
                        onMinimize = { viewModel.setLiveMinimized(true) },
                        onEndCall = viewModel::endLiveCall,
                        onRetry = viewModel::startLiveCall,
                        onOpenApiKeys = {
                            viewModel.endLiveCall()
                            openApiKeysSignal++
                            navController.navigate(ShailaRoutes.SETTINGS) {
                                popUpTo(ShailaRoutes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                if (needsAccessibilityDialog) {
                    AlertDialog(
                        onDismissRequest = viewModel::dismissAccessibilityDialog,
                        title = {
                            Text(
                                if (isBn) {
                                    "ফোন ও টাইপিং কন্ট্রোল চালু করুন"
                                } else {
                                    "Enable Phone & Typing Control"
                                }
                            )
                        },
                        text = {
                            Text(
                                if (isBn) {
                                    "যেকোনো অ্যাপের ভিতরে টাইপ করা, সার্চ করা, বাটনে ট্যাপ বা স্ক্রল করতে একবার Android Settings → Accessibility → \"Shaila (শাইলা) ফোন কন্ট্রোল\" চালু করে দিন।"
                                } else {
                                    "To let Shaila type in keyboards, tap buttons, and scroll inside any app, turn on \"Shaila Phone Control\" in Android Accessibility Settings."
                                }
                            )
                        },
                        confirmButton = {
                            Button(onClick = viewModel::openAccessibilitySettings) {
                                Text(if (isBn) "সেটিংসে চালু করুন" else "Open Settings")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = viewModel::dismissAccessibilityDialog) {
                                Text(if (isBn) "পরে" else "Later")
                            }
                        }
                    )
                }
            }
        }
    }
}
