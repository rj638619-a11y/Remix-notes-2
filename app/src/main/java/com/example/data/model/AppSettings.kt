package com.example.data.model

data class AppSettings(
    val theme: String = "auto", // "auto", "light", "dark"
    val reduceTransparency: Boolean = false,
    val readingFontSize: Int = 17, // 13..24
    val autoSync: Boolean = true,
    val syncFolderUri: String? = null,
    val syncFolderName: String? = null,
    val lastSyncTime: Long = 0L,
    val sortOrder: String = "edited", // "edited", "created", "title"
    val viewMode: String = "list", // "list", "grid"
    val biometricLockEnabled: Boolean = false,
    val faceEnrolled: Boolean = false,
    val faceSignature: String = "",
    val pinCode: String = "",
    val appLockEnabled: Boolean = false,
    val readerMode: String = "html", // "html" or "pdf"
    val pdfPageMode: String = "continuous", // "continuous", "single"
    val pdfColorFilter: String = "default", // "default", "sepia", "dark", "invert"
    val pdfRenderQuality: String = "sharp", // "sharp", "balanced"
    val geminiApiKey: String = "",
    val hapticsEnabled: Boolean = true
)
