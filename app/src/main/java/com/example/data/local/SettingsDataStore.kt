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
import com.example.data.model.ShailaCatalog
import com.example.data.model.SpeechLanguage
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
            voiceSpeed = prefs[Keys.VOICE_SPEED] ?: 1.0f,
            elevenLabsStability = prefs[Keys.ELEVENLABS_STABILITY] ?: 0.50f,
            elevenLabsStyle = prefs[Keys.ELEVENLABS_STYLE] ?: 0.25f,
            continuousConversation = prefs[Keys.CONTINUOUS_CONVO] ?: false,
            autoSpeakReplies = prefs[Keys.AUTO_SPEAK] ?: true,
            holdToTalkMode = prefs[Keys.HOLD_TO_TALK] ?: false,
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            accentPreset = AccentColorPreset.entries.find { it.id == prefs[Keys.ACCENT_PRESET] }
                ?: AccentColorPreset.VIOLET,
            apkUpdateUrl = prefs[Keys.APK_UPDATE_URL] ?: ""
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
