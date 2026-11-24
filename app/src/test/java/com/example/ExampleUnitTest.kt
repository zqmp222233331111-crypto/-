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
}
