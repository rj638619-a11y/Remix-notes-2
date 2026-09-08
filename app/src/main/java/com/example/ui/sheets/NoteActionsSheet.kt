package com.example.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NoteEntity
import com.example.ui.theme.GlassTheme

import androidx.compose.material.icons.filled.Label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteActionsSheet(
    note: NoteEntity,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onZenReading: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenStopwatch: () -> Unit,
    onSetCategory: () -> Unit,
    onTogglePin: () -> Unit,
    onCopyText: () -> Unit,
    onDuplicate: () -> Unit,
    onExportFile: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRestore: (() -> Unit)? = null,
    onToggleLock: () -> Unit = {}
) {
    val colors = GlassTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
        scrimColor = colors.shadow.copy(alpha = 0.4f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = note.displayTitle,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                color = colors.text,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )

            ActionRowItem(
                icon = Icons.Default.Visibility,
                label = "Open note",
                onClick = { onDismiss(); onOpen() }
            )

            ActionRowItem(
                icon = Icons.Default.Fullscreen,
                label = "Full screen reading",
                sub = "Only the note — no bars, no title",
                onClick = { onDismiss(); onZenReading() }
            )

            ActionRowItem(
                icon = Icons.Default.Label,
                label = "Set Category / Tag",
                sub = if (note.category.isNullOrBlank()) "No category set" else "Current: ${note.category}",
                onClick = { onDismiss(); onSetCategory() }
            )

            ActionRowItem(
                icon = Icons.Default.Schedule,
                label = "Timer",
                sub = "Counts down in the background",
                onClick = { onDismiss(); onOpenTimer() }
            )

            ActionRowItem(
                icon = Icons.Default.Timer,
                label = "Stopwatch",
                sub = "With laps — keeps running too",
                onClick = { onDismiss(); onOpenStopwatch() }
            )

            ActionRowItem(
                icon = Icons.Default.PushPin,
                label = if (note.pinned) "Unpin" else "Pin to top",
                onClick = { onDismiss(); onTogglePin() }
            )

            ActionRowItem(
                icon = if (note.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                label = if (note.isLocked) "Remove Note Lock" else "Lock Note with PIN & Face",
                sub = if (note.isLocked) "Anyone can view this note" else "Require Face Unlock or PIN to view",
                tint = if (note.isLocked) colors.accent else colors.text,
                onClick = { onDismiss(); onToggleLock() }
            )

            ActionRowItem(
                icon = Icons.Default.ContentCopy,
                label = "Copy text",
                sub = "Whole note to the clipboard",
                onClick = { onDismiss(); onCopyText() }
            )

            ActionRowItem(
                icon = Icons.Default.FileCopy,
                label = "Duplicate",
                onClick = { onDismiss(); onDuplicate() }
            )

            ActionRowItem(
                icon = Icons.Default.Download,
                label = "Export as file",
                sub = if (note.type == "html") ".html document" else ".txt document",
                onClick = { onDismiss(); onExportFile() }
            )

            ActionRowItem(
                icon = Icons.Default.Share,
                label = "Share",
                onClick = { onDismiss(); onShare() }
            )

            if (note.isDeleted && onRestore != null) {
                ActionRowItem(
                    icon = Icons.Default.Restore,
                    label = "Restore Note",
                    sub = "Move back to active notes",
                    tint = Color(0xFF10B981),
                    onClick = { onDismiss(); onRestore() }
                )
            }

            ActionRowItem(
                icon = Icons.Default.Delete,
                label = if (note.isDeleted) "Delete forever" else "Move to Trash",
                sub = if (note.isDeleted) "Permanent removal" else "Can be restored anytime",
                tint = Color(0xFFEF4444),
                onClick = { onDismiss(); onDelete() }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetCategorySheet(
    note: NoteEntity,
    existingCategories: List<String>,
    onDismiss: () -> Unit,
    onSaveCategory: (String?) -> Unit
) {
    val colors = GlassTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val categoryInputState = remember { androidx.compose.runtime.mutableStateOf(note.category ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
        scrimColor = colors.shadow.copy(alpha = 0.4f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Set Category",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.text,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            androidx.compose.foundation.text.BasicTextField(
                value = categoryInputState.value,
                onValueChange = { categoryInputState.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.field)
                    .padding(16.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = colors.text,
                    fontSize = 16.sp
                ),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (categoryInputState.value.isEmpty()) {
                            Text("E.g. Work, Ideas, Travel...", color = colors.textTertiary, fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                }
            )

            if (existingCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Existing Categories",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    existingCategories.forEach { cat ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.field)
                                .clickable { categoryInputState.value = cat }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(text = cat, fontSize = 14.sp, color = colors.text)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.field)
                        .clickable { onSaveCategory(null) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Clear", color = colors.text, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accent)
                        .clickable { onSaveCategory(categoryInputState.value.trim().takeIf { it.isNotBlank() }) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Save",
                        color = if (colors.isDark) colors.bg else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActionRowItem(
    icon: ImageVector,
    label: String,
    sub: String? = null,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val colors = GlassTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val itemTint = tint ?: colors.text

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.field),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = itemTint,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                color = itemTint
            )
            if (sub != null) {
                Text(
                    text = sub,
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}
