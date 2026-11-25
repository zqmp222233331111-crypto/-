package com.example

import com.example.data.model.AppLanguage
import com.example.data.model.AppSettings
import com.example.data.model.MemoryItem
import com.example.data.model.ShailaCatalog
import com.example.data.model.VoiceProviderType
import com.example.data.remote.ClaudeApiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `system prompt enforces Shaila and Amir identity plus memories`() {
        val service = ClaudeApiService()
        val memories = listOf(
            MemoryItem(id = 1, fact = "User runs a tech YouTube channel"),
            MemoryItem(id = 2, fact = "Prefers warm Bengali replies")
        )
        val prompt = service.buildAssistantSystemPrompt(
            memories = memories,
            preferredLanguage = AppLanguage.BENGALI,
            spokenStyle = true
        )

        assertTrue(prompt.contains("Shaila"))
        assertTrue(prompt.contains("শাইলা"))
        assertTrue(prompt.contains("Amir"))
        assertTrue(prompt.contains("আমির"))
        assertTrue(prompt.contains("User runs a tech YouTube channel"))
        assertTrue(prompt.contains("NEVER use markdown formatting"))
        assertTrue(prompt.contains("money/payments, passwords/credentials, or deleting personal data"))
    }

    @Test
    fun `default settings use Bengali and ElevenLabs`() {
        val defaults = AppSettings()
        assertEquals(AppLanguage.BENGALI, defaults.appLanguage)
        assertEquals(VoiceProviderType.ELEVENLABS, defaults.voiceProvider)
        assertTrue(defaults.darkTheme)
        assertTrue(defaults.autoSpeakReplies)
    }

    @Test
    fun `all three voice providers have populated voices`() {
        assertTrue(ShailaCatalog.voicesFor(VoiceProviderType.ELEVENLABS).isNotEmpty())
        assertTrue(ShailaCatalog.voicesFor(VoiceProviderType.OPENAI).isNotEmpty())
        assertTrue(ShailaCatalog.voicesFor(VoiceProviderType.XAI).isNotEmpty())
    }

    @Test
    fun `default image regions stay within normalized bounds`() {
        val cfg = com.example.data.model.DefaultImageRegions.defaultConfig
        com.example.data.model.ImageRegionKey.entries.forEach { key ->
            val rect = cfg.get(key)
            assertTrue(rect.x in 0f..1f)
            assertTrue(rect.y in 0f..1f)
            assertTrue(rect.x + rect.width <= 1.001f)
            assertTrue(rect.y + rect.height <= 1.001f)
        }
    }

    @Test
    fun `parseReleaseJson handles custom manifest and GitHub release JSON`() {
        val customJson = """
            {
              "versionName": "1.2.0",
              "versionCode": 3,
              "apkUrl": "https://example.com/Shaila-1.2.0.apk",
              "fileSizeBytes": 15728640,
              "releaseNotes": "Voice improvements"
            }
        """.trimIndent()
        val parsedCustom = com.example.data.remote.AppUpdateManager.parseReleaseJson(
            body = customJson,
            currentVersionName = "1.0",
            currentVersionCode = 1
        )
        assertEquals("1.2.0", parsedCustom.latestVersionName)
        assertEquals(3, parsedCustom.latestVersionCode)
        assertEquals("https://example.com/Shaila-1.2.0.apk", parsedCustom.resolvedApkUrl)
        assertTrue(parsedCustom.isUpdateAvailable)
        assertEquals("15.0 MB", com.example.data.remote.AppUpdateManager.formatFileSize(parsedCustom.fileSizeBytes))

        val githubJson = """
            {
              "tag_name": "v1.0",
              "body": "Initial release",
              "assets": [
                {
                  "name": "Shaila-release.apk",
                  "size": 10485760,
                  "browser_download_url": "https://github.com/amir/shaila/releases/download/v1.0/Shaila-release.apk"
                }
              ]
            }
        """.trimIndent()
        val parsedGithub = com.example.data.remote.AppUpdateManager.parseReleaseJson(
            body = githubJson,
            currentVersionName = "1.0",
            currentVersionCode = 1
        )
        assertEquals("1.0", parsedGithub.latestVersionName)
        assertEquals(
            "https://github.com/amir/shaila/releases/download/v1.0/Shaila-release.apk",
            parsedGithub.resolvedApkUrl
        )
        assertEquals(false, parsedGithub.isUpdateAvailable)
    }

    @Test
    fun `normalizeReleaseSourceUrl converts GitHub repo URLs to releases API`() {
        assertEquals(
            "https://api.github.com/repos/amir/shaila/releases/latest",
            com.example.data.remote.AppUpdateManager.normalizeReleaseSourceUrl("https://github.com/amir/shaila")
        )
        assertEquals(
            "https://api.github.com/repos/amir/shaila/releases/latest",
            com.example.data.remote.AppUpdateManager.normalizeReleaseSourceUrl("https://github.com/amir/shaila/releases/latest")
        )
        val directApk = "https://github.com/amir/shaila/releases/download/latest/Shaila.apk"
        assertEquals(
            directApk,
            com.example.data.remote.AppUpdateManager.normalizeReleaseSourceUrl(directApk)
        )
    }

    @Test
    fun `api keys snapshot parses comma separated groq keys and checks brain and voice availability`() {
        val snapshot = com.example.data.local.ApiKeysSnapshot(
            groqKey = "gsk_first, gsk_second;\ngsk_third"
        )
        assertEquals(listOf("gsk_first", "gsk_second", "gsk_third"), snapshot.groqKeysList())
        assertTrue(snapshot.hasAnyChatBrainKey())
        assertEquals(false, snapshot.hasAnyVoiceApiKey())

        assertTrue(com.example.data.local.ApiKeysSnapshot(geminiKey = "AIzaTest").hasAnyVoiceApiKey())
        assertTrue(com.example.data.local.ApiKeysSnapshot(elevenLabsKey = "xi-test").hasAnyVoiceApiKey())

        val empty = com.example.data.local.ApiKeysSnapshot()
        assertEquals(false, empty.hasAnyChatBrainKey())
        assertEquals(false, empty.hasAnyVoiceApiKey())
    }

    @Test
    fun `gemini live voices and custom elevenlabs voices merge properly`() {
        val defaults = AppSettings()
        assertEquals(com.example.data.model.GeminiLiveVoice.AOEDE, defaults.geminiLiveVoice)

        val custom = com.example.data.model.VoiceOption(
            voiceId = "custom_123",
            name = "My Female Voice",
            descriptionBn = "Custom",
            descriptionEn = "Custom",
            provider = VoiceProviderType.ELEVENLABS,
            isCustom = true
        )
        val merged = defaults.copy(customElevenLabsVoices = listOf(custom)).allElevenLabsVoices()
        assertEquals("custom_123", merged.first().voiceId)
        assertTrue(merged.size > ShailaCatalog.elevenLabsVoices.size)
    }

    @Test
    fun `character and personality defaults plus custom prompt work cleanly`() {
        val defaults = AppSettings()
        assertEquals("Shaila", defaults.assistantName)
        assertEquals(com.example.data.model.HomeCharacterStyle.HALF_BODY_SHAILA, defaults.homeCharacterStyle)
        assertEquals(com.example.data.model.PersonalityPreset.FRIENDLY, defaults.personalityPreset)
        assertEquals(com.example.data.model.ResponseStyle.BALANCED, defaults.responseStyle)
        assertEquals(false, defaults.showHerEverywhere)
        assertEquals(false, defaults.reduceMotion)
        assertEquals(false, defaults.listenOnAppOpen)

        val customized = defaults.copy(
            assistantName = "Nadia",
            personalityPreset = com.example.data.model.PersonalityPreset.CUSTOM,
            customPersonalityName = "Sweet Companion",
            customPersonalityPrompt = "Speak gently with poetic Bengali warmth.",
            responseStyle = com.example.data.model.ResponseStyle.CONCISE
        )
        assertEquals("Sweet Companion", customized.effectivePersonalityLabel(true))

        val prompt = ClaudeApiService().buildAssistantSystemPrompt(
            memories = emptyList(),
            preferredLanguage = AppLanguage.BENGALI,
            spokenStyle = true,
            assistantName = customized.assistantName,
            personalityPreset = customized.personalityPreset,
            customPersonalityPrompt = customized.customPersonalityPrompt,
            responseStyle = customized.responseStyle
        )
        assertTrue(prompt.contains("Nadia"))
        assertTrue(prompt.contains("Speak gently with poetic Bengali warmth."))
        assertTrue(prompt.contains("1-2 natural sentences"))
        assertTrue(prompt.contains("Amir"))
    }

    @Test
    fun `pcm audio processor amplifies quiet speech safely and builds valid wav header`() {
        // Create 4 samples of quiet 16-bit PCM: +4000, -4000, +25000 (near peak), -25000
        val buf = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.putShort(4000)
        buf.putShort(-4000)
        buf.putShort(25000)
        buf.putShort(-25000)
        val input = buf.array()

        val boosted = com.example.data.remote.PcmAudioProcessor.applySoftGain(input, gain = 2.0f)
        val outBuf = java.nio.ByteBuffer.wrap(boosted).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        // Quiet sample should be doubled cleanly without clipping
        assertTrue(outBuf.getShort(0) in 7900..8100)
        assertTrue(outBuf.getShort(2) in -8100..-7900)
        // Peak sample should stay soft-limited within Short range without wrapping
        assertTrue(outBuf.getShort(4) in 25000..Short.MAX_VALUE)
        assertTrue(outBuf.getShort(6) in Short.MIN_VALUE..-25000)

        assertTrue(com.example.data.remote.PcmAudioProcessor.computePcm16RmsNormalized(boosted) > 0.5f)
        assertEquals(
            24000,
            com.example.data.remote.PcmAudioProcessor.parseSampleRateFromMime("audio/L16;codec=pcm;rate=24000")
        )

        val wav = com.example.data.remote.PcmAudioProcessor.wrapPcm16AsWav(boosted, sampleRate = 24000)
        assertEquals(44 + boosted.size, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals("data", String(wav, 36, 4))
    }

    @Test
    fun `phone action parser extracts all actions and strips tags from spoken reply`() {
        val raw = """
            ঠিক আছে, ইউটিউব খুলে বাংলা গান লিখে দিচ্ছি।
            [[ACTION:OPEN_APP|YouTube]]
            [[ACTION:TAP_TEXT|Search]]
            [[ACTION:TYPE_TEXT|বাংলা রোমান্টিক গান]]
            [[ACTION:SUBMIT]]
            [[ACTION:SCROLL|down]]
            [[ACTION:GLOBAL|back]]
            [[ACTION:OPEN_URL|https://example.com]]
        """.trimIndent()

        val (cleanText, actions) = com.example.data.device.PhoneActionParser.parseActionTags(raw)
        assertEquals("ঠিক আছে, ইউটিউব খুলে বাংলা গান লিখে দিচ্ছি।", cleanText)
        assertEquals(7, actions.size)
        assertEquals(com.example.data.device.PhoneAction.OpenApp("YouTube"), actions[0])
        assertEquals(com.example.data.device.PhoneAction.TapText("Search"), actions[1])
        assertEquals(com.example.data.device.PhoneAction.TypeText("বাংলা রোমান্টিক গান"), actions[2])
        assertEquals(com.example.data.device.PhoneAction.Submit, actions[3])
        assertEquals(com.example.data.device.PhoneAction.Scroll("down"), actions[4])
        assertEquals(com.example.data.device.PhoneAction.SystemNav("back"), actions[5])
        assertEquals(com.example.data.device.PhoneAction.OpenUrl("https://example.com"), actions[6])
    }

    @Test
    fun `fallback phone command parser detects Bengali app open search type and hardware commands`() {
        val ytSearch = com.example.data.device.PhoneActionParser.inferFallbackActionsFromUserPrompt(
            "ইউটিউব খুলে বাংলা রোমান্টিক গান লিখে সার্চ করো"
        )
        assertTrue(ytSearch.contains(com.example.data.device.PhoneAction.OpenApp("youtube")))
        assertTrue(ytSearch.contains(com.example.data.device.PhoneAction.TypeText("বাংলা রোমান্টিক গান")))
        assertTrue(ytSearch.contains(com.example.data.device.PhoneAction.Submit))

        val torchOn = com.example.data.device.PhoneActionParser.inferFallbackActionsFromUserPrompt("টর্চ জ্বালাও")
        assertEquals(listOf(com.example.data.device.PhoneAction.Flashlight(true)), torchOn)

        val dial = com.example.data.device.PhoneActionParser.inferFallbackActionsFromUserPrompt("01712345678 নম্বরে কল দাও")
        assertEquals(listOf(com.example.data.device.PhoneAction.Dial("01712345678")), dial)

        val prompt = ClaudeApiService().buildAssistantSystemPrompt(
            memories = emptyList(),
            preferredLanguage = AppLanguage.BENGALI,
            spokenStyle = true
        )
        assertTrue(prompt.contains("NEVER sound like a robotic AI"))
        assertTrue(prompt.contains("LOYALTY & USER RETENTION"))
    }
}
