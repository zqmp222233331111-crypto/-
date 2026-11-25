package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AccentColorPreset
import com.example.data.model.AppLanguage
import com.example.data.model.AppSettings
import com.example.data.model.GeminiLiveVoice
import com.example.data.model.HomeCharacterStyle
import com.example.data.model.PersonalityPreset
import com.example.data.model.ResponseStyle
import com.example.data.model.ShailaCatalog
import com.example.data.model.SpeechLanguage
import com.example.data.model.VoiceOption
import com.example.data.model.VoiceProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.shailaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "shaila_settings"
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val SPEECH_LANGUAGE = stringPreferencesKey("speech_language")
        val CLAUDE_MODEL = stringPreferencesKey("claude_model")
        val VOICE_PROVIDER = stringPreferencesKey("voice_provider")
        val ELEVENLABS_VOICE = stringPreferencesKey("elevenlabs_voice")
        val OPENAI_VOICE = stringPreferencesKey("openai_voice")
        val XAI_VOICE = stringPreferencesKey("xai_voice")
        val GEMINI_LIVE_VOICE = stringPreferencesKey("gemini_live_voice")
        val CUSTOM_ELEVENLABS_VOICES = stringPreferencesKey("custom_elevenlabs_voices")
        val VOICE_SPEED = floatPreferencesKey("voice_speed")
        val ELEVENLABS_STABILITY = floatPreferencesKey("elevenlabs_stability")
        val ELEVENLABS_STYLE = floatPreferencesKey("elevenlabs_style")
        val CONTINUOUS_CONVO = booleanPreferencesKey("continuous_convo")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val HOLD_TO_TALK = booleanPreferencesKey("hold_to_talk")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val REGION_ICON_ART = stringPreferencesKey("region_icon_art")
        val REGION_PORTRAIT = stringPreferencesKey("region_portrait")
        val REGION_FULL_BODY = stringPreferencesKey("region_full_body")
        val REGION_AVATAR = stringPreferencesKey("region_avatar")
        val APK_UPDATE_URL = stringPreferencesKey("apk_update_url")
        val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        val PERSONALITY_PRESET = stringPreferencesKey("personality_preset")
        val CUSTOM_PERSONALITY_NAME = stringPreferencesKey("custom_personality_name")
        val CUSTOM_PERSONALITY_PROMPT = stringPreferencesKey("custom_personality_prompt")
        val RESPONSE_STYLE = stringPreferencesKey("response_style")
        val HOME_CHARACTER_STYLE = stringPreferencesKey("home_character_style")
        val CUSTOM_CHARACTER_URI = stringPreferencesKey("custom_character_uri")
        val SHOW_HER_EVERYWHERE = booleanPreferencesKey("show_her_everywhere")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val LISTEN_ON_APP_OPEN = booleanPreferencesKey("listen_on_app_open")
    }

    val imageRegionsFlow: Flow<com.example.data.model.ImageRegionsConfig> = context.shailaDataStore.data.map { prefs ->
        com.example.data.model.ImageRegionsConfig(
            iconArt = parseRegion(prefs[Keys.REGION_ICON_ART], com.example.data.model.DefaultImageRegions.iconArt),
            portraitFace = parseRegion(prefs[Keys.REGION_PORTRAIT], com.example.data.model.DefaultImageRegions.portraitFace),
            fullBody = parseRegion(prefs[Keys.REGION_FULL_BODY], com.example.data.model.DefaultImageRegions.fullBody),
            avatarFace = parseRegion(prefs[Keys.REGION_AVATAR], com.example.data.model.DefaultImageRegions.avatarFace)
        )
    }

    private fun parseRegion(
        raw: String?,
        fallback: com.example.data.model.ImageRegionRect
    ): com.example.data.model.ImageRegionRect {
        if (raw.isNullOrBlank()) return fallback
        val parts = raw.split(",").mapNotNull { it.toFloatOrNull() }
        if (parts.size != 4) return fallback
        return com.example.data.model.ImageRegionRect(parts[0], parts[1], parts[2], parts[3]).clamped()
    }

    private fun encodeRegion(r: com.example.data.model.ImageRegionRect): String =
        "${r.x},${r.y},${r.width},${r.height}"

    private fun parseCustomVoices(raw: String?): List<VoiceOption> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("||")
            if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                val desc = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "কাস্টম ফিমেল ভয়েস"
                VoiceOption(
                    voiceId = parts[0].trim(),
                    name = parts[1].trim(),
                    descriptionBn = desc,
                    descriptionEn = desc,
                    provider = VoiceProviderType.ELEVENLABS,
                    isCustom = true
                )
            } else {
                null
            }
        }.distinctBy { it.voiceId }
    }

    private fun encodeCustomVoices(voices: List<VoiceOption>): String =
        voices.joinToString("\n") { v ->
            val safeName = v.name.replace("||", " ").replace("\n", " ").trim()
            val safeDesc = v.descriptionBn.replace("||", " ").replace("\n", " ").trim()
            "${v.voiceId.trim()}||$safeName||$safeDesc"
        }

    val settingsFlow: Flow<AppSettings> = context.shailaDataStore.data.map { prefs ->
        AppSettings(
            onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false,
            appLanguage = AppLanguage.entries.find { it.code == prefs[Keys.APP_LANGUAGE] }
                ?: AppLanguage.BENGALI,
            speechLanguage = SpeechLanguage.entries.find { it.localeTag == prefs[Keys.SPEECH_LANGUAGE] }
                ?: SpeechLanguage.BENGALI_BD,
            claudeModel = prefs[Keys.CLAUDE_MODEL] ?: ShailaCatalog.claudeModels.first().modelId,
            voiceProvider = VoiceProviderType.entries.find { it.id == prefs[Keys.VOICE_PROVIDER] }
                ?: VoiceProviderType.ELEVENLABS,
            elevenLabsVoiceId = prefs[Keys.ELEVENLABS_VOICE]
                ?: ShailaCatalog.elevenLabsVoices.first().voiceId,
            openAiVoiceId = prefs[Keys.OPENAI_VOICE] ?: ShailaCatalog.openAiVoices.first().voiceId,
            xaiVoiceId = prefs[Keys.XAI_VOICE] ?: ShailaCatalog.xaiVoices.first().voiceId,
            geminiLiveVoice = GeminiLiveVoice.entries.find { it.voiceName == prefs[Keys.GEMINI_LIVE_VOICE] }
                ?: GeminiLiveVoice.AOEDE,
            customElevenLabsVoices = parseCustomVoices(prefs[Keys.CUSTOM_ELEVENLABS_VOICES]),
            voiceSpeed = prefs[Keys.VOICE_SPEED] ?: 1.0f,
            elevenLabsStability = prefs[Keys.ELEVENLABS_STABILITY] ?: 0.50f,
            elevenLabsStyle = prefs[Keys.ELEVENLABS_STYLE] ?: 0.25f,
            continuousConversation = prefs[Keys.CONTINUOUS_CONVO] ?: false,
            autoSpeakReplies = prefs[Keys.AUTO_SPEAK] ?: true,
            holdToTalkMode = prefs[Keys.HOLD_TO_TALK] ?: false,
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            accentPreset = AccentColorPreset.entries.find { it.id == prefs[Keys.ACCENT_PRESET] }
                ?: AccentColorPreset.VIOLET,
            apkUpdateUrl = prefs[Keys.APK_UPDATE_URL] ?: "",
            assistantName = prefs[Keys.ASSISTANT_NAME]?.takeIf { it.isNotBlank() } ?: "Shaila",
            personalityPreset = PersonalityPreset.entries.find { it.id == prefs[Keys.PERSONALITY_PRESET] }
                ?: PersonalityPreset.FRIENDLY,
            customPersonalityName = prefs[Keys.CUSTOM_PERSONALITY_NAME] ?: "",
            customPersonalityPrompt = prefs[Keys.CUSTOM_PERSONALITY_PROMPT] ?: "",
            responseStyle = ResponseStyle.entries.find { it.id == prefs[Keys.RESPONSE_STYLE] }
                ?: ResponseStyle.BALANCED,
            homeCharacterStyle = HomeCharacterStyle.entries.find { it.id == prefs[Keys.HOME_CHARACTER_STYLE] }
                ?: HomeCharacterStyle.HALF_BODY_SHAILA,
            customCharacterUri = prefs[Keys.CUSTOM_CHARACTER_URI] ?: "",
            showHerEverywhere = prefs[Keys.SHOW_HER_EVERYWHERE] ?: false,
            reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false,
            listenOnAppOpen = prefs[Keys.LISTEN_ON_APP_OPEN] ?: false
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.shailaDataStore.edit { it[Keys.ONBOARDING_DONE] = completed }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.shailaDataStore.edit { it[Keys.APP_LANGUAGE] = language.code }
    }

    suspend fun setSpeechLanguage(language: SpeechLanguage) {
        context.shailaDataStore.edit { it[Keys.SPEECH_LANGUAGE] = language.localeTag }
    }

    suspend fun setClaudeModel(modelId: String) {
        context.shailaDataStore.edit { it[Keys.CLAUDE_MODEL] = modelId }
    }

    suspend fun setVoiceProvider(provider: VoiceProviderType) {
        context.shailaDataStore.edit { it[Keys.VOICE_PROVIDER] = provider.id }
    }

    suspend fun setVoiceForProvider(provider: VoiceProviderType, voiceId: String) {
        context.shailaDataStore.edit { prefs ->
            when (provider) {
                VoiceProviderType.ELEVENLABS -> prefs[Keys.ELEVENLABS_VOICE] = voiceId
                VoiceProviderType.OPENAI -> prefs[Keys.OPENAI_VOICE] = voiceId
                VoiceProviderType.XAI -> prefs[Keys.XAI_VOICE] = voiceId
            }
        }
    }

    suspend fun setGeminiLiveVoice(voice: GeminiLiveVoice) {
        context.shailaDataStore.edit { it[Keys.GEMINI_LIVE_VOICE] = voice.voiceName }
    }

    suspend fun saveCustomElevenLabsVoices(voices: List<VoiceOption>) {
        context.shailaDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_ELEVENLABS_VOICES] = encodeCustomVoices(voices.distinctBy { it.voiceId })
        }
    }

    suspend fun addCustomElevenLabsVoice(voice: VoiceOption) {
        context.shailaDataStore.edit { prefs ->
            val current = parseCustomVoices(prefs[Keys.CUSTOM_ELEVENLABS_VOICES])
            val updated = (listOf(voice.copy(isCustom = true)) + current).distinctBy { it.voiceId }
            prefs[Keys.CUSTOM_ELEVENLABS_VOICES] = encodeCustomVoices(updated)
            prefs[Keys.ELEVENLABS_VOICE] = voice.voiceId
            prefs[Keys.VOICE_PROVIDER] = VoiceProviderType.ELEVENLABS.id
        }
    }

    suspend fun setVoiceSpeed(speed: Float) {
        context.shailaDataStore.edit { it[Keys.VOICE_SPEED] = speed.coerceIn(0.6f, 1.6f) }
    }

    suspend fun setElevenLabsStability(value: Float) {
        context.shailaDataStore.edit { it[Keys.ELEVENLABS_STABILITY] = value.coerceIn(0f, 1f) }
    }

    suspend fun setElevenLabsStyle(value: Float) {
        context.shailaDataStore.edit { it[Keys.ELEVENLABS_STYLE] = value.coerceIn(0f, 1f) }
    }

    suspend fun setContinuousConversation(enabled: Boolean) {
        context.shailaDataStore.edit { it[Keys.CONTINUOUS_CONVO] = enabled }
    }

    suspend fun setAutoSpeakReplies(enabled: Boolean) {
        context.shailaDataStore.edit { it[Keys.AUTO_SPEAK] = enabled }
    }

    suspend fun setHoldToTalkMode(enabled: Boolean) {
        context.shailaDataStore.edit { it[Keys.HOLD_TO_TALK] = enabled }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.shailaDataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    suspend fun setAccentPreset(preset: AccentColorPreset) {
        context.shailaDataStore.edit { it[Keys.ACCENT_PRESET] = preset.id }
    }

    suspend fun setApkUpdateUrl(url: String) {
        context.shailaDataStore.edit { it[Keys.APK_UPDATE_URL] = url.trim() }
    }

    suspend fun setAssistantName(name: String) {
        context.shailaDataStore.edit {
            it[Keys.ASSISTANT_NAME] = name.trim().ifBlank { "Shaila" }
        }
    }

    suspend fun setPersonalityPreset(preset: PersonalityPreset) {
        context.shailaDataStore.edit { it[Keys.PERSONALITY_PRESET] = preset.id }
    }

    suspend fun setCustomPersonality(name: String, prompt: String) {
        context.shailaDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_PERSONALITY_NAME] = name.trim()
            prefs[Keys.CUSTOM_PERSONALITY_PROMPT] = prompt.trim()
            prefs[Keys.PERSONALITY_PRESET] = PersonalityPreset.CUSTOM.id
        }
    }

    suspend fun setResponseStyle(style: ResponseStyle) {
        context.shailaDataStore.edit { it[Keys.RESPONSE_STYLE] = style.id }
    }

    suspend fun setHomeCharacterStyle(style: HomeCharacterStyle) {
        context.shailaDataStore.edit { it[Keys.HOME_CHARACTER_STYLE] = style.id }
    }

    suspend fun setCustomCharacterUri(uri: String) {
        context.shailaDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_CHARACTER_URI] = uri.trim()
            if (uri.isNotBlank()) {
                prefs[Keys.HOME_CHARACTER_STYLE] = HomeCharacterStyle.CUSTOM_GALLERY.id
            }
        }
    }

    suspend fun setShowHerEverywhere(enabled: Boolean) {
        context.shailaDataStore.edit { it[Keys.SHOW_HER_EVERYWHERE] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.shailaDataStore.edit { it[Keys.REDUCE_MOTION] = enabled }
    }

    suspend fun setListenOnAppOpen(enabled: Boolean) {
        context.shailaDataStore.edit { it[Keys.LISTEN_ON_APP_OPEN] = enabled }
    }

    suspend fun saveImageRegion(
        key: com.example.data.model.ImageRegionKey,
        rect: com.example.data.model.ImageRegionRect
    ) {
        val prefKey = when (key) {
            com.example.data.model.ImageRegionKey.ICON_ART -> Keys.REGION_ICON_ART
            com.example.data.model.ImageRegionKey.PORTRAIT_FACE -> Keys.REGION_PORTRAIT
            com.example.data.model.ImageRegionKey.FULL_BODY -> Keys.REGION_FULL_BODY
            com.example.data.model.ImageRegionKey.AVATAR_FACE -> Keys.REGION_AVATAR
        }
        context.shailaDataStore.edit { it[prefKey] = encodeRegion(rect.clamped()) }
    }

    suspend fun resetImageRegions() {
        context.shailaDataStore.edit {
            it.remove(Keys.REGION_ICON_ART)
            it.remove(Keys.REGION_PORTRAIT)
            it.remove(Keys.REGION_FULL_BODY)
            it.remove(Keys.REGION_AVATAR)
        }
    }
}
