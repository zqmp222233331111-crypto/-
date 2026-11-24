package com.example.data.remote

import android.content.Context
import com.example.data.local.SecureStorageManager
import com.example.data.model.AppSettings
import com.example.data.model.ShailaCatalog
import com.example.data.model.VoiceProviderType

data class SpeakOutcome(
    val usedProviderLabel: String,
    val succeeded: Boolean
)

class VoiceEngine(
    context: Context,
    secureStorage: SecureStorageManager
) {
    private val streamPlayer = AudioStreamPlayer(context.applicationContext)
    val elevenLabs = ElevenLabsTtsProvider(secureStorage, streamPlayer)
    val openAi = OpenAiTtsProvider(secureStorage, streamPlayer)
    val xai = XaiTtsProvider(secureStorage, streamPlayer)
    private val deviceTts = AndroidBuiltInTtsProvider(context.applicationContext)

    private fun providerFor(type: VoiceProviderType): TtsProvider = when (type) {
        VoiceProviderType.ELEVENLABS -> elevenLabs
        VoiceProviderType.OPENAI -> openAi
        VoiceProviderType.XAI -> xai
    }

    /**
     * Common TTS interface entrypoint used across the entire app.
     * Automatically falls back to the next provider, then finally to built-in Android TTS.
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
        if (cleanText.isEmpty()) return SpeakOutcome("None", false)

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

        for ((index, type) in orderedProviders.withIndex()) {
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
            }
        }

        // Final fallback: phone's built-in Android TTS
        val fallbackResult = deviceTts.speak(
            text = cleanText,
            voiceId = "",
            options = options,
            onPlaybackStarted = onPlaybackStarted
        )
        return SpeakOutcome(deviceTts.displayLabel, fallbackResult.isSuccess)
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
        return if (cloudResult.isSuccess) {
            Result.success(provider.displayLabel)
        } else {
            // Try device TTS preview if API key isn't configured yet, while reporting cause
            val fallback = deviceTts.speak(sampleText, "", options, onPlaybackStarted)
            if (fallback.isSuccess) {
                Result.failure(
                    IllegalStateException(
                        "${provider.displayLabel} প্রিভিউ ব্যর্থ হয়েছে (${cloudResult.exceptionOrNull()?.message}), তাই ফোনের বিল্ট-ইন ভয়েস বাজানো হয়েছে।"
                    )
                )
            } else {
                Result.failure(cloudResult.exceptionOrNull() ?: IllegalStateException("Preview failed"))
            }
        }
    }

    fun stop() {
        streamPlayer.stop()
        deviceTts.stop()
    }

    fun release() {
        stop()
        deviceTts.shutdown()
    }
}
