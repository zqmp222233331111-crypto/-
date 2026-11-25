package com.example.data.remote

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import com.example.data.model.AppLanguage
import com.example.data.model.VoiceProviderType
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.sin

data class TtsOptions(
    val speed: Float = 1.0f,
    val stability: Float = 0.5f,
    val style: Float = 0.25f,
    val language: AppLanguage = AppLanguage.BENGALI
)

interface TtsProvider {
    val providerType: VoiceProviderType?
    val displayLabel: String

    suspend fun speak(
        text: String,
        voiceId: String,
        options: TtsOptions = TtsOptions(),
        onPlaybackStarted: () -> Unit = {}
    ): Result<Unit>

    fun stop()
}

class AudioStreamPlayer(private val context: Context) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var activeFocusRequest: AudioFocusRequest? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var levelJob: Job? = null

    private val _playbackEnergy = MutableStateFlow(0f)
    val playbackEnergy: StateFlow<Float> = _playbackEnergy.asStateFlow()

    private val mediaAttributes: AudioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .build()

    private val legacyFocusListener = AudioManager.OnAudioFocusChangeListener { }

    private fun requestPlaybackFocus() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(mediaAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(legacyFocusListener)
                    .build()
                activeFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    legacyFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        }
    }

    private fun abandonPlaybackFocus() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                activeFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(legacyFocusListener)
            }
        }
    }

    suspend fun playStream(
        inputStream: InputStream,
        fileExtension: String = "mp3",
        onStarted: () -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("shaila_tts_", ".$fileExtension", context.cacheDir)
        try {
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
                output.flush()
            }

            if (tempFile.length() == 0L) {
                tempFile.delete()
                return@withContext Result.failure(IllegalStateException("Empty audio stream"))
            }

            playFile(tempFile, onStarted)
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        }
    }

    suspend fun playAudioBytes(
        bytes: ByteArray,
        fileExtension: String = "wav",
        onStarted: () -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Empty audio payload"))
        }
        val tempFile = File.createTempFile("shaila_tts_", ".$fileExtension", context.cacheDir)
        try {
            FileOutputStream(tempFile).use { output ->
                output.write(bytes)
                output.flush()
            }
            playFile(tempFile, onStarted)
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        }
    }

    private fun startEnergyEnvelope(player: MediaPlayer) {
        levelJob?.cancel()
        levelJob = scope.launch {
            var tick = 0f
            while (isActive) {
                val playing = runCatching { player.isPlaying }.getOrDefault(false)
                if (!playing) break
                tick += 0.35f
                val syllable = (0.35f + 0.45f * abs(sin(tick)) + 0.20f * abs(sin(tick * 2.3f)))
                    .coerceIn(0.15f, 1f)
                _playbackEnergy.value = syllable
                delay(45)
            }
            _playbackEnergy.value = 0f
        }
    }

    private fun stopEnergyEnvelope() {
        levelJob?.cancel()
        levelJob = null
        _playbackEnergy.value = 0f
    }

    private suspend fun playFile(file: File, onStarted: () -> Unit): Result<Unit> =
        withContext(Dispatchers.Main) {
            stop()
            requestPlaybackFocus()
            suspendCancellableCoroutine { cont ->
                val player = MediaPlayer().apply {
                    setAudioAttributes(mediaAttributes)
                    setVolume(1.0f, 1.0f)
                    setOnPreparedListener { mp ->
                        onStarted()
                        mp.setVolume(1.0f, 1.0f)
                        mp.start()
                        startEnergyEnvelope(mp)
                    }
                    setOnCompletionListener { mp ->
                        stopEnergyEnvelope()
                        abandonPlaybackFocus()
                        mp.release()
                        if (mediaPlayer === mp) mediaPlayer = null
                        file.delete()
                        if (cont.isActive) cont.resume(Result.success(Unit))
                    }
                    setOnErrorListener { mp, what, extra ->
                        stopEnergyEnvelope()
                        abandonPlaybackFocus()
                        mp.release()
                        if (mediaPlayer === mp) mediaPlayer = null
                        file.delete()
                        if (cont.isActive) {
                            cont.resume(Result.failure(IllegalStateException("MediaPlayer error $what/$extra")))
                        }
                        true
                    }
                }

                mediaPlayer = player
                try {
                    player.setDataSource(file.absolutePath)
                    player.prepareAsync()
                } catch (e: Exception) {
                    stopEnergyEnvelope()
                    abandonPlaybackFocus()
                    player.release()
                    if (mediaPlayer === player) mediaPlayer = null
                    file.delete()
                    if (cont.isActive) cont.resume(Result.failure(e))
                }

                cont.invokeOnCancellation {
                    stop()
                    file.delete()
                }
            }
        }

    fun stop() {
        stopEnergyEnvelope()
        abandonPlaybackFocus()
        runCatching {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        mediaPlayer = null
    }
}
