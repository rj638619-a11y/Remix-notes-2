package com.example.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.GeminiSearchMode
import com.example.data.model.NoteEntity
import com.example.ui.components.GlassIconButton
import com.example.ui.theme.GlassTheme
import com.example.ui.util.telegramBounceClickable
import com.example.util.DateFormatter
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NoteEditorScreen(
    note: NoteEntity,
    fontSize: Int,
    initialZenMode: Boolean = false,
    onBack: () -> Unit,
    onSave: (title: String, content: String) -> Unit,
    onOpenMore: () -> Unit,
    onShare: (() -> Unit)? = null,
    onAskGemini: ((query: String, mode: GeminiSearchMode) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = GlassTheme.colors
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var title by remember(note.id) { mutableStateOf(note.title) }
    var bodyTextFieldValue by remember(note.id) { mutableStateOf(TextFieldValue(note.content)) }

    // Mode:
    // For text note: "edit" or "read"
    // For html note: "code" (edit HTML) or "preview" (render HTML)
    val defaultMode = remember(note.id) {
        if (note.type == "html") {
            if (note.content.isBlank()) "code" else "preview"
        } else {
            if (initialZenMode) "read" else "edit"
        }
    }
    var mode by remember(note.id) { mutableStateOf(defaultMode) }

    var isZenMode by remember(note.id, initialZenMode) { mutableStateOf(initialZenMode) }
    var isJsEnabled by remember(note.id) { mutableStateOf(true) }
    var reloadTrigger by remember { mutableLongStateOf(0L) }
    var saveStatus by remember { mutableStateOf("Saved") }
    var dynamicFontSize by remember(fontSize) { mutableIntStateOf(fontSize) }

    // Unified auto-save debounce effect for both text & HTML notes
    LaunchedEffect(title, bodyTextFieldValue.text) {
        if (title != note.title || bodyTextFieldValue.text != note.content) {
            saveStatus = "Saving…"
            delay(400)
            onSave(title, bodyTextFieldValue.text)
            saveStatus = "Saved · ${DateFormatter.fmtClock(System.currentTimeMillis())}"
        }
    }

    BackHandler {
        if (isZenMode) {
            isZenMode = false
        } else {
            onSave(title, bodyTextFieldValue.text)
            onBack()
        }
    }

    val readScrollState = rememberScrollState()
    val editScrollState = rememberScrollState()

    // Reading progress calculation based on active reading state
    val maxScroll = readScrollState.maxValue
    val scrollProgress = if (maxScroll > 0) (readScrollState.value.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f) else 0f

    val wordCount = remember(bodyTextFieldValue.text) {
        val trimmed = bodyTextFieldValue.text.trim()
        if (trimmed.isEmpty()) 0 else trimmed.split(Regex("\\s+")).size
    }
    val charCount = remember(bodyTextFieldValue.text) {
        bodyTextFieldValue.text.length
    }
    val readMinutes = maxOf(1, (wordCount + 199) / 200)

    val isHtml = note.type == "html"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar (hidden in Zen mode)
            if (!isZenMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarTop + 6.dp, start = 12.dp, end = 12.dp, bottom = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = if (colors.isReduced) 0.dp else 12.dp,
                                shape = RoundedCornerShape(20.dp),
                                ambientColor = colors.shadow,
                                spotColor = colors.shadow
                            )
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.glass)
                            .border(1.dp, colors.glassBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
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
                                    .telegramBounceClickable {
                                        onSave(title, bodyTextFieldValue.text)
                                        onBack()
                                    },
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
                                    value = title,
                                    onValueChange = { title = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.text
                                    ),
                                    cursorBrush = SolidColor(if (isHtml) Color(0xFFF97316) else colors.accent),
                                    decorationBox = { innerTextField ->
                                        if (title.isBlank()) {
                                            Text(
                                                text = if (isHtml) "HTML Document" else "Untitled Note",
                                                color = colors.textTertiary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isHtml) Color(0xFFE44D26) else Color(0xFF2563EB))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = if (isHtml) "HTML" else "NOTE",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isHtml) "$charCount chars · $wordCount words" else "$wordCount words · $readMinutes min",
                                        fontSize = 11.5.sp,
                                        color = colors.textSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Gemini AI Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFA855F7).copy(alpha = 0.18f))
                                    .telegramBounceClickable {
                                        val prompt = if (isHtml) {
                                            "Please analyze and provide a comprehensive summary and code review of this HTML document titled '${title.ifBlank { "Untitled" }}':\n\n${bodyTextFieldValue.text}"
                                        } else {
                                            "Please analyze, summarize, and answer questions about this note titled '${title.ifBlank { "Untitled" }}':\n\n${bodyTextFieldValue.text}"
                                        }
                                        onAskGemini?.invoke(prompt, GeminiSearchMode.ASK_NOTES)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Assistant",
                                    tint = Color(0xFFA855F7),
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Fullscreen / Zen Mode Toggle
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.field)
                                    .telegramBounceClickable {
                                        isZenMode = true
                                        if (isHtml) {
                                            mode = "preview"
                                        } else if (mode != "read") {
                                            mode = "read"
                                        }
                                    },
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

                            // Share Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent.copy(alpha = 0.15f))
                                    .telegramBounceClickable {
                                        onShare?.invoke()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = colors.accent,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // More 3-dot Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
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
            }

            // Main Editor / Preview Content Area with smooth animated transition
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                         slideInHorizontally(animationSpec = tween(220), initialOffsetX = { if (targetState == "preview" || targetState == "read") it / 6 else -it / 6 }))
                            .togetherWith(
                                fadeOut(animationSpec = tween(180)) +
                                slideOutHorizontally(animationSpec = tween(180), targetOffsetX = { if (targetState == "preview" || targetState == "read") -it / 6 else it / 6 })
                            )
                    },
                    label = "EditorContentTransition"
                ) { currentMode ->
                    when {
                        // HTML Live Preview
                        isHtml && currentMode == "preview" -> {
                            HtmlPreviewView(
                                htmlContent = bodyTextFieldValue.text,
                                fontSize = dynamicFontSize,
                                isJsEnabled = isJsEnabled,
                                isZenMode = isZenMode,
                                reloadTrigger = reloadTrigger,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        start = if (isZenMode) 0.dp else 12.dp,
                                        end = if (isZenMode) 0.dp else 12.dp,
                                        top = if (isZenMode) 0.dp else 6.dp,
                                        bottom = if (isZenMode) 0.dp else 76.dp
                                    )
                            )
                        }

                        // Text Markdown Reading View
                        !isHtml && currentMode == "read" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(readScrollState)
                                    .padding(
                                        start = if (isZenMode) 22.dp else 20.dp,
                                        end = if (isZenMode) 22.dp else 20.dp,
                                        top = if (isZenMode) (statusBarTop + 20.dp) else 12.dp,
                                        bottom = if (isZenMode) 80.dp else 84.dp
                                    )
                            ) {
                                if (!isZenMode && title.isNotBlank()) {
                                    Text(
                                        text = title,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.4).sp,
                                        color = colors.text,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(colors.hairline)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                MarkdownReadView(
                                    content = bodyTextFieldValue.text,
                                    fontSize = dynamicFontSize,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Editing View (HTML code editor or Text note editor)
                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(editScrollState)
                                    .padding(
                                        start = if (isZenMode) 20.dp else 18.dp,
                                        end = if (isZenMode) 20.dp else 18.dp,
                                        top = if (isZenMode) (statusBarTop + 20.dp) else 10.dp,
                                        bottom = if (isZenMode) 80.dp else 84.dp
                                    )
                            ) {
                                // Monospace for HTML, Serif/Sans for Text
                                BasicTextField(
                                    value = bodyTextFieldValue,
                                    onValueChange = { bodyTextFieldValue = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 350.dp),
                                    textStyle = if (isHtml) {
                                        TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.5.sp,
                                            lineHeight = 20.sp,
                                            color = colors.text
                                        )
                                    } else {
                                        TextStyle(
                                            fontFamily = FontFamily.Serif,
                                            fontSize = dynamicFontSize.sp,
                                            lineHeight = (dynamicFontSize * 1.72).sp,
                                            color = colors.text
                                        )
                                    },
                                    cursorBrush = SolidColor(if (isHtml) Color(0xFFF97316) else colors.accentSecondary),
                                    decorationBox = { inner ->
                                        if (bodyTextFieldValue.text.isEmpty()) {
                                            Text(
                                                text = if (isHtml) "<!DOCTYPE html>\n<html>\n  <head>\n    <title>${title.ifBlank { "My HTML Document" }}</title>\n  </head>\n  <body>\n    <h1>Hello World</h1>\n    <p>Start writing your HTML note here...</p>\n  </body>\n</html>" else "Start writing…",
                                                fontFamily = if (isHtml) FontFamily.Monospace else FontFamily.Serif,
                                                fontSize = if (isHtml) 13.5.sp else dynamicFontSize.sp,
                                                color = colors.textTertiary
                                            )
                                        }
                                        inner()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Quick Formatting Row when in active Code editing mode (above bottom HUD)
            if (!isZenMode && isHtml && (mode == "code" || mode == "edit")) {
                HtmlFormattingToolbar(
                    onTag = { prefix, suffix, placeholder ->
                        insertAtCursor(prefix, suffix, placeholder, bodyTextFieldValue) {
                            bodyTextFieldValue = it
                        }
                    },
                    onSnippet = { snippet ->
                        insertAtCursor(snippet, "", "", bodyTextFieldValue) {
                            bodyTextFieldValue = it
                        }
                    }
                )
            } else if (!isZenMode && !isHtml && mode == "edit") {
                FormattingToolbar(
                    onHeading = { level ->
                        val prefix = when (level) {
                            1 -> "# "
                            2 -> "## "
                            else -> "### "
                        }
                        insertLinePrefix(prefix, bodyTextFieldValue) { bodyTextFieldValue = it }
                    },
                    onBold = { insertAtCursor("**", "**", "bold", bodyTextFieldValue) { bodyTextFieldValue = it } },
                    onItalic = { insertAtCursor("*", "*", "italic", bodyTextFieldValue) { bodyTextFieldValue = it } },
                    onCode = { insertAtCursor("`", "`", "code", bodyTextFieldValue) { bodyTextFieldValue = it } },
                    onBulletList = { insertLinePrefix("- ", bodyTextFieldValue) { bodyTextFieldValue = it } },
                    onCheckbox = { insertLinePrefix("[ ] ", bodyTextFieldValue) { bodyTextFieldValue = it } },
                    onQuote = { insertLinePrefix("> ", bodyTextFieldValue) { bodyTextFieldValue = it } },
                    onDivider = { insertAtCursor("\n---\n", "", "", bodyTextFieldValue) { bodyTextFieldValue = it } },
                    onInsertDate = {
                        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                        insertAtCursor(ts, "", "", bodyTextFieldValue) { bodyTextFieldValue = it }
                    }
                )
            }

            // Bottom Floating Glass Control HUD (matching PDF viewer design)
            if (!isZenMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp, start = 12.dp, end = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .shadow(16.dp, RoundedCornerShape(22.dp), ambientColor = colors.shadow, spotColor = colors.shadow)
                            .clip(RoundedCornerShape(22.dp))
                            .background(colors.glass)
                            .border(1.dp, colors.glassBorder, RoundedCornerShape(22.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Mode Segment Toggle (Code / Preview or Edit / Read)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.field)
                                    .padding(2.dp)
                            ) {
                                if (isHtml) {
                                    EditorSegmentButton(
                                        label = "Code",
                                        isSelected = mode == "code" || mode == "edit",
                                        onClick = { mode = "code" }
                                    )
                                    EditorSegmentButton(
                                        label = "Preview",
                                        isSelected = mode == "preview",
                                        onClick = { mode = "preview" }
                                    )
                                } else {
                                    EditorSegmentButton(
                                        label = "Edit",
                                        isSelected = mode == "edit",
                                        onClick = { mode = "edit" }
                                    )
                                    EditorSegmentButton(
                                        label = "Read",
                                        isSelected = mode == "read",
                                        onClick = { mode = "read" }
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(colors.hairline)
                            )

                            // Controls depending on HTML Preview vs Code Mode
                            if (isHtml && mode == "preview") {
                                // JS Toggle Chip
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isJsEnabled) Color(0xFF34C759).copy(alpha = 0.18f) else colors.field)
                                        .clickable { isJsEnabled = !isJsEnabled }
                                        .padding(horizontal = 9.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (isJsEnabled) "JS On" else "JS Off",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isJsEnabled) Color(0xFF34C759) else colors.textTertiary
                                    )
                                }

                                // Reload Button
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(colors.field)
                                        .telegramBounceClickable { reloadTrigger = System.currentTimeMillis() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reload",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Zoom Out (-)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(colors.field)
                                        .telegramBounceClickable {
                                            dynamicFontSize = (dynamicFontSize - 1).coerceAtLeast(12)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Zoom Out",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                // Font size pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.field)
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "${dynamicFontSize}sp",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.text
                                    )
                                }

                                // Zoom In (+)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(colors.field)
                                        .telegramBounceClickable {
                                            dynamicFontSize = (dynamicFontSize + 1).coerceAtMost(30)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Zoom In",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            } else if (isHtml) {
                                // Code mode productivity buttons
                                // Beautify / Auto Format HTML Button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.field)
                                        .telegramBounceClickable {
                                            val formatted = formatHtmlSnippet(bodyTextFieldValue.text)
                                            bodyTextFieldValue = TextFieldValue(formatted, TextRange(formatted.length))
                                        }
                                        .padding(horizontal = 9.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Format",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accent
                                    )
                                }

                                // AI Code Fix / Explain Button in Lower Bar
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFA855F7).copy(alpha = 0.16f))
                                        .telegramBounceClickable {
                                            onAskGemini?.invoke(
                                                "Please inspect this HTML note, check for errors, format the layout cleanly, and suggest enhancements:\n\n${bodyTextFieldValue.text}",
                                                GeminiSearchMode.ASK_NOTES
                                            )
                                        }
                                        .padding(horizontal = 9.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color(0xFFA855F7),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "AI Assist",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFA855F7)
                                        )
                                    }
                                }
                            } else {
                                // Text note reading size controls
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(colors.field)
                                        .telegramBounceClickable {
                                            dynamicFontSize = (dynamicFontSize - 1).coerceAtLeast(12)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Zoom Out",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.field)
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "${dynamicFontSize}sp",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.text
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(colors.field)
                                        .telegramBounceClickable {
                                            dynamicFontSize = (dynamicFontSize + 1).coerceAtMost(30)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Zoom In",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Zen Reading Progress Bar (top edge)
        if (isZenMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopStart)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(scrollProgress)
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(colors.accentSecondary, colors.accent)
                            )
                        )
                )
            }
        }

        // Floating Zen Controls Cluster (accessible in Zen mode)
        AnimatedVisibility(
            visible = isZenMode,
            enter = fadeIn(animationSpec = tween(250)) + slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
            ),
            exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusBarTop + 12.dp, end = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .shadow(16.dp, RoundedCornerShape(999.dp), ambientColor = colors.shadow, spotColor = colors.shadow)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.card.copy(alpha = 0.88f))
                    .border(1.dp, colors.glassBorder, RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val toggleLabel = if (isHtml) {
                    if (mode == "preview") "Code" else "Preview"
                } else {
                    if (mode == "read") "Edit" else "Read"
                }
                val toggleIcon = if (isHtml) {
                    if (mode == "preview") Icons.Default.Code else Icons.Default.Visibility
                } else {
                    if (mode == "read") Icons.Default.Edit else Icons.Default.Visibility
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable {
                            mode = if (isHtml) {
                                if (mode == "preview") "code" else "preview"
                            } else {
                                if (mode == "read") "edit" else "read"
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = toggleIcon,
                        contentDescription = null,
                        tint = colors.text,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = toggleLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .width(1.dp)
                        .height(18.dp)
                        .background(colors.hairline)
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { isZenMode = false }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = colors.text,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Exit",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorSegmentButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = GlassTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (isSelected) colors.card else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) colors.text else colors.textSecondary
        )
    }
}

// Helpers for inserting text snippets
private fun insertAtCursor(
    prefix: String,
    suffix: String,
    placeholder: String,
    current: TextFieldValue,
    onUpdate: (TextFieldValue) -> Unit
) {
    val text = current.text
    val selection = current.selection
    val start = selection.min
    val end = selection.max

    val before = text.substring(0, start)
    val after = text.substring(end)
    val selectedText = if (start != end) text.substring(start, end) else placeholder
    val inserted = prefix + selectedText + suffix

    val newText = before + inserted + after
    val newCursorPos = start + inserted.length
    onUpdate(TextFieldValue(text = newText, selection = TextRange(newCursorPos)))
}

private fun insertLinePrefix(
    prefix: String,
    current: TextFieldValue,
    onUpdate: (TextFieldValue) -> Unit
) {
    val text = current.text
    val selection = current.selection
    val start = selection.min

    val before = text.substring(0, start)
    val lineStart = before.lastIndexOf('\n') + 1
    val lineEndIndex = text.indexOf('\n', start)
    val lineEnd = if (lineEndIndex == -1) text.length else lineEndIndex

    val line = text.substring(lineStart, lineEnd)

    val (newLine, newCursor) = if (line.startsWith(prefix)) {
        Pair(line.substring(prefix.length), maxOf(lineStart, start - prefix.length))
    } else {
        Pair(prefix + line, start + prefix.length)
    }

    val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
    onUpdate(TextFieldValue(text = newText, selection = TextRange(newCursor)))
}

/**
 * Lightweight HTML formatter to auto-indent common tag structures.
 */
private fun formatHtmlSnippet(html: String): String {
    if (html.isBlank()) return html
    val tokens = html.split(Regex("(?<=>)(?=<)|(?<=\\n)"))
    val sb = StringBuilder()
    var indent = 0
    val indentStr = "  "

    for (rawToken in tokens) {
        val token = rawToken.trim()
        if (token.isEmpty()) continue

        if (token.startsWith("</") || token.startsWith("-->")) {
            indent = (indent - 1).coerceAtLeast(0)
        }

        sb.append(indentStr.repeat(indent))
        sb.append(token)
        sb.append("\n")

        if (token.startsWith("<") && !token.startsWith("</") && !token.startsWith("<!--") && !token.endsWith("/>") && !token.startsWith("<!") &&
            !token.matches(Regex("(?i)<(img|br|hr|input|meta|link)[^>]*>"))
        ) {
            // Check if tag is closed on same line
            val tagName = token.substringAfter("<").substringBefore(" ").substringBefore(">")
            if (!token.contains("</$tagName>")) {
                indent++
            }
        }
    }
    return sb.toString().trimEnd()
}
