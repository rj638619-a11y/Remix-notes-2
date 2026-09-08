package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
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
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassTheme

@Composable
fun GlassDock(
    isSyncActive: Boolean,
    onSyncClick: () -> Unit,
    onCreateClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = GlassTheme.colors
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val dockHeight = if (isLandscape) 52.dp else 64.dp
    val buttonSize = if (isLandscape) 44.dp else 54.dp
    val iconSize = if (isLandscape) 24.dp else 28.dp

    Box(
        modifier = modifier
            .widthIn(max = if (isLandscape) 420.dp else 500.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .shadow(if (colors.isReduced) 0.dp else 16.dp, RoundedCornerShape(24.dp), ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(colors.glass, RoundedCornerShape(24.dp))
            .border(1.dp, colors.glassBorder, RoundedCornerShape(24.dp))
            .height(dockHeight)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sync Dock Button
            DockIconButton(
                icon = Icons.Default.Sync,
                label = "Sync",
                showDot = isSyncActive,
                onClick = onSyncClick
            )

            // Central Amber Plus Button
            val newInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .shadow(12.dp, CircleShape, spotColor = Color(0x66BE8C00))
                    .clip(CircleShape)
                    .background(colors.accentSecondary)
                    .clickable(
                        interactionSource = newInteraction,
                        indication = ripple(bounded = true, radius = buttonSize / 2),
                        onClick = onCreateClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create note",
                    tint = Color(0xFF231A00),
                    modifier = Modifier.size(iconSize)
                )
            }

            // Settings Dock Button
            DockIconButton(
                icon = Icons.Default.Tune,
                label = "Settings",
                showDot = false,
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun DockIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    showDot: Boolean,
    onClick: () -> Unit
) {
    val colors = GlassTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
                if (showDot) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(Color(0xFF34C759))
                    )
                }
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
