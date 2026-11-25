package com.example.data.remote

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
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
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var levelJob: Job? = null

    private val _playbackEnergy = MutableStateFlow(0f)
    val playbackEnergy: StateFlow<Float> = _playbackEnergy.asStateFlow()

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
            suspendCancellableCoroutine { cont ->
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .build()
                    )
                    setOnPreparedListener { mp ->
                        onStarted()
                        mp.start()
                        startEnergyEnvelope(mp)
                    }
                    setOnCompletionListener { mp ->
                        stopEnergyEnvelope()
                        mp.release()
                        if (mediaPlayer === mp) mediaPlayer = null
                        file.delete()
                        if (cont.isActive) cont.resume(Result.success(Unit))
                    }
                    setOnErrorListener { mp, what, extra ->
                        stopEnergyEnvelope()
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
        runCatching {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        mediaPlayer = null
    }
}
