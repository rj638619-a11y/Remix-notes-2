package com.example.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.GeminiSearchMode
import com.example.data.model.NoteEntity
import com.example.ui.components.GlassIconButton
import com.example.ui.theme.GlassTheme
import com.example.ui.util.telegramBounceClickable
import com.example.util.PdfHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thread-safe, hardware-accelerated memory-efficient PDF Page Rendering Engine.
 * Utilizes LRU caching with Mutex protection to completely avoid PdfRenderer concurrent access crashes.
 */
class PdfPageEngine(
    private val renderer: PdfRenderer?,
    private val displayDensity: Float,
    private val isLowEndDevice: Boolean = false
) {
    private val mutex = Mutex()
    private val maxMemoryBytes = (Runtime.getRuntime().maxMemory() / 8).toInt().coerceIn(16 * 1024 * 1024, 64 * 1024 * 1024)

    private val lruCache = object : LruCache<String, Bitmap>(maxMemoryBytes) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    suspend fun renderPage(
        pageIndex: Int,
        zoomScale: Float,
        renderQuality: String = "sharp"
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (renderer == null || pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

        // Calculate dynamic supersampled resolution for razor-sharp text
        val baseMultiplier = when {
            isLowEndDevice -> 1.5f
            renderQuality == "sharp" -> 2.2f
            else -> 1.8f
        }

        // Clamp zoom factor to ensure low-end safety while giving razor sharpness on high zoom
        val effectiveScale = (zoomScale.coerceIn(1.0f, if (isLowEndDevice) 2.5f else 4.0f))
        val targetDpiScale = (baseMultiplier * effectiveScale).coerceIn(1.5f, 4.5f)

        // Cache key includes quantised zoom bracket to prevent excessive memory churn
        val quantisedScale = (Math.round(targetDpiScale * 2) / 2.0f)
        val cacheKey = "p_${pageIndex}_s_${quantisedScale}"

        synchronized(lruCache) {
            val cached = lruCache.get(cacheKey)
            if (cached != null && !cached.isRecycled) {
                return@withContext cached
            }
        }

        // Render page with strict Mutex lock (PdfRenderer is not thread-safe)
        mutex.withLock {
            try {
                val page = renderer.openPage(pageIndex)
                val targetWidth = (page.width * (displayDensity / 1.5f) * quantisedScale).toInt().coerceAtLeast(300)
                val targetHeight = (page.height * (displayDensity / 1.5f) * quantisedScale).toInt().coerceAtLeast(400)

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(AndroidColor.WHITE)

                val paint = Paint().apply {
                    isAntiAlias = true
                    isFilterBitmap = true
                    isDither = true
                }

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                synchronized(lruCache) {
                    lruCache.put(cacheKey, bitmap)
                }

                bitmap
            } catch (t: Throwable) {
                null
            }
        }
    }

    fun clearCache() {
        synchronized(lruCache) {
            lruCache.evictAll()
        }
    }
}

enum class PdfPaperTheme {
    DEFAULT, // Crisp White Paper
    SEPIA,   // Warm Eye-Friendly Tint
    NIGHT,   // Inverted Night Reading (eye strain reduction)
    OLED     // Pure Black Matrix
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfRendererView(
    note: NoteEntity,
    initialZenMode: Boolean = false,
    onBack: () -> Unit,
    onSaveTitle: (String) -> Unit = {},
    onSharePdf: (File) -> Unit = {},
    onOpenMore: () -> Unit = {},
    onAskGemini: (prompt: String, mode: GeminiSearchMode) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = GlassTheme.colors
    val density = LocalDensity.current.density
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val scope = rememberCoroutineScope()

    var pdfFile by remember { mutableStateOf<File?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageEngine by remember { mutableStateOf<PdfPageEngine?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Zen & Layout controls
    var isZenMode by remember(note.id, initialZenMode) { mutableStateOf(initialZenMode) }
    var isContinuousMode by remember { mutableStateOf(true) }
    var paperTheme by remember { mutableStateOf(if (colors.isDark) PdfPaperTheme.NIGHT else PdfPaperTheme.DEFAULT) }

    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // In-PDF Search dialog
    var isSearchOpen by remember { mutableStateOf(false) }
    var pdfSearchQuery by remember { mutableStateOf("") }

    // Jump to page dialog
    var isJumpPageOpen by remember { mutableStateOf(false) }
    var jumpPageInput by remember { mutableStateOf("") }

    // Editable Title
    var editableTitle by remember(note.id) { mutableStateOf(note.title) }

    // Lazy list and Pager states
    val listState = rememberLazyListState()
    val pagerState = rememberPagerState(pageCount = { maxOf(1, pageCount) })

    // Auto-detect low memory / low-end mobile
    val isLowEnd = remember {
        val memoryClass = (context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.memoryClass ?: 128
        memoryClass < 200
    }

    // Dynamic zoom debouncing for instant vector text sharpening
    var debouncedScaleForRendering by remember { mutableFloatStateOf(1.0f) }
    var zoomDebounceJob by remember { mutableStateOf<Job?>(null) }

    fun onScaleChanged(newScale: Float) {
        val clamped = newScale.coerceIn(0.85f, 4.0f)
        scale = clamped
        if (clamped <= 1.0f) {
            offsetX = 0f
            offsetY = 0f
        }
        zoomDebounceJob?.cancel()
        zoomDebounceJob = scope.launch {
            delay(220) // Debounce so pan/pinch is ultra-smooth 60fps/120fps, then sharpens crisp
            debouncedScaleForRendering = clamped
        }
    }

    // Load PDF file and renderer asynchronously
    LaunchedEffect(note.id, note.updatedAt) {
        isLoading = true
        errorMessage = null
        withContext(Dispatchers.IO) {
            try {
                val file = PdfHelper.getPdfFileForNote(context, note)
                pdfFile = file
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                fileDescriptor = pfd
                val renderer = PdfRenderer(pfd)
                pdfRenderer = renderer
                pageCount = renderer.pageCount
                pageEngine = PdfPageEngine(renderer, displayDensity = density, isLowEndDevice = isLowEnd)
                isLoading = false
            } catch (t: Throwable) {
                errorMessage = t.localizedMessage ?: "Failed to render PDF document"
                isLoading = false
            }
        }
    }

    DisposableEffect(note.id) {
        onDispose {
            try {
                pageEngine?.clearCache()
                pdfRenderer?.close()
                fileDescriptor?.close()
            } catch (_: Throwable) {}
        }
    }

    val currentPage by remember {
        derivedStateOf {
            if (isContinuousMode) {
                (listState.firstVisibleItemIndex + 1).coerceIn(1, maxOf(1, pageCount))
            } else {
                (pagerState.currentPage + 1).coerceIn(1, maxOf(1, pageCount))
            }
        }
    }

    BackHandler {
        if (isZenMode) {
            isZenMode = false
        } else {
            onBack()
        }
    }

    // Color filter for paper theme
    val colorFilter = remember(paperTheme) {
        when (paperTheme) {
            PdfPaperTheme.DEFAULT -> null
            PdfPaperTheme.SEPIA -> {
                val matrix = ColorMatrix(
                    floatArrayOf(
                        0.94f, 0f, 0f, 0f, 15f,
                        0f, 0.88f, 0f, 0f, 10f,
                        0f, 0f, 0.76f, 0f, 5f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                ColorFilter.colorMatrix(matrix)
            }
            PdfPaperTheme.NIGHT -> {
                // Invert colors for night reading with high contrast white text on dark background
                val matrix = ColorMatrix(
                    floatArrayOf(
                        -0.85f, 0f, 0f, 0f, 230f,
                        0f, -0.85f, 0f, 0f, 230f,
                        0f, 0f, -0.85f, 0f, 230f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                ColorFilter.colorMatrix(matrix)
            }
            PdfPaperTheme.OLED -> {
                val matrix = ColorMatrix(
                    floatArrayOf(
                        -1.0f, 0f, 0f, 0f, 255f,
                        0f, -1.0f, 0f, 0f, 255f,
                        0f, 0f, -1.0f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                ColorFilter.colorMatrix(matrix)
            }
        }
    }

    val readerBgColor = when (paperTheme) {
        PdfPaperTheme.DEFAULT -> if (colors.isDark) Color(0xFF18181B) else Color(0xFFF3F4F6)
        PdfPaperTheme.SEPIA -> Color(0xFFFBF0D9)
        PdfPaperTheme.NIGHT -> Color(0xFF121214)
        PdfPaperTheme.OLED -> Color(0xFF000000)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(readerBgColor)
    ) {
        if (isLoading) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Rendering Crisp PDF Document…",
                    fontSize = 15.sp,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Hardware-accelerated dynamic vector clarity",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } else if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "PDF Display Error",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.danger
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorMessage ?: "Unable to read document",
                    fontSize = 13.5.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accent)
                        .clickable { onBack() }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Go Back",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // Main Zoomable Document Canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                // Toggle Zen/Immersive mode on tap
                                isZenMode = !isZenMode
                            },
                            onDoubleTap = {
                                // Cycle double tap zoom (1x -> 2.2x -> 3.5x -> 1x)
                                val nextScale = when {
                                    scale < 1.4f -> 2.0f
                                    scale < 2.8f -> 3.2f
                                    else -> 1.0f
                                }
                                onScaleChanged(nextScale)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val targetScale = (scale * zoom).coerceIn(0.85f, 4.0f)
                            scale = targetScale
                            if (targetScale > 1.0f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            zoomDebounceJob?.cancel()
                            zoomDebounceJob = scope.launch {
                                delay(220)
                                debouncedScaleForRendering = targetScale
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
            ) {
                if (isContinuousMode) {
                    // Continuous Vertical Flow
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = if (isZenMode) 20.dp else statusBarTop + 68.dp,
                            bottom = if (isZenMode) 80.dp else 100.dp,
                            start = 8.dp,
                            end = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(pageCount) { index ->
                            PdfPageCard(
                                pageEngine = pageEngine,
                                pageIndex = index,
                                renderScale = debouncedScaleForRendering,
                                pageCount = pageCount,
                                colorFilter = colorFilter,
                                paperTheme = paperTheme
                            )
                        }
                    }
                } else {
                    // Single Page Horizontal Presentation
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = if (isZenMode) 20.dp else statusBarTop + 68.dp,
                            bottom = if (isZenMode) 80.dp else 100.dp,
                            start = 12.dp,
                            end = 12.dp
                        ),
                        pageSpacing = 16.dp
                    ) { pageIndex ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            PdfPageCard(
                                pageEngine = pageEngine,
                                pageIndex = pageIndex,
                                renderScale = debouncedScaleForRendering,
                                pageCount = pageCount,
                                colorFilter = colorFilter,
                                paperTheme = paperTheme
                            )
                        }
                    }
                }
            }

            // Top Header Bar (Animated in Zen mode)
            AnimatedVisibility(
                visible = !isZenMode,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarTop + 6.dp, start = 12.dp, end = 12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.glass)
                        .border(1.dp, colors.glassBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(colors.field)
                                .telegramBounceClickable { onBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                                contentDescription = "Back",
                                tint = colors.text,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Title & Subtitle with Quick Edit
                        Column(modifier = Modifier.weight(1f)) {
                            BasicTextField(
                                value = editableTitle,
                                onValueChange = {
                                    editableTitle = it
                                    onSaveTitle(it)
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.text
                                ),
                                cursorBrush = SolidColor(colors.accent),
                                decorationBox = { innerTextField ->
                                    if (editableTitle.isBlank()) {
                                        Text("PDF Document", color = colors.textTertiary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                    innerTextField()
                                }
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFDC2626))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "PDF DRIVE",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Page $currentPage of $pageCount · ${(scale * 100).toInt()}%",
                                    fontSize = 11.5.sp,
                                    color = colors.accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Gemini AI Quick Analysis Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFA855F7).copy(alpha = 0.18f))
                                .telegramBounceClickable {
                                    onAskGemini("Please analyze and provide a comprehensive summary of this PDF document titled '${note.displayTitle}'", GeminiSearchMode.ASK_NOTES)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Document Insights",
                                tint = Color(0xFFA855F7),
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Full-screen Zen Mode Toggle
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(colors.field)
                                .telegramBounceClickable { isZenMode = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Zen Mode",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Share PDF Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.15f))
                                .telegramBounceClickable {
                                    pdfFile?.let { onSharePdf(it) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share PDF",
                                tint = colors.accent,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // More 3-dot Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(colors.field)
                                .telegramBounceClickable { onOpenMore() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "Options",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Floating Glass Control HUD
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 12.dp, end = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .shadow(16.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.glass)
                        .border(1.dp, colors.glassBorder, RoundedCornerShape(22.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Previous Page
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colors.field)
                                .telegramBounceClickable {
                                    scope.launch {
                                        val prev = (currentPage - 2).coerceAtLeast(0)
                                        if (isContinuousMode) {
                                            listState.animateScrollToItem(prev)
                                        } else {
                                            pagerState.animateScrollToPage(prev)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NavigateBefore,
                                contentDescription = "Previous Page",
                                tint = colors.text,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Page Jump / Scrubber Trigger Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.field)
                                .clickable {
                                    jumpPageInput = currentPage.toString()
                                    isJumpPageOpen = true
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$currentPage / $pageCount",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.text
                            )
                        }

                        // Next Page
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colors.field)
                                .telegramBounceClickable {
                                    scope.launch {
                                        val next = currentPage.coerceAtMost(pageCount - 1)
                                        if (isContinuousMode) {
                                            listState.animateScrollToItem(next)
                                        } else {
                                            pagerState.animateScrollToPage(next)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NavigateNext,
                                contentDescription = "Next Page",
                                tint = colors.text,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(2.dp))

                        // Zoom Out (-)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colors.field)
                                .telegramBounceClickable {
                                    onScaleChanged(scale - 0.25f)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Zoom Out",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Reset / Fit Screen
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (scale != 1.0f) colors.accent.copy(alpha = 0.2f) else colors.field)
                                .telegramBounceClickable {
                                    onScaleChanged(1.0f)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FitScreen,
                                contentDescription = "Fit Screen",
                                tint = if (scale != 1.0f) colors.accent else colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Zoom In (+)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colors.field)
                                .telegramBounceClickable {
                                    onScaleChanged(scale + 0.25f)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Zoom In",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(2.dp))

                        // Continuous vs Single Page Switch
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colors.field)
                                .telegramBounceClickable {
                                    isContinuousMode = !isContinuousMode
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isContinuousMode) Icons.Default.ViewStream else Icons.Default.ViewCarousel,
                                contentDescription = "Toggle Flow Mode",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Paper Theme Switcher
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colors.field)
                                .telegramBounceClickable {
                                    paperTheme = when (paperTheme) {
                                        PdfPaperTheme.DEFAULT -> PdfPaperTheme.SEPIA
                                        PdfPaperTheme.SEPIA -> PdfPaperTheme.NIGHT
                                        PdfPaperTheme.NIGHT -> PdfPaperTheme.OLED
                                        PdfPaperTheme.OLED -> PdfPaperTheme.DEFAULT
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (paperTheme) {
                                    PdfPaperTheme.DEFAULT -> Icons.Default.LightMode
                                    PdfPaperTheme.SEPIA -> Icons.Default.WbSunny
                                    PdfPaperTheme.NIGHT -> Icons.Default.DarkMode
                                    PdfPaperTheme.OLED -> Icons.Default.DarkMode
                                },
                                contentDescription = "Theme Filter",
                                tint = when (paperTheme) {
                                    PdfPaperTheme.DEFAULT -> colors.textSecondary
                                    PdfPaperTheme.SEPIA -> Color(0xFFD97706)
                                    PdfPaperTheme.NIGHT -> Color(0xFF60A5FA)
                                    PdfPaperTheme.OLED -> Color(0xFFA78BFA)
                                },
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Exit Zen if in Zen mode
                        if (isZenMode) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(colors.field)
                                    .telegramBounceClickable { isZenMode = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = "Exit Zen",
                                    tint = colors.text,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Jump to Page Dialog
    if (isJumpPageOpen) {
        AlertDialog(
            onDismissRequest = { isJumpPageOpen = false },
            title = {
                Text(
                    text = "Jump to Page",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.text
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a page number between 1 and $pageCount:",
                        fontSize = 13.5.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    BasicTextField(
                        value = jumpPageInput,
                        onValueChange = { jumpPageInput = it.filter { ch -> ch.isDigit() }.take(5) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val target = (jumpPageInput.toIntOrNull() ?: 1).coerceIn(1, pageCount) - 1
                                scope.launch {
                                    if (isContinuousMode) {
                                        listState.scrollToItem(target)
                                    } else {
                                        pagerState.scrollToPage(target)
                                    }
                                }
                                isJumpPageOpen = false
                            }
                        ),
                        textStyle = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.field)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = (jumpPageInput.toIntOrNull() ?: 1).coerceIn(1, pageCount) - 1
                        scope.launch {
                            if (isContinuousMode) {
                                listState.scrollToItem(target)
                            } else {
                                pagerState.scrollToPage(target)
                            }
                        }
                        isJumpPageOpen = false
                    }
                ) {
                    Text("Go", fontWeight = FontWeight.Bold, color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { isJumpPageOpen = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.card,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

/**
 * Individual high-fidelity hardware-accelerated PDF page card.
 * Automatically requests dynamic supersampling when zoom scale increases.
 */
@Composable
private fun PdfPageCard(
    pageEngine: PdfPageEngine?,
    pageIndex: Int,
    renderScale: Float,
    pageCount: Int,
    colorFilter: ColorFilter?,
    paperTheme: PdfPaperTheme
) {
    val colors = GlassTheme.colors
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember(pageIndex) { mutableStateOf(true) }

    // Re-render dynamically whenever debounced zoom changes
    LaunchedEffect(pageEngine, pageIndex, renderScale) {
        if (pageEngine == null) return@LaunchedEffect
        isRendering = bitmap == null
        val rendered = pageEngine.renderPage(
            pageIndex = pageIndex,
            zoomScale = renderScale,
            renderQuality = "sharp"
        )
        if (rendered != null) {
            bitmap = rendered
            isRendering = false
        }
    }

    val pageCardBg = when (paperTheme) {
        PdfPaperTheme.DEFAULT -> Color.White
        PdfPaperTheme.SEPIA -> Color(0xFFFDF6E2)
        PdfPaperTheme.NIGHT -> Color(0xFF1E1E22)
        PdfPaperTheme.OLED -> Color(0xFF0A0A0A)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(pageCardBg)
            .border(1.dp, if (paperTheme == PdfPaperTheme.DEFAULT) Color(0x1F000000) else Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .shadow(if (paperTheme == PdfPaperTheme.DEFAULT) 3.dp else 0.dp, RoundedCornerShape(14.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(colors.field),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = colors.accent,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "PAGE ${pageIndex + 1} OF $pageCount",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (paperTheme == PdfPaperTheme.DEFAULT) Color(0xFF6B7280) else colors.textTertiary,
            letterSpacing = 1.sp
        )
    }
}
