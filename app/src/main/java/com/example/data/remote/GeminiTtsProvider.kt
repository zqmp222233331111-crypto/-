package com.example.data.remote

import android.util.Base64
import com.example.data.local.ApiKeyType
import com.example.data.local.SecureStorageManager
import com.example.data.model.GeminiLiveVoice
import com.example.data.model.VoiceProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Generates natural human speech via Gemini TTS (`gemini-2.5-flash-preview-tts`) using the
 * user's existing Gemini API key so Home & Chat replies also get rich female voices (Aoede, Kore,
 * Leda, Zephyr) without needing any extra paid voice key.
 */
class GeminiTtsProvider(
    private val secureStorage: SecureStorageManager,
    private val streamPlayer: AudioStreamPlayer
) : TtsProvider {

    override val providerType: VoiceProviderType? = null
    override val displayLabel: String = "Gemini Voice"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    override suspend fun speak(
        text: String,
        voiceId: String,
        options: TtsOptions,
        onPlaybackStarted: () -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val apiKey = secureStorage.getKey(ApiKeyType.GEMINI).trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key missing"))
        }

        val resolvedVoice = voiceId.ifBlank { GeminiLiveVoice.AOEDE.voiceName }
        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", text))
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put(
                        "speechConfig",
                        JSONObject().put(
                            "voiceConfig",
                            JSONObject().put(
                                "prebuiltVoiceConfig",
                                JSONObject().put("voiceName", resolvedVoice)
                            )
                        )
                    )
                }
            )
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Gemini TTS HTTP ${response.code}")
                    )
                }

                val json = JSONObject(raw)
                val parts = json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?: return@withContext Result.failure(IllegalStateException("No audio parts in Gemini TTS response"))

                var audioBase64 = ""
                var mimeType = "audio/L16;codec=pcm;rate=24000"
                for (i in 0 until parts.length()) {
                    val inlineData = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                    val data = inlineData.optString("data")
                    val mime = inlineData.optString("mimeType")
                    if (data.isNotBlank() && mime.startsWith("audio", ignoreCase = true)) {
                        audioBase64 = data
                        mimeType = mime
                        break
                    }
                }

                if (audioBase64.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Empty Gemini TTS audio"))
                }

                val decoded = Base64.decode(audioBase64, Base64.DEFAULT)
                val audioBytes: Pair<ByteArray, String> = if (mimeType.contains("wav", ignoreCase = true)) {
                    decoded to "wav"
                } else if (mimeType.contains("mpeg", ignoreCase = true) || mimeType.contains("mp3", ignoreCase = true)) {
                    decoded to "mp3"
                } else {
                    val sampleRate = PcmAudioProcessor.parseSampleRateFromMime(mimeType, 24000)
                    val boostedPcm = PcmAudioProcessor.applySoftGain(decoded, gain = 1.95f)
                    PcmAudioProcessor.wrapPcm16AsWav(boostedPcm, sampleRate = sampleRate) to "wav"
                }

                streamPlayer.playAudioBytes(
                    bytes = audioBytes.first,
                    fileExtension = audioBytes.second,
                    onStarted = onPlaybackStarted
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun stop() {
        streamPlayer.stop()
    }
}
