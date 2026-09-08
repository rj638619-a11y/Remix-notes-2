package com.example.ui.sheets

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassTheme
import com.example.ui.util.telegramBounceClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNoteSheet(
    onDismiss: () -> Unit,
    onCreateText: () -> Unit,
    onCreateHtml: () -> Unit,
    onCreatePdf: () -> Unit
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "New Note / Document",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                color = colors.text,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
            )

            CreateOptionItem(
                icon = Icons.Default.TextFields,
                title = "Create Plain Text Note",
                subtitle = "Write plainly — auto formatted with headers, lists & checkboxes",
                onClick = {
                    onDismiss()
                    onCreateText()
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            CreateOptionItem(
                icon = Icons.Default.Code,
                title = "Create HTML Code Note",
                subtitle = "Write interactive HTML markup with live web view preview",
                onClick = {
                    onDismiss()
                    onCreateHtml()
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            CreateOptionItem(
                icon = Icons.Default.Description,
                title = "Create / Import PDF Document",
                subtitle = "Read crisp zero-loss PDF files like Google Drive with zoom & page controls",
                iconTint = Color(0xFFEF4444),
                onClick = {
                    onDismiss()
                    onCreatePdf()
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Cancel button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.field)
                    .telegramBounceClickable { onDismiss() }
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CreateOptionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color? = null,
    onClick: () -> Unit
) {
    val colors = GlassTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .telegramBounceClickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.chipOnBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: colors.chipOnTx,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                color = colors.text
            )
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
