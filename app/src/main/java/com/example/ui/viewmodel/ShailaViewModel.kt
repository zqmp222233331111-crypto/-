package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ApiKeyType
import com.example.data.local.ApiKeysSnapshot
import com.example.data.local.SecureStorageManager
import com.example.data.local.SettingsDataStore
import com.example.data.local.ShailaDatabase
import com.example.data.media.ExtractedFrame
import com.example.data.media.SpeechEvent
import com.example.data.media.SpeechRecognitionManager
import com.example.data.media.VideoFrameExtractor
import com.example.data.model.AccentColorPreset
import com.example.data.model.AppLanguage
import com.example.data.model.AppSettings
import com.example.data.model.ChatMessage
import com.example.data.model.ContentToolType
import com.example.data.model.GalleryMediaItem
import com.example.data.model.MemoryItem
import com.example.data.model.SpeechLanguage
import com.example.data.model.ToolLength
import com.example.data.model.ToolTone
import com.example.data.model.TranslationDirection
import com.example.data.model.VideoAnalysisResult
import com.example.data.model.VoiceProviderType
import com.example.data.remote.ApkDownloader
import com.example.data.remote.AppUpdateManager
import com.example.data.remote.BuildVersionInfo
import com.example.data.remote.ClaudeApiService
import com.example.data.remote.DownloadProgressState
import com.example.data.remote.ShailaAiException
import com.example.data.remote.VoiceEngine
import com.example.data.repository.ShailaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OrbState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

data class KeyTestStatus(
    val testing: Boolean = false,
    val success: Boolean? = null,
    val message: String = ""
)

data class GalleryUiState(
    val selectedItem: GalleryMediaItem? = null,
    val extractedFrames: List<ExtractedFrame> = emptyList(),
    val isExtractingFrames: Boolean = false,
    val isAnalyzing: Boolean = false,
    val analysisResult: VideoAnalysisResult? = null,
    val errorMessage: String? = null
)

data class ToolUiState(
    val isGenerating: Boolean = false,
    val resultText: String = "",
    val errorMessage: String? = null
)

data class ApkUpdateUiState(
    val isChecking: Boolean = false,
    val buildInfo: BuildVersionInfo = BuildVersionInfo(),
    val checkError: String? = null,
    val lastCheckedAtMillis: Long? = null,
    val downloadState: DownloadProgressState = DownloadProgressState.Idle
)

class ShailaViewModel(application: Application) : AndroidViewModel(application) {

    private val database = ShailaDatabase.getInstance(application)
    private val settingsStore = SettingsDataStore(application)
    private val secureStorage = SecureStorageManager(application)
    private val claudeService = ClaudeApiService()
    private val voiceEngine = VoiceEngine(application, secureStorage)
    private val appUpdateManager = AppUpdateManager(application)
    private val apkDownloader = ApkDownloader(application)
    val frameExtractor = VideoFrameExtractor(application)
    val speechManager = SpeechRecognitionManager(application)

    private val repository = ShailaRepository(
        context = application,
        database = database,
        settingsStore = settingsStore,
        secureStorage = secureStorage,
        claudeService = claudeService,
        voiceEngine = voiceEngine,
        frameExtractor = frameExtractor
    )

    val settings: StateFlow<AppSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )

    val imageRegions: StateFlow<com.example.data.model.ImageRegionsConfig> = repository.imageRegionsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = com.example.data.model.DefaultImageRegions.defaultConfig
    )

    val messages: StateFlow<List<ChatMessage>> = repository.messagesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val memories: StateFlow<List<MemoryItem>> = repository.memoriesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val galleryMedia: StateFlow<List<GalleryMediaItem>> = repository.galleryMediaFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val apiKeys: StateFlow<ApiKeysSnapshot> = repository.keysState

    val rmsLevel: StateFlow<Float> = speechManager.rmsLevel

    private val _orbState = MutableStateFlow(OrbState.IDLE)
    val orbState: StateFlow<OrbState> = _orbState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _activeVoiceSource = MutableStateFlow<String?>(null)
    val activeVoiceSource: StateFlow<String?> = _activeVoiceSource.asStateFlow()

    private val _pendingSafetyPrompt = MutableStateFlow<String?>(null)
    val pendingSafetyPrompt: StateFlow<String?> = _pendingSafetyPrompt.asStateFlow()

    private val _needsApiKeyBanner = MutableStateFlow(false)
    val needsApiKeyBanner: StateFlow<Boolean> = _needsApiKeyBanner.asStateFlow()

    private val _hasMicPermission = MutableStateFlow(false)
    val hasMicPermission: StateFlow<Boolean> = _hasMicPermission.asStateFlow()

    private val _galleryState = MutableStateFlow(GalleryUiState())
    val galleryState: StateFlow<GalleryUiState> = _galleryState.asStateFlow()

    private val _toolState = MutableStateFlow(ToolUiState())
    val toolState: StateFlow<ToolUiState> = _toolState.asStateFlow()

    private val _keyTestStatuses = MutableStateFlow<Map<ApiKeyType, KeyTestStatus>>(emptyMap())
    val keyTestStatuses: StateFlow<Map<ApiKeyType, KeyTestStatus>> = _keyTestStatuses.asStateFlow()

    private val _voicePreviewStatus = MutableStateFlow<String?>(null)
    val voicePreviewStatus: StateFlow<String?> = _voicePreviewStatus.asStateFlow()

    private val _apkUpdateState = MutableStateFlow(
        ApkUpdateUiState(buildInfo = appUpdateManager.getCurrentBuildInfo())
    )
    val apkUpdateState: StateFlow<ApkUpdateUiState> = _apkUpdateState.asStateFlow()

    private var hasGreetedThisSession = false
    private var voiceJob: Job? = null
    private var apkDownloadJob: Job? = null
    private var pendingGalleryVoiceTarget: GalleryMediaItem? = null

    init {
        observeSpeechEvents()
    }

    private fun observeSpeechEvents() {
        viewModelScope.launch {
            speechManager.events.collectLatest { event ->
                when (event) {
                    SpeechEvent.Idle -> {
                        if (_orbState.value == OrbState.LISTENING) {
                            _orbState.value = OrbState.IDLE
                        }
                        _partialTranscript.value = ""
                    }
                    SpeechEvent.Listening -> {
                        _orbState.value = OrbState.LISTENING
                        _statusMessage.value = null
                    }
                    is SpeechEvent.Partial -> {
                        _orbState.value = OrbState.LISTENING
                        _partialTranscript.value = event.text
                    }
                    is SpeechEvent.FinalResult -> {
                        _partialTranscript.value = ""
                        speechManager.resetToIdle()
                        val galleryTarget = pendingGalleryVoiceTarget
                        if (galleryTarget != null) {
                            pendingGalleryVoiceTarget = null
                            analyzeSelectedGalleryMedia(
                                customQuestion = event.text,
                                outputLanguage = settings.value.appLanguage
                            )
                        } else {
                            submitUserMessage(event.text, fromVoice = true)
                        }
                    }
                    is SpeechEvent.Error -> {
                        _partialTranscript.value = ""
                        _orbState.value = OrbState.IDLE
                        pendingGalleryVoiceTarget = null
                        val isBn = settings.value.appLanguage == AppLanguage.BENGALI
                        _statusMessage.value = if (isBn) event.messageBn else event.messageEn
                        speechManager.resetToIdle()
                    }
                }
            }
        }
    }

    fun updateMicPermission(granted: Boolean) {
        _hasMicPermission.value = granted
    }

    fun triggerStartupGreetingIfNeeded() {
        if (hasGreetedThisSession) return
        val current = settings.value
        if (!current.onboardingCompleted) return
        hasGreetedThisSession = true
        if (!current.autoSpeakReplies) return

        val greeting = repository.getGreetingText(current.appLanguage)
        viewModelScope.launch {
            delay(450)
            speakOut(greeting, allowContinuousNext = false)
        }
    }

    fun getCurrentGreeting(): String = repository.getGreetingText(settings.value.appLanguage)

    fun startVoiceListening(galleryTarget: GalleryMediaItem? = null) {
        pendingGalleryVoiceTarget = galleryTarget
        stopSpeaking()
        val current = settings.value
        speechManager.startListening(current.speechLanguage, current.appLanguage)
    }

    fun stopVoiceListening() {
        speechManager.stopListening()
    }

    fun cancelVoiceListening() {
        pendingGalleryVoiceTarget = null
        speechManager.cancel()
        _orbState.value = OrbState.IDLE
    }

    fun submitUserMessage(rawText: String, fromVoice: Boolean = false, bypassSafetyCheck: Boolean = false) {
        val text = rawText.trim()
        if (text.isEmpty()) return

        if (!bypassSafetyCheck && repository.requiresSafetyConfirmation(text)) {
            _pendingSafetyPrompt.value = text
            return
        }

        _statusMessage.value = null
        _needsApiKeyBanner.value = false
        _orbState.value = OrbState.THINKING

        viewModelScope.launch {
            val result = repository.askAssistant(text)
            result.onSuccess { reply ->
                if (settings.value.autoSpeakReplies) {
                    speakOut(reply.text, allowContinuousNext = fromVoice)
                } else {
                    _orbState.value = OrbState.IDLE
                }
            }.onFailure { error ->
                _orbState.value = OrbState.IDLE
                val isBn = settings.value.appLanguage == AppLanguage.BENGALI
                when (error) {
                    is ShailaAiException.MissingApiKey -> {
                        _needsApiKeyBanner.value = true
                        _statusMessage.value = if (isBn) error.messageBn else error.messageEn
                    }
                    is ShailaAiException -> {
                        _statusMessage.value = if (isBn) error.messageBn else error.messageEn
                    }
                    else -> {
                        _statusMessage.value = error.localizedMessage
                            ?: if (isBn) "কিছু একটা সমস্যা হয়েছে।" else "Something went wrong."
                    }
                }
            }
        }
    }

    fun confirmSafetyPrompt() {
        val prompt = _pendingSafetyPrompt.value ?: return
        _pendingSafetyPrompt.value = null
        submitUserMessage(prompt, fromVoice = false, bypassSafetyCheck = true)
    }

    fun dismissSafetyPrompt() {
        _pendingSafetyPrompt.value = null
    }

    fun speakOut(text: String, allowContinuousNext: Boolean = false) {
        voiceJob?.cancel()
        voiceJob = viewModelScope.launch {
            _orbState.value = OrbState.SPEAKING
            val outcome = repository.speakReply(text)
            _activeVoiceSource.value = outcome.usedProviderLabel
            _orbState.value = OrbState.IDLE
            if (allowContinuousNext && settings.value.continuousConversation && _hasMicPermission.value) {
                delay(350)
                startVoiceListening()
            }
        }
    }

    fun stopSpeaking() {
        voiceJob?.cancel()
        voiceEngine.stop()
        if (_orbState.value == OrbState.SPEAKING) {
            _orbState.value = OrbState.IDLE
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // Gallery actions
    fun addPickedGalleryUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            repository.addGalleryUris(uris)
        }
    }

    fun removeGalleryItem(uri: String) {
        viewModelScope.launch {
            repository.removeGalleryItem(uri)
            if (_galleryState.value.selectedItem?.uri == uri) {
                _galleryState.value = GalleryUiState()
            }
        }
    }

    fun selectGalleryItem(item: GalleryMediaItem) {
        _galleryState.value = GalleryUiState(
            selectedItem = item,
            isExtractingFrames = item.isVideo
        )
        if (item.isVideo) {
            viewModelScope.launch {
                val frames = repository.extractFramesForPreview(Uri.parse(item.uri))
                _galleryState.value = _galleryState.value.copy(
                    extractedFrames = frames,
                    isExtractingFrames = false
                )
            }
        }
    }

    fun clearSelectedGalleryItem() {
        _galleryState.value = GalleryUiState()
    }

    fun analyzeSelectedGalleryMedia(
        customQuestion: String,
        outputLanguage: AppLanguage
    ) {
        val selected = _galleryState.value.selectedItem ?: return
        _galleryState.value = _galleryState.value.copy(
            isAnalyzing = true,
            errorMessage = null
        )
        viewModelScope.launch {
            val result = repository.analyzeGalleryMedia(
                uri = Uri.parse(selected.uri),
                isVideo = selected.isVideo,
                customQuestion = customQuestion,
                outputLanguage = outputLanguage,
                preExtractedFrames = _galleryState.value.extractedFrames
            )
            result.onSuccess { analysis ->
                _galleryState.value = _galleryState.value.copy(
                    isAnalyzing = false,
                    analysisResult = analysis
                )
                if (settings.value.autoSpeakReplies && analysis.rawSummary.isNotBlank()) {
                    speakOut(analysis.rawSummary)
                }
            }.onFailure { err ->
                val isBn = settings.value.appLanguage == AppLanguage.BENGALI
                val msg = if (err is ShailaAiException) {
                    if (isBn) err.messageBn else err.messageEn
                } else {
                    err.localizedMessage ?: if (isBn) "বিশ্লেষণ ব্যর্থ হয়েছে।" else "Analysis failed."
                }
                _galleryState.value = _galleryState.value.copy(
                    isAnalyzing = false,
                    errorMessage = msg
                )
            }
        }
    }

    // Content Tools actions
    fun generateToolOutput(
        tool: ContentToolType,
        topic: String,
        tone: ToolTone,
        length: ToolLength,
        direction: TranslationDirection
    ) {
        if (topic.isBlank()) return
        _toolState.value = ToolUiState(isGenerating = true)
        viewModelScope.launch {
            val res = repository.generateToolContent(tool, topic, tone, length, direction)
            res.onSuccess { text ->
                _toolState.value = ToolUiState(isGenerating = false, resultText = text)
            }.onFailure { err ->
                val isBn = settings.value.appLanguage == AppLanguage.BENGALI
                val msg = if (err is ShailaAiException) {
                    if (isBn) err.messageBn else err.messageEn
                } else {
                    err.localizedMessage ?: if (isBn) "লেখা তৈরি করা যায়নি।" else "Generation failed."
                }
                _toolState.value = ToolUiState(isGenerating = false, errorMessage = msg)
            }
        }
    }

    fun resetToolState() {
        _toolState.value = ToolUiState()
    }

    // Memory actions
    fun addMemory(fact: String, category: String = "ব্যক্তিগত") {
        viewModelScope.launch { repository.addMemory(fact, category) }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch { repository.deleteMemory(id) }
    }

    fun clearAllMemories() {
        viewModelScope.launch { repository.clearMemories() }
    }

    fun clearChatHistory() {
        viewModelScope.launch { repository.clearChatHistory() }
    }

    // Settings & Keys
    fun saveApiKey(type: ApiKeyType, key: String) {
        viewModelScope.launch {
            repository.saveApiKey(type, key)
            _keyTestStatuses.value = _keyTestStatuses.value - type
        }
    }

    fun testApiKey(type: ApiKeyType, keyInput: String) {
        _keyTestStatuses.value = _keyTestStatuses.value + (
            type to KeyTestStatus(testing = true, message = "যাচাই করা হচ্ছে...")
        )
        viewModelScope.launch {
            repository.saveApiKey(type, keyInput)
            val result = repository.testApiKey(type, keyInput)
            val status = result.fold(
                onSuccess = { msg -> KeyTestStatus(testing = false, success = true, message = msg) },
                onFailure = { err ->
                    val msg = if (err is ShailaAiException) err.messageBn else err.localizedMessage ?: "যাচাই ব্যর্থ"
                    KeyTestStatus(testing = false, success = false, message = msg)
                }
            )
            _keyTestStatuses.value = _keyTestStatuses.value + (type to status)
        }
    }

    fun previewVoice(provider: VoiceProviderType, voiceId: String) {
        _voicePreviewStatus.value = "ভয়েস বাজানো হচ্ছে..."
        viewModelScope.launch {
            val res = repository.previewVoice(provider, voiceId)
            _voicePreviewStatus.value = res.fold(
                onSuccess = { label -> "$label ভয়েস প্রিভিউ সম্পন্ন" },
                onFailure = { err -> err.localizedMessage ?: "প্রিভিউ সম্পন্ন" }
            )
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { repository.setOnboardingCompleted(true) }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setAppLanguage(language) }
    }

    fun setSpeechLanguage(language: SpeechLanguage) {
        viewModelScope.launch { repository.setSpeechLanguage(language) }
    }

    fun setClaudeModel(modelId: String) {
        viewModelScope.launch { repository.setClaudeModel(modelId) }
    }

    fun setVoiceProvider(provider: VoiceProviderType) {
        viewModelScope.launch { repository.setVoiceProvider(provider) }
    }

    fun setVoiceForProvider(provider: VoiceProviderType, voiceId: String) {
        viewModelScope.launch { repository.setVoiceForProvider(provider, voiceId) }
    }

    fun setVoiceSpeed(speed: Float) {
        viewModelScope.launch { repository.setVoiceSpeed(speed) }
    }

    fun setElevenLabsStability(value: Float) {
        viewModelScope.launch { repository.setElevenLabsStability(value) }
    }

    fun setElevenLabsStyle(value: Float) {
        viewModelScope.launch { repository.setElevenLabsStyle(value) }
    }

    fun setContinuousConversation(enabled: Boolean) {
        viewModelScope.launch { repository.setContinuousConversation(enabled) }
    }

    fun setAutoSpeakReplies(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoSpeakReplies(enabled) }
    }

    fun setHoldToTalkMode(enabled: Boolean) {
        viewModelScope.launch { repository.setHoldToTalkMode(enabled) }
    }

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch { repository.setDarkTheme(dark) }
    }

    fun setAccentPreset(preset: AccentColorPreset) {
        viewModelScope.launch { repository.setAccentPreset(preset) }
    }

    fun setApkUpdateUrl(url: String) {
        viewModelScope.launch { repository.setApkUpdateUrl(url.trim()) }
    }

    fun checkLatestBuild(urlOverride: String? = null) {
        val targetUrl = (urlOverride ?: settings.value.apkUpdateUrl).trim()
        viewModelScope.launch {
            if (targetUrl.isNotBlank()) {
                repository.setApkUpdateUrl(targetUrl)
            }
            _apkUpdateState.value = _apkUpdateState.value.copy(
                isChecking = true,
                checkError = null
            )
            val result = appUpdateManager.checkLatestBuild(targetUrl)
            result.onSuccess { info ->
                _apkUpdateState.value = _apkUpdateState.value.copy(
                    isChecking = false,
                    buildInfo = info,
                    checkError = null,
                    lastCheckedAtMillis = System.currentTimeMillis()
                )
            }.onFailure { err ->
                _apkUpdateState.value = _apkUpdateState.value.copy(
                    isChecking = false,
                    checkError = err.localizedMessage ?: "ভার্সন চেক ব্যর্থ হয়েছে।"
                )
            }
        }
    }

    fun startApkDownload(urlInput: String) {
        val trimmedInput = urlInput.trim()
        viewModelScope.launch {
            if (trimmedInput.isNotBlank()) {
                repository.setApkUpdateUrl(trimmedInput)
            }

            var buildInfo = _apkUpdateState.value.buildInfo
            var targetApkUrl = when {
                trimmedInput.endsWith(".apk", ignoreCase = true) -> trimmedInput
                !buildInfo.resolvedApkUrl.isNullOrBlank() && trimmedInput == settings.value.apkUpdateUrl ->
                    buildInfo.resolvedApkUrl!!
                else -> trimmedInput
            }

            // If user provided a JSON/GitHub Releases URL without checking first, resolve the APK asset URL first
            if (targetApkUrl.isNotBlank() && !targetApkUrl.contains(".apk", ignoreCase = true)) {
                _apkUpdateState.value = _apkUpdateState.value.copy(isChecking = true, checkError = null)
                val checkRes = appUpdateManager.checkLatestBuild(targetApkUrl)
                checkRes.onSuccess { info ->
                    buildInfo = info
                    _apkUpdateState.value = _apkUpdateState.value.copy(
                        isChecking = false,
                        buildInfo = info,
                        lastCheckedAtMillis = System.currentTimeMillis()
                    )
                    targetApkUrl = info.resolvedApkUrl.orEmpty()
                }.onFailure { err ->
                    _apkUpdateState.value = _apkUpdateState.value.copy(
                        isChecking = false,
                        checkError = err.localizedMessage ?: "APK লিংক খুঁজে পাওয়া যায়নি।"
                    )
                    return@launch
                }
            }

            if (targetApkUrl.isBlank()) {
                _apkUpdateState.value = _apkUpdateState.value.copy(
                    downloadState = DownloadProgressState.Failed("রিলিজ থেকে কোনো APK ফাইল লিংক পাওয়া যায়নি।")
                )
                return@launch
            }

            val enqueueRes = apkDownloader.downloadApk(
                apkUrl = targetApkUrl,
                versionLabel = buildInfo.latestVersionName ?: buildInfo.currentVersionName
            )
            enqueueRes.onSuccess { (downloadId, fileName) ->
                apkDownloadJob?.cancel()
                apkDownloadJob = viewModelScope.launch {
                    apkDownloader.observeDownload(downloadId, fileName).collectLatest { progress ->
                        _apkUpdateState.value = _apkUpdateState.value.copy(downloadState = progress)
                    }
                }
            }.onFailure { err ->
                _apkUpdateState.value = _apkUpdateState.value.copy(
                    downloadState = DownloadProgressState.Failed(
                        err.localizedMessage ?: "DownloadManager শুরু করা যায়নি।"
                    )
                )
            }
        }
    }

    fun cancelApkDownload() {
        val current = _apkUpdateState.value.downloadState
        if (current is DownloadProgressState.Downloading) {
            apkDownloadJob?.cancel()
            apkDownloader.cancelDownload(current.downloadId)
            _apkUpdateState.value = _apkUpdateState.value.copy(
                downloadState = DownloadProgressState.Idle
            )
        }
    }

    fun openSystemDownloads() {
        apkDownloader.openSystemDownloads()
    }

    fun updateImageRegion(
        key: com.example.data.model.ImageRegionKey,
        rect: com.example.data.model.ImageRegionRect
    ) {
        viewModelScope.launch { repository.saveImageRegion(key, rect) }
    }

    fun resetImageRegions() {
        viewModelScope.launch { repository.resetImageRegions() }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.destroy()
        voiceEngine.release()
    }
}
