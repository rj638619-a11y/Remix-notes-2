package com.example.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassTheme
import com.example.util.DateFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSheet(
    isAutoSync: Boolean,
    readerMode: String = "html",
    syncFolderName: String?,
    lastSyncTime: Long,
    onDismiss: () -> Unit,
    onSelectFolder: () -> Unit,
    onClearFolder: () -> Unit,
    onSyncNow: () -> Unit,
    onSyncFullDevice: () -> Unit,
    onSyncPdfDevice: () -> Unit,
    onImportFiles: () -> Unit,
    onImportPdf: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit
) {
    val colors = GlassTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isPdfMode = readerMode == "pdf"

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
                text = if (isPdfMode) "PDF Drive & Storage Sync" else "HTML & Device Sync",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                color = colors.text,
                modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)
            )

            // Primary Card: Dynamic by Mode
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.field)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isPdfMode) Color(0xFFDC2626) else Color(0xFF34C759))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isPdfMode) "Full Device PDF Sync" else "Full Device HTML Sync",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.chipOnBg)
                            .clickable {
                                onDismiss()
                                if (isPdfMode) onSyncPdfDevice() else onSyncFullDevice()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Scan All",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.chipOnTx
                        )
                    }
                }

                Text(
                    text = if (isPdfMode) {
                        "Automatically indexes and discovers all PDF documents across your phone (Documents, Downloads, Books, internal/external storage, and MediaStore) with zero quality loss."
                    } else {
                        "Automatically indexes and syncs every .html and .htm file found across your phone (Documents, Downloads, internal/external storage, and MediaStore)."
                    },
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (lastSyncTime > 0L) {
                    Text(
                        text = "Last synced: ${DateFormatter.fmtClock(lastSyncTime)}",
                        fontSize = 11.5.sp,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action: Full Device PDF Scan
            SyncActionRow(
                icon = Icons.Default.PictureAsPdf,
                label = "Scan full device for PDF Documents",
                sub = "Search entire phone storage for all .pdf books and files",
                onClick = {
                    onDismiss()
                    onSyncPdfDevice()
                }
            )

            // Action: Full Device HTML Scan
            SyncActionRow(
                icon = Icons.Default.Html,
                label = "Scan full device for HTML Notes",
                sub = "Search phone storage for .html & .htm notes",
                onClick = {
                    onDismiss()
                    onSyncFullDevice()
                }
            )

            // Current Sync Folder Option
            val hasFolder = !syncFolderName.isNullOrEmpty()
            SyncActionRow(
                icon = if (hasFolder) Icons.Default.FolderOpen else Icons.Default.CreateNewFolder,
                label = if (hasFolder) "Synced folder: $syncFolderName" else "Select custom sync folder",
                sub = if (hasFolder) "Tap to change folder" else "Limit auto-sync to a specific directory",
                onClick = { onDismiss(); onSelectFolder() }
            )

            if (hasFolder) {
                SyncActionRow(
                    icon = Icons.Default.Sync,
                    label = "Sync selected folder now",
                    sub = "Scan $syncFolderName and update notes",
                    onClick = {
                        onDismiss()
                        onSyncNow()
                    }
                )
            }

            // Action: Import individual PDF files
            SyncActionRow(
                icon = Icons.Default.PictureAsPdf,
                label = "Import PDF Document",
                sub = "Select and import a PDF file to read with dynamic zoom",
                onClick = { onDismiss(); onImportPdf() }
            )

            // Action: Import individual HTML / Text files
            SyncActionRow(
                icon = Icons.Default.UploadFile,
                label = "Import HTML / Text files",
                sub = "Pick specific .html, .md, or .txt documents",
                onClick = { onDismiss(); onImportFiles() }
            )

            // Auto-sync switch row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.field),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-sync on app launch",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text
                    )
                    Text(
                        text = "Keeps device notes and documents synchronized automatically",
                        fontSize = 12.sp,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                Switch(
                    checked = isAutoSync,
                    onCheckedChange = onToggleAutoSync,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF34C759)
                    )
                )
            }

            Text(
                text = "Smart deduplication: Identical files are skipped, while modified files update without duplicate clutter.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Done button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.field)
                    .clickable { onDismiss() }
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Done",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun SyncActionRow(
    icon: ImageVector,
    label: String,
    sub: String,
    onClick: () -> Unit
) {
    val colors = GlassTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.field),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text
            )
            Text(
                text = sub,
                fontSize = 12.sp,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}
