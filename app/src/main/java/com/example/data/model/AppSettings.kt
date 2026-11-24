package com.example.data.model

enum class AppLanguage(val code: String, val displayBn: String, val displayEn: String) {
    BENGALI("bn", "বাংলা (ডিফল্ট)", "Bengali (Default)"),
    ENGLISH("en", "English", "English")
}

enum class SpeechLanguage(val localeTag: String, val displayBn: String, val displayEn: String) {
    BENGALI_BD("bn-BD", "বাংলা (bn-BD)", "Bengali (bn-BD)"),
    ENGLISH_US("en-US", "English (en-US)", "English (en-US)"),
    AUTO("auto", "অটো-ডিটেক্ট", "Auto-detect")
}

enum class VoiceProviderType(
    val id: String,
    val titleBn: String,
    val titleEn: String,
    val subtitleBn: String
) {
    ELEVENLABS(
        id = "elevenlabs",
        titleBn = "ElevenLabs (মাল্টিলিঙ্গুয়াল)",
        titleEn = "ElevenLabs Multilingual",
        subtitleBn = "প্রাকৃতিক বাংলা ও ইংরেজি স্ট্রিমিং কণ্ঠ"
    ),
    OPENAI(
        id = "openai",
        titleBn = "OpenAI TTS",
        titleEn = "OpenAI TTS",
        subtitleBn = "দ্রুত ও পরিষ্কার স্টুডিও-মানের ভয়েস"
    ),
    XAI(
        id = "xai",
        titleBn = "Grok / xAI Voice",
        titleEn = "Grok / xAI Voice",
        subtitleBn = "এক্সপ্রেসিভ রিয়েল-টাইম কনভারসেশনাল কণ্ঠ"
    )
}

enum class AccentColorPreset(val id: String, val labelBn: String, val labelEn: String) {
    VIOLET("violet", "বেগুনি গ্লো", "Violet Glow"),
    CYAN("cyan", "সায়ান অরোরা", "Cyan Aurora"),
    ROSE("rose", "রোজ কোয়ার্টজ", "Rose Quartz"),
    EMERALD("emerald", "এমেরাল্ড মিন্ট", "Emerald Mint"),
    AMBER("amber", "অ্যাম্বার গোল্ড", "Amber Gold")
}

data class VoiceOption(
    val voiceId: String,
    val name: String,
    val descriptionBn: String,
    val descriptionEn: String,
    val provider: VoiceProviderType
)

data class ClaudeModelOption(
    val modelId: String,
    val label: String,
    val noteBn: String
)

object ShailaCatalog {
    val claudeModels = listOf(
        ClaudeModelOption(
            modelId = "claude-sonnet-4-20250514",
            label = "Claude Sonnet 4",
            noteBn = "স্মার্ট, দ্রুত ও প্রাকৃতিক বাংলা (প্রস্তাবিত)"
        ),
        ClaudeModelOption(
            modelId = "claude-3-7-sonnet-20250219",
            label = "Claude 3.7 Sonnet",
            noteBn = "উচ্চমানের বিশ্লেষণ ও সৃজনশীল লেখা"
        ),
        ClaudeModelOption(
            modelId = "claude-3-5-haiku-20241022",
            label = "Claude 3.5 Haiku",
            noteBn = "অত্যন্ত দ্রুত ভয়েস রেসপন্স"
        )
    )

    val elevenLabsVoices = listOf(
        VoiceOption("EXAVITQu4vr4xnSDxMaL", "Sarah", "উষ্ণ ও মমতাময়ী নারী কণ্ঠ", "Warm & expressive female", VoiceProviderType.ELEVENLABS),
        VoiceOption("9BWtsMINqrJLrRacOk9x", "Aria", "প্রাণবন্ত ও আধুনিক সহকারী কণ্ঠ", "Expressive & bright female", VoiceProviderType.ELEVENLABS),
        VoiceOption("21m00Tcm4TlvDq8ikWAM", "Rachel", "শান্ত ও স্পষ্ট কণ্ঠস্বর", "Calm & clear female", VoiceProviderType.ELEVENLABS),
        VoiceOption("XB0fDUnXU5powFXDhCwa", "Charlotte", "কোমল ও গল্প বলার কণ্ঠ", "Soft storytelling voice", VoiceProviderType.ELEVENLABS),
        VoiceOption("pNInz6obpgDQGcFmaJgB", "Adam", "গভীর ও আত্মবিশ্বাসী পুরুষ কণ্ঠ", "Deep & confident male", VoiceProviderType.ELEVENLABS),
        VoiceOption("ErXwobaYiN019PkySvjV", "Antoni", "বন্ধুত্বপূর্ণ পুরুষ কণ্ঠ", "Friendly warm male", VoiceProviderType.ELEVENLABS)
    )

    val openAiVoices = listOf(
        VoiceOption("coral", "Coral", "আন্তরিক ও প্রাণবন্ত নারী কণ্ঠ", "Warm & lively female", VoiceProviderType.OPENAI),
        VoiceOption("nova", "Nova", "স্পষ্ট ও আধুনিক নারী কণ্ঠ", "Clear & modern female", VoiceProviderType.OPENAI),
        VoiceOption("shimmer", "Shimmer", "মৃদু ও স্নিগ্ধ কণ্ঠস্বর", "Gentle & soothing", VoiceProviderType.OPENAI),
        VoiceOption("sage", "Sage", "শান্ত ও বুদ্ধিদীপ্ত কণ্ঠ", "Calm & thoughtful", VoiceProviderType.OPENAI),
        VoiceOption("alloy", "Alloy", "সুষম ও বহুমুখী কণ্ঠ", "Balanced & versatile", VoiceProviderType.OPENAI),
        VoiceOption("onyx", "Onyx", "গভীর পুরুষ কণ্ঠ", "Deep male voice", VoiceProviderType.OPENAI)
    )

    val xaiVoices = listOf(
        VoiceOption("ara", "Ara", "উষ্ণ ও স্বাভাবিক কথোপকথন কণ্ঠ", "Warm conversational female", VoiceProviderType.XAI),
        VoiceOption("eve", "Eve", "প্রাণবন্ত ও উদ্যমী কণ্ঠ", "Energetic & bright", VoiceProviderType.XAI),
        VoiceOption("sal", "Sal", "সুষম ও প্রাকৃতিক কণ্ঠ", "Smooth & balanced", VoiceProviderType.XAI),
        VoiceOption("rex", "Rex", "দৃঢ় ও স্পষ্ট পুরুষ কণ্ঠ", "Confident & clear male", VoiceProviderType.XAI),
        VoiceOption("leo", "Leo", "বন্ধুত্বপূর্ণ পুরুষ কণ্ঠ", "Friendly male", VoiceProviderType.XAI)
    )

    fun voicesFor(provider: VoiceProviderType): List<VoiceOption> = when (provider) {
        VoiceProviderType.ELEVENLABS -> elevenLabsVoices
        VoiceProviderType.OPENAI -> openAiVoices
        VoiceProviderType.XAI -> xaiVoices
    }

    fun defaultVoiceId(provider: VoiceProviderType): String = voicesFor(provider).first().voiceId
}

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.BENGALI,
    val speechLanguage: SpeechLanguage = SpeechLanguage.BENGALI_BD,
    val claudeModel: String = ShailaCatalog.claudeModels.first().modelId,
    val voiceProvider: VoiceProviderType = VoiceProviderType.ELEVENLABS,
    val elevenLabsVoiceId: String = ShailaCatalog.elevenLabsVoices.first().voiceId,
    val openAiVoiceId: String = ShailaCatalog.openAiVoices.first().voiceId,
    val xaiVoiceId: String = ShailaCatalog.xaiVoices.first().voiceId,
    val voiceSpeed: Float = 1.0f,
    val elevenLabsStability: Float = 0.50f,
    val elevenLabsStyle: Float = 0.25f,
    val continuousConversation: Boolean = false,
    val autoSpeakReplies: Boolean = true,
    val holdToTalkMode: Boolean = false,
    val darkTheme: Boolean = true,
    val accentPreset: AccentColorPreset = AccentColorPreset.VIOLET,
    val apkUpdateUrl: String = ""
) {
    fun currentVoiceId(): String = when (voiceProvider) {
        VoiceProviderType.ELEVENLABS -> elevenLabsVoiceId
        VoiceProviderType.OPENAI -> openAiVoiceId
        VoiceProviderType.XAI -> xaiVoiceId
    }
}
