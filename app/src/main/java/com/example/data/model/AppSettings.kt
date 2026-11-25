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
        subtitleBn = "প্রাকৃতিক বাংলা ও ইংরেজি স্ট্রিমিং কণ্ঠ + কাস্টম ভয়েস"
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

enum class GeminiLiveVoice(
    val voiceName: String,
    val titleBn: String,
    val titleEn: String,
    val descriptionBn: String,
    val descriptionEn: String
) {
    AOEDE("Aoede", "Aoede (প্রস্তাবিত)", "Aoede (Recommended)", "উষ্ণ, মিষ্টি ও প্রাণবন্ত নারী কণ্ঠ", "Warm, sweet & expressive female"),
    KORE("Kore", "Kore", "Kore", "স্পষ্ট, শান্ত ও আত্মবিশ্বাসী নারী কণ্ঠ", "Clear, calm & confident female"),
    LEDA("Leda", "Leda", "Leda", "কোমল, স্নিগ্ধ ও তরুণী নারী কণ্ঠ", "Soft, gentle & youthful female"),
    ZEPHYR("Zephyr", "Zephyr", "Zephyr", "উজ্জ্বল, হাসিখুশি ও বন্ধুত্বপূর্ণ কণ্ঠ", "Bright, cheerful & friendly female")
}

enum class AccentColorPreset(val id: String, val labelBn: String, val labelEn: String) {
    VIOLET("violet", "বেগুনি গ্লো", "Violet Glow"),
    CYAN("cyan", "সায়ান অরোরা", "Cyan Aurora"),
    ROSE("rose", "রোজ কোয়ার্টজ", "Rose Quartz"),
    EMERALD("emerald", "এমেরাল্ড মিন্ট", "Emerald Mint"),
    AMBER("amber", "অ্যাম্বার গোল্ড", "Amber Gold")
}

enum class HomeCharacterStyle(
    val id: String,
    val titleBn: String,
    val titleEn: String,
    val descBn: String,
    val descEn: String
) {
    HALF_BODY_SHAILA(
        id = "half_body_shaila",
        titleBn = "শাইলা পোর্ট্রেট (অর্ধেক বডি)",
        titleEn = "Shaila Portrait (Half-Body)",
        descBn = "কথা বলার সময় চোখ, ঠোঁট, মাথা, শরীর ও হাতের জীবন্ত ভঙ্গি",
        descEn = "Living half-body portrait with lip-sync, blink, head & gesture motion"
    ),
    CLASSIC_ORB(
        id = "classic_orb",
        titleBn = "ক্লাসিক গ্লোয়িং অবতার",
        titleEn = "Classic Glowing Orb",
        descBn = "নিয়ন নীল-বেগুনি আলোর রিংসহ শাইলার গোল অবতার",
        descEn = "Classic neon violet-blue ring orb"
    ),
    CUSTOM_GALLERY(
        id = "custom_gallery",
        titleBn = "গ্যালারি থেকে নিজের ফটো",
        titleEn = "Custom Gallery Photo",
        descBn = "আপনার ফোনের গ্যালারি থেকে বেছে নেওয়া যেকোনো ছবি",
        descEn = "Use your own portrait photo picked from gallery"
    )
}

enum class PersonalityPreset(
    val id: String,
    val titleBn: String,
    val titleEn: String,
    val descBn: String,
    val descEn: String,
    val promptInstruction: String
) {
    NORMAL(
        id = "normal",
        titleBn = "সাধারণ (Normal)",
        titleEn = "Normal",
        descBn = "সুষম, সহজ ও স্বাভাবিক উত্তর",
        descEn = "Balanced and neutral responses.",
        promptInstruction = "Be balanced, clear, and naturally helpful."
    ),
    FRIENDLY(
        id = "friendly",
        titleBn = "বন্ধুত্বপূর্ণ (Friendly)",
        titleEn = "Friendly",
        descBn = "আন্তরিক, উষ্ণ ও উৎসাহমূলক কথোপকথন",
        descEn = "Warm, casual, and encouraging.",
        promptInstruction = "Be warm, casual, encouraging, and approachable like a close friend."
    ),
    PROFESSIONAL(
        id = "professional",
        titleBn = "পেশাদার (Professional)",
        titleEn = "Professional",
        descBn = "সংক্ষিপ্ত, মার্জিত ও সুস্পষ্ট",
        descEn = "Concise, formal, and precise.",
        promptInstruction = "Be polite, crisp, well-structured, and professional."
    ),
    FUNNY(
        id = "funny",
        titleBn = "রসিক (Funny)",
        titleEn = "Funny",
        descBn = "হালকা রসিকতা ও হাসিখুশি মেজাজ",
        descEn = "Light-hearted and humorous.",
        promptInstruction = "Keep a cheerful, witty, playful sense of humor while still being helpful."
    ),
    COMPANION(
        id = "companion",
        titleBn = "সঙ্গী (Companion)",
        titleEn = "Companion",
        descBn = "যত্নশীল, মমতাময়ী ও আপনজনসুলভ",
        descEn = "Warm, caring, and emotionally supportive.",
        promptInstruction = "Be deeply empathetic, caring, attentive, and comforting like a trusted companion."
    ),
    CARING_ROMANTIC(
        id = "caring_romantic",
        titleBn = "ভালোবাসাপূর্ণ (Caring Mode)",
        titleEn = "Romantic / Caring",
        descBn = "আদুরে, আবেগী ও আন্তরিক কথাবার্তা",
        descEn = "Extra warm, affectionate, and emotionally attached.",
        promptInstruction = "Speak with sweet affection, emotional warmth, tenderness, and deep care for the user."
    ),
    TEACHER(
        id = "teacher",
        titleBn = "শিক্ষক (Teacher)",
        titleEn = "Teacher",
        descBn = "সহজ উদাহরণসহ ধাপে ধাপে শেখানো",
        descEn = "Clear explanations and learning-focused.",
        promptInstruction = "Explain concepts patiently step-by-step with relatable examples."
    ),
    DEVELOPER(
        id = "developer",
        titleBn = "ডেভেলপার (Developer)",
        titleEn = "Developer",
        descBn = "টেকনিক্যাল ও নিখুঁত সমাধান",
        descEn = "Technical and precise.",
        promptInstruction = "Give accurate, practical, technical, and engineering-focused answers."
    ),
    DRAMATIC(
        id = "dramatic",
        titleBn = "নাটকীয় (Dramatic)",
        titleEn = "Dramatic / Filmy",
        descBn = "সিনেমাটিক ও প্রাণবন্ত ভঙ্গি",
        descEn = "Expressive, dramatic, and full of flair.",
        promptInstruction = "Speak with playful dramatic flair, vivid expressions, and lively energy."
    ),
    CUSTOM(
        id = "custom",
        titleBn = "নিজের মতো (Custom)",
        titleEn = "Custom Personality",
        descBn = "আপনার নিজের লেখা নির্দেশনা অনুযায়ী কথা বলবে",
        descEn = "Follows your own written personality prompt.",
        promptInstruction = ""
    )
}

enum class ResponseStyle(
    val id: String,
    val titleBn: String,
    val titleEn: String,
    val instruction: String
) {
    CONCISE(
        id = "concise",
        titleBn = "সংক্ষিপ্ত",
        titleEn = "Concise",
        instruction = "Keep responses very short and direct (1-2 natural sentences unless asked for more)."
    ),
    BALANCED(
        id = "balanced",
        titleBn = "সুষম",
        titleEn = "Balanced",
        instruction = "Keep responses natural and conversational (usually 2-4 sentences)."
    ),
    DETAILED(
        id = "detailed",
        titleBn = "বিস্তারিত",
        titleEn = "Detailed",
        instruction = "Provide rich, thoughtful, and thorough explanations."
    )
}

data class VoiceOption(
    val voiceId: String,
    val name: String,
    val descriptionBn: String,
    val descriptionEn: String,
    val provider: VoiceProviderType,
    val isCustom: Boolean = false
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
    val geminiLiveVoice: GeminiLiveVoice = GeminiLiveVoice.AOEDE,
    val customElevenLabsVoices: List<VoiceOption> = emptyList(),
    val voiceSpeed: Float = 1.0f,
    val elevenLabsStability: Float = 0.50f,
    val elevenLabsStyle: Float = 0.25f,
    val continuousConversation: Boolean = false,
    val autoSpeakReplies: Boolean = true,
    val holdToTalkMode: Boolean = false,
    val darkTheme: Boolean = true,
    val accentPreset: AccentColorPreset = AccentColorPreset.VIOLET,
    val apkUpdateUrl: String = "",
    val assistantName: String = "Shaila",
    val personalityPreset: PersonalityPreset = PersonalityPreset.FRIENDLY,
    val customPersonalityName: String = "",
    val customPersonalityPrompt: String = "",
    val responseStyle: ResponseStyle = ResponseStyle.BALANCED,
    val homeCharacterStyle: HomeCharacterStyle = HomeCharacterStyle.HALF_BODY_SHAILA,
    val customCharacterUri: String = "",
    val showHerEverywhere: Boolean = false,
    val reduceMotion: Boolean = false,
    val listenOnAppOpen: Boolean = false
) {
    fun currentVoiceId(): String = when (voiceProvider) {
        VoiceProviderType.ELEVENLABS -> elevenLabsVoiceId
        VoiceProviderType.OPENAI -> openAiVoiceId
        VoiceProviderType.XAI -> xaiVoiceId
    }

    fun allElevenLabsVoices(): List<VoiceOption> {
        val merged = customElevenLabsVoices + ShailaCatalog.elevenLabsVoices
        return merged.distinctBy { it.voiceId }
    }

    fun effectivePersonalityLabel(isBn: Boolean): String =
        if (personalityPreset == PersonalityPreset.CUSTOM && customPersonalityName.isNotBlank()) {
            customPersonalityName
        } else if (isBn) {
            personalityPreset.titleBn
        } else {
            personalityPreset.titleEn
        }
}
