package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun NoteRowItem(
    note: NoteSummary,
    searchQuery: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = GlassTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Title row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (note.title.isNotBlank()) {
                        HighlightedText(
                            text = note.title,
                            query = searchQuery,
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp
                            ),
                            textColor = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    } else {
                        Text(
                            text = "Untitled",
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textTertiary
                        )
                    }

                    if (note.pinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = colors.accent,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    if (note.isLocked) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked Note",
                            tint = colors.accent,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    if (note.type == "html") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(colors.chipOnBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "HTML",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp,
                                color = colors.chipOnTx
                            )
                        }
                    } else if (note.type == "pdf") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFFDC2626))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PDF",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                // Snippet & Date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    val displayText = if (note.isLocked) {
                        "Locked Note • Tap to unlock with Face or PIN"
                    } else if (note.type == "pdf") {
                        if (note.snippetPreview.isNotBlank()) note.snippetPreview else "PDF Document • Tap to read with dynamic zoom"
                    } else {
                        note.snippetPreview
                    }
                    HighlightedText(
                        text = displayText,
                        query = searchQuery,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            lineHeight = 19.sp
                        ),
                        textColor = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (!note.category.isNullOrBlank()) {
                        Text(
                            text = " · #${note.category}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent
                        )
                    }

                    Text(
                        text = " · ${DateFormatter.fmtItem(note.updatedAt)}",
                        fontSize = 13.sp,
                        color = colors.textTertiary
                    )
                }
            }

            // More 3-dot Button
            val moreInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = moreInteraction,
                        indication = ripple(bounded = true, radius = 18.dp),
                        onClick = onMoreClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Options",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Hairline Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.hairline)
        )
    }
}
