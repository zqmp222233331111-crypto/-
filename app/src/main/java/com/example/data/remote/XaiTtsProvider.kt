package com.example.data.remote

import com.example.data.local.ApiKeyType
import com.example.data.local.SecureStorageManager
import com.example.data.model.VoiceProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class XaiTtsProvider(
    private val secureStorage: SecureStorageManager,
    private val player: AudioStreamPlayer
) : TtsProvider {

    override val providerType = VoiceProviderType.XAI
    override val displayLabel = "Grok / xAI Voice"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    override suspend fun speak(
        text: String,
        voiceId: String,
        options: TtsOptions,
        onPlaybackStarted: () -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val apiKey = secureStorage.getKey(ApiKeyType.XAI)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("xAI API key missing"))
        }

        val payload = JSONObject().apply {
            put("text", text)
            put("voice_id", voiceId)
            put("output_format", JSONObject().apply {
                put("codec", "mp3")
            })
        }

        val request = Request.Builder()
            .url("https://api.x.ai/v1/tts")
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("xAI Voice HTTP ${response.code}")
                    )
                }
                val stream = response.body?.byteStream()
                    ?: return@withContext Result.failure(IllegalStateException("Empty body"))
                player.playStream(stream, "mp3", onPlaybackStarted)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testApiKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("Key is empty"))
        val request = Request.Builder()
            .url("https://api.x.ai/v1/models")
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("xAI / Grok API Key যাচাই সফল হয়েছে!")
                } else {
                    Result.failure(IllegalStateException("xAI HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun stop() {
        player.stop()
    }
}
