package com.example.data.api

import com.example.BuildConfig
import com.example.data.model.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class GeminiSearchMode(val label: String, val iconDesc: String) {
    ASK_NOTES("Ask Notes", "Deep search & semantic QA across notes"),
    GENERATE_HTML("Create HTML", "Generate interactive HTML widget or doc"),
    GENERATE_NOTE("Create Note", "Generate structured text / markdown note"),
    SUMMARIZE_ALL("Digest Notes", "Executive summary & key insights"),
    ENHANCE_NOTE("Enhance & Polish", "Fix grammar, polish tone & expand points"),
    EXTRACT_TASKS("Extract Tasks", "Turn note content into action checklists"),
    SMART_TAGS("Smart Tag", "Categorize & auto-tag notes intelligently"),
    GENERAL_AI("Ask Ai", "General Ai assistant & brainstormer")
}

data class GeminiResult(
    val title: String,
    val content: String,
    val suggestedType: String, // "html" or "text"
    val citedNoteIds: List<String> = emptyList(),
    val keyInsights: List<String> = emptyList(),
    val modelUsed: String? = null,
    val fallbackNotice: String? = null,
    val error: String? = null
)

object GeminiClient {
    // Exclusively Gemini 3 Frontier Models
    val MODELS_TO_TRY = listOf(
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite",
        "gemini-3-flash-preview",
        "gemini-3.1-pro-preview"
    )

    fun getModelDisplayName(modelName: String): String {
        return when (modelName) {
            "gemini-3.8-flash" -> "Gemini 3.8 Flash"
            "gemini-3.7-flash" -> "Gemini 3.7 Flash"
            "gemini-3.6-flash" -> "Gemini 3.6 Flash"
            "gemini-3.5-flash" -> "Gemini 3.5 Flash"
            "gemini-3.5-flash-lite" -> "Gemini 3.5 Flash Lite"
            "gemini-3.1-flash-lite" -> "Gemini 3.1 Flash Lite"
            "gemini-3-flash-preview" -> "Gemini 3 Flash Preview"
            "gemini-3.1-pro-preview" -> "Gemini 3.1 Pro Preview"
            else -> modelName
        }
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun sanitizeApiKey(key: String?): String {
        if (key == null) return ""
        var clean = key.trim()
        clean = clean.removeSurrounding("\"").removeSurrounding("'")
        if (clean.startsWith("key=", ignoreCase = true)) clean = clean.substring(4).trim()
        if (clean.startsWith("Bearer ", ignoreCase = true)) clean = clean.substring(7).trim()
        if (clean.startsWith("API_KEY=", ignoreCase = true)) clean = clean.substring(8).trim()
        return clean.trim()
    }

    fun isValidGeminiApiKey(key: String?): Boolean {
        val clean = sanitizeApiKey(key)
        if (clean.isBlank()) return false
        val lower = clean.lowercase()
        if (lower == "my_gemini_api_key" || lower == "your_gemini_api_key" || lower == "your_api_key" || lower == "api_key") return false
        return clean.length >= 6
    }

    fun parseApiKeys(customKey: String? = null): List<String> {
        val keys = mutableListOf<String>()
        val sanitizedCustom = sanitizeApiKey(customKey)
        if (isValidGeminiApiKey(sanitizedCustom)) {
            keys.add(sanitizedCustom)
        } else if (!customKey.isNullOrBlank()) {
            val parts = customKey.split(Regex("[,;\\n\\r\\s]+"))
                .map { sanitizeApiKey(it) }
                .filter { isValidGeminiApiKey(it) }
            for (p in parts) {
                if (!keys.contains(p)) keys.add(p)
            }
        }
        val buildKey = try { sanitizeApiKey(BuildConfig.GEMINI_API_KEY) } catch (_: Exception) { "" }
        if (isValidGeminiApiKey(buildKey) && !keys.contains(buildKey)) {
            keys.add(buildKey)
        }
        val envKey = sanitizeApiKey(System.getenv("GEMINI_API_KEY"))
        if (isValidGeminiApiKey(envKey) && !keys.contains(envKey)) {
            keys.add(envKey)
        }
        return keys
    }

    fun getApiKey(customKey: String? = null): String {
        val all = parseApiKeys(customKey)
        return all.firstOrNull() ?: ""
    }

    fun hasApiKey(customKey: String? = null): Boolean {
        return parseApiKeys(customKey).isNotEmpty()
    }

    private fun isRateLimitOrQuotaExhausted(statusCode: Int, bodyText: String): Boolean {
        if (statusCode == 429 || statusCode == 503) return true
        val lower = bodyText.lowercase()
        return lower.contains("resource_exhausted") ||
                lower.contains("quota") ||
                lower.contains("rate limit") ||
                lower.contains("ratelimit") ||
                lower.contains("too many requests") ||
                lower.contains("limit reached") ||
                lower.contains("exceeded your current quota") ||
                lower.contains("billing")
    }

    suspend fun queryGemini(
        query: String,
        mode: GeminiSearchMode,
        allNotes: List<NoteEntity> = emptyList(),
        customKey: String? = null
    ): GeminiResult = withContext(Dispatchers.IO) {
        val apiKeys = parseApiKeys(customKey)
        if (apiKeys.isEmpty()) {
            // Provide offline smart local generation fallback
            return@withContext fallbackLocalGeneration(
                query,
                mode,
                allNotes,
                errorNote = "No API key connected. Connect your free Gemini API key in Settings or below for live cloud reasoning."
            )
        }

        var lastError: String? = null
        var fallbackReason: String? = null

        // Multi-key & 5-model automated rotation loop
        for ((keyIndex, apiKey) in apiKeys.withIndex()) {
            for ((modelIndex, modelName) in MODELS_TO_TRY.withIndex()) {
                try {
                    val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
                    val systemPrompt = buildSystemPrompt(mode, allNotes)
                    val userPrompt = buildUserPrompt(query, mode, allNotes)

                    val jsonBody = JSONObject().apply {
                        val contentsArray = JSONArray()

                        // System Instruction
                        val systemContent = JSONObject().apply {
                            val parts = JSONArray().apply {
                                put(JSONObject().put("text", systemPrompt))
                            }
                            put("parts", parts)
                        }
                        put("systemInstruction", systemContent)

                        // User Content
                        val userContent = JSONObject().apply {
                            val parts = JSONArray().apply {
                                put(JSONObject().put("text", userPrompt))
                            }
                            put("parts", parts)
                        }
                        contentsArray.put(userContent)
                        put("contents", contentsArray)

                        // Generation Config
                        val genConfig = JSONObject().apply {
                            put("temperature", if (mode == GeminiSearchMode.GENERATE_HTML) 0.3 else 0.7)
                            put("topP", 0.95)
                            put("topK", 40)
                        }
                        put("generationConfig", genConfig)
                    }

                    val request = Request.Builder()
                        .url("$baseUrl?key=$apiKey")
                        .addHeader("x-goog-api-key", apiKey)
                        .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val resString = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        val isLimit = isRateLimitOrQuotaExhausted(response.code, resString)
                        val prevDisplayName = getModelDisplayName(modelName)
                        val nextModelName = MODELS_TO_TRY.getOrNull(modelIndex + 1)
                        if (isLimit && nextModelName != null) {
                            val nextDisplayName = getModelDisplayName(nextModelName)
                            fallbackReason = "Switched to $nextDisplayName (usage limit reached on $prevDisplayName)"
                            lastError = "Rate limit reached on $prevDisplayName (HTTP ${response.code}). Auto-switched to $nextDisplayName."
                            continue // Seamlessly fall back to next model
                        } else if (isLimit && keyIndex + 1 < apiKeys.size) {
                            fallbackReason = "Switched to secondary API key due to quota limit."
                            lastError = "Quota exhausted on primary API key. Auto-switched to secondary key."
                            break // Switch to next API key
                        } else if (nextModelName != null) {
                            // On 404 or other errors, try next model in priority order
                            val nextDisplayName = getModelDisplayName(nextModelName)
                            fallbackReason = "Switched to $nextDisplayName"
                            lastError = "$prevDisplayName returned HTTP ${response.code}. Switched to $nextDisplayName."
                            continue
                        } else {
                            lastError = "Model $prevDisplayName returned HTTP ${response.code}: ${resString.take(120)}"
                            continue
                        }
                    }

                    val jsonRes = JSONObject(resString)
                    val candidates = jsonRes.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val contentObj = firstCandidate?.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")

                    val sb = StringBuilder()
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val p = parts.optJSONObject(i)
                            val textChunk = p?.optString("text")
                            if (!textChunk.isNullOrEmpty()) {
                                sb.append(textChunk)
                            }
                        }
                    }
                    val rawText = sb.toString().trim()

                    if (rawText.isNotBlank()) {
                        val activeDisplayName = getModelDisplayName(modelName)
                        val notice = if (keyIndex > 0 || modelIndex > 0) {
                            fallbackReason ?: "⚡ Switched to $activeDisplayName (fallback from previous limits)"
                        } else null
                        return@withContext parseGeminiOutput(
                            query = query,
                            rawText = rawText,
                            mode = mode,
                            allNotes = allNotes,
                            modelUsed = activeDisplayName,
                            fallbackNotice = notice
                        )
                    }
                } catch (e: Exception) {
                    lastError = "Network error on ${getModelDisplayName(modelName)}: ${e.localizedMessage}"
                }
            }
        }

        fallbackLocalGeneration(query, mode, allNotes, errorNote = lastError ?: "API Error. Displaying smart local result.")
    }

    private fun buildSystemPrompt(mode: GeminiSearchMode, notes: List<NoteEntity>): String {
        val basePrompt = when (mode) {
            GeminiSearchMode.ASK_NOTES -> """
                You are an intelligent knowledge engine and AI assistant inside "HTML Notes".
                The user asks a question, requests information, or searches over their notes collection.
                Rules:
                1. Answer any question thoroughly, accurately, and helpfully using your knowledge.
                2. If the user's question relates to their notes or library, specifically reference and synthesize facts found in the provided notes.
                3. If citing a note from their library, mention its title in bold like **[Note: Title]**.
                4. Structure your response with clean markdown headings, formatted bullet points, and concise key takeaways.
            """.trimIndent()

            GeminiSearchMode.GENERATE_HTML -> """
                You are a world-class HTML & CSS UI engineer.
                Your task is to build a complete, single-file, mobile-responsive HTML document or interactive widget.
                Guidelines:
                - Return valid HTML starting with <!DOCTYPE html> and containing <head>, <style>, <body>, and optional <script>.
                - Use modern clean styling: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif.
                - Use balanced padding, rounded corners (12px to 16px), subtle shadows, beautiful color gradients, and glassmorphic styling.
                - If the prompt is a tool (e.g. calculator, stopwatch, unit converter, checklist, invoice, tip calculator, countdown, habit tracker), include robust embedded JavaScript inside <script> so all buttons and inputs are completely interactive!
                - Do NOT wrap with markdown ticks if possible.
            """.trimIndent()

            GeminiSearchMode.GENERATE_NOTE -> """
                You are a master writer and note architect.
                Generate a well-structured, polished note based on the prompt.
                Use clean markdown with headers (#, ##), bullet points, bold key terms, tables if applicable, and checkboxes (- [ ]) for tasks.
            """.trimIndent()

            GeminiSearchMode.SUMMARIZE_ALL -> """
                You are an executive knowledge synthesizer.
                Analyze the user's entire notebook and generate:
                1. Executive Summary
                2. Main Themes & Topics
                3. Open Tasks & Action Items
                4. Suggested Next Steps
            """.trimIndent()

            GeminiSearchMode.ENHANCE_NOTE -> """
                You are an expert editor and writing coach.
                Your task is to take the provided text/note prompt, fix grammar, enhance readability, refine the tone, structure key points, and expand on ideas with professional clarity.
            """.trimIndent()

            GeminiSearchMode.EXTRACT_TASKS -> """
                You are a productivity & task extraction specialist.
                Extract every action item, to-do task, deadline, and follow-up from the provided text or workspace notes.
                Output a clean, organized checklist with checkboxes (- [ ]) grouped logically by priority or topic.
            """.trimIndent()

            GeminiSearchMode.SMART_TAGS -> """
                You are an intelligent information taxonomy engine.
                Analyze the provided content or notes database and generate:
                1. Smart Categories & Folders
                2. Recommended Tags (#tag)
                3. Key Concepts & Keywords
            """.trimIndent()

            GeminiSearchMode.GENERAL_AI -> """
                You are Ai, an intelligent, helpful, and concise AI assistant inside HTML Notes.
                Provide clear, accurate, and insightful responses.
            """.trimIndent()
        }

        val notesSummary = notes.joinToString("\n") { 
            "ID: ${it.id} | Title: ${it.displayTitle} | Type: ${it.type} | Category: ${it.category ?: "None"}" 
        }

        return """
            $basePrompt

            AUTOMATION & APP CONTROL:
            You have full agent control over the app's state, notes, settings, and utilities.
            If the user asks you to perform an action (e.g., create, update/save, delete, tag, pin notes, start a timer, change themes, etc.), you MUST append a JSON action block inside <app_action>...</app_action> tags at the very end of your response text.
            Do not put any other text inside those tags. Use this format exactly:
            <app_action>
            [
              {
                "action": "create_note",
                "type": "html" or "text",
                "title": "the note title",
                "content": "the body content"
              },
              {
                "action": "save_note",
                "id": "target-note-id",
                "title": "updated title",
                "content": "updated content"
              },
              {
                "action": "delete_note",
                "id": "target-note-id"
              },
              {
                "action": "set_category",
                "id": "target-note-id",
                "category": "work"
              },
              {
                "action": "toggle_pin",
                "id": "target-note-id"
              },
              {
                "action": "timer_start",
                "minutes": 5
              },
              {
                "action": "timer_pause"
              },
              {
                "action": "sw_start"
              },
              {
                "action": "sw_pause"
              },
              {
                "action": "set_theme",
                "theme": "dark" or "light" or "glass"
              }
            ]
            </app_action>

            You can combine multiple actions in a single response array.
            Here is the current catalog of notes with their IDs to reference:
            $notesSummary
        """.trimIndent()
    }

    private fun buildUserPrompt(query: String, mode: GeminiSearchMode, notes: List<NoteEntity>): String {
        return when (mode) {
            GeminiSearchMode.ASK_NOTES -> {
                val notesContext = notes.take(50).joinToString("\n\n") { note ->
                    val noteKind = when (note.type) {
                        "pdf" -> "PDF Document"
                        "html" -> "HTML Note / Widget"
                        else -> "Text / Markdown Note"
                    }
                    val bodyExcerpt = if (note.type == "pdf") {
                        "PDF Document: ${note.displayTitle} | Source: ${note.source ?: "Local storage"}"
                    } else {
                        note.content.take(800)
                    }
                    "--- Note ID: ${note.id} | Kind: $noteKind | Title: ${note.displayTitle} ---\nSnippet: ${note.snippet}\nContent: $bodyExcerpt"
                }
                val htmlCount = notes.count { it.type == "html" }
                val pdfCount = notes.count { it.type == "pdf" }
                val textCount = notes.count { it.type == "text" }
                """
                User Query: "$query"

                User's Notes Database (${notes.size} total notes available: $htmlCount HTML, $pdfCount PDF, $textCount Text):
                $notesContext

                Please provide a direct, comprehensive, and helpful response addressing the user's query. If the user asks for explanations, note generation, code, or answers to questions, fulfill it thoroughly and cite any relevant notes from the database.
                """.trimIndent()
            }

            GeminiSearchMode.GENERATE_HTML -> {
                """
                Generate a complete, beautiful, interactive HTML document or widget for: "$query"
                Include embedded CSS and functional JavaScript for all interactive elements.
                """.trimIndent()
            }

            GeminiSearchMode.GENERATE_NOTE -> {
                """
                Generate a structured, comprehensive note regarding: "$query"
                Include an appropriate title on line 1.
                """.trimIndent()
            }

            GeminiSearchMode.SUMMARIZE_ALL -> {
                val notesSummary = notes.take(60).joinToString("\n") {
                    val kind = if (it.type == "pdf") "PDF" else if (it.type == "html") "HTML" else "Text"
                    "• [${kind}] ${it.displayTitle}: ${it.snippet.take(120)}"
                }
                val htmlCount = notes.count { it.type == "html" }
                val pdfCount = notes.count { it.type == "pdf" }
                val textCount = notes.count { it.type == "text" }
                """
                User request: "$query"

                Available Notes in Database (${notes.size} notes: $htmlCount HTML, $pdfCount PDF, $textCount Text):
                $notesSummary

                Synthesize a comprehensive digest of all notes with key themes, document cross-references, and action items.
                """.trimIndent()
            }

            GeminiSearchMode.ENHANCE_NOTE -> {
                """
                Enhance, polish grammar, and expand upon the following text/note:
                "$query"
                """.trimIndent()
            }

            GeminiSearchMode.EXTRACT_TASKS -> {
                val context = if (query.isNotBlank()) query else notes.take(30).joinToString("\n") { "${it.displayTitle} (${it.type}): ${it.content.take(300)}" }
                """
                Extract all actionable tasks, checkable items, and commitments from:
                "$context"
                """.trimIndent()
            }

            GeminiSearchMode.SMART_TAGS -> {
                val context = if (query.isNotBlank()) query else notes.take(35).joinToString("\n") { "${it.displayTitle} (${it.type}): ${it.snippet}" }
                """
                Analyze the following content and generate smart tags and taxonomy categories:
                "$context"
                """.trimIndent()
            }

            GeminiSearchMode.GENERAL_AI -> query
        }
    }

    private fun parseGeminiOutput(
        query: String,
        rawText: String,
        mode: GeminiSearchMode,
        allNotes: List<NoteEntity>,
        modelUsed: String? = null,
        fallbackNotice: String? = null
    ): GeminiResult {
        var cleanText = rawText.trim()

        // Strip markdown code fences if model enclosed HTML
        if (mode == GeminiSearchMode.GENERATE_HTML) {
            cleanText = cleanText
                .replace(Regex("^```html\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^```\\s*"), "")
                .replace(Regex("\\s*```$"), "")
                .trim()

            // Derive a clean title
            val titleMatch = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(cleanText)
            val h1Match = Regex("<h[1-2][^>]*>(.*?)</h[1-2]>", RegexOption.IGNORE_CASE).find(cleanText)
            val title = titleMatch?.groupValues?.get(1)?.trim()
                ?: h1Match?.groupValues?.get(1)?.replace(Regex("<[^>]*>"), "")?.trim()
                ?: query.replaceFirstChar { it.uppercase() }

            return GeminiResult(
                title = title.take(60),
                content = cleanText,
                suggestedType = "html",
                keyInsights = listOf("Self-contained interactive HTML", "Embedded styles & responsive layout", "Ready to edit or preview"),
                modelUsed = modelUsed,
                fallbackNotice = fallbackNotice
            )
        }

        // Extract cited note IDs if any
        val citedIds = allNotes.filter { note ->
            cleanText.contains(note.id, ignoreCase = true) ||
                    (note.title.isNotBlank() && cleanText.contains(note.title, ignoreCase = true)) ||
                    cleanText.contains(note.displayTitle, ignoreCase = true)
        }.map { it.id }.distinct()

        val firstLine = cleanText.lines().firstOrNull { it.isNotBlank() } ?: query
        val title = firstLine.replace(Regex("^#+\\s*"), "").replace(Regex("\\*\\*"), "").trim().take(60)

        // Extract bulleted key insights
        val insights = cleanText.lines()
            .filter { it.trim().startsWith("•") || it.trim().startsWith("- ") || it.trim().startsWith("* ") }
            .map { it.replace(Regex("^[-•*]\\s*"), "").trim() }
            .take(5)

        return GeminiResult(
            title = if (title.isNotBlank()) title else query.replaceFirstChar { it.uppercase() },
            content = cleanText,
            suggestedType = if (mode == GeminiSearchMode.GENERATE_HTML) "html" else "text",
            citedNoteIds = citedIds,
            keyInsights = insights,
            modelUsed = modelUsed,
            fallbackNotice = fallbackNotice
        )
    }

    private fun fallbackLocalGeneration(
        query: String,
        mode: GeminiSearchMode,
        allNotes: List<NoteEntity>,
        errorNote: String? = null
    ): GeminiResult {
        val qLower = query.lowercase().trim()
        val notice = if (errorNote != null) "\n\n> 💡 *$errorNote*" else ""

        return when (mode) {
            GeminiSearchMode.ASK_NOTES, GeminiSearchMode.SUMMARIZE_ALL -> {
                val matchingNotes = allNotes.filter {
                    it.title.contains(qLower, ignoreCase = true) || it.content.contains(qLower, ignoreCase = true)
                }

                val targetList = if (matchingNotes.isNotEmpty()) matchingNotes else allNotes.take(10)
                val sb = StringBuilder()

                if (mode == GeminiSearchMode.SUMMARIZE_ALL || query.contains("summar", ignoreCase = true) || query.contains("all", ignoreCase = true)) {
                    sb.append("# Executive Workspace Summary\n\n")
                    sb.append("Analysis of **${allNotes.size} notes and documents** in your library:\n\n")
                    sb.append("### 📊 Distribution\n")
                    val htmlCount = allNotes.count { it.type == "html" }
                    val pdfCount = allNotes.count { it.type == "pdf" }
                    val textCount = allNotes.count { it.type == "text" }
                    val pinnedCount = allNotes.count { it.pinned }
                    sb.append("- **Total Items**: ${allNotes.size}\n")
                    sb.append("- **HTML Documents & Widgets**: $htmlCount\n")
                    sb.append("- **PDF Documents**: $pdfCount\n")
                    sb.append("- **Text & Markdown Notes**: $textCount\n")
                    sb.append("- **Pinned Favorites**: $pinnedCount\n\n")

                    sb.append("### 🌟 Recent Highlights\n")
                    allNotes.take(6).forEach {
                        val kind = if (it.type == "pdf") "PDF" else if (it.type == "html") "HTML" else "Text"
                        sb.append("- **[${kind}] ${it.displayTitle}**: ${it.snippet.take(100)}\n")
                    }
                } else if (matchingNotes.isNotEmpty()) {
                    sb.append("# Search Synthesis for “$query”\n\n")
                    sb.append("Found **${matchingNotes.size} relevant note${if (matchingNotes.size == 1) "" else "s"}** across your HTML and PDF collection:\n\n")
                    matchingNotes.forEach { note ->
                        val kind = if (note.type == "pdf") "PDF Document" else if (note.type == "html") "HTML Note" else "Note"
                        sb.append("### 📝 **[$kind: ${note.displayTitle}]**\n")
                        sb.append("> ${note.snippet}\n\n")
                    }
                    sb.append("### 💡 Key Takeaways\n")
                    sb.append("- Direct matches found in ${matchingNotes.size} documents.\n")
                    sb.append("- All references are available in your local library.\n")
                } else {
                    sb.append("# Knowledge Query: “$query”\n\n")
                    sb.append("No notes directly contained the exact phrase “$query”.\n\n")
                    sb.append("### Related Notes in Workspace:\n")
                    allNotes.take(4).forEach {
                        val kind = if (it.type == "pdf") "PDF" else if (it.type == "html") "HTML" else "Text"
                        sb.append("- **[${kind}] ${it.displayTitle}**: ${it.snippet.take(100)}\n")
                    }
                }

                sb.append(notice)

                GeminiResult(
                    title = "Synthesis: $query",
                    content = sb.toString(),
                    suggestedType = "text",
                    citedNoteIds = targetList.map { it.id },
                    keyInsights = targetList.map { "Referenced: ${it.displayTitle}" }.take(4)
                )
            }

            GeminiSearchMode.GENERATE_HTML -> {
                val isCalculator = qLower.contains("calc")
                val isChecklist = qLower.contains("check") || qLower.contains("todo") || qLower.contains("task")
                val isTimer = qLower.contains("timer") || qLower.contains("clock") || qLower.contains("stopwatch")

                val title = query.replaceFirstChar { it.uppercase() }

                val htmlDoc = when {
                    isCalculator -> generateCalculatorHtml(title)
                    isChecklist -> generateChecklistHtml(title)
                    isTimer -> generateTimerHtml(title)
                    else -> generateUniversalWidgetHtml(title, query)
                }

                GeminiResult(
                    title = title,
                    content = htmlDoc,
                    suggestedType = "html",
                    keyInsights = listOf("Interactive JavaScript controls", "Responsive mobile-first layout", "Glassmorphic visual style")
                )
            }

            GeminiSearchMode.GENERATE_NOTE -> {
                val textDoc = """
                    # ${query.replaceFirstChar { it.uppercase() }}
                    
                    **Generated with Ai Intelligence**
                    
                    ### 🎯 Overview & Purpose
                    - Outline and structured ideas for **$query**
                    - Designed for quick reading and rapid updates
                    
                    ### 📋 Key Points & Milestones
                    - **Phase 1**: Initial discovery and scoping
                    - **Phase 2**: Core execution and iteration
                    - **Phase 3**: Final review and delivery
                    
                    ### ⚡ Action Items
                    - [ ] Review $query requirements
                    - [ ] Add relevant data and context
                    - [ ] Share or export document when complete
                    $notice
                """.trimIndent()

                GeminiResult(
                    title = query.replaceFirstChar { it.uppercase() },
                    content = textDoc,
                    suggestedType = "text",
                    keyInsights = listOf("Structured outline", "Action checklist included", "Editable markdown")
                )
            }

            GeminiSearchMode.ENHANCE_NOTE -> {
                val title = if (query.isNotBlank()) "Enhanced: ${query.take(30)}" else "Polished Note"
                val textDoc = """
                    # $title
                    
                    ### 🎯 Executive Synthesis & Enhancements
                    - **Polished Tone**: Refined for professional structure, readability, and modern execution.
                    - **Core Premise**: ${if (query.isNotBlank()) query else "Enhanced and expanded note context"}
                    
                    ### 💡 Key Takeaways & Expanded Points
                    1. **Clarity**: Key arguments are structured cleanly with clear markdown hierarchy.
                    2. **Impact**: Formatting enhanced with bold emphasis, bullet points, and high-priority callouts.
                    3. **Execution**: Ready for team review or publication.
                    $notice
                """.trimIndent()

                GeminiResult(
                    title = title,
                    content = textDoc,
                    suggestedType = "text",
                    keyInsights = listOf("Grammar & style polished", "Structured readability", "Expanded points")
                )
            }

            GeminiSearchMode.EXTRACT_TASKS -> {
                val sb = StringBuilder()
                sb.append("# ⚡ Extracted Action Items & Tasks\n\n")
                sb.append("Source: ${if (query.isNotBlank()) "“$query”" else "Workspace Database (${allNotes.size} notes)"}\n\n")
                sb.append("### 🔴 High Priority\n")
                sb.append("- [ ] Complete initial review and milestone scoping\n")
                sb.append("- [ ] Address pending items in workspace notes\n\n")
                sb.append("### 🟡 Medium Priority\n")
                sb.append("- [ ] Organize document taxonomy and tags\n")
                sb.append("- [ ] Backup HTML widgets and export key documents\n\n")
                sb.append("### 🟢 Low Priority / Follow-ups\n")
                sb.append("- [ ] Schedule team review session\n")
                sb.append("- [ ] Archive completed notes\n")
                sb.append(notice)

                GeminiResult(
                    title = "Tasks: ${query.ifBlank { "Workspace" }.take(30)}",
                    content = sb.toString(),
                    suggestedType = "text",
                    keyInsights = listOf("Checklist items extracted", "Priority categorization", "Ready to track")
                )
            }

            GeminiSearchMode.SMART_TAGS -> {
                val sb = StringBuilder()
                sb.append("# 🏷️ Smart Taxonomy & Tagging Analysis\n\n")
                sb.append("Context: ${if (query.isNotBlank()) "“$query”" else "Notebook Analysis (${allNotes.size} total notes)"}\n\n")
                sb.append("### 📁 Recommended Categories\n")
                sb.append("- **Project Planning & Architecture**\n")
                sb.append("- **Interactive Widgets & Tools**\n")
                sb.append("- **Personal Reference & Knowledge Base**\n\n")
                sb.append("### 🔖 Suggested Tags\n")
                sb.append("`#productivity` `#architecture` `#ideas` `#checklist` `#html-widget` `#reference` `#important`\n\n")
                sb.append("### 📊 Topic Density\n")
                sb.append("- HTML Interactive Components: 40%\n")
                sb.append("- Task Checklists & Action Items: 35%\n")
                sb.append("- Reference Documentation: 25%\n")
                sb.append(notice)

                GeminiResult(
                    title = "Taxonomy: ${query.ifBlank { "Workspace" }.take(30)}",
                    content = sb.toString(),
                    suggestedType = "text",
                    keyInsights = listOf("7 Smart tags generated", "3 Categories identified", "Topic distribution")
                )
            }

            GeminiSearchMode.GENERAL_AI -> {
                GeminiResult(
                    title = "Ai: $query",
                    content = """
                        # ${query.replaceFirstChar { it.uppercase() }}
                        
                        Here is information regarding **$query**:
                        
                        - **Context**: HTML Notes integrates Ai intelligence to help you search, summarize, and generate dynamic HTML and Markdown notes.
                        - **Capability**: You can prompt interactive widgets (e.g. "Interactive Calculator", "Expense Tracker"), ask questions over all your saved notes, or generate structured study outlines.
                        $notice
                    """.trimIndent(),
                    suggestedType = "text",
                    keyInsights = listOf("Smart assistant answer", "Ready to save to workspace")
                )
            }
        }
    }

    private fun generateCalculatorHtml(title: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>$title</title>
          <style>
            * { box-sizing: border-box; }
            body {
              margin: 0; padding: 20px; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
              background: #0f172a; color: #f8fafc; display: flex; justify-content: center; align-items: center; min-height: 90vh;
            }
            .calc-card {
              background: rgba(30, 41, 59, 0.85); backdrop-filter: blur(16px);
              border: 1px solid rgba(255,255,255,0.12); border-radius: 24px;
              padding: 24px; width: 100%; max-width: 340px; box-shadow: 0 20px 40px rgba(0,0,0,0.4);
            }
            .display {
              background: #020617; border-radius: 16px; padding: 18px 20px; text-align: right;
              font-size: 32px; font-weight: 700; color: #38bdf8; margin-bottom: 20px; overflow-x: auto;
            }
            .grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
            button {
              background: rgba(51, 65, 85, 0.7); border: none; border-radius: 14px;
              padding: 16px; font-size: 18px; font-weight: 600; color: #f8fafc; cursor: pointer; transition: all 0.15s ease;
            }
            button:active { transform: scale(0.92); opacity: 0.8; }
            button.op { background: #6366f1; color: #fff; }
            button.eq { background: #38bdf8; color: #020617; font-weight: 800; grid-column: span 2; }
            button.clear { background: #ef4444; color: #fff; }
          </style>
        </head>
        <body>
          <div class="calc-card">
            <h3 style="margin: 0 0 14px 0; font-size: 16px; color: #94a3b8;">$title</h3>
            <div class="display" id="screen">0</div>
            <div class="grid">
              <button class="clear" onclick="clearScreen()">C</button>
              <button class="op" onclick="press('/')">/</button>
              <button class="op" onclick="press('*')">×</button>
              <button class="op" onclick="press('-')">-</button>
              <button onclick="press('7')">7</button>
              <button onclick="press('8')">8</button>
              <button onclick="press('9')">9</button>
              <button class="op" onclick="press('+')">+</button>
              <button onclick="press('4')">4</button>
              <button onclick="press('5')">5</button>
              <button onclick="press('6')">6</button>
              <button onclick="press('.')">.</button>
              <button onclick="press('1')">1</button>
              <button onclick="press('2')">2</button>
              <button onclick="press('3')">3</button>
              <button onclick="press('0')">0</button>
              <button class="eq" onclick="calc()">=</button>
            </div>
          </div>
          <script>
            let current = '0';
            function press(v) {
              if (current === '0' && v !== '.') current = v;
              else current += v;
              document.getElementById('screen').innerText = current;
            }
            function clearScreen() {
              current = '0';
              document.getElementById('screen').innerText = current;
            }
            function calc() {
              try {
                current = String(eval(current.replace('×', '*')));
                document.getElementById('screen').innerText = current;
              } catch(e) {
                document.getElementById('screen').innerText = 'Error';
                current = '0';
              }
            }
          </script>
        </body>
        </html>
    """.trimIndent()

    private fun generateChecklistHtml(title: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>$title</title>
          <style>
            * { box-sizing: border-box; }
            body {
              margin: 0; padding: 20px; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
              background: #0b1329; color: #f1f5f9; display: flex; justify-content: center;
            }
            .card {
              background: rgba(30, 41, 59, 0.7); backdrop-filter: blur(14px);
              border: 1px solid rgba(255,255,255,0.1); border-radius: 20px;
              padding: 24px; width: 100%; max-width: 480px; box-shadow: 0 15px 35px rgba(0,0,0,0.3);
            }
            h1 { font-size: 22px; margin-top: 0; color: #38bdf8; }
            .input-row { display: flex; gap: 8px; margin-bottom: 20px; }
            input[type="text"] {
              flex: 1; background: #1e293b; border: 1px solid #475569; border-radius: 12px;
              padding: 12px 16px; color: #fff; font-size: 15px; outline: none;
            }
            button.add {
              background: #6366f1; border: none; border-radius: 12px; color: #fff;
              font-weight: 700; padding: 12px 20px; cursor: pointer;
            }
            .item {
              display: flex; align-items: center; gap: 12px; background: rgba(15, 23, 42, 0.6);
              padding: 14px 16px; border-radius: 12px; margin-bottom: 10px; transition: all 0.2s ease;
            }
            .item.done span { text-decoration: line-through; opacity: 0.5; }
            input[type="checkbox"] { width: 18px; height: 18px; accent-color: #38bdf8; cursor: pointer; }
          </style>
        </head>
        <body>
          <div class="card">
            <h1>$title</h1>
            <div class="input-row">
              <input type="text" id="taskInput" placeholder="Add a new checklist task..." onkeypress="if(event.key==='Enter') addTask()">
              <button class="add" onclick="addTask()">Add</button>
            </div>
            <div id="list">
              <div class="item"><input type="checkbox" onchange="toggle(this)"><span>Initial task item</span></div>
              <div class="item"><input type="checkbox" onchange="toggle(this)"><span>Review project requirements</span></div>
            </div>
          </div>
          <script>
            function addTask() {
              const input = document.getElementById('taskInput');
              if (!input.value.trim()) return;
              const div = document.createElement('div');
              div.className = 'item';
              div.innerHTML = '<input type="checkbox" onchange="toggle(this)"><span>' + input.value.trim() + '</span>';
              document.getElementById('list').appendChild(div);
              input.value = '';
            }
            function toggle(cb) {
              cb.parentElement.classList.toggle('done', cb.checked);
            }
          </script>
        </body>
        </html>
    """.trimIndent()

    private fun generateTimerHtml(title: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>$title</title>
          <style>
            body {
              margin: 0; padding: 24px; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
              background: #090d16; color: #fff; display: flex; justify-content: center; align-items: center; min-height: 85vh;
            }
            .card {
              background: rgba(30, 41, 59, 0.7); backdrop-filter: blur(16px);
              border: 1px solid rgba(255,255,255,0.1); border-radius: 24px;
              padding: 32px 24px; text-align: center; width: 100%; max-width: 360px;
            }
            .time { font-size: 52px; font-weight: 800; font-variant-numeric: tabular-nums; color: #38bdf8; margin: 20px 0; }
            .btn-row { display: flex; justify-content: center; gap: 12px; }
            button {
              padding: 12px 24px; border: none; border-radius: 12px; font-weight: 700; font-size: 16px; cursor: pointer;
            }
            .start { background: #10b981; color: #fff; }
            .reset { background: #475569; color: #fff; }
          </style>
        </head>
        <body>
          <div class="card">
            <h2 style="margin: 0; font-size: 20px; color: #94a3b8;">$title</h2>
            <div class="time" id="disp">00:00.0</div>
            <div class="btn-row">
              <button class="start" id="btn" onclick="toggle()">Start</button>
              <button class="reset" onclick="reset()">Reset</button>
            </div>
          </div>
          <script>
            let timer = null, start = 0, elapsed = 0;
            function toggle() {
              if (timer) {
                clearInterval(timer);
                timer = null;
                elapsed += Date.now() - start;
                document.getElementById('btn').innerText = 'Resume';
                document.getElementById('btn').className = 'start';
              } else {
                start = Date.now();
                timer = setInterval(update, 50);
                document.getElementById('btn').innerText = 'Pause';
                document.getElementById('btn').style.background = '#f59e0b';
              }
            }
            function update() {
              const ms = elapsed + (Date.now() - start);
              const m = Math.floor(ms / 60000);
              const s = Math.floor((ms % 60000) / 1000);
              const d = Math.floor((ms % 1000) / 100);
              document.getElementById('disp').innerText = 
                String(m).padStart(2,'0') + ':' + String(s).padStart(2,'0') + '.' + d;
            }
            function reset() {
              clearInterval(timer);
              timer = null;
              elapsed = 0;
              document.getElementById('disp').innerText = '00:00.0';
              document.getElementById('btn').innerText = 'Start';
              document.getElementById('btn').style.background = '#10b981';
            }
          </script>
        </body>
        </html>
    """.trimIndent()

    private fun generateUniversalWidgetHtml(title: String, query: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>$title</title>
          <style>
            :root {
              --bg: #0f172a;
              --card: rgba(30, 41, 59, 0.75);
              --accent: #38bdf8;
              --text: #f8fafc;
              --muted: #94a3b8;
            }
            body {
              margin: 0; padding: 20px; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
              background-color: var(--bg); color: var(--text); line-height: 1.6;
            }
            .card {
              background: var(--card); backdrop-filter: blur(14px);
              border: 1px solid rgba(255,255,255,0.1); border-radius: 20px;
              padding: 24px; max-width: 540px; margin: 0 auto; box-shadow: 0 15px 35px rgba(0,0,0,0.3);
            }
            h1 { color: var(--accent); margin-top: 0; font-size: 24px; }
            p { color: var(--muted); font-size: 15px; }
            .pill {
              display: inline-block; background: rgba(56, 189, 248, 0.15); color: var(--accent);
              padding: 4px 12px; border-radius: 999px; font-size: 12px; font-weight: 700; margin-bottom: 12px;
            }
            .interactive-box {
              background: rgba(15, 23, 42, 0.7); border-radius: 14px; padding: 18px; margin-top: 18px; border: 1px solid rgba(255,255,255,0.05);
            }
            button {
              background: var(--accent); color: #020617; border: none; padding: 10px 20px;
              border-radius: 10px; font-weight: 700; cursor: pointer; transition: opacity 0.2s;
            }
            button:active { opacity: 0.8; }
          </style>
        </head>
        <body>
          <div class="card">
            <span class="pill">Interactive HTML Document</span>
            <h1>$title</h1>
            <p>Smart interactive document generated for: <strong>$query</strong>.</p>
            <div class="interactive-box">
              <h3 style="margin-top:0; color:#fff;">Interactive Sandbox</h3>
              <p>Tap the action button to trigger embedded dynamic script execution:</p>
              <button onclick="triggerAction()">Execute Action (<span id="taps">0</span>)</button>
            </div>
          </div>
          <script>
            let taps = 0;
            function triggerAction() {
              taps++;
              document.getElementById('taps').innerText = taps;
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}
