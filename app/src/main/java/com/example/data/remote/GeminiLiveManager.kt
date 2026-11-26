package com.example.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
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
import java.util.concurrent.TimeUnit

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
    val isSpeakerOn: Boolean = true,
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
 * via loudspeaker (USAGE_MEDIA by default, or earpiece on toggle) with soft gain boost and live RMS lip-sync.
 */
class GeminiLiveManager(
    context: Context,
    private val restService: GroqAndGeminiRestService
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
    private var noiseSuppressor: NoiseSuppressor? = null
    private var audioTrack: AudioTrack? = null
    private var micStreamJob: Job? = null
    private var silenceDecayJob: Job? = null
    private var sessionJob: Job? = null

    private var savedAudioMode: Int? = null
    private var savedSpeakerphoneOn: Boolean? = null
    private var activeFocusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

    private val audioWriteLock = Any()
    @Volatile
    private var lastAudioWriteAtMs = 0L
    @Volatile
    private var manualInterruptAtMs = 0L
    @Volatile
    private var setupComplete = false
    @Volatile
    private var manualStopRequested = false

    fun startSession(
        apiKey: String,
        voice: GeminiLiveVoice,
        systemPrompt: String,
        language: AppLanguage,
        onTranscriptTurn: (userText: String, assistantText: String) -> Unit = { _, _ -> },
        onToolCall: suspend (name: String, args: JSONObject) -> JSONObject = { _, _ -> JSONObject().put("success", false) }
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
            isSpeakerOn = true,
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
                onTranscriptTurn = onTranscriptTurn,
                onToolCall = onToolCall
            )
        }
    }

    private fun buildLiveToolsArray(): JSONArray {
        fun decl(name: String, description: String, properties: JSONObject, required: List<String> = emptyList()): JSONObject {
            return JSONObject().apply {
                put("name", name)
                put("description", description)
                put(
                    "parameters",
                    JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", properties)
                        if (required.isNotEmpty()) {
                            put("required", JSONArray(required))
                        }
                    }
                )
            }
        }

        val declarations = JSONArray().apply {
            put(
                decl(
                    name = "open_app",
                    description = "Opens any installed app on the user's phone by name (e.g. YouTube, WhatsApp, Facebook, Chrome, Settings, Camera, Gallery, Play Store).",
                    properties = JSONObject().put(
                        "app_name",
                        JSONObject().put("type", "STRING").put("description", "App name in English or Bengali")
                    ),
                    required = listOf("app_name")
                )
            )
            put(
                decl(
                    name = "type_text",
                    description = "Types text into the currently focused text box, keyboard field, or search bar inside the open app.",
                    properties = JSONObject().apply {
                        put("text", JSONObject().put("type", "STRING").put("description", "Exact text to type"))
                        put("submit", JSONObject().put("type", "BOOLEAN").put("description", "Whether to press Search/Send/Enter right after typing"))
                    },
                    required = listOf("text")
                )
            )
            put(
                decl(
                    name = "tap_button",
                    description = "Taps a visible button, icon, tab, or text item on the current phone screen by its label.",
                    properties = JSONObject().put(
                        "label",
                        JSONObject().put("type", "STRING").put("description", "Visible text or button name such as Search, Send, Play")
                    ),
                    required = listOf("label")
                )
            )
            put(
                decl(
                    name = "scroll_screen",
                    description = "Scrolls the current app screen up or down.",
                    properties = JSONObject().put(
                        "direction",
                        JSONObject().put("type", "STRING").put("description", "down or up")
                    ),
                    required = listOf("direction")
                )
            )
            put(
                decl(
                    name = "system_navigation",
                    description = "Performs Android system navigation actions: back, home, recents, notifications, quick_settings.",
                    properties = JSONObject().put(
                        "action",
                        JSONObject().put("type", "STRING").put("description", "One of back, home, recents, notifications, quick_settings")
                    ),
                    required = listOf("action")
                )
            )
            put(
                decl(
                    name = "open_url",
                    description = "Opens a website URL or performs a Google search in the browser.",
                    properties = JSONObject().put(
                        "url",
                        JSONObject().put("type", "STRING").put("description", "Website URL or search query")
                    ),
                    required = listOf("url")
                )
            )
            put(
                decl(
                    name = "get_screen_info",
                    description = "Reads the currently visible app and buttons/input boxes on screen so you know where to tap or type.",
                    properties = JSONObject()
                )
            )
            put(
                decl(
                    name = "dial_number",
                    description = "Opens the phone dialer with the requested phone number ready to call.",
                    properties = JSONObject().put(
                        "phone_number",
                        JSONObject().put("type", "STRING").put("description", "Phone number digits")
                    ),
                    required = listOf("phone_number")
                )
            )
            put(
                decl(
                    name = "flashlight",
                    description = "Turns the phone flashlight torch on or off.",
                    properties = JSONObject().put(
                        "turn_on",
                        JSONObject().put("type", "BOOLEAN").put("description", "true to turn on, false to turn off")
                    ),
                    required = listOf("turn_on")
                )
            )
            put(
                decl(
                    name = "set_alarm",
                    description = "Sets an alarm on the user's phone at a specified hour and minute.",
                    properties = JSONObject().apply {
                        put("hour", JSONObject().put("type", "INTEGER").put("description", "Hour in 24h format (0-23)"))
                        put("minute", JSONObject().put("type", "INTEGER").put("description", "Minute (0-59)"))
                        put("label", JSONObject().put("type", "STRING").put("description", "Optional alarm label"))
                    },
                    required = listOf("hour", "minute")
                )
            )
        }
        return JSONArray().put(JSONObject().put("functionDeclarations", declarations))
    }

    private fun openWebSocket(
        apiKey: String,
        candidateModels: List<String>,
        modelIndex: Int,
        initialModel: String,
        voice: GeminiLiveVoice,
        systemPrompt: String,
        language: AppLanguage,
        onTranscriptTurn: (String, String) -> Unit,
        onToolCall: suspend (String, JSONObject) -> JSONObject,
        useLowLatencyExtras: Boolean = true
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
                                    if (useLowLatencyExtras && initialModel.contains("2.5")) {
                                        put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                                    }
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
                            if (useLowLatencyExtras) {
                                put(
                                    "realtimeInputConfig",
                                    JSONObject().put(
                                        "automaticActivityDetection",
                                        JSONObject().apply {
                                            put("disabled", false)
                                            // LOW start sensitivity prevents room hiss, breathing, and tail echoes
                                            // from triggering phantom assistant turns when nobody is speaking.
                                            put("startOfSpeechSensitivity", "START_SENSITIVITY_LOW")
                                            put("endOfSpeechSensitivity", "END_SENSITIVITY_HIGH")
                                            put("prefixPaddingMs", 80)
                                            put("silenceDurationMs", 380)
                                        }
                                    )
                                )
                            }
                            put(
                                "systemInstruction",
                                JSONObject().put(
                                    "parts",
                                    JSONArray().put(JSONObject().put("text", systemPrompt))
                                )
                            )
                            put("inputAudioTranscription", JSONObject())
                            put("outputAudioTranscription", JSONObject())
                            put("tools", buildLiveToolsArray())
                        }
                    )
                }
                ws.send(setupPayload.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleServerJson(
                    ws = ws,
                    raw = text,
                    language = language,
                    userBuffer = userBuffer,
                    assistantBuffer = assistantBuffer,
                    onTranscriptTurn = onTranscriptTurn,
                    onToolCall = onToolCall
                )
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                handleServerJson(
                    ws = ws,
                    raw = bytes.utf8(),
                    language = language,
                    userBuffer = userBuffer,
                    assistantBuffer = assistantBuffer,
                    onTranscriptTurn = onTranscriptTurn,
                    onToolCall = onToolCall
                )
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                if (!setupComplete && !manualStopRequested && useLowLatencyExtras) {
                    openWebSocket(
                        apiKey = apiKey,
                        candidateModels = candidateModels,
                        modelIndex = modelIndex,
                        initialModel = initialModel,
                        voice = voice,
                        systemPrompt = systemPrompt,
                        language = language,
                        onTranscriptTurn = onTranscriptTurn,
                        onToolCall = onToolCall,
                        useLowLatencyExtras = false
                    )
                } else if (!setupComplete && !manualStopRequested && modelIndex + 1 < candidateModels.size) {
                    val nextModel = candidateModels[modelIndex + 1]
                    openWebSocket(
                        apiKey = apiKey,
                        candidateModels = candidateModels,
                        modelIndex = modelIndex + 1,
                        initialModel = nextModel,
                        voice = voice,
                        systemPrompt = systemPrompt,
                        language = language,
                        onTranscriptTurn = onTranscriptTurn,
                        onToolCall = onToolCall,
                        useLowLatencyExtras = true
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
                if (!setupComplete && useLowLatencyExtras) {
                    openWebSocket(
                        apiKey = apiKey,
                        candidateModels = candidateModels,
                        modelIndex = modelIndex,
                        initialModel = initialModel,
                        voice = voice,
                        systemPrompt = systemPrompt,
                        language = language,
                        onTranscriptTurn = onTranscriptTurn,
                        onToolCall = onToolCall,
                        useLowLatencyExtras = false
                    )
                    return
                }
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
                        onTranscriptTurn = onTranscriptTurn,
                        onToolCall = onToolCall,
                        useLowLatencyExtras = true
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
        ws: WebSocket,
        raw: String,
        language: AppLanguage,
        userBuffer: StringBuilder,
        assistantBuffer: StringBuilder,
        onTranscriptTurn: (String, String) -> Unit,
        onToolCall: suspend (String, JSONObject) -> JSONObject
    ) {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return

        val errObj = root.optJSONObject("error")
        if (errObj != null) {
            val msg = errObj.optString("message").ifBlank {
                if (language == AppLanguage.BENGALI) "Gemini Live সার্ভার ত্রুটি দিয়েছে।" else "Gemini Live returned an error."
            }
            stopAudioHardware()
            _sessionState.value = _sessionState.value.copy(
                status = LiveCallStatus.ERROR,
                errorMessage = msg
            )
            return
        }

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

        val toolCallObj = root.optJSONObject("toolCall")
        val fnCalls = toolCallObj?.optJSONArray("functionCalls")
        if (fnCalls != null && fnCalls.length() > 0) {
            scope.launch {
                val responsesArr = JSONArray()
                for (i in 0 until fnCalls.length()) {
                    val call = fnCalls.optJSONObject(i) ?: continue
                    val callId = call.optString("id")
                    val fnName = call.optString("name")
                    val args = call.optJSONObject("args") ?: JSONObject()
                    val resultJson = runCatching { onToolCall(fnName, args) }
                        .getOrElse { err -> JSONObject().put("success", false).put("error", err.localizedMessage ?: "Failed") }

                    responsesArr.put(
                        JSONObject().apply {
                            put("id", callId)
                            put("name", fnName)
                            put("response", resultJson)
                        }
                    )
                }
                val toolResponsePayload = JSONObject().put(
                    "toolResponse",
                    JSONObject().put("functionResponses", responsesArr)
                ).toString()
                ws.send(toolResponsePayload)
            }
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
                    // Wait until AudioTrack playback finishes and room echo fades before switching to LISTENING
                    while (isActive && !manualStopRequested) {
                        val quietFor = System.currentTimeMillis() - lastAudioWriteAtMs
                        if (quietFor >= 680L) break
                        delay((700L - quietFor).coerceIn(120L, 450L))
                    }
                    if (!manualStopRequested && System.currentTimeMillis() - lastAudioWriteAtMs >= 650L) {
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

    private fun configureAudioRouting(speakerOn: Boolean) {
        runCatching {
            if (savedAudioMode == null) {
                savedAudioMode = audioManager.mode
            }
            @Suppress("DEPRECATION")
            if (savedSpeakerphoneOn == null) {
                savedSpeakerphoneOn = audioManager.isSpeakerphoneOn
            }

            if (speakerOn) {
                // Keep MODE_NORMAL so USAGE_MEDIA plays loud through the main bottom loudspeaker
                // (or connected Bluetooth/wired headphones) instead of the tiny call earpiece.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                }
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_NORMAL
            } else {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val earpiece = audioManager.availableCommunicationDevices
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                    if (earpiece != null) {
                        audioManager.setCommunicationDevice(earpiece)
                    } else {
                        audioManager.clearCommunicationDevice()
                    }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = false
                }
            }

            val attrs = AudioAttributes.Builder()
                .setUsage(if (speakerOn) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(focusListener)
                    .build()
                activeFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    focusListener,
                    if (speakerOn) AudioManager.STREAM_MUSIC else AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        }
    }

    private fun restoreAudioRouting() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                activeFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusListener)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            @Suppress("DEPRECATION")
            savedSpeakerphoneOn?.let { audioManager.isSpeakerphoneOn = it }
            savedAudioMode?.let { audioManager.mode = it }
        }
        savedSpeakerphoneOn = null
        savedAudioMode = null
    }

    private fun startAudioOutput() {
        val speakerOn = _sessionState.value.isSpeakerOn
        configureAudioRouting(speakerOn)

        val sampleRate = 24000
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4800)

        synchronized(audioWriteLock) {
            runCatching { audioTrack?.release() }
            val builder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(
                            if (speakerOn) AudioAttributes.USAGE_MEDIA
                            else AudioAttributes.USAGE_VOICE_COMMUNICATION
                        )
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
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            audioTrack = builder.build().apply {
                setVolume(AudioTrack.getMaxVolume())
                play()
            }
        }
    }

    private fun playPcmChunk(pcmBytes: ByteArray) {
        if (pcmBytes.isEmpty()) return
        val boostedPcm = PcmAudioProcessor.applySoftGain(pcmBytes, gain = 1.95f)
        val rms = PcmAudioProcessor.computePcm16RmsNormalized(boostedPcm, boostedPcm.size)
        _speakingLevel.value = (_speakingLevel.value * 0.35f + rms * 0.65f).coerceIn(0f, 1f)
        lastAudioWriteAtMs = System.currentTimeMillis()

        synchronized(audioWriteLock) {
            val track = audioTrack ?: return
            runCatching {
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }
                track.write(boostedPcm, 0, boostedPcm.size, AudioTrack.WRITE_BLOCKING)
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
    private fun createAudioRecord(sampleRate: Int, bufferSize: Int): AudioRecord? {
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )
        for (src in sources) {
            val candidate = try {
                AudioRecord(
                    src,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            } catch (_: Exception) {
                null
            }
            if (candidate != null && candidate.state == AudioRecord.STATE_INITIALIZED) {
                return candidate
            }
            runCatching { candidate?.release() }
        }
        return null
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

            val recorder = createAudioRecord(sampleRate, minBuf * 2)
            if (recorder == null) {
                _sessionState.value = _sessionState.value.copy(
                    status = LiveCallStatus.ERROR,
                    errorMessage = "মাইক্রোফোন চালু করা যায়নি।"
                )
                return@launch
            }

            audioRecord = recorder
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = runCatching {
                    AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = true }
                }.getOrNull()
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = runCatching {
                    NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true }
                }.getOrNull()
            }

            val buffer = ByteArray(640) // 20ms low-latency chunks at 16kHz 16-bit mono
            val silenceBuffer = ByteArray(640)
            var speechHangoverUntilMs = 0L
            var bargeInConsecutiveFrames = 0
            runCatching { recorder.startRecording() }

            while (isActive && !manualStopRequested) {
                val read = runCatching { recorder.read(buffer, 0, buffer.size) }.getOrDefault(-1)
                if (read > 0) {
                    val state = _sessionState.value
                    if (state.isMuted) {
                        _micLevel.value = 0f
                        bargeInConsecutiveFrames = 0
                        continue
                    }

                    val now = System.currentTimeMillis()
                    val rms = PcmAudioProcessor.computePcm16RmsNormalized(buffer, read)
                    val msSinceSpeakerAudio = now - lastAudioWriteAtMs
                    val isEchoWindow = state.status == LiveCallStatus.SPEAKING || msSinceSpeakerAudio < 750L
                    val inManualInterruptCooldown = now - manualInterruptAtMs < 220L

                    val shouldTransmitMic: Boolean = when {
                        inManualInterruptCooldown -> false
                        isEchoWindow -> {
                            // Anti-loop echo gate: suppress speaker playback echo while still allowing
                            // genuine sustained close-mic barge-in from the user.
                            val bargeThreshold = if (state.isSpeakerOn) {
                                maxOf(0.58f, _speakingLevel.value * 0.90f + 0.32f)
                            } else {
                                0.16f
                            }
                            val neededFrames = if (state.isSpeakerOn) 8 else 3 // 160ms speaker / 60ms earpiece
                            if (rms >= bargeThreshold) {
                                bargeInConsecutiveFrames++
                            } else {
                                bargeInConsecutiveFrames = 0
                            }
                            if (bargeInConsecutiveFrames >= neededFrames) {
                                // User is genuinely interrupting mid-speech: flush speaker audio immediately
                                flushAudioTrack()
                                _speakingLevel.value = 0f
                                _sessionState.value = state.copy(status = LiveCallStatus.LISTENING)
                                speechHangoverUntilMs = now + 450L
                                true
                            } else {
                                false
                            }
                        }
                        else -> {
                            bargeInConsecutiveFrames = 0
                            // Ambient noise gate: ignore static room noise when nobody is talking
                            if (rms >= 0.048f) {
                                speechHangoverUntilMs = now + 350L
                                true
                            } else {
                                now < speechHangoverUntilMs
                            }
                        }
                    }

                    _micLevel.value = if (shouldTransmitMic) rms else 0f

                    val payloadBytes = if (shouldTransmitMic) buffer else silenceBuffer
                    val b64 = Base64.encodeToString(payloadBytes, 0, read, Base64.NO_WRAP)
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

    fun interruptAssistantPlayback() {
        manualInterruptAtMs = System.currentTimeMillis()
        lastAudioWriteAtMs = 0L
        flushAudioTrack()
        _speakingLevel.value = 0f
        val current = _sessionState.value
        if (current.isActive) {
            _sessionState.value = current.copy(
                status = LiveCallStatus.LISTENING,
                liveCaption = ""
            )
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

    fun toggleMute() {
        val current = _sessionState.value
        _sessionState.value = current.copy(isMuted = !current.isMuted)
        if (_sessionState.value.isMuted) _micLevel.value = 0f
    }

    fun toggleSpeaker() {
        val nextSpeaker = !_sessionState.value.isSpeakerOn
        _sessionState.value = _sessionState.value.copy(isSpeakerOn = nextSpeaker)
        if (setupComplete && !manualStopRequested) {
            scope.launch {
                startAudioOutput()
            }
        }
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
        runCatching { noiseSuppressor?.release() }
        noiseSuppressor = null

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

        restoreAudioRouting()
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
