package com.example.data.remote

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Pure PCM 16-bit audio helpers for gain normalization with soft-knee limiting,
 * RMS energy extraction (for real-time lip-sync), and WAV container packaging.
 */
object PcmAudioProcessor {

    /**
     * Amplifies 16-bit signed little-endian mono PCM audio by [gain] while applying a
     * smooth soft-knee limiter above 0.72 full-scale to prevent harsh digital clipping.
     */
    fun applySoftGain(
        pcmBytes: ByteArray,
        gain: Float = 1.95f,
        length: Int = pcmBytes.size
    ): ByteArray {
        val evenLen = (length.coerceAtMost(pcmBytes.size) / 2) * 2
        if (evenLen <= 0) return ByteArray(0)
        if (abs(gain - 1.0f) < 0.01f) {
            return pcmBytes.copyOf(evenLen)
        }

        val out = ByteArray(evenLen)
        val inBuf = ByteBuffer.wrap(pcmBytes, 0, evenLen).order(ByteOrder.LITTLE_ENDIAN)
        val outBuf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)

        val knee = 0.72
        val headroom = 1.0 - knee
        val sampleCount = evenLen / 2
        for (i in 0 until sampleCount) {
            val raw = inBuf.getShort(i * 2).toDouble() / 32768.0
            val boosted = raw * gain.toDouble()
            val absBoosted = abs(boosted)
            val limited = if (absBoosted <= knee) {
                boosted
            } else {
                sign(boosted) * (knee + headroom * tanh((absBoosted - knee) / headroom))
            }
            val scaled = (limited * 32767.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outBuf.putShort(i * 2, scaled.toShort())
        }
        return out
    }

    /**
     * Computes normalized RMS energy [0f..1f] from 16-bit signed little-endian PCM bytes.
     */
    fun computePcm16RmsNormalized(bytes: ByteArray, length: Int = bytes.size): Float {
        val sampleCount = length.coerceAtMost(bytes.size) / 2
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

    /**
     * Extracts sample rate from MIME types like "audio/L16;codec=pcm;rate=24000".
     */
    fun parseSampleRateFromMime(mimeType: String, defaultRate: Int = 24000): Int {
        val match = Regex("""rate=(\d+)""", RegexOption.IGNORE_CASE).find(mimeType)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 8000..96000 } ?: defaultRate
    }

    /**
     * Prepends a standard 44-byte RIFF/WAVE PCM header so raw linear PCM can be played by MediaPlayer.
     */
    fun wrapPcm16AsWav(
        pcmBytes: ByteArray,
        sampleRate: Int = 24000,
        channels: Int = 1
    ): ByteArray {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val dataSize = pcmBytes.size
        val totalDataLen = 36 + dataSize

        val wav = ByteArray(44 + dataSize)
        val header = ByteBuffer.wrap(wav, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        // "RIFF"
        header.put('R'.code.toByte())
        header.put('I'.code.toByte())
        header.put('F'.code.toByte())
        header.put('F'.code.toByte())
        header.putInt(totalDataLen)
        // "WAVE"
        header.put('W'.code.toByte())
        header.put('A'.code.toByte())
        header.put('V'.code.toByte())
        header.put('E'.code.toByte())
        // "fmt "
        header.put('f'.code.toByte())
        header.put('m'.code.toByte())
        header.put('t'.code.toByte())
        header.put(' '.code.toByte())
        header.putInt(16) // PCM subchunk1 size
        header.putShort(1.toShort()) // AudioFormat = 1 (PCM)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        // "data"
        header.put('d'.code.toByte())
        header.put('a'.code.toByte())
        header.put('t'.code.toByte())
        header.put('a'.code.toByte())
        header.putInt(dataSize)

        System.arraycopy(pcmBytes, 0, wav, 44, dataSize)
        return wav
    }
}
