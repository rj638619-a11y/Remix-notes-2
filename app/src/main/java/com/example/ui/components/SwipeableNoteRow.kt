package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NoteSummary
import com.example.ui.theme.GlassTheme
import com.example.util.VibrationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableNoteRow(
    note: NoteSummary,
    searchQuery: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isTrashMode: Boolean = false,
    onRestore: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val colors = GlassTheme.colors

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (isTrashMode) {
                        // Swipe right in trash: Restore note
                        VibrationHelper.vibrate(context, 16)
                        onRestore?.invoke()
                        true
                    } else {
                        // Swipe right in active: Pin / Unpin
                        VibrationHelper.vibrate(context, 12)
                        onTogglePin()
                        false // Return false so the item animates back smoothly
                    }
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Swipe left: Delete (or delete permanently in trash)
                    VibrationHelper.vibrate(context, 16)
                    onDelete()
                    true // Return true to dismiss the item
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.35f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isStartToEnd = direction == SwipeToDismissBoxValue.StartToEnd
            val isEndToStart = direction == SwipeToDismissBoxValue.EndToStart

            val backgroundColor = when {
                isStartToEnd -> if (isTrashMode) Color(0xFF10B981).copy(alpha = 0.22f) else colors.accent.copy(alpha = 0.22f)
                isEndToStart -> Color(0xFFE53935).copy(alpha = 0.22f)
                else -> Color.Transparent
            }

            val iconColor = when {
                isStartToEnd -> if (isTrashMode) Color(0xFF10B981) else colors.accent
                isEndToStart -> Color(0xFFEF5350)
                else -> Color.Transparent
            }

            val alignment = when {
                isStartToEnd -> Alignment.CenterStart
                isEndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(backgroundColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (isStartToEnd) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isTrashMode) Icons.Default.Restore else Icons.Default.PushPin,
                            contentDescription = if (isTrashMode) "Restore" else if (note.pinned) "Unpin" else "Pin",
                            tint = iconColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = if (isTrashMode) "Restore" else if (note.pinned) "Unpin" else "Pin",
                            color = iconColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                } else if (isEndToStart) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isTrashMode) "Delete Forever" else "Move to Trash",
                            color = iconColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Icon(
                            imageVector = if (isTrashMode) Icons.Default.DeleteForever else Icons.Default.Delete,
                            contentDescription = if (isTrashMode) "Delete Forever" else "Move to Trash",
                            tint = iconColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        NoteRowItem(
            note = note,
            searchQuery = searchQuery,
            onClick = onClick,
            onLongClick = onLongClick,
            onMoreClick = onMoreClick,
            modifier = Modifier.background(colors.bg)
        )
    }
}
