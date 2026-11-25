package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class ApiKeyType(val prefKey: String) {
    GEMINI("key_gemini"),
    GROQ("key_groq"),
    CLAUDE("key_claude"),
    ELEVENLABS("key_elevenlabs"),
    OPENAI("key_openai"),
    XAI("key_xai")
}

data class ApiKeysSnapshot(
    val geminiKey: String = "",
    val groqKey: String = "",
    val claudeKey: String = "",
    val elevenLabsKey: String = "",
    val openAiKey: String = "",
    val xaiKey: String = ""
) {
    fun groqKeysList(): List<String> = groqKey
        .split(",", "\n", ";")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    fun hasAnyChatBrainKey(): Boolean =
        geminiKey.isNotBlank() || groqKeysList().isNotEmpty() || claudeKey.isNotBlank()

    fun hasAnyVoiceApiKey(): Boolean =
        geminiKey.isNotBlank() || elevenLabsKey.isNotBlank() || openAiKey.isNotBlank() || xaiKey.isNotBlank()
}

class SecureStorageManager(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "shaila_secure_keys_v1",
        Context.MODE_PRIVATE
    )

    private val _keysState = MutableStateFlow(loadAllKeys())
    val keysState: StateFlow<ApiKeysSnapshot> = _keysState.asStateFlow()

    fun saveKey(type: ApiKeyType, rawValue: String) {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(type.prefKey).apply()
        } else {
            val encrypted = encrypt(trimmed)
            prefs.edit().putString(type.prefKey, encrypted).apply()
        }
        _keysState.value = loadAllKeys()
    }

    fun getKey(type: ApiKeyType): String {
        val stored = prefs.getString(type.prefKey, null) ?: return ""
        return decrypt(stored)
    }

    fun clearAllKeys() {
        prefs.edit().clear().apply()
        _keysState.value = ApiKeysSnapshot()
    }

    private fun loadAllKeys(): ApiKeysSnapshot = ApiKeysSnapshot(
        geminiKey = getKey(ApiKeyType.GEMINI),
        groqKey = getKey(ApiKeyType.GROQ),
        claudeKey = getKey(ApiKeyType.CLAUDE),
        elevenLabsKey = getKey(ApiKeyType.ELEVENLABS),
        openAiKey = getKey(ApiKeyType.OPENAI),
        xaiKey = getKey(ApiKeyType.XAI)
    )

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + cipherBytes.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    private fun decrypt(encoded: String): String {
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < 2) return ""
            val ivLength = combined[0].toInt() and 0xFF
            if (combined.size <= 1 + ivLength) return ""
            val iv = combined.copyOfRange(1, 1 + ivLength)
            val cipherBytes = combined.copyOfRange(1 + ivLength, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "shaila_api_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
