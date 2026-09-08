package com.example.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassTheme
import com.example.util.MarkdownParser
import com.example.util.ReadBlock

@Composable
fun MarkdownReadView(
    content: String,
    fontSize: Int,
    modifier: Modifier = Modifier
) {
    val colors = GlassTheme.colors
    
    // Memoize block parsing and inline formatting so scrolling never triggers regex passes
    val preparedBlocks = remember(content, colors.field, colors.text) {
        val rawBlocks = MarkdownParser.parse(content)
        rawBlocks.map { block ->
            val textToParse = when (block) {
                is ReadBlock.Heading -> block.text
                is ReadBlock.Paragraph -> block.text
                is ReadBlock.BulletItem -> block.text
                is ReadBlock.CheckboxItem -> block.text
                is ReadBlock.Blockquote -> block.text
                is ReadBlock.CodeBlock -> block.code
                is ReadBlock.Divider -> ""
            }
            val annotated = if (block is ReadBlock.CodeBlock || block is ReadBlock.Divider) {
                AnnotatedString(textToParse)
            } else {
                parseInlineText(textToParse, colors.field, colors.text)
            }
            PreparedReadBlock(block, annotated)
        }
    }

    if (preparedBlocks.isEmpty()) {
        Text(
            text = "Nothing written yet.",
            fontSize = fontSize.sp,
            fontFamily = FontFamily.Serif,
            color = colors.textTertiary,
            modifier = modifier.padding(vertical = 12.dp)
        )
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        for (item in preparedBlocks) {
            when (val block = item.block) {
                is ReadBlock.Heading -> {
                    val headingSize = when (block.level) {
                        1 -> (fontSize * 1.5).sp
                        2 -> (fontSize * 1.25).sp
                        3 -> (fontSize * 1.1).sp
                        else -> fontSize.sp
                    }
                    Text(
                        text = item.annotated,
                        fontSize = headingSize,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = colors.text,
                        lineHeight = (headingSize.value * 1.3).sp,
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                    )
                }

                is ReadBlock.Paragraph -> {
                    Text(
                        text = item.annotated,
                        fontSize = fontSize.sp,
                        fontFamily = FontFamily.Serif,
                        color = colors.text,
                        lineHeight = (fontSize * 1.7).sp,
                        modifier = Modifier.padding(vertical = 5.dp)
                    )
                }

                is ReadBlock.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = fontSize.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = item.annotated,
                            fontSize = fontSize.sp,
                            fontFamily = FontFamily.Serif,
                            color = colors.text,
                            lineHeight = (fontSize * 1.6).sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is ReadBlock.CheckboxItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (block.checked) colors.accentSecondary else colors.field)
                                .border(1.dp, if (block.checked) colors.accentSecondary else colors.hairline, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (block.checked) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF231A00),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = item.annotated,
                            fontSize = fontSize.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = if (block.checked) FontWeight.Medium else FontWeight.Normal,
                            color = if (block.checked) colors.textSecondary else colors.text,
                            lineHeight = (fontSize * 1.6).sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is ReadBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.accentSecondary)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = item.annotated,
                            fontSize = fontSize.sp,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            color = colors.textSecondary,
                            lineHeight = (fontSize * 1.6).sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is ReadBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.field)
                            .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = item.annotated,
                            fontSize = (fontSize - 2).coerceAtLeast(11).sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.text,
                            lineHeight = (fontSize * 1.4).sp
                        )
                    }
                }

                is ReadBlock.Divider -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .height(1.dp)
                            .background(colors.hairline)
                    )
                }
            }
        }
    }
}

private data class PreparedReadBlock(
    val block: ReadBlock,
    val annotated: AnnotatedString
)

private fun parseInlineText(text: String, codeBg: Color, textColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0

        // Handle **bold**, *italic*, `code`
        val regex = Regex("(\\*\\*([^*]+)\\*\\*)|(\\*([^*]+)\\*)|(`([^`]+)`)")
        val matches = regex.findAll(text)

        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1

            if (start > currentIndex) {
                append(text.substring(currentIndex, start))
            }

            when {
                match.value.startsWith("**") -> {
                    val inner = match.groupValues[2]
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(inner)
                    pop()
                }
                match.value.startsWith("*") -> {
                    val inner = match.groupValues[4]
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(inner)
                    pop()
                }
                match.value.startsWith("`") -> {
                    val inner = match.groupValues[6]
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg))
                    append(" $inner ")
                    pop()
                }
            }

            currentIndex = end
        }

        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}
