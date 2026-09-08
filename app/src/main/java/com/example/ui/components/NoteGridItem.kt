package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NoteSummary
import com.example.ui.theme.GlassTheme
import com.example.util.DateFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteGridItem(
    note: NoteSummary,
    searchQuery: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = GlassTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .shadow(if (colors.isDark || colors.isReduced) 0.dp else 2.dp, RoundedCornerShape(18.dp), ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card)
            .border(1.dp, colors.hairline, RoundedCornerShape(18.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(13.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Date badge at top
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp, end = 26.dp)
            ) {
                Text(
                    text = DateFormatter.fmtItem(note.updatedAt),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textTertiary
                )
                if (note.isLocked) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.accent.copy(alpha = 0.16f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = colors.accent,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "LOCKED",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.accent
                            )
                        }
                    }
                } else if (note.type == "pdf") {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFDC2626))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "PDF",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else if (note.type == "html") {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.chipOnBg)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "HTML",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.chipOnTx
                        )
                    }
                }
            }

            // Title
            if (note.title.isNotBlank()) {
                HighlightedText(
                    text = note.title,
                    query = searchQuery,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    textColor = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "Untitled",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textTertiary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Category
            if (!note.category.isNullOrBlank()) {
                Text(
                    text = "#${note.category}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Snippet
            val gridSnippet = if (note.isLocked) {
                "Locked Note • Tap to unlock with Face or PIN"
            } else {
                note.snippetPreview
            }

            HighlightedText(
                text = gridSnippet,
                query = searchQuery,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp
                ),
                textColor = colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        // More 3-dot Button in top right
        val moreInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = moreInteraction,
                    indication = ripple(bounded = true, radius = 14.dp),
                    onClick = onMoreClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "Options",
                tint = colors.textTertiary,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}
