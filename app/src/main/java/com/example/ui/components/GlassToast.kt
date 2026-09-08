package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassTheme
import com.example.ui.viewmodel.ToastEvent

@Composable
fun GlassToast(
    toastEvent: ToastEvent?,
    modifier: Modifier = Modifier
) {
    val colors = GlassTheme.colors

    AnimatedVisibility(
        visible = toastEvent != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier
    ) {
        if (toastEvent != null) {
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 88.dp)
                    .widthIn(max = 340.dp)
                    .shadow(16.dp, RoundedCornerShape(999.dp), ambientColor = colors.shadow, spotColor = colors.shadow)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.glass)
                    .border(1.dp, colors.glassBorder, RoundedCornerShape(999.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val message = when (toastEvent) {
                        is ToastEvent.Simple -> toastEvent.message
                        is ToastEvent.WithAction -> toastEvent.message
                    }

                    Text(
                        text = message,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (toastEvent is ToastEvent.WithAction) {
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = toastEvent.actionLabel,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.accent,
                            modifier = Modifier.clickable { toastEvent.onAction() }
                        )
                    }
                }
            }
        }
    }
}
