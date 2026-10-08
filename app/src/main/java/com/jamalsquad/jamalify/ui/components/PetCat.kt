package com.jamalsquad.jamalify.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.VectorConverter
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

private val Blush = Color(0xFFFF9EB5)

/**
 * Kucing peliharaan kecil di beranda. Ekornya selalu bergoyang dan sesekali
 * berkedip; saat musik diputar kepalanya mengangguk mengikuti irama dan
 * matanya tersenyum. Diketuk, ia melompat dan memunculkan hati.
 *
 * Digambar di kanvas 64×64 lalu diskalakan, garisnya senada dengan ikon
 * tipis lain; hanya pipinya yang diberi warna.
 */
@Composable
fun PetCat(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    walking: Boolean = false,
    facingRight: Boolean = false,
    onTap: () -> Unit = {}
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val fur = MaterialTheme.colorScheme.surfaceContainerHigh

    val loop = rememberInfiniteTransition(label = "cat")
    val tail by loop.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (isPlaying) 450 else 1100), RepeatMode.Reverse),
        label = "tail"
    )
    val beat by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing)),
        label = "beat"
    )
    val step by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing)),
        label = "step"
    )

    val blink = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2200L + Random.nextLong(2600))
            blink.animateTo(0.1f, tween(70))
            blink.animateTo(1f, tween(110))
        }
    }

    val jump = remember { Animatable(0f) }
    val heart = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Canvas(
        modifier = modifier
            .size(size)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onTap()
                scope.launch {
                    jump.snapTo(0f)
                    jump.animateTo(1f, tween(160))
                    jump.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                scope.launch {
                    heart.snapTo(0f)
                    heart.animateTo(1f, tween(900))
                    heart.snapTo(0f)
                }
            }
    ) {
        val unit = this.size.minDimension / 64f
        scale(unit, pivot = Offset.Zero) {
            val bob = if (isPlaying && !walking) sin(beat * 2 * PI).toFloat() * 1.6f else 0f
            val stride = if (walking) sin(step * 2 * PI).toFloat() else 0f
            val lift = -jump.value * 8f - abs(stride) * 2f
            // Gambar dasarnya menghadap kiri (ekor di kanan); dicerminkan
            // saat berjalan ke kanan supaya ekornya selalu di belakang.
            scale(if (facingRight) -1f else 1f, 1f, pivot = Offset(32f, 32f)) {
                rotate(if (walking) -4f else 0f, pivot = Offset(32f, 60f)) {
                    translate(top = lift) {
                        drawCat(ink, fur, tail, bob, blink.value, happy = isPlaying || jump.value > 0f, stride = stride)
                    }
                }
            }
            if (heart.value > 0f) {
                drawHeart(Offset(50f, 14f - heart.value * 12f), 1f - heart.value)
            }
            if (isPlaying) drawNote(ink, beat)
        }
    }
}

private fun DrawScope.drawCat(
    ink: Color,
    fur: Color,
    tail: Float,
    bob: Float,
    eyeOpen: Float,
    happy: Boolean,
    stride: Float = 0f
) {
    val line = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Ekor: berpangkal di pinggul kanan, berayun di sekitar pangkalnya.
    rotate(degrees = tail * 14f, pivot = Offset(44f, 54f)) {
        val tailPath = Path().apply {
            moveTo(44f, 54f)
            cubicTo(56f, 54f, 58f, 44f, 54f, 36f)
        }
        drawPath(tailPath, ink, style = Stroke(width = 4.5f, cap = StrokeCap.Round))
        drawPath(tailPath, fur, style = Stroke(width = 2f, cap = StrokeCap.Round))
    }

    // Badan duduk.
    val body = Path().apply {
        moveTo(20f, 60f)
        cubicTo(16f, 46f, 22f, 36f, 32f, 36f)
        cubicTo(42f, 36f, 48f, 46f, 44f, 60f)
        close()
    }
    drawPath(body, fur)
    drawPath(body, ink, style = line)
    // Kaki depan; saat berjalan bergantian terangkat.
    val leftLift = stride.coerceAtLeast(0f) * 4f
    val rightLift = (-stride).coerceAtLeast(0f) * 4f
    drawLine(ink, Offset(28f, 52f), Offset(28f, 60f - leftLift), 2f, StrokeCap.Round)
    drawLine(ink, Offset(36f, 52f), Offset(36f, 60f - rightLift), 2f, StrokeCap.Round)

    // Kepala, mengangguk saat musik diputar.
    translate(top = bob) {
        val head = Path().apply {
            moveTo(14f, 30f)
            lineTo(15f, 12f)
            lineTo(23f, 18f)
            quadraticTo(32f, 15f, 41f, 18f)
            lineTo(49f, 12f)
            lineTo(50f, 30f)
            cubicTo(50f, 40f, 42f, 44f, 32f, 44f)
            cubicTo(22f, 44f, 14f, 40f, 14f, 30f)
            close()
        }
        drawPath(head, fur)
        drawPath(head, ink, style = line)

        // Bagian dalam telinga.
        drawLine(ink.copy(alpha = 0.35f), Offset(17f, 17f), Offset(20f, 19f), 1.5f, StrokeCap.Round)
        drawLine(ink.copy(alpha = 0.35f), Offset(47f, 17f), Offset(44f, 19f), 1.5f, StrokeCap.Round)

        // Mata: titik yang berkedip, atau lengkung "^ ^" saat senang.
        if (happy) {
            for (cx in listOf(24f, 40f)) {
                val arc = Path().apply {
                    moveTo(cx - 3f, 30f)
                    quadraticTo(cx, 26f, cx + 3f, 30f)
                }
                drawPath(arc, ink, style = line)
            }
        } else {
            for (cx in listOf(24f, 40f)) {
                val h = 6f * eyeOpen
                drawOval(ink, Offset(cx - 2.2f, 29f - h / 2), Size(4.4f, h.coerceAtLeast(1.2f)))
            }
        }

        // Pipi merona.
        drawOval(Blush.copy(alpha = 0.7f), Offset(17f, 32f), Size(6f, 3.5f))
        drawOval(Blush.copy(alpha = 0.7f), Offset(41f, 32f), Size(6f, 3.5f))

        // Hidung dan mulut "w".
        drawCircle(ink, 1.2f, Offset(32f, 32f))
        val mouth = Path().apply {
            moveTo(29f, 34f)
            quadraticTo(30.5f, 36.5f, 32f, 34f)
            quadraticTo(33.5f, 36.5f, 35f, 34f)
        }
        drawPath(mouth, ink, style = Stroke(1.5f, cap = StrokeCap.Round))

        // Kumis.
        val whisker = ink.copy(alpha = 0.5f)
        drawLine(whisker, Offset(6f, 30f), Offset(14f, 32f), 1.2f, StrokeCap.Round)
        drawLine(whisker, Offset(7f, 36f), Offset(15f, 35f), 1.2f, StrokeCap.Round)
        drawLine(whisker, Offset(58f, 30f), Offset(50f, 32f), 1.2f, StrokeCap.Round)
        drawLine(whisker, Offset(57f, 36f), Offset(49f, 35f), 1.2f, StrokeCap.Round)
    }
}

private fun DrawScope.drawHeart(at: Offset, alpha: Float) {
    val s = 5f
    val path = Path().apply {
        moveTo(at.x, at.y + s * 0.9f)
        cubicTo(at.x - s * 1.6f, at.y - s * 0.1f, at.x - s * 0.6f, at.y - s * 1.2f, at.x, at.y - s * 0.3f)
        cubicTo(at.x + s * 0.6f, at.y - s * 1.2f, at.x + s * 1.6f, at.y - s * 0.1f, at.x, at.y + s * 0.9f)
        close()
    }
    drawPath(path, Blush.copy(alpha = alpha))
}

/** Not kecil yang melayang naik dari samping kepala, satu per ketukan. */
private fun DrawScope.drawNote(ink: Color, beat: Float) {
    val x = 6f + beat * 2f
    val y = 16f - beat * 10f
    val color = ink.copy(alpha = (1f - beat) * 0.8f)
    drawCircle(color, 2f, Offset(x, y))
    drawLine(color, Offset(x + 1.8f, y), Offset(x + 1.8f, y - 7f), 1.3f, StrokeCap.Round)
    drawLine(color, Offset(x + 1.8f, y - 7f), Offset(x + 4.5f, y - 5.5f), 1.3f, StrokeCap.Round)
}
