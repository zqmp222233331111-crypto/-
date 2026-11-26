package com.example.data.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.data.model.AppLanguage
import com.example.data.model.SpeechLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class SpeechEvent {
    data object Idle : SpeechEvent()
    data object Listening : SpeechEvent()
    data class Partial(val text: String) : SpeechEvent()
    data class FinalResult(val text: String) : SpeechEvent()
    data class Error(val messageBn: String, val messageEn: String) : SpeechEvent()
}

class SpeechRecognitionManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    private val _events = MutableStateFlow<SpeechEvent>(SpeechEvent.Idle)
    val events: StateFlow<SpeechEvent> = _events.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        speechLanguage: SpeechLanguage,
        appLanguage: AppLanguage
    ) {
        if (!isAvailable()) {
            _events.value = SpeechEvent.Error(
                messageBn = "এই ডিভাইসে ভয়েস রিকগনিশন সার্ভিস পাওয়া যায়নি।",
                messageEn = "Speech recognition service is unavailable on this device."
            )
            return
        }

        stopListeningInternal(resetState = false)

        val instance = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        recognizer = instance

        instance.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _events.value = SpeechEvent.Listening
            }

            override fun onBeginningOfSpeech() {
                _events.value = SpeechEvent.Listening
            }

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _rmsLevel.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                _rmsLevel.value = 0f
            }

            override fun onError(error: Int) {
                _rmsLevel.value = 0f
                val (bn, en) = mapError(error)
                _events.value = SpeechEvent.Error(bn, en)
            }

            override fun onResults(results: Bundle?) {
                _rmsLevel.value = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalText = matches?.firstOrNull().orEmpty().trim()
                if (finalText.isNotEmpty()) {
                    _events.value = SpeechEvent.FinalResult(finalText)
                } else {
                    _events.value = SpeechEvent.Error(
                        messageBn = "কিছু শুনতে পাইনি, অনুগ্রহ করে আবার বলুন।",
                        messageEn = "Didn't catch that. Please try speaking again."
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull().orEmpty().trim()
                if (partial.isNotEmpty()) {
                    _events.value = SpeechEvent.Partial(partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val primaryLocale = when (speechLanguage) {
            SpeechLanguage.BENGALI_BD -> "bn-BD"
            SpeechLanguage.ENGLISH_US -> "en-US"
            SpeechLanguage.AUTO -> if (appLanguage == AppLanguage.BENGALI) "bn-BD" else "en-US"
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryLocale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryLocale)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 360L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 280L)
            if (speechLanguage == SpeechLanguage.AUTO) {
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("bn-BD", "en-US"))
            }
        }

        _events.value = SpeechEvent.Listening
        instance.startListening(intent)
    }

    fun stopListening() {
        runCatching { recognizer?.stopListening() }
    }

    fun cancel() {
        stopListeningInternal(resetState = true)
    }

    fun resetToIdle() {
        _events.value = SpeechEvent.Idle
        _rmsLevel.value = 0f
    }

    private fun stopListeningInternal(resetState: Boolean) {
        _rmsLevel.value = 0f
        runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
        if (resetState) {
            _events.value = SpeechEvent.Idle
        }
    }

    fun destroy() {
        stopListeningInternal(resetState = true)
    }

    private fun mapError(code: Int): Pair<String, String> = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH ->
            "কথা বোঝা যায়নি, অনুগ্রহ করে আরেকটু স্পষ্টভাবে বলুন।" to "Could not recognize speech. Please try again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "কোনো কথা শোনা যায়নি। আবার মাইক্রোফোন ট্যাপ করুন।" to "No speech heard. Tap the mic to try again."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "ভয়েস চিনতে ইন্টারনেট সংযোগ প্রয়োজন।" to "Network connection needed for speech recognition."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "মাইক্রোফোন অনুমতি প্রয়োজন।" to "Microphone permission is required."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "ভয়েস ইঞ্জিন ব্যস্ত আছে, এক মুহূর্ত অপেক্ষা করুন।" to "Speech recognizer is busy, please wait."
        else ->
            "ভয়েস ইনপুটে সমস্যা হয়েছে। আবার চেষ্টা করুন।" to "Voice input failed. Please try again."
    }
}
