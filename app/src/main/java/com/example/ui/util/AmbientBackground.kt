package com.example.ui.util

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun AmbientBackground(
    isDark: Boolean,
    isReduced: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (isReduced) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        if (isDark) {
            // Dark mode amber ambient top-left glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x12F2B90C), Color.Transparent),
                    center = Offset(w * 0.12f, -h * 0.06f),
                    radius = w * 0.85f
                ),
                center = Offset(w * 0.12f, -h * 0.06f),
                radius = w * 0.85f
            )

            // Dark mode subtle purple ambient right glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x0D7850C8), Color.Transparent),
                    center = Offset(w * 1.08f, h * 0.26f),
                    radius = w * 0.95f
                ),
                center = Offset(w * 1.08f, h * 0.26f),
                radius = w * 0.95f
            )
        } else {
            // Light mode amber ambient top-left glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x1CF2B90C), Color.Transparent),
                    center = Offset(w * 0.12f, -h * 0.06f),
                    radius = w * 0.85f
                ),
                center = Offset(w * 0.12f, -h * 0.06f),
                radius = w * 0.85f
            )

            // Light mode warm orange ambient right glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x12E07A3C), Color.Transparent),
                    center = Offset(w * 1.08f, h * 0.26f),
                    radius = w * 0.95f
                ),
                center = Offset(w * 1.08f, h * 0.26f),
                radius = w * 0.95f
            )
        }
    }
}
