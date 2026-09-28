package com.jamalsquad.jamalify.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Visualisator batang audio animasi bergerak (Equalizer Wave).
 * Ditampilkan di MiniPlayer dan PlayerScreen saat lagu dimainkan.
 */
@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 4,
    barWidth: Dp = 3.dp,
    barSpacing: Dp = 2.dp,
    height: Dp = 18.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "audio_visualizer")

    val anim1 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bar1"
    )
    val anim2 by transition.animateFloat(
        initialValue = 0.5f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bar2"
    )
    val anim3 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bar3"
    )
    val anim4 by transition.animateFloat(
        initialValue = 0.7f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bar4"
    )

    val factors = listOf(anim1, anim2, anim3, anim4)

    val totalWidth = (barWidth * barCount) + (barSpacing * (barCount - 1))

    Canvas(
        modifier = modifier
            .width(totalWidth)
            .height(height)
    ) {
        val widthPx = barWidth.toPx()
        val spacingPx = barSpacing.toPx()
        val canvasHeight = size.height

        for (i in 0 until barCount) {
            val factor = if (isPlaying) factors[i % factors.size] else 0.15f
            val barHeight = (canvasHeight * factor).coerceAtLeast(4f)
            val x = i * (widthPx + spacingPx)
            val y = canvasHeight - barHeight

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(widthPx, barHeight),
                cornerRadius = CornerRadius(widthPx / 2, widthPx / 2)
            )
        }
    }
}
