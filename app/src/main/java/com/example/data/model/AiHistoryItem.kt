package com.example.data.model

data class AiHistoryItem(
    val id: String,
    val query: String,
    val modeType: String, // "html" or "pdf"
    val aiSearchMode: String, // e.g. "ASK_NOTES", "GENERATE_HTML", "SUMMARIZE_ALL", etc.
    val responseTitle: String,
    val responseContent: String,
    val suggestedType: String, // "html" or "text"
    val timestamp: Long = System.currentTimeMillis()
)
