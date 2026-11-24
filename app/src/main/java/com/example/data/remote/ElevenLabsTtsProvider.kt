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

class ElevenLabsTtsProvider(
    private val secureStorage: SecureStorageManager,
    private val player: AudioStreamPlayer
) : TtsProvider {

    override val providerType = VoiceProviderType.ELEVENLABS
    override val displayLabel = "ElevenLabs"

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
        val apiKey = secureStorage.getKey(ApiKeyType.ELEVENLABS)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("ElevenLabs API key missing"))
        }

        val payload = JSONObject().apply {
            put("text", text)
            put("model_id", "eleven_multilingual_v2")
            put(
                "voice_settings",
                JSONObject().apply {
                    put("stability", options.stability.toDouble())
                    put("similarity_boost", 0.78)
                    put("style", options.style.toDouble())
                    put("use_speaker_boost", true)
                    put("speed", options.speed.coerceIn(0.7f, 1.2f).toDouble())
                }
            )
        }

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId/stream?optimize_streaming_latency=2")
            .addHeader("xi-api-key", apiKey.trim())
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "audio/mpeg")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("ElevenLabs HTTP ${response.code}")
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
            .url("https://api.elevenlabs.io/v1/user")
            .addHeader("xi-api-key", apiKey.trim())
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("ElevenLabs API Key যাচাই সফল হয়েছে!")
                } else {
                    Result.failure(IllegalStateException("ElevenLabs HTTP ${response.code}"))
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
