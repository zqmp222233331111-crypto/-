package com.example.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Base64
import com.example.data.model.AppLanguage
import com.example.data.model.GeminiLiveVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

enum class LiveCallStatus {
    IDLE,
    CONNECTING,
    LISTENING,
    SPEAKING,
    ERROR
}

data class LiveCallSessionState(
    val status: LiveCallStatus = LiveCallStatus.IDLE,
    val isMuted: Boolean = false,
    val isMinimized: Boolean = false,
    val activeVoiceName: String = GeminiLiveVoice.AOEDE.voiceName,
    val modeLabel: String = "Gemini Live",
    val liveCaption: String = "",
    val errorMessage: String? = null
) {
    val isActive: Boolean
        get() = status == LiveCallStatus.CONNECTING ||
            status == LiveCallStatus.LISTENING ||
            status == LiveCallStatus.SPEAKING
}

/**
 * Bidirectional real-time voice conversation manager backed by Google Gemini Live WebSocket API
 * (`BidiGenerateContent`), streaming 16kHz PCM microphone input and playing 24kHz PCM audio output
 * with live RMS energy measurement for real-time lip-sync animation.
 */
class GeminiLiveManager(
    context: Context,
    private val restService: GroqAndGeminiRestService
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _sessionState = MutableStateFlow(LiveCallSessionState())
    val sessionState: StateFlow<LiveCallSessionState> = _sessionState.asStateFlow()

    private val _speakingLevel = MutableStateFlow(0f)
    val speakingLevel: StateFlow<Float> = _speakingLevel.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var audioTrack: AudioTrack? = null
    private var micStreamJob: Job? = null
    private var silenceDecayJob: Job? = null
    private var sessionJob: Job? = null

    private val audioWriteLock = Any()
    @Volatile
    private var lastAudioWriteAtMs = 0L
    @Volatile
    private var setupComplete = false
    @Volatile
    private var manualStopRequested = false

    fun startSession(
        apiKey: String,
        voice: GeminiLiveVoice,
        systemPrompt: String,
        language: AppLanguage,
        onTranscriptTurn: (userText: String, assistantText: String) -> Unit = { _, _ -> }
    ) {
        endSession()
        val key = apiKey.trim()
        if (key.isBlank()) {
            _sessionState.value = LiveCallSessionState(
                status = LiveCallStatus.ERROR,
                activeVoiceName = voice.voiceName,
                errorMessage = if (language == AppLanguage.BENGALI) {
                    "লাইভ কলের জন্য Settings → API Keys-এ একটি ফ্রি Gemini Key যোগ করুন।"
                } else {
                    "Please add a free Gemini API key in Settings → API Keys for Live Voice."
                }
            )
            return
        }

        manualStopRequested = false
        setupComplete = false
        _sessionState.value = LiveCallSessionState(
            status = LiveCallStatus.CONNECTING,
            isMuted = false,
            isMinimized = false,
            activeVoiceName = voice.voiceName,
            modeLabel = "Gemini Live • ${voice.voiceName}",
            liveCaption = if (language == AppLanguage.BENGALI) "শাইলার সাথে সংযুক্ত হচ্ছে..." else "Connecting to Shaila..."
        )

        sessionJob = scope.launch {
            val candidateModels = restService.discoverLiveModels(key)
            val modelName = candidateModels.firstOrNull()
                ?: "models/gemini-2.5-flash-native-audio-preview-12-2025"
            openWebSocket(
                apiKey = key,
                candidateModels = candidateModels,
                modelIndex = 0,
                initialModel = modelName,
                voice = voice,
                systemPrompt = systemPrompt,
                language = language,
                onTranscriptTurn = onTranscriptTurn
            )
        }
    }

    private fun openWebSocket(
        apiKey: String,
        candidateModels: List<String>,
        modelIndex: Int,
        initialModel: String,
        voice: GeminiLiveVoice,
        systemPrompt: String,
        language: AppLanguage,
        onTranscriptTurn: (String, String) -> Unit
    ) {
        if (manualStopRequested) return

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        val userBuffer = StringBuilder()
        val assistantBuffer = StringBuilder()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                val setupPayload = JSONObject().apply {
                    put(
                        "setup",
                        JSONObject().apply {
                            put("model", initialModel)
                            put(
                                "generationConfig",
                                JSONObject().apply {
                                    put("responseModalities", JSONArray().put("AUDIO"))
                                    put(
                                        "speechConfig",
                                        JSONObject().apply {
                                            put(
                                                "voiceConfig",
                                                JSONObject().apply {
                                                    put(
                                                        "prebuiltVoiceConfig",
                                                        JSONObject().put("voiceName", voice.voiceName)
                                                    )
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                            put(
                                "systemInstruction",
                                JSONObject().put(
                                    "parts",
                                    JSONArray().put(JSONObject().put("text", systemPrompt))
                                )
                            )
                            put("inputAudioTranscription", JSONObject())
                            put("outputAudioTranscription", JSONObject())
                        }
                    )
                }
                ws.send(setupPayload.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleServerJson(
                    raw = text,
                    language = language,
                    userBuffer = userBuffer,
                    assistantBuffer = assistantBuffer,
                    onTranscriptTurn = onTranscriptTurn
                )
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                handleServerJson(
                    raw = bytes.utf8(),
                    language = language,
                    userBuffer = userBuffer,
                    assistantBuffer = assistantBuffer,
                    onTranscriptTurn = onTranscriptTurn
                )
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                if (!setupComplete && !manualStopRequested && modelIndex + 1 < candidateModels.size) {
                    val nextModel = candidateModels[modelIndex + 1]
                    openWebSocket(
                        apiKey = apiKey,
                        candidateModels = candidateModels,
                        modelIndex = modelIndex + 1,
                        initialModel = nextModel,
                        voice = voice,
                        systemPrompt = systemPrompt,
                        language = language,
                        onTranscriptTurn = onTranscriptTurn
                    )
                } else if (!manualStopRequested && _sessionState.value.status != LiveCallStatus.ERROR) {
                    stopAudioHardware()
                    val msg = reason.ifBlank {
                        if (language == AppLanguage.BENGALI) "লাইভ সংযোগ বন্ধ হয়েছে (Code $code)" else "Live session closed ($code)"
                    }
                    _sessionState.value = _sessionState.value.copy(
                        status = if (code == 1000) LiveCallStatus.IDLE else LiveCallStatus.ERROR,
                        errorMessage = if (code == 1000) null else msg
                    )
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (manualStopRequested) return
                if (!setupComplete && modelIndex + 1 < candidateModels.size) {
                    val nextModel = candidateModels[modelIndex + 1]
                    openWebSocket(
                        apiKey = apiKey,
                        candidateModels = candidateModels,
                        modelIndex = modelIndex + 1,
                        initialModel = nextModel,
                        voice = voice,
                        systemPrompt = systemPrompt,
                        language = language,
                        onTranscriptTurn = onTranscriptTurn
                    )
                    return
                }
                stopAudioHardware()
                _sessionState.value = _sessionState.value.copy(
                    status = LiveCallStatus.ERROR,
                    errorMessage = t.localizedMessage ?: if (language == AppLanguage.BENGALI) {
                        "লাইভ সংযোগ ব্যর্থ হয়েছে। ইন্টারনেট ও Gemini Key যাচাই করুন।"
                    } else {
                        "Live connection failed. Please check internet and Gemini Key."
                    }
                )
            }
        })
    }

    private fun handleServerJson(
        raw: String,
        language: AppLanguage,
        userBuffer: StringBuilder,
        assistantBuffer: StringBuilder,
        onTranscriptTurn: (String, String) -> Unit
    ) {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return

        if (root.has("setupComplete") && !setupComplete) {
            setupComplete = true
            startAudioOutput()
            startMicStreaming()
            startSilenceDecayLoop()
            _sessionState.value = _sessionState.value.copy(
                status = LiveCallStatus.LISTENING,
                liveCaption = if (language == AppLanguage.BENGALI) "শাইলা শুনছে... সরাসরি কথা বলুন" else "Shaila is listening... Speak naturally"
            )
            return
        }

        val serverContent = root.optJSONObject("serverContent") ?: return

        if (serverContent.optBoolean("interrupted", false)) {
            flushAudioTrack()
            _speakingLevel.value = 0f
            _sessionState.value = _sessionState.value.copy(
                status = LiveCallStatus.LISTENING,
                liveCaption = if (language == AppLanguage.BENGALI) "শুনছি..." else "Listening..."
            )
        }

        val inTranscript = serverContent.optJSONObject("inputTranscription")?.optString("text").orEmpty()
        if (inTranscript.isNotBlank()) {
            userBuffer.append(inTranscript)
            _sessionState.value = _sessionState.value.copy(
                liveCaption = userBuffer.toString().trim().takeLast(90)
            )
        }

        val outTranscript = serverContent.optJSONObject("outputTranscription")?.optString("text").orEmpty()
        if (outTranscript.isNotBlank()) {
            assistantBuffer.append(outTranscript)
            _sessionState.value = _sessionState.value.copy(
                liveCaption = assistantBuffer.toString().trim().takeLast(110)
            )
        }

        val modelTurn = serverContent.optJSONObject("modelTurn")
        val parts = modelTurn?.optJSONArray("parts")
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val inlineData = part.optJSONObject("inlineData") ?: continue
                val mime = inlineData.optString("mimeType")
                val b64 = inlineData.optString("data")
                if (mime.startsWith("audio/pcm", ignoreCase = true) && b64.isNotBlank()) {
                    val pcm = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: continue
                    playPcmChunk(pcm)
                    if (_sessionState.value.status != LiveCallStatus.SPEAKING) {
                        _sessionState.value = _sessionState.value.copy(
                            status = LiveCallStatus.SPEAKING
                        )
                    }
                }
            }
        }

        if (serverContent.optBoolean("turnComplete", false)) {
            val uText = userBuffer.toString().trim()
            val aText = assistantBuffer.toString().trim()
            if (uText.isNotEmpty() || aText.isNotEmpty()) {
                onTranscriptTurn(uText, aText)
            }
            userBuffer.clear()
            assistantBuffer.clear()
            if (_sessionState.value.status == LiveCallStatus.SPEAKING) {
                scope.launch {
                    delay(260)
                    if (!manualStopRequested && System.currentTimeMillis() - lastAudioWriteAtMs > 220L) {
                        _speakingLevel.value = 0f
                        _sessionState.value = _sessionState.value.copy(
                            status = LiveCallStatus.LISTENING,
                            liveCaption = if (language == AppLanguage.BENGALI) "শুনছি..." else "Listening..."
                        )
                    }
                }
            }
        }
    }

    private fun startAudioOutput() {
        val sampleRate = 24000
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4800)

        synchronized(audioWriteLock) {
            runCatching { audioTrack?.release() }
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .apply { play() }
        }
    }

    private fun playPcmChunk(pcmBytes: ByteArray) {
        if (pcmBytes.isEmpty()) return
        val rms = computePcm16RmsNormalized(pcmBytes, pcmBytes.size)
        _speakingLevel.value = (_speakingLevel.value * 0.35f + rms * 0.65f).coerceIn(0f, 1f)
        lastAudioWriteAtMs = System.currentTimeMillis()

        synchronized(audioWriteLock) {
            val track = audioTrack ?: return
            runCatching {
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }
                track.write(pcmBytes, 0, pcmBytes.size, AudioTrack.WRITE_BLOCKING)
            }
        }
    }

    private fun flushAudioTrack() {
        synchronized(audioWriteLock) {
            runCatching {
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.play()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startMicStreaming() {
        micStreamJob?.cancel()
        micStreamJob = scope.launch {
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(3200)

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
            } catch (_: Exception) {
                null
            }

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                _sessionState.value = _sessionState.value.copy(
                    status = LiveCallStatus.ERROR,
                    errorMessage = "মাইক্রোফোন চালু করা যায়নি।"
                )
                recorder?.release()
                return@launch
            }

            audioRecord = recorder
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = runCatching {
                    AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = true }
                }.getOrNull()
            }

            val buffer = ByteArray(1600) // ~50ms chunks at 16kHz 16-bit mono
            runCatching { recorder.startRecording() }

            while (isActive && !manualStopRequested) {
                val read = runCatching { recorder.read(buffer, 0, buffer.size) }.getOrDefault(-1)
                if (read > 0) {
                    if (_sessionState.value.isMuted) {
                        _micLevel.value = 0f
                        continue
                    }
                    val rms = computePcm16RmsNormalized(buffer, read)
                    _micLevel.value = rms

                    val b64 = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                    val msg = JSONObject().put(
                        "realtimeInput",
                        JSONObject().put(
                            "mediaChunks",
                            JSONArray().put(
                                JSONObject()
                                    .put("mimeType", "audio/pcm;rate=16000")
                                    .put("data", b64)
                            )
                        )
                    ).toString()
                    webSocket?.send(msg)
                }
            }
        }
    }

    private fun startSilenceDecayLoop() {
        silenceDecayJob?.cancel()
        silenceDecayJob = scope.launch {
            while (isActive && !manualStopRequested) {
                delay(90)
                if (System.currentTimeMillis() - lastAudioWriteAtMs > 180L && _speakingLevel.value > 0f) {
                    _speakingLevel.value = (_speakingLevel.value * 0.55f).let { if (it < 0.03f) 0f else it }
                }
            }
        }
    }

    private fun computePcm16RmsNormalized(bytes: ByteArray, length: Int): Float {
        val sampleCount = length / 2
        if (sampleCount <= 0) return 0f
        val shortBuf = ByteBuffer.wrap(bytes, 0, sampleCount * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        var sumSq = 0.0
        for (i in 0 until sampleCount) {
            val sample = shortBuf.get(i).toDouble() / 32768.0
            sumSq += sample * sample
        }
        val rms = sqrt(sumSq / sampleCount)
        return (rms * 4.5).toFloat().coerceIn(0f, 1f)
    }

    fun toggleMute() {
        val current = _sessionState.value
        _sessionState.value = current.copy(isMuted = !current.isMuted)
        if (_sessionState.value.isMuted) _micLevel.value = 0f
    }

    fun setMinimized(minimized: Boolean) {
        _sessionState.value = _sessionState.value.copy(isMinimized = minimized)
    }

    private fun stopAudioHardware() {
        micStreamJob?.cancel()
        micStreamJob = null
        silenceDecayJob?.cancel()
        silenceDecayJob = null

        runCatching { echoCanceler?.release() }
        echoCanceler = null

        runCatching {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
                it.release()
            }
        }
        audioRecord = null

        synchronized(audioWriteLock) {
            runCatching {
                audioTrack?.let {
                    if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop()
                    it.release()
                }
            }
            audioTrack = null
        }

        _speakingLevel.value = 0f
        _micLevel.value = 0f
    }

    fun endSession() {
        manualStopRequested = true
        sessionJob?.cancel()
        sessionJob = null
        runCatching { webSocket?.close(1000, "User ended call") }
        webSocket = null
        stopAudioHardware()
        _sessionState.value = LiveCallSessionState(status = LiveCallStatus.IDLE)
    }
}
