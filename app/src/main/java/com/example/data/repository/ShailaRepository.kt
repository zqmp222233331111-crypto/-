package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.local.ApiKeyType
import com.example.data.local.ApiKeysSnapshot
import com.example.data.local.SecureStorageManager
import com.example.data.local.SettingsDataStore
import com.example.data.local.ShailaDatabase
import com.example.data.media.ExtractedFrame
import com.example.data.media.VideoFrameExtractor
import com.example.data.model.AccentColorPreset
import com.example.data.model.AppLanguage
import com.example.data.model.AppSettings
import com.example.data.model.ChatMessage
import com.example.data.model.ContentToolType
import com.example.data.model.GalleryMediaItem
import com.example.data.model.MemoryItem
import com.example.data.model.SenderRole
import com.example.data.model.SpeechLanguage
import com.example.data.model.ToolLength
import com.example.data.model.ToolTone
import com.example.data.model.TranslationDirection
import com.example.data.model.VideoAnalysisResult
import com.example.data.model.VoiceProviderType
import com.example.data.remote.ClaudeApiService
import com.example.data.remote.ShailaAiException
import com.example.data.remote.SpeakOutcome
import com.example.data.remote.VoiceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.util.Calendar

class ShailaRepository(
    private val context: Context,
    private val database: ShailaDatabase,
    private val settingsStore: SettingsDataStore,
    private val secureStorage: SecureStorageManager,
    private val claudeService: ClaudeApiService,
    val voiceEngine: VoiceEngine,
    val frameExtractor: VideoFrameExtractor
) {
    val settingsFlow: Flow<AppSettings> = settingsStore.settingsFlow
    val imageRegionsFlow: Flow<com.example.data.model.ImageRegionsConfig> = settingsStore.imageRegionsFlow
    val messagesFlow: Flow<List<ChatMessage>> = database.chatDao().observeMessages()
    val memoriesFlow: Flow<List<MemoryItem>> = database.memoryDao().observeMemories()
    val galleryMediaFlow: Flow<List<GalleryMediaItem>> = database.galleryMediaDao().observeMedia()
    val keysState: StateFlow<ApiKeysSnapshot> = secureStorage.keysState

    fun getGreetingText(language: AppLanguage): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (language == AppLanguage.BENGALI) {
            when (hour) {
                in 5..11 -> "সুপ্রভাত, আজ কী করতে পারি?"
                in 12..15 -> "শুভ দুপুর, আজ কী করতে পারি?"
                in 16..18 -> "শুভ বিকাল, আজ কী করতে পারি?"
                else -> "শুভ সন্ধ্যা, আজ কী করতে পারি?"
            }
        } else {
            when (hour) {
                in 5..11 -> "Good morning! How can I help you today?"
                in 12..16 -> "Good afternoon! How can I help you today?"
                else -> "Good evening! How can I help you today?"
            }
        }
    }

    fun requiresSafetyConfirmation(text: String): Boolean {
        val lower = text.lowercase()
        val sensitiveKeywords = listOf(
            "password", "passcode", "pin", "otp", "bank", "bkash", "nagad", "rocket",
            "payment", "pay ", "transfer", "money", "credit card", "debit card",
            "delete all", "clear memory", "clear chat", "remove all",
            "পাসওয়ার্ড", "পিন", "টাকা", "বিকাশ", "নগদ", "ব্যাংক", "পেমেন্ট",
            "মুছে ফেল", "ডিলিট", "সব মেমোরি", "সব চ্যাট"
        )
        return sensitiveKeywords.any { lower.contains(it) }
    }

    fun isIdentityQuestion(text: String): Boolean {
        val lower = text.lowercase().trim()
        val creatorQueries = listOf(
            "তোমাকে কে বানিয়েছে", "তোমার নির্মাতা কে", "তোমার ডেভেলপার কে",
            "কে বানিয়েছে তোমাকে", "তোমার নাম কী", "তোমার নাম কি",
            "who made you", "who created you", "who is your developer",
            "who built you", "what is your name", "what's your name"
        )
        return creatorQueries.any { lower.contains(it) }
    }

    fun localIdentityReply(text: String, language: AppLanguage): String {
        val lower = text.lowercase()
        val asksCreator = lower.contains("বানিয়ে") || lower.contains("নির্মাতা") ||
            lower.contains("ডেভেলপার") || lower.contains("made") ||
            lower.contains("created") || lower.contains("developer") || lower.contains("built")
        val isEnglish = text.none { it in '\u0980'..'\u09FF' } &&
            (language == AppLanguage.ENGLISH || lower.contains("who") || lower.contains("what"))

        return if (asksCreator) {
            if (isEnglish) {
                "I'm Shaila. I was created by Amir, he is my developer."
            } else {
                "আমি শাইলা। আমাকে বানিয়েছেন আমির, উনিই আমার ডেভেলপার।"
            }
        } else {
            if (isEnglish) {
                "My name is Shaila, your personal AI assistant created by Amir."
            } else {
                "আমার নাম শাইলা। আমি আপনার ব্যক্তিগত এআই সহকারী, আমাকে বানিয়েছেন আমির।"
            }
        }
    }

    suspend fun askAssistant(
        userText: String,
        mediaUri: Uri? = null,
        isVideo: Boolean = false
    ): Result<ChatMessage> {
        val settings = settingsFlow.first()
        val claudeKey = secureStorage.getKey(ApiKeyType.CLAUDE)

        val userMsg = ChatMessage(
            role = SenderRole.USER,
            text = userText,
            mediaUri = mediaUri?.toString(),
            isVideo = isVideo
        )
        database.chatDao().insertMessage(userMsg)

        // If the user asks Shaila's identity/creator and Claude key isn't configured yet,
        // answer cleanly with Shaila/Amir identity rather than blocking on a key error.
        if (claudeKey.isBlank() && isIdentityQuestion(userText)) {
            val reply = localIdentityReply(userText, settings.appLanguage)
            val saved = ChatMessage(role = SenderRole.ASSISTANT, text = reply)
            database.chatDao().insertMessage(saved)
            return Result.success(saved)
        }

        return try {
            val memories = database.memoryDao().getAllMemories()
            val history = database.chatDao().getRecentMessages(14).reversed()
            val imagesBase64 = if (mediaUri != null) {
                if (isVideo) {
                    frameExtractor.extractVideoFrames(mediaUri, frameCount = 4).map { it.base64Jpeg }
                } else {
                    listOfNotNull(frameExtractor.encodePhotoToBase64(mediaUri))
                }
            } else {
                emptyList()
            }

            val replyText = claudeService.sendConversation(
                apiKey = claudeKey,
                model = settings.claudeModel,
                history = history.dropLast(1),
                userPrompt = userText,
                memories = memories,
                preferredLanguage = settings.appLanguage,
                base64Images = imagesBase64
            )
            val saved = ChatMessage(role = SenderRole.ASSISTANT, text = replyText)
            val rowId = database.chatDao().insertMessage(saved)
            Result.success(saved.copy(id = rowId))
        } catch (e: ShailaAiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(ShailaAiException.ServerError(e.localizedMessage ?: "Error"))
        }
    }

    suspend fun speakReply(text: String): SpeakOutcome {
        val settings = settingsFlow.first()
        return voiceEngine.speak(
            text = text,
            voiceId = settings.currentVoiceId(),
            settings = settings
        )
    }

    suspend fun previewVoice(provider: VoiceProviderType, voiceId: String): Result<String> {
        val settings = settingsFlow.first()
        return voiceEngine.previewVoice(provider, voiceId, settings)
    }

    suspend fun generateToolContent(
        tool: ContentToolType,
        topic: String,
        tone: ToolTone,
        length: ToolLength,
        direction: TranslationDirection
    ): Result<String> {
        val settings = settingsFlow.first()
        val claudeKey = secureStorage.getKey(ApiKeyType.CLAUDE)
        val memories = database.memoryDao().getAllMemories()
        return try {
            val output = claudeService.generateToolContent(
                apiKey = claudeKey,
                model = settings.claudeModel,
                tool = tool,
                topic = topic,
                tone = tone,
                length = length,
                direction = direction,
                memories = memories,
                preferredLanguage = settings.appLanguage
            )
            Result.success(output)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractFramesForPreview(uri: Uri): List<ExtractedFrame> {
        return frameExtractor.extractVideoFrames(uri, frameCount = 5)
    }

    suspend fun analyzeGalleryMedia(
        uri: Uri,
        isVideo: Boolean,
        customQuestion: String,
        outputLanguage: AppLanguage,
        preExtractedFrames: List<ExtractedFrame> = emptyList()
    ): Result<VideoAnalysisResult> {
        val settings = settingsFlow.first()
        val claudeKey = secureStorage.getKey(ApiKeyType.CLAUDE)
        val memories = database.memoryDao().getAllMemories()

        val framesBase64 = if (isVideo) {
            if (preExtractedFrames.isNotEmpty()) {
                preExtractedFrames.map { it.base64Jpeg }
            } else {
                frameExtractor.extractVideoFrames(uri, frameCount = 5).map { it.base64Jpeg }
            }
        } else {
            listOfNotNull(frameExtractor.encodePhotoToBase64(uri))
        }

        if (framesBase64.isEmpty()) {
            return Result.failure(
                ShailaAiException.ServerError("মিডিয়া ফাইল থেকে ফ্রেম পড়া যায়নি।")
            )
        }

        return try {
            val result = claudeService.analyzeMediaForCreator(
                apiKey = claudeKey,
                model = settings.claudeModel,
                base64Frames = framesBase64,
                isVideo = isVideo,
                customQuestion = customQuestion,
                memories = memories,
                outputLanguage = outputLanguage
            )
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addGalleryUris(uris: List<Uri>) {
        val items = mutableListOf<GalleryMediaItem>()
        for (uri in uris) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val meta = frameExtractor.inspectMedia(uri)
            items.add(
                GalleryMediaItem(
                    uri = uri.toString(),
                    isVideo = meta.isVideo,
                    displayName = meta.displayName,
                    durationMs = meta.durationMs
                )
            )
        }
        if (items.isNotEmpty()) {
            database.galleryMediaDao().insertAll(items)
        }
    }

    suspend fun removeGalleryItem(uri: String) = database.galleryMediaDao().deleteByUri(uri)

    suspend fun addMemory(fact: String, category: String = "সাধারণ") {
        val trimmed = fact.trim()
        if (trimmed.isNotEmpty()) {
            database.memoryDao().insertMemory(MemoryItem(fact = trimmed, category = category))
        }
    }

    suspend fun deleteMemory(id: Long) = database.memoryDao().deleteById(id)

    suspend fun clearMemories() = database.memoryDao().clearAll()

    suspend fun clearChatHistory() = database.chatDao().clearAll()

    suspend fun saveApiKey(type: ApiKeyType, key: String) = secureStorage.saveKey(type, key)

    suspend fun testApiKey(type: ApiKeyType, keyInput: String): Result<String> {
        val settings = settingsFlow.first()
        return when (type) {
            ApiKeyType.CLAUDE -> claudeService.testApiKey(keyInput, settings.claudeModel)
            ApiKeyType.ELEVENLABS -> voiceEngine.elevenLabs.testApiKey(keyInput)
            ApiKeyType.OPENAI -> voiceEngine.openAi.testApiKey(keyInput)
            ApiKeyType.XAI -> voiceEngine.xai.testApiKey(keyInput)
        }
    }

    // Settings mutations
    suspend fun setOnboardingCompleted(done: Boolean) = settingsStore.setOnboardingCompleted(done)
    suspend fun setAppLanguage(lang: AppLanguage) = settingsStore.setAppLanguage(lang)
    suspend fun setSpeechLanguage(lang: SpeechLanguage) = settingsStore.setSpeechLanguage(lang)
    suspend fun setClaudeModel(modelId: String) = settingsStore.setClaudeModel(modelId)
    suspend fun setVoiceProvider(provider: VoiceProviderType) = settingsStore.setVoiceProvider(provider)
    suspend fun setVoiceForProvider(provider: VoiceProviderType, voiceId: String) =
        settingsStore.setVoiceForProvider(provider, voiceId)
    suspend fun setVoiceSpeed(speed: Float) = settingsStore.setVoiceSpeed(speed)
    suspend fun setElevenLabsStability(value: Float) = settingsStore.setElevenLabsStability(value)
    suspend fun setElevenLabsStyle(value: Float) = settingsStore.setElevenLabsStyle(value)
    suspend fun setContinuousConversation(enabled: Boolean) = settingsStore.setContinuousConversation(enabled)
    suspend fun setAutoSpeakReplies(enabled: Boolean) = settingsStore.setAutoSpeakReplies(enabled)
    suspend fun setHoldToTalkMode(enabled: Boolean) = settingsStore.setHoldToTalkMode(enabled)
    suspend fun setDarkTheme(dark: Boolean) = settingsStore.setDarkTheme(dark)
    suspend fun setAccentPreset(preset: AccentColorPreset) = settingsStore.setAccentPreset(preset)
    suspend fun setApkUpdateUrl(url: String) = settingsStore.setApkUpdateUrl(url)
    suspend fun saveImageRegion(
        key: com.example.data.model.ImageRegionKey,
        rect: com.example.data.model.ImageRegionRect
    ) = settingsStore.saveImageRegion(key, rect)
    suspend fun resetImageRegions() = settingsStore.resetImageRegions()
}
