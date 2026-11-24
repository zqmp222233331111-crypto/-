package com.example.data.remote

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.data.model.AppLanguage
import com.example.data.model.VoiceProviderType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

class AndroidBuiltInTtsProvider(private val context: Context) : TtsProvider {

    override val providerType: VoiceProviderType? = null
    override val displayLabel = "Phone TTS"

    private var tts: TextToSpeech? = null
    private val readyDeferred = CompletableDeferred<Boolean>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            readyDeferred.complete(status == TextToSpeech.SUCCESS)
        }
    }

    override suspend fun speak(
        text: String,
        voiceId: String,
        options: TtsOptions,
        onPlaybackStarted: () -> Unit
    ): Result<Unit> = withContext(Dispatchers.Main) {
        val ready = readyDeferred.await()
        val engine = tts
        if (!ready || engine == null) {
            return@withContext Result.failure(IllegalStateException("Device TTS unavailable"))
        }

        val hasBengali = text.any { it in '\u0980'..'\u09FF' }
        val targetLocale = if (hasBengali || options.language == AppLanguage.BENGALI) {
            Locale.forLanguageTag("bn-BD")
        } else {
            Locale.US
        }

        val langResult = engine.setLanguage(targetLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(if (hasBengali) Locale.forLanguageTag("bn-IN") else Locale.US)
        }
        engine.setSpeechRate(options.speed.coerceIn(0.6f, 1.6f))

        suspendCancellableCoroutine { cont ->
            val utteranceId = UUID.randomUUID().toString()
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    if (id == utteranceId) onPlaybackStarted()
                }

                override fun onDone(id: String?) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resume(Result.success(Unit))
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resume(Result.failure(IllegalStateException("Device TTS error")))
                    }
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resume(Result.failure(IllegalStateException("Device TTS error $errorCode")))
                    }
                }
            })

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            val queued = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (queued != TextToSpeech.SUCCESS && cont.isActive) {
                cont.resume(Result.failure(IllegalStateException("TTS queue failed")))
            }

            cont.invokeOnCancellation {
                engine.stop()
            }
        }
    }

    override fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching { tts?.shutdown() }
        tts = null
    }
}
