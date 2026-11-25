package com.example.data.remote

import com.example.data.model.AppLanguage
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryItem
import com.example.data.model.SenderRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Supports:
 * 1) Groq Chat Completions with comma-separated API key rotation on rate limits.
 * 2) Gemini REST generateContent fallback (when Claude & Groq keys are not provided).
 * 3) API Key testing & Gemini Live model discovery.
 */
class GroqAndGeminiRestService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun sendGroqConversation(
        keys: List<String>,
        systemPrompt: String,
        history: List<ChatMessage>,
        userPrompt: String
    ): String = withContext(Dispatchers.IO) {
        if (keys.isEmpty()) throw ShailaAiException.MissingApiKey()

        val messagesArr = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            history.takeLast(12).forEach { msg ->
                put(
                    JSONObject()
                        .put("role", if (msg.role == SenderRole.USER) "user" else "assistant")
                        .put("content", msg.text)
                )
            }
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }

        val payload = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", messagesArr)
            put("temperature", 0.7)
            put("max_tokens", 700)
        }.toString()

        var lastError: ShailaAiException = ShailaAiException.ServerError("Groq request failed")
        for (rawKey in keys) {
            val key = rawKey.trim()
            if (key.isBlank()) continue
            val req = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    when (resp.code) {
                        in 200..299 -> {
                            val json = JSONObject(raw)
                            val choices = json.optJSONArray("choices")
                            val content = choices?.optJSONObject(0)
                                ?.optJSONObject("message")
                                ?.optString("content")
                                .orEmpty()
                                .trim()
                            if (content.isNotBlank()) return@withContext content
                            lastError = ShailaAiException.ServerError("Empty Groq response")
                        }
                        401, 403 -> lastError = ShailaAiException.InvalidApiKey()
                        429 -> {
                            // Try next comma-separated Groq key if rate-limited
                            lastError = ShailaAiException.QuotaExceeded()
                        }
                        else -> lastError = ShailaAiException.ServerError("Groq HTTP ${resp.code}")
                    }
                }
            } catch (_: UnknownHostException) {
                throw ShailaAiException.NoInternet()
            } catch (e: IOException) {
                lastError = ShailaAiException.ServerError(e.localizedMessage ?: "Network error")
            }
        }
        throw lastError
    }

    suspend fun sendGeminiRestConversation(
        apiKey: String,
        systemPrompt: String,
        history: List<ChatMessage>,
        userPrompt: String,
        base64Images: List<String> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isBlank()) throw ShailaAiException.MissingApiKey()

        val contents = JSONArray()
        history.takeLast(12).forEach { msg ->
            contents.put(
                JSONObject()
                    .put("role", if (msg.role == SenderRole.USER) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
            )
        }

        val currentParts = JSONArray()
        base64Images.forEach { img ->
            currentParts.put(
                JSONObject().put(
                    "inlineData",
                    JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", img)
                )
            )
        }
        currentParts.put(JSONObject().put("text", userPrompt))
        contents.put(JSONObject().put("role", "user").put("parts", currentParts))

        val payload = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            )
            put("contents", contents)
        }

        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$key")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                when (resp.code) {
                    in 200..299 -> {
                        val json = JSONObject(raw)
                        val candidates = json.optJSONArray("candidates")
                        val parts = candidates?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                        val sb = StringBuilder()
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                val t = parts.optJSONObject(i)?.optString("text").orEmpty()
                                if (t.isNotBlank()) sb.append(t)
                            }
                        }
                        sb.toString().trim().ifBlank {
                            throw ShailaAiException.ServerError("Empty Gemini response")
                        }
                    }
                    400, 401, 403 -> throw ShailaAiException.InvalidApiKey()
                    429 -> throw ShailaAiException.QuotaExceeded()
                    else -> throw ShailaAiException.ServerError("Gemini HTTP ${resp.code}")
                }
            }
        } catch (_: UnknownHostException) {
            throw ShailaAiException.NoInternet()
        } catch (e: ShailaAiException) {
            throw e
        } catch (e: IOException) {
            throw ShailaAiException.ServerError(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun testGeminiKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isBlank()) return@withContext Result.failure(IllegalArgumentException("Key is empty"))
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
            .get()
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success("Gemini API Key যাচাই সফল হয়েছে! Live Voice প্রস্তুত।")
                } else {
                    Result.failure(IllegalStateException("Gemini HTTP ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testGroqKeys(rawInput: String): Result<String> = withContext(Dispatchers.IO) {
        val keys = rawInput.split(",", "\n", ";").map { it.trim() }.filter { it.isNotEmpty() }
        if (keys.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Key is empty"))
        var validCount = 0
        var lastCode = 0
        for (key in keys) {
            val req = Request.Builder()
                .url("https://api.groq.com/openai/v1/models")
                .addHeader("Authorization", "Bearer $key")
                .get()
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) validCount++ else lastCode = resp.code
                }
            } catch (e: Exception) {
                if (keys.size == 1) return@withContext Result.failure(e)
            }
        }
        if (validCount > 0) {
            Result.success("$validCount টি Groq API Key যাচাই সফল হয়েছে!")
        } else {
            Result.failure(IllegalStateException("Groq HTTP $lastCode"))
        }
    }

    /**
     * Queries available Gemini models for this API key and returns prioritized models
     * supporting `bidiGenerateContent` (Gemini Live API).
     */
    suspend fun discoverLiveModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val defaults = listOf(
            "models/gemini-2.5-flash-native-audio-preview-12-2025",
            "models/gemini-2.5-flash-native-audio-preview-09-2025",
            "models/gemini-2.0-flash-live-001",
            "models/gemini-2.0-flash-exp"
        )
        val key = apiKey.trim()
        if (key.isBlank()) return@withContext defaults
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100&key=$key")
            .get()
            .build()
        return@withContext try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use defaults
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val modelsArr = json.optJSONArray("models") ?: return@use defaults
                val found = mutableListOf<String>()
                for (i in 0 until modelsArr.length()) {
                    val obj = modelsArr.optJSONObject(i) ?: continue
                    val name = obj.optString("name")
                    val methods = obj.optJSONArray("supportedGenerationMethods") ?: continue
                    var supportsBidi = false
                    for (j in 0 until methods.length()) {
                        if (methods.optString(j) == "bidiGenerateContent") {
                            supportsBidi = true
                            break
                        }
                    }
                    if (supportsBidi && name.isNotBlank()) {
                        found.add(name)
                    }
                }
                if (found.isEmpty()) {
                    defaults
                } else {
                    val sorted = found.sortedWith(
                        compareByDescending<String> { it.contains("native-audio", ignoreCase = true) }
                            .thenByDescending { it.contains("2.5", ignoreCase = true) }
                            .thenByDescending { it.contains("live", ignoreCase = true) }
                    )
                    (sorted + defaults).distinct()
                }
            }
        } catch (_: Exception) {
            defaults
        }
    }
}
