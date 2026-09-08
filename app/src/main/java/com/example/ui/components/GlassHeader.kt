package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.example.data.api.GeminiSearchMode
import com.example.ui.theme.GlassTheme
import com.example.ui.util.telegramBounceClickable

@Composable
fun ReaderModeSwitch(
    readerMode: String,
    onReaderModeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = GlassTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.field)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val options = listOf(
            "html" to "HTML Notes",
            "pdf" to "PDF Drive"
        )
        options.forEach { (mode, label) ->
            val isSelected = readerMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) colors.chipOnBg else Color.Transparent)
                    .telegramBounceClickable { onReaderModeChange(mode) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) colors.chipOnTx else colors.textSecondary
                )
            }
        }
    }
}

@Composable
fun GlassHeader(
    isScrolled: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: String,
    categories: List<String> = emptyList(),
    trashCount: Int = 0,
    onFilterChange: (String) -> Unit,
    viewMode: String,
    onToggleView: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSort: () -> Unit,
    onOpenGemini: (query: String, mode: GeminiSearchMode) -> Unit,
    readerMode: String,
    onReaderModeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = GlassTheme.colors
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val infiniteTransition = rememberInfiniteTransition(label = "gemini_sparkle")
    val sparkleGlow by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkle_glow"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (colors.isReduced) 0.dp else 12.dp, RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp), ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(colors.glass, RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
            .border(
                1.dp,
                colors.glassBorder,
                RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
            )
            .padding(
                top = if (isLandscape) 4.dp else statusBarTop + 10.dp,
                bottom = if (isLandscape) 8.dp else 12.dp,
                start = 16.dp,
                end = 16.dp
            )
    ) {
        Column {
            if (!isLandscape) {
                // Top mini row (Portrait)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AnimatedVisibility(
                        visible = isScrolled,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = if (readerMode == "pdf") "PDF Drive" else "HTML Notes",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            ),
                            color = colors.text
                        )
                    }

                    if (!isScrolled) {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeToggleIconButton(
                            isDark = colors.isDark,
                            onClick = onToggleTheme,
                            size = 36.dp,
                            iconSize = 18.dp
                        )
                        GlassIconButton(
                            icon = if (viewMode == "grid") Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                            contentDescription = "View mode",
                            onClick = onToggleView,
                            size = 36.dp,
                            iconSize = 18.dp
                        )
                        GlassIconButton(
                            icon = Icons.Default.Sort,
                            contentDescription = "Sort notes",
                            onClick = onOpenSort,
                            size = 36.dp,
                            iconSize = 18.dp
                        )
                    }
                }

                // Big Title + Switch Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (readerMode == "pdf") "PDF Drive" else "HTML Notes",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            letterSpacing = (-0.6).sp
                        ),
                        color = colors.text
                    )
                    
                    ReaderModeSwitch(
                        readerMode = readerMode,
                        onReaderModeChange = onReaderModeChange,
                        modifier = Modifier.width(180.dp)
                    )
                }
            } else {
                // Compact Landscape Top Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (readerMode == "pdf") "PDF Drive" else "HTML Notes",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            ),
                            color = colors.text
                        )
                        
                        ReaderModeSwitch(
                            readerMode = readerMode,
                            onReaderModeChange = onReaderModeChange,
                            modifier = Modifier.width(150.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeToggleIconButton(
                            isDark = colors.isDark,
                            onClick = onToggleTheme,
                            size = 34.dp,
                            iconSize = 16.dp
                        )
                        GlassIconButton(
                            icon = if (viewMode == "grid") Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                            contentDescription = "View mode",
                            onClick = onToggleView,
                            size = 34.dp,
                            iconSize = 16.dp
                        )
                        GlassIconButton(
                            icon = Icons.Default.Sort,
                            contentDescription = "Sort notes",
                            onClick = onOpenSort,
                            size = 34.dp,
                            iconSize = 16.dp
                        )
                    }
                }
            }

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.field)
                    .border(
                        1.dp,
                        if (searchQuery.isNotEmpty()) Brush.linearGradient(
                            listOf(
                                Color(0xFF6366F1).copy(alpha = sparkleGlow),
                                Color(0xFFA855F7).copy(alpha = sparkleGlow)
                            )
                        ) else SolidColor(colors.glassBorder),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(start = 12.dp, end = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (searchQuery.isNotEmpty()) Color(0xFFA855F7) else colors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = colors.text,
                            fontSize = 15.5.sp
                        ),
                        cursorBrush = SolidColor(Color(0xFFA855F7)),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = if (readerMode == "pdf") "Search PDF Drive or ask Ai..." else "Search notes or ask Ai...",
                                    color = colors.textTertiary,
                                    fontSize = 14.5.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colors.field)
                                .clickable { onSearchQueryChange("") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = colors.textTertiary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // Gemini AI Sparkle Button in Search Bar
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF6366F1),
                                        Color(0xFFA855F7),
                                        Color(0xFFEC4899)
                                    )
                                )
                            )
                            .clickable {
                                onOpenGemini(searchQuery, GeminiSearchMode.ASK_NOTES)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Ask Ai",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            if (searchQuery.isNotBlank()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Ask Ai",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Filter Chips + Ai Quick Trigger Chip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sparkle Ai Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF6366F1).copy(alpha = 0.22f),
                                    Color(0xFFA855F7).copy(alpha = 0.22f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF6366F1).copy(alpha = 0.5f),
                                    Color(0xFFA855F7).copy(alpha = 0.5f)
                                )
                            ),
                            RoundedCornerShape(999.dp)
                        )
                        .clickable { onOpenGemini(searchQuery, GeminiSearchMode.ASK_NOTES) }
                        .padding(horizontal = 13.dp, vertical = 6.5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFA855F7),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Ai Search",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (colors.isDark) Color(0xFFC084FC) else Color(0xFF7E22CE)
                        )
                    }
                }

                if (readerMode == "pdf") {
                    FilterChipItem(
                        label = "All PDFs",
                        isSelected = selectedFilter == "all",
                        onClick = { onFilterChange("all") }
                    )
                    FilterChipItem(
                        label = "Pinned",
                        isSelected = selectedFilter == "pinned",
                        onClick = { onFilterChange("pinned") }
                    )
                    FilterChipItem(
                        label = "Recent",
                        isSelected = selectedFilter == "recent",
                        onClick = { onFilterChange("recent") }
                    )
                    FilterChipItem(
                        label = "Imported",
                        isSelected = selectedFilter == "imported",
                        onClick = { onFilterChange("imported") }
                    )
                    FilterChipItem(
                        label = "Documents",
                        isSelected = selectedFilter == "docs",
                        onClick = { onFilterChange("docs") }
                    )
                } else {
                    FilterChipItem(
                        label = "All",
                        isSelected = selectedFilter == "all",
                        onClick = { onFilterChange("all") }
                    )
                    FilterChipItem(
                        label = "Text",
                        isSelected = selectedFilter == "text",
                        onClick = { onFilterChange("text") }
                    )
                    FilterChipItem(
                        label = "HTML",
                        isSelected = selectedFilter == "html",
                        onClick = { onFilterChange("html") }
                    )
                    FilterChipItem(
                        label = "Pinned",
                        isSelected = selectedFilter == "pinned",
                        onClick = { onFilterChange("pinned") }
                    )
                }

                categories.forEach { cat ->
                    FilterChipItem(
                        label = "#$cat",
                        isSelected = selectedFilter == "cat_$cat",
                        onClick = { onFilterChange("cat_$cat") }
                    )
                }

                // Dedicated Trash Folder Chip
                FilterChipItem(
                    label = if (trashCount > 0) "🗑️ Trash ($trashCount)" else "🗑️ Trash",
                    isSelected = selectedFilter == "trash",
                    onClick = { onFilterChange("trash") }
                )
            }
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = GlassTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isSelected) colors.chipOnBg else colors.field)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) colors.chipOnTx else colors.textSecondary
        )
    }
}
