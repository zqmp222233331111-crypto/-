package com.example.data.remote

import android.content.Context
import com.example.data.local.ApiKeyType
import com.example.data.local.SecureStorageManager
import com.example.data.model.AppSettings
import com.example.data.model.ShailaCatalog
import com.example.data.model.VoiceProviderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SpeakOutcome(
    val usedProviderLabel: String,
    val succeeded: Boolean,
    val missingVoiceKey: Boolean = false,
    val errorMessage: String? = null
)

class VoiceEngine(
    context: Context,
    private val secureStorage: SecureStorageManager
) {
    private val streamPlayer = AudioStreamPlayer(context.applicationContext)
    val elevenLabs = ElevenLabsTtsProvider(secureStorage, streamPlayer)
    val openAi = OpenAiTtsProvider(secureStorage, streamPlayer)
    val xai = XaiTtsProvider(secureStorage, streamPlayer)
    val geminiTts = GeminiTtsProvider(secureStorage, streamPlayer)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _ttsSpeechLevel = MutableStateFlow(0f)
    val ttsSpeechLevel: StateFlow<Float> = _ttsSpeechLevel.asStateFlow()

    init {
        scope.launch {
            streamPlayer.playbackEnergy.collect { energy ->
                _ttsSpeechLevel.value = energy
            }
        }
    }

    private fun providerFor(type: VoiceProviderType): TtsProvider = when (type) {
        VoiceProviderType.ELEVENLABS -> elevenLabs
        VoiceProviderType.OPENAI -> openAi
        VoiceProviderType.XAI -> xai
    }

    private fun hasKeyFor(type: VoiceProviderType): Boolean = when (type) {
        VoiceProviderType.ELEVENLABS -> secureStorage.getKey(ApiKeyType.ELEVENLABS).isNotBlank()
        VoiceProviderType.OPENAI -> secureStorage.getKey(ApiKeyType.OPENAI).isNotBlank()
        VoiceProviderType.XAI -> secureStorage.getKey(ApiKeyType.XAI).isNotBlank()
    }

    fun hasAnyVoiceKey(): Boolean = secureStorage.keysState.value.hasAnyVoiceApiKey() ||
        secureStorage.getKey(ApiKeyType.GEMINI).isNotBlank() ||
        secureStorage.getKey(ApiKeyType.ELEVENLABS).isNotBlank() ||
        secureStorage.getKey(ApiKeyType.OPENAI).isNotBlank() ||
        secureStorage.getKey(ApiKeyType.XAI).isNotBlank()

    /**
     * Strict API-only TTS entrypoint (no offline/robotic device TTS fallback):
     * 1) Configured cloud TTS (ElevenLabs -> OpenAI -> xAI) when keys are present
     * 2) Gemini Voice TTS (`gemini-2.5-flash-preview-tts`) when a Gemini key is present
     * Without a valid API key, Shaila does not speak.
     */
    suspend fun speak(
        text: String,
        voiceId: String,
        settings: AppSettings,
        onPlaybackStarted: () -> Unit = {}
    ): SpeakOutcome {
        val cleanText = text
            .replace("*", "")
            .replace("#", "")
            .replace("`", "")
            .trim()
        if (cleanText.isEmpty()) return SpeakOutcome("", false)

        if (!hasAnyVoiceKey()) {
            return SpeakOutcome(
                usedProviderLabel = "",
                succeeded = false,
                missingVoiceKey = true,
                errorMessage = "API Key ছাড়া ভয়েস চালু হবে না।"
            )
        }

        val options = TtsOptions(
            speed = settings.voiceSpeed,
            stability = settings.elevenLabsStability,
            style = settings.elevenLabsStyle,
            language = settings.appLanguage
        )

        val orderedProviders = buildList {
            add(settings.voiceProvider)
            VoiceProviderType.entries.filter { it != settings.voiceProvider }.forEach { add(it) }
        }

        var lastError: String? = null
        for ((index, type) in orderedProviders.withIndex()) {
            if (!hasKeyFor(type)) continue
            val provider = providerFor(type)
            val resolvedVoiceId = if (index == 0) {
                voiceId.ifBlank { ShailaCatalog.defaultVoiceId(type) }
            } else {
                when (type) {
                    VoiceProviderType.ELEVENLABS -> settings.elevenLabsVoiceId
                    VoiceProviderType.OPENAI -> settings.openAiVoiceId
                    VoiceProviderType.XAI -> settings.xaiVoiceId
                }
            }
            val result = provider.speak(
                text = cleanText,
                voiceId = resolvedVoiceId,
                options = options,
                onPlaybackStarted = onPlaybackStarted
            )
            if (result.isSuccess) {
                return SpeakOutcome(provider.displayLabel, true)
            } else {
                lastError = result.exceptionOrNull()?.localizedMessage
            }
        }

        if (secureStorage.getKey(ApiKeyType.GEMINI).isNotBlank()) {
            val geminiRes = geminiTts.speak(
                text = cleanText,
                voiceId = settings.geminiLiveVoice.voiceName,
                options = options,
                onPlaybackStarted = onPlaybackStarted
            )
            if (geminiRes.isSuccess) {
                return SpeakOutcome("Gemini • ${settings.geminiLiveVoice.voiceName}", true)
            } else {
                lastError = geminiRes.exceptionOrNull()?.localizedMessage
            }
        }

        return SpeakOutcome(
            usedProviderLabel = "",
            succeeded = false,
            missingVoiceKey = false,
            errorMessage = lastError ?: "ভয়েস API থেকে অডিও পাওয়া যায়নি।"
        )
    }

    suspend fun previewVoice(
        providerType: VoiceProviderType,
        voiceId: String,
        settings: AppSettings,
        onPlaybackStarted: () -> Unit = {}
    ): Result<String> {
        val sampleText = if (settings.appLanguage == com.example.data.model.AppLanguage.BENGALI) {
            "নমস্কার, আমি শাইলা। আপনার ব্যক্তিগত সহকারী হিসেবে আমি সবসময় পাশে আছি।"
        } else {
            "Hello, I'm Shaila, your personal AI voice assistant."
        }
        val options = TtsOptions(
            speed = settings.voiceSpeed,
            stability = settings.elevenLabsStability,
            style = settings.elevenLabsStyle,
            language = settings.appLanguage
        )
        val provider = providerFor(providerType)
        val cloudResult = provider.speak(sampleText, voiceId, options, onPlaybackStarted)
        if (cloudResult.isSuccess) {
            return Result.success(provider.displayLabel)
        }

        return Result.failure(
            cloudResult.exceptionOrNull()
                ?: IllegalStateException("${provider.displayLabel} প্রিভিউয়ের জন্য API Key যোগ করুন।")
        )
    }

    fun stop() {
        streamPlayer.stop()
    }

    fun release() {
        stop()
    }
}
