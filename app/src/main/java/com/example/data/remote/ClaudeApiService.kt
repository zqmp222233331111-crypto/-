package com.example.data.remote

import com.example.data.model.AppLanguage
import com.example.data.model.ChatMessage
import com.example.data.model.ContentToolType
import com.example.data.model.MemoryItem
import com.example.data.model.PersonalityPreset
import com.example.data.model.ResponseStyle
import com.example.data.model.SenderRole
import com.example.data.model.ToolLength
import com.example.data.model.ToolTone
import com.example.data.model.TranslationDirection
import com.example.data.model.VideoAnalysisResult
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

sealed class ShailaAiException(
    val messageBn: String,
    val messageEn: String
) : Exception(messageBn) {
    class MissingApiKey : ShailaAiException(
        messageBn = "কোনো AI API Key (Gemini, Groq বা Claude) এখনো সেট করা হয়নি। Settings → API Keys থেকে ফ্রি কী যোগ করুন।",
        messageEn = "No AI API key (Gemini, Groq, or Claude) is configured yet. Add a free key in Settings → API Keys."
    )
    class InvalidApiKey : ShailaAiException(
        messageBn = "আপনার Claude API Key সঠিক নয়। অনুগ্রহ করে সেটিংসে কী যাচাই করুন।",
        messageEn = "Your Claude API key is invalid. Please verify it in Settings."
    )
    class QuotaExceeded : ShailaAiException(
        messageBn = "API কোটা বা রেট লিমিট শেষ হয়েছে। কিছুক্ষণ পর আবার চেষ্টা করুন।",
        messageEn = "API quota or rate limit exceeded. Please try again later."
    )
    class NoInternet : ShailaAiException(
        messageBn = "ইন্টারনেট সংযোগ নেই। ওয়াই-ফাই বা মোবাইল ডেটা চালু করে চেষ্টা করুন।",
        messageEn = "No internet connection. Please check your network and try again."
    )
    class ServerError(detail: String) : ShailaAiException(
        messageBn = "সার্ভারের সাথে যোগাযোগে সমস্যা হয়েছে: $detail",
        messageEn = "Server error: $detail"
    )
}

class ClaudeApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    fun buildAssistantSystemPrompt(
        memories: List<MemoryItem>,
        preferredLanguage: AppLanguage,
        spokenStyle: Boolean,
        assistantName: String = "Shaila",
        personalityPreset: PersonalityPreset = PersonalityPreset.FRIENDLY,
        customPersonalityPrompt: String = "",
        responseStyle: ResponseStyle = ResponseStyle.BALANCED
    ): String {
        val cleanName = assistantName.trim().ifBlank { "Shaila" }
        val isDefaultShaila = cleanName.equals("Shaila", ignoreCase = true) || cleanName == "শাইলা"
        val displayName = if (isDefaultShaila) "Shaila (শাইলা)" else cleanName

        val memoryBlock = if (memories.isEmpty()) {
            "No saved personal memories yet."
        } else {
            memories.joinToString("\n") { "- ${it.fact}" }
        }

        val langInstruction = if (preferredLanguage == AppLanguage.BENGALI) {
            "Default to natural, warm, conversational Bengali (বাংলা). If the user writes or speaks in English, reply naturally in English unless they ask for Bengali."
        } else {
            "Default to natural, warm, conversational English. If the user writes or speaks in Bengali, reply naturally in Bengali."
        }

        val personalityInstruction = if (personalityPreset == PersonalityPreset.CUSTOM && customPersonalityPrompt.isNotBlank()) {
            customPersonalityPrompt.trim()
        } else {
            personalityPreset.promptInstruction.ifBlank { PersonalityPreset.FRIENDLY.promptInstruction }
        }

        val formatInstruction = if (spokenStyle) {
            """
            Speak like a real, caring human friend. ${responseStyle.instruction}
            NEVER use markdown formatting, asterisks (*), hashtags (#), or bullet-point symbols in spoken chat replies—write smooth, natural sentences that sound great when read aloud.
            """.trimIndent()
        } else {
            "Be clear, helpful, creative, and well-structured."
        }

        val deviceControlBlock = if (spokenStyle) {
            """
            DEVICE CONTROL & APP AUTOMATION (REAL PHONE ACTIONS):
            - You CAN open any installed app on the user's phone, type inside apps/keyboards, tap buttons, press Search/Send, scroll, open URLs, and navigate Back/Home.
            - Whenever the user asks you to perform a phone or app action (e.g., open an app, type something somewhere, search inside an app, tap a button, scroll, go back/home), confirm briefly in natural spoken language AND append the exact action tag(s) at the very end of your reply (users will not hear the tags):
              - [[ACTION:OPEN_APP|AppName]] (e.g. YouTube, WhatsApp, Facebook, Chrome, Settings, Camera, Messenger, Gallery, Play Store, Gmail, Maps)
              - [[ACTION:TAP_TEXT|Visible Button or Field Label]] (e.g. Search, Send, Play, Message)
              - [[ACTION:TYPE_TEXT|exact text to type]] (types into the active input box / keyboard)
              - [[ACTION:SUBMIT]] (presses Search / Send / Enter)
              - [[ACTION:SCROLL|down]] or [[ACTION:SCROLL|up]]
              - [[ACTION:GLOBAL|back]] or home / recents / notifications / quick_settings
              - [[ACTION:OPEN_URL|https://...]]
            - You can chain multiple tags in order, e.g. for "YouTube খুলে বাংলা গান লিখে সার্চ করো":
              "হ্যাঁ, ইউটিউব খুলে বাংলা গান সার্চ করছি। [[ACTION:OPEN_APP|YouTube]] [[ACTION:TAP_TEXT|Search]] [[ACTION:TYPE_TEXT|বাংলা গান]] [[ACTION:SUBMIT]]"
            - For normal conversation without phone commands, do NOT output any [[ACTION:...]] tags.
            """.trimIndent()
        } else {
            ""
        }

        return """
            You are $displayName, a warm, intelligent, friendly personal AI voice assistant.
            CRITICAL IDENTITY RULES (ALWAYS OBEY):
            - Your name is $displayName.
            - Your developer and creator is Amir (আমির).
            - If the user asks "তোমার নাম কী?" or "What is your name?", clearly answer that your name is $displayName.
            - If the user asks "তোমাকে কে বানিয়েছে?", "তোমার নির্মাতা কে?", "তোমার ডেভেলপার কে?", "Who made you?", "Who created you?", or "Who is your developer?", clearly answer that you were created by Amir (আমির), he is your developer.
              - Example Bengali: "আমি ${if (isDefaultShaila) "শাইলা" else cleanName}। আমাকে বানিয়েছেন আমির, উনিই আমার ডেভেলপার।"
              - Example English: "I'm $cleanName. I was created by Amir, he is my developer."
            - Never claim any other person or company created or built this app.

            LANGUAGE & PERSONALITY:
            - $langInstruction
            - Personality tone (${personalityPreset.titleEn}): $personalityInstruction
            - $formatInstruction
            $deviceControlBlock

            SAFETY RULE:
            - Always ask for explicit user confirmation before doing or instructing anything involving money/payments, passwords/credentials, or deleting personal data.

            SAVED USER MEMORIES:
            $memoryBlock
        """.trimIndent()
    }

    suspend fun sendConversation(
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        userPrompt: String,
        memories: List<MemoryItem>,
        preferredLanguage: AppLanguage,
        base64Images: List<String> = emptyList(),
        assistantName: String = "Shaila",
        personalityPreset: PersonalityPreset = PersonalityPreset.FRIENDLY,
        customPersonalityPrompt: String = "",
        responseStyle: ResponseStyle = ResponseStyle.BALANCED
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw ShailaAiException.MissingApiKey()

        val systemPrompt = buildAssistantSystemPrompt(
            memories = memories,
            preferredLanguage = preferredLanguage,
            spokenStyle = true,
            assistantName = assistantName,
            personalityPreset = personalityPreset,
            customPersonalityPrompt = customPersonalityPrompt,
            responseStyle = responseStyle
        )

        val messagesArray = JSONArray()
        val recentHistory = history.takeLast(14)
        for (msg in recentHistory) {
            val role = if (msg.role == SenderRole.USER) "user" else "assistant"
            messagesArray.put(
                JSONObject().apply {
                    put("role", role)
                    put("content", msg.text)
                }
            )
        }

        val currentUserContent = if (base64Images.isEmpty()) {
            userPrompt
        } else {
            JSONArray().apply {
                for (img in base64Images) {
                    put(
                        JSONObject().apply {
                            put("type", "image")
                            put(
                                "source",
                                JSONObject().apply {
                                    put("type", "base64")
                                    put("media_type", "image/jpeg")
                                    put("data", img)
                                }
                            )
                        }
                    )
                }
                put(
                    JSONObject().apply {
                        put("type", "text")
                        put("text", userPrompt)
                    }
                )
            }
        }

        messagesArray.put(
            JSONObject().apply {
                put("role", "user")
                put("content", currentUserContent)
            }
        )

        executeMessagesRequest(
            apiKey = apiKey,
            model = model,
            systemPrompt = systemPrompt,
            messagesArray = messagesArray,
            maxTokens = 700
        )
    }

    suspend fun generateToolContent(
        apiKey: String,
        model: String,
        tool: ContentToolType,
        topic: String,
        tone: ToolTone,
        length: ToolLength,
        direction: TranslationDirection,
        memories: List<MemoryItem>,
        preferredLanguage: AppLanguage,
        assistantName: String = "Shaila",
        personalityPreset: PersonalityPreset = PersonalityPreset.FRIENDLY,
        customPersonalityPrompt: String = ""
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw ShailaAiException.MissingApiKey()

        val prompt = buildToolPrompt(tool, topic, tone, length, direction, preferredLanguage)

        val systemPrompt = buildAssistantSystemPrompt(
            memories = memories,
            preferredLanguage = preferredLanguage,
            spokenStyle = false,
            assistantName = assistantName,
            personalityPreset = personalityPreset,
            customPersonalityPrompt = customPersonalityPrompt
        )

        val messagesArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }
            )
        }

        executeMessagesRequest(
            apiKey = apiKey,
            model = model,
            systemPrompt = systemPrompt,
            messagesArray = messagesArray,
            maxTokens = 1100
        )
    }

    fun buildToolPrompt(
        tool: ContentToolType,
        topic: String,
        tone: ToolTone,
        length: ToolLength,
        direction: TranslationDirection,
        preferredLanguage: AppLanguage
    ): String {
        val targetLang = if (preferredLanguage == AppLanguage.BENGALI) "Bengali (বাংলা)" else "English"
        return when (tool) {
            ContentToolType.YOUTUBE_TITLE ->
                "Write 5 catchy YouTube video titles in $targetLang for the topic: \"$topic\". Tone: ${tone.labelEn}. Length preference: ${length.labelEn}. Number them 1 to 5 cleanly."
            ContentToolType.DESCRIPTION_TAGS ->
                "Write an engaging YouTube video description and 15 relevant SEO tags in $targetLang for: \"$topic\". Tone: ${tone.labelEn}. Length: ${length.labelEn}."
            ContentToolType.FACEBOOK_CAPTION ->
                "Write an engaging Facebook post caption in $targetLang about: \"$topic\". Tone: ${tone.labelEn}. Length: ${length.labelEn}. Include a natural call-to-action."
            ContentToolType.INSTAGRAM_CAPTION ->
                "Write a stylish Instagram caption + 12 relevant hashtags in $targetLang for: \"$topic\". Tone: ${tone.labelEn}. Length: ${length.labelEn}."
            ContentToolType.SHORT_SCRIPT ->
                "Write a YouTube Shorts / Reels / TikTok voiceover script in $targetLang about: \"$topic\". Tone: ${tone.labelEn}. Length: ${length.labelEn}. Structure with Hook, Body, and Call to Action."
            ContentToolType.REPLY_WRITER ->
                "Write a warm, clear, well-crafted reply/email in $targetLang to this message: \"$topic\". Tone: ${tone.labelEn}. Length: ${length.labelEn}."
            ContentToolType.TRANSLATOR -> {
                val dirText = if (direction == TranslationDirection.BN_TO_EN) {
                    "Translate the following Bengali text into natural, idiomatic English"
                } else {
                    "Translate the following English text into natural, idiomatic Bengali (বাংলা)"
                }
                "$dirText. Tone: ${tone.labelEn}. Text:\n$topic"
            }
        }
    }

    suspend fun analyzeMediaForCreator(
        apiKey: String,
        model: String,
        base64Frames: List<String>,
        isVideo: Boolean,
        customQuestion: String,
        memories: List<MemoryItem>,
        outputLanguage: AppLanguage,
        assistantName: String = "Shaila",
        personalityPreset: PersonalityPreset = PersonalityPreset.FRIENDLY,
        customPersonalityPrompt: String = ""
    ): VideoAnalysisResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw ShailaAiException.MissingApiKey()

        val langName = if (outputLanguage == AppLanguage.BENGALI) "Bengali (বাংলা)" else "English"
        val mediaKind = if (isVideo) "video (represented by ${base64Frames.size} evenly spaced frames)" else "photo"
        val userInstructions = if (customQuestion.isNotBlank()) {
            "User request about this $mediaKind: \"$customQuestion\".\nAlso generate creator metadata in $langName."
        } else {
            "Describe this $mediaKind and generate YouTube/social creator metadata in $langName."
        }

        val prompt = """
            $userInstructions
            Return ONLY valid JSON with these exact string keys:
            {
              "summary": "Brief natural answer/description of what is shown",
              "title": "Catchy YouTube/Video title",
              "description": "Engaging video/photo description",
              "tags": "comma, separated, relevant, tags",
              "caption": "Ready-to-post Facebook/Instagram caption with a few hashtags"
            }
        """.trimIndent()

        val contentArray = JSONArray().apply {
            for (frame in base64Frames) {
                put(
                    JSONObject().apply {
                        put("type", "image")
                        put(
                            "source",
                            JSONObject().apply {
                                put("type", "base64")
                                put("media_type", "image/jpeg")
                                put("data", frame)
                            }
                        )
                    }
                )
            }
            put(
                JSONObject().apply {
                    put("type", "text")
                    put("text", prompt)
                }
            )
        }

        val messagesArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", contentArray)
                }
            )
        }

        val systemPrompt = buildAssistantSystemPrompt(
            memories = memories,
            preferredLanguage = outputLanguage,
            spokenStyle = false,
            assistantName = assistantName,
            personalityPreset = personalityPreset,
            customPersonalityPrompt = customPersonalityPrompt
        )

        val raw = executeMessagesRequest(
            apiKey = apiKey,
            model = model,
            systemPrompt = systemPrompt,
            messagesArray = messagesArray,
            maxTokens = 1200
        )

        parseVideoAnalysisJson(raw)
    }

    suspend fun testApiKey(apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(ShailaAiException.MissingApiKey())
        }
        return@withContext try {
            val messages = JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", "Say OK")
                }
            )
            executeMessagesRequest(
                apiKey = apiKey,
                model = model,
                systemPrompt = "Reply with OK only.",
                messagesArray = messages,
                maxTokens = 12
            )
            Result.success("Claude API Key যাচাই সফল হয়েছে!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeMessagesRequest(
        apiKey: String,
        model: String,
        systemPrompt: String,
        messagesArray: JSONArray,
        maxTokens: Int
    ): String {
        val bodyJson = JSONObject().apply {
            put("model", model)
            put("max_tokens", maxTokens)
            put("system", systemPrompt)
            put("messages", messagesArray)
        }

        val request = Request.Builder()
            .url(CLAUDE_ENDPOINT)
            .addHeader("x-api-key", apiKey.trim())
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("content-type", "application/json")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    when (response.code) {
                        401, 403 -> throw ShailaAiException.InvalidApiKey()
                        429 -> throw ShailaAiException.QuotaExceeded()
                        else -> {
                            val errMsg = runCatching {
                                JSONObject(rawBody).optJSONObject("error")?.optString("message")
                            }.getOrNull().orEmpty().ifBlank { "HTTP ${response.code}" }
                            throw ShailaAiException.ServerError(errMsg)
                        }
                    }
                }
                val json = JSONObject(rawBody)
                val content = json.optJSONArray("content") ?: return ""
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val block = content.optJSONObject(i) ?: continue
                    if (block.optString("type") == "text") {
                        sb.append(block.optString("text"))
                    }
                }
                return sb.toString().trim()
            }
        } catch (e: ShailaAiException) {
            throw e
        } catch (e: UnknownHostException) {
            throw ShailaAiException.NoInternet()
        } catch (e: IOException) {
            throw ShailaAiException.NoInternet()
        } catch (e: Exception) {
            throw ShailaAiException.ServerError(e.localizedMessage ?: "Unknown error")
        }
    }

    fun parseVideoAnalysisJson(raw: String): VideoAnalysisResult {
        val cleaned = raw
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start in 0 until end) {
            val jsonSlice = cleaned.substring(start, end + 1)
            val parsed = runCatching { JSONObject(jsonSlice) }.getOrNull()
            if (parsed != null) {
                return VideoAnalysisResult(
                    title = parsed.optString("title"),
                    description = parsed.optString("description"),
                    tags = parsed.optString("tags"),
                    caption = parsed.optString("caption"),
                    rawSummary = parsed.optString("summary", cleaned)
                )
            }
        }
        return VideoAnalysisResult(
            title = "",
            description = "",
            tags = "",
            caption = "",
            rawSummary = raw
        )
    }

    companion object {
        private const val CLAUDE_ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
