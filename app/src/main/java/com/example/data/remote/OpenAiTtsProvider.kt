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

class OpenAiTtsProvider(
    private val secureStorage: SecureStorageManager,
    private val player: AudioStreamPlayer
) : TtsProvider {

    override val providerType = VoiceProviderType.OPENAI
    override val displayLabel = "OpenAI TTS"

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
        val apiKey = secureStorage.getKey(ApiKeyType.OPENAI)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("OpenAI API key missing"))
        }

        val payload = JSONObject().apply {
            put("model", "gpt-4o-mini-tts")
            put("input", text)
            put("voice", voiceId)
            put("speed", options.speed.coerceIn(0.5f, 1.8f).toDouble())
            put("response_format", "mp3")
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/speech")
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("OpenAI TTS HTTP ${response.code}")
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
            .url("https://api.openai.com/v1/models")
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("OpenAI API Key যাচাই সফল হয়েছে!")
                } else {
                    Result.failure(IllegalStateException("OpenAI HTTP ${response.code}"))
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
