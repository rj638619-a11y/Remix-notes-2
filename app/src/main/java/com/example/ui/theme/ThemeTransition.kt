package com.example.ui.theme

import android.graphics.Bitmap
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Controller managing Telegram-style circular reveal theme transition.
 * It snapshots the current UI before switching themes, then expands a circular cutout
 * from the switch button position to smoothly reveal the new theme underneath at 60/120fps.
 */
class ThemeTransitionState {
    var buttonOrigin by mutableStateOf(Offset.Unspecified)
    var isTransitioning by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var isTargetDark by mutableStateOf(false)

    var snapshotBitmap by mutableStateOf<Bitmap?>(null)
    var fallbackOldBg by mutableStateOf(Color.Transparent)

    private var animationJob: Job? = null

    fun recordOrigin(offset: Offset) {
        buttonOrigin = offset
    }

    /**
     * Snapshots the screen right as the user taps the toggle button
     * before the theme state recomposes.
     */
    fun prepareTransition(origin: Offset, view: View?, oldBg: Color) {
        buttonOrigin = origin
        fallbackOldBg = oldBg
        if (view != null && view.width > 0 && view.height > 0) {
            try {
                val oldBmp = snapshotBitmap
                val newBmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(newBmp)
                view.draw(canvas)
                snapshotBitmap = newBmp
                oldBmp?.recycle()
            } catch (_: Throwable) {
                // Fallback gracefully to old background color
            }
        }
    }

    /**
     * Executes the circular reveal animation with snappy Telegram-style deceleration.
     */
    fun startTransition(toDark: Boolean, scope: CoroutineScope) {
        isTargetDark = toDark
        isTransitioning = true
        animationJob?.cancel()
        animationJob = scope.launch {
            val anim = Animatable(0f)
            // Telegram circular reveal curve: fast explosion out, gentle deceleration to edge
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 400,
                    easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
                )
            ) {
                progress = value
            }

            // Cleanup snapshot upon full reveal
            val bmp = snapshotBitmap
            snapshotBitmap = null
            bmp?.recycle()
            isTransitioning = false
            progress = 0f
        }
    }
}

val LocalThemeTransition = compositionLocalOf { ThemeTransitionState() }

/**
 * Overlay that renders the Telegram-style circular reveal.
 * The previous theme snapshot is drawn with a circular cutout (ClipOp.Difference)
 * centered at the theme toggle button, expanding until the entire new screen is revealed.
 */
@Composable
fun ThemeLightWaveOverlay(
    state: ThemeTransitionState,
    modifier: Modifier = Modifier
) {
    if (!state.isTransitioning || state.progress <= 0f) return

    val progress = state.progress
    val isToDark = state.isTargetDark
    val bitmap = state.snapshotBitmap
    val fallbackBg = state.fallbackOldBg
    val circlePath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val origin = if (state.buttonOrigin.isSpecified) state.buttonOrigin else Offset(w - 120f, 140f)
        val ox = origin.x.coerceIn(0f, w)
        val oy = origin.y.coerceIn(0f, h)
        val center = Offset(ox, oy)

        // Diagonal distance to the farthest corner
        val d1 = hypot(ox, oy)
        val d2 = hypot(w - ox, oy)
        val d3 = hypot(ox, h - oy)
        val d4 = hypot(w - ox, h - oy)
        val maxRadius = maxOf(d1, d2, d3, d4) * 1.05f

        val currentRadius = progress * maxRadius

        circlePath.reset()
        circlePath.addOval(
            Rect(
                center = center,
                radius = currentRadius
            )
        )

        // ClipOp.Difference draws the previous screen everywhere EXCEPT inside the expanding circle.
        // The newly rendered theme underneath is thus revealed inside the circle.
        clipPath(path = circlePath, clipOp = ClipOp.Difference) {
            if (bitmap != null && !bitmap.isRecycled) {
                drawImage(bitmap.asImageBitmap())
            } else {
                val defaultBg = if (isToDark) LightGlassColors.bg else DarkGlassColors.bg
                drawRect(color = if (fallbackBg != Color.Transparent) fallbackBg else defaultBg)
            }
        }

        // Clean, subtle edge rim for depth during expansion (matches Telegram)
        if (currentRadius > 8f && currentRadius < maxRadius) {
            val strokeColor = if (isToDark) {
                Color.Black.copy(alpha = (0.25f * (1f - progress)).coerceIn(0f, 0.25f))
            } else {
                Color.White.copy(alpha = (0.35f * (1f - progress)).coerceIn(0f, 0.35f))
            }
            drawCircle(
                color = strokeColor,
                radius = currentRadius,
                center = center,
                style = Stroke(width = 2.5f)
            )
        }
    }
}
