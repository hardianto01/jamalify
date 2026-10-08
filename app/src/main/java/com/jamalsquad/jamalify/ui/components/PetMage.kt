package com.jamalsquad.jamalify.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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

private val Ink = Color(0xFF1A1C24)
private val Hair = Color(0xFFF1F2F6)
private val HairShade = Color(0xFFC6CCD8)
private val Skin = Color(0xFFFCF1EA)
private val EarShade = Color(0xFFEBC9BE)
private val Iris = Color(0xFF5AA888)
private val IrisDark = Color(0xFF2F6E57)
private val Earring = Color(0xFFD2384A)
private val Cloth = Color(0xFFF7F5EF)
private val Gold = Color(0xFFD7AE4A)
private val Dark = Color(0xFF2A2B33)
private val Boot = Color(0xFF7A5234)
private val Shaft = Color(0xFFF2EEE4)
private val Gem = Color(0xFFD83A4E)
private val Cover = Color(0xFF7A5638)
private val Page = Color(0xFFFFFBF0)
private val Wood = Color(0xFF8E5C30)
private val WoodDark = Color(0xFF6A4222)
private val Petals = listOf(Color(0xFFF4A6C0), Color(0xFFFFFFFF), Color(0xFFF7D56B), Color(0xFFB9A4F0))

/**
 * Aksi kecil Frieren. Diam: berdiri memegang tongkat (IDLE), membaca
 * grimoire (READ), terkantuk-kantuk (NAP), sihir penumbuh bunga (SPELL).
 * Pose dialog: STARE menatap datar, MIMIC kepalanya ditelan peti harta.
 */
private enum class FAct { IDLE, READ, NAP, SPELL, STARE, MIMIC }

private class FLine(val text: String, val act: FAct)

private val MageSkits = listOf(
    listOf(FLine("Lagu ini… 80 tahun lalu juga ada yang mirip.", FAct.STARE)),
    listOf(FLine("Sihir penumbuh bunga. Tidak berguna, tapi aku suka.", FAct.SPELL)),
    listOf(FLine("Peti harta! Pasti isinya grimoire langka…", FAct.STARE), FLine("Gelap… Fern, tolong…", FAct.MIMIC)),
    listOf(FLine("Himmel pasti suka lagu ini.", FAct.IDLE)),
    listOf(FLine("Lima menit lagi…", FAct.NAP))
)

/**
 * Peliharaan chibi penyihir elf: rambut putih dikuncir dua, telinga runcing,
 * mata hijau yang selalu setengah mengantuk, jubah putih bertepi emas, dan
 * tongkat bermata merah — penghormatan untuk Frieren, digambar sendiri. Saat
 * diam ia bergantian membaca grimoire, terkantuk-kantuk, atau menumbuhkan
 * bunga. Diketuk, ia bicara lewat [onSpeech] seperti [PetGenius].
 */
@Composable
fun PetMage(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    walking: Boolean = false,
    facingRight: Boolean = false,
    onTap: () -> Unit = {},
    onSpeech: (String?) -> Unit = {}
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val loop = rememberInfiniteTransition(label = "mage")
    val step by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(560, easing = LinearEasing)),
        label = "step"
    )
    val beat by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing)),
        label = "beat"
    )

    var act by remember { mutableStateOf(FAct.IDLE) }
    val progress = remember { Animatable(0f) }
    val jump = remember { Animatable(0f) }
    val blink = remember { Animatable(1f) }
    var requested by remember { mutableStateOf<List<FLine>?>(null) }
    var taps by remember { mutableIntStateOf(0) }
    var lastSkit by remember { mutableStateOf<List<FLine>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000L + Random.nextLong(3000))
            blink.animateTo(0.1f, tween(90))
            blink.animateTo(1f, tween(140))
        }
    }

    LaunchedEffect(walking, taps) {
        if (walking) {
            act = FAct.IDLE
            return@LaunchedEffect
        }
        requested?.let { skit ->
            try {
                for (line in skit) {
                    onSpeech(line.text)
                    playMage(line.act, progress, speaking = line.text.length) { act = it }
                }
            } finally {
                onSpeech(null)
            }
            requested = null
        }
        while (true) {
            delay(1800L + Random.nextLong(2500))
            val next = listOf(FAct.READ, FAct.READ, FAct.NAP, FAct.IDLE, FAct.SPELL).random()
            playMage(next, progress) { act = it }
        }
    }

    Canvas(
        modifier = modifier
            .size(size)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onTap()
                requested = MageSkits.filter { it !== lastSkit }.random().also { lastSkit = it }
                taps++
                scope.launch {
                    jump.snapTo(0f)
                    jump.animateTo(1f, tween(150))
                    jump.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
            }
    ) {
        val unit = this.size.minDimension / 64f
        scale(unit, pivot = Offset.Zero) {
            val stride = if (walking) sin(step * 2 * PI).toFloat() else 0f
            val sway = if (isPlaying && !walking) sin(beat * 2 * PI).toFloat() else 0f
            val lift = -jump.value * 7f - abs(stride) * 1f
            scale(if (facingRight) -1f else 1f, 1f, pivot = Offset(32f, 32f)) {
                translate(top = lift) {
                    drawMage(if (walking) FAct.IDLE else act, progress.value, stride, sway, blink.value)
                }
            }
            // Huruf Z tidak ikut dicerminkan supaya tetap terbaca.
            if (act == FAct.NAP && !walking) drawSleepZ(ink, progress.value)
            else if (isPlaying && !walking) drawMageNote(ink, beat)
        }
    }
}

private suspend fun playMage(
    next: FAct,
    progress: Animatable<Float, *>,
    speaking: Int = 0,
    setAct: (FAct) -> Unit
) {
    setAct(next)
    progress.snapTo(0f)
    val duration = if (speaking > 0) 1200 + speaking * 55 else when (next) {
        FAct.READ -> 3200
        FAct.NAP -> 3600
        FAct.SPELL -> 2800
        else -> 1800
    }
    progress.animateTo(1f, tween(duration, easing = LinearEasing))
    setAct(FAct.IDLE)
    progress.snapTo(0f)
}

private fun DrawScope.drawMage(act: FAct, t: Float, stride: Float, sway: Float, eyeOpen: Float) {
    val line = Stroke(width = 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val mimic = act == FAct.MIMIC

    if (!mimic) drawTwinTails(sway * 1.5f + stride * 1.2f, line)
    drawStaff(act, t, stride, line)
    // Saat ditelan peti kakinya menendang-nendang.
    drawMageBody(if (mimic) sin(t * 10 * PI).toFloat() else stride, line)

    if (mimic) {
        drawMimic(t, line)
        return
    }
    val nod = if (act == FAct.NAP) 1f + sin(t * 6 * PI).toFloat().coerceAtLeast(0f) * 1.4f else 0f
    val tilt = if (act == FAct.STARE) 7f else sway * 3f
    translate(top = nod) {
        rotate(tilt, pivot = Offset(32f, 34f)) { drawMageHead(act, eyeOpen, line) }
    }
    drawMageHands(act, t)
    if (act == FAct.SPELL) drawFlowers(t)
}

/** Kuncir dua panjang di belakang badan, berayun mengikuti musik dan langkah. */
private fun DrawScope.drawTwinTails(swing: Float, line: Stroke) {
    for (s in listOf(-1f, 1f)) {
        val rx = 32f + s * 13.5f
        val tip = Offset(rx + s * 2f + swing, 51f)
        val tail = Path().apply {
            moveTo(rx - 3.5f, 24f)
            cubicTo(rx - 4.5f + s, 34f, tip.x - 3f, 44f, tip.x, tip.y)
            cubicTo(tip.x + 3f, 44f, rx + 4.5f + s, 34f, rx + 3.5f, 24f)
            close()
        }
        drawPath(tail, Hair)
        drawPath(tail, Ink, style = line)
        drawLine(HairShade, Offset(rx, 29f), Offset(tip.x, tip.y - 4f), 0.8f, StrokeCap.Round)
        drawRoundRect(Dark, Offset(rx - 3f, 26f), Size(6f, 2.2f), CornerRadius(1f))
    }
}

/** Tongkat bermata merah: dipegang tegak, diacungkan saat merapal, tergeletak saat ditelan peti. */
private fun DrawScope.drawStaff(act: FAct, t: Float, stride: Float, line: Stroke) {
    if (act == FAct.MIMIC) {
        drawLine(Ink, Offset(4f, 60f), Offset(22f, 60f), 3f, StrokeCap.Round)
        drawLine(Shaft, Offset(4f, 60f), Offset(22f, 60f), 1.7f, StrokeCap.Round)
        drawCircle(Gem, 1.6f, Offset(3f, 60f))
        return
    }
    val angle = when (act) {
        FAct.SPELL -> -32f * minOf(1f, t * 4f)
        FAct.READ -> 10f
        else -> stride * 6f
    }
    rotate(angle, pivot = Offset(47.5f, 40f)) {
        drawLine(Ink, Offset(48f, 19f), Offset(48f, 58f), 3f, StrokeCap.Round)
        drawLine(Shaft, Offset(48f, 19f), Offset(48f, 58f), 1.7f, StrokeCap.Round)
        drawArc(Gold, 50f, 260f, false, Offset(44f, 11f), Size(8f, 8f), style = Stroke(1.8f, cap = StrokeCap.Round))
        drawCircle(Gem, 1.9f, Offset(48f, 15f))
        drawCircle(Color.White.copy(alpha = 0.7f), 0.6f, Offset(47.4f, 14.4f))
        if (act == FAct.SPELL) {
            // Kilau di ujung tongkat.
            for (i in 0 until 3) {
                val phase = (t * 3f + i / 3f) % 1f
                val c = Offset(48f + (i - 1) * 4f, 10f - phase * 5f)
                val r = 1.6f * (1f - phase)
                drawLine(Gold, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 0.8f, StrokeCap.Round)
                drawLine(Gold, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 0.8f, StrokeCap.Round)
            }
        }
    }
}

/** Badan: jubah pendek putih bertepi emas, rok putih, stoking hitam, sepatu bot. */
private fun DrawScope.drawMageBody(stride: Float, line: Stroke) {
    val leftLift = stride.coerceAtLeast(0f) * 3f
    val rightLift = (-stride).coerceAtLeast(0f) * 3f
    for ((x, up) in listOf(28.5f to leftLift, 35.5f to rightLift)) {
        drawLine(Ink, Offset(x, 47f), Offset(x, 55f - up), 4.6f, StrokeCap.Round)
        drawLine(Dark, Offset(x, 47f), Offset(x, 55f - up), 3.2f, StrokeCap.Round)
        drawRoundRect(Boot, Offset(x - 2.6f, 54f - up), Size(5.2f, 4.2f), CornerRadius(1.5f))
        drawRoundRect(Ink, Offset(x - 2.6f, 54f - up), Size(5.2f, 4.2f), CornerRadius(1.5f), style = Stroke(0.9f))
    }

    val skirt = Path().apply {
        moveTo(25f, 40f); lineTo(39f, 40f); lineTo(42f, 49f)
        quadraticTo(32f, 51f, 22f, 49f); close()
    }
    drawPath(skirt, Cloth)
    drawPath(skirt, Ink, style = line)
    val hem = Path().apply { moveTo(23f, 47.4f); quadraticTo(32f, 49.3f, 41f, 47.4f) }
    drawPath(hem, Gold, style = Stroke(1f))

    val cape = Path().apply {
        moveTo(26f, 33f); lineTo(38f, 33f)
        quadraticTo(42f, 34f, 43f, 42f)
        quadraticTo(32f, 45f, 21f, 42f)
        quadraticTo(22f, 34f, 26f, 33f); close()
    }
    drawPath(cape, Cloth)
    drawPath(cape, Ink, style = line)
    val trim = Path().apply { moveTo(21.8f, 41f); quadraticTo(32f, 44f, 42.2f, 41f) }
    drawPath(trim, Gold, style = Stroke(1.2f))
    val collar = Path().apply { moveTo(29.5f, 33f); lineTo(34.5f, 33f); lineTo(32f, 37f); close() }
    drawPath(collar, Dark)
    drawCircle(Gold, 0.9f, Offset(32f, 37.2f))
}

private fun DrawScope.drawMageSleeve(from: Offset, to: Offset) {
    drawLine(Ink, from, to, 4.6f, StrokeCap.Round)
    drawLine(Cloth, from, to, 3.1f, StrokeCap.Round)
}

private fun DrawScope.drawMageHand(at: Offset, r: Float = 1.9f) {
    drawCircle(Skin, r, at)
    drawCircle(Ink, r, at, style = Stroke(0.9f))
}

private fun DrawScope.drawMageHands(act: FAct, t: Float) {
    if (act == FAct.READ) {
        // Grimoire terbuka di depan dada, halamannya sesekali dibalik.
        drawRoundRect(Cover, Offset(24f, 35.5f), Size(16f, 8f), CornerRadius(1.2f))
        drawRoundRect(Ink, Offset(24f, 35.5f), Size(16f, 8f), CornerRadius(1.2f), style = Stroke(0.9f))
        drawRect(Page, Offset(25f, 36.2f), Size(6.6f, 6.4f))
        drawRect(Page, Offset(32.4f, 36.2f), Size(6.6f, 6.4f))
        for (i in 0 until 3) {
            val y = 38f + i * 1.5f
            drawLine(HairShade, Offset(26f, y), Offset(30.5f, y), 0.5f)
            drawLine(HairShade, Offset(33.4f, y), Offset(38f, y), 0.5f)
        }
        val flip = (t * 2f) % 1f
        if (flip > 0.8f) {
            val w = (1f - (flip - 0.8f) / 0.2f) * 6.6f
            drawRect(Page, Offset(32f, 35.6f), Size(w, 6.4f))
            drawRect(Ink, Offset(32f, 35.6f), Size(w, 6.4f), style = Stroke(0.4f))
        }
        drawMageSleeve(Offset(24f, 36f), Offset(24.5f, 42f))
        drawMageSleeve(Offset(40f, 36f), Offset(39.5f, 42f))
        drawMageHand(Offset(24.5f, 42.5f))
        drawMageHand(Offset(39.5f, 42.5f))
    } else {
        drawMageSleeve(Offset(24f, 36f), Offset(23.5f, 42f))
        drawMageHand(Offset(23.5f, 42.5f))
        drawMageSleeve(Offset(40f, 36f), Offset(46.5f, 39.5f))
        drawMageHand(Offset(47.5f, 40f))
    }
}

private fun DrawScope.drawMageHead(act: FAct, eyeOpen: Float, line: Stroke) {
    // Rambut belakang.
    drawOval(Hair, Offset(16f, 4.5f), Size(32f, 29f))
    drawOval(Ink, Offset(16f, 4.5f), Size(32f, 29f), style = line)

    // Telinga elf yang runcing ke samping.
    for (s in listOf(-1f, 1f)) {
        val ear = Path().apply {
            moveTo(32f + s * 12.5f, 19.5f)
            lineTo(32f + s * 23.5f, 15f)
            lineTo(32f + s * 12.5f, 25.5f)
            close()
        }
        drawPath(ear, Skin)
        drawPath(ear, Ink, style = line)
        drawLine(EarShade, Offset(32f + s * 14.5f, 21.5f), Offset(32f + s * 20.5f, 16.8f), 0.9f, StrokeCap.Round)
    }

    drawOval(Skin, Offset(19f, 9.5f), Size(26f, 24f))
    drawOval(Ink, Offset(19f, 9.5f), Size(26f, 24f), style = line)

    // Poni belah tengah dengan helai samping sampai pipi.
    val bangs = Path().apply {
        moveTo(18f, 28f)
        cubicTo(15f, 8f, 25f, 4f, 32f, 4f)
        cubicTo(39f, 4f, 49f, 8f, 46f, 28f)
        lineTo(44f, 18f)
        lineTo(41.5f, 17.5f)
        lineTo(39.5f, 12f)
        lineTo(37.5f, 17.5f)
        lineTo(34f, 15.5f)
        lineTo(32.5f, 10.5f)
        lineTo(30f, 15.5f)
        lineTo(26.5f, 17.5f)
        lineTo(24.5f, 12f)
        lineTo(22.5f, 17.5f)
        lineTo(20f, 18f)
        close()
    }
    drawPath(bangs, Hair)
    drawPath(bangs, Ink, style = line)
    drawLine(HairShade, Offset(24f, 7.5f), Offset(22f, 13f), 0.8f, StrokeCap.Round)
    drawLine(HairShade, Offset(40f, 7.5f), Offset(42f, 13f), 0.8f, StrokeCap.Round)

    // Mata hijau yang selalu setengah mengantuk; terpejam saat tidur.
    val look = when (act) {
        FAct.READ -> Offset(0f, 0.9f)
        FAct.SPELL -> Offset(-0.6f, -0.3f)
        else -> Offset.Zero
    }
    val closed = act == FAct.NAP || eyeOpen < 0.35f
    for (x in listOf(26.3f, 37.7f)) {
        if (closed) {
            val shut = Path().apply { moveTo(x - 2.8f, 23.5f); quadraticTo(x, 25.3f, x + 2.8f, 23.5f) }
            drawPath(shut, Ink, style = Stroke(1.2f, cap = StrokeCap.Round))
            continue
        }
        val top = 21.6f
        val h = 4.8f * (if (act == FAct.STARE) 1.1f else 1f) * eyeOpen
        drawOval(Color.White, Offset(x - 2.7f, top), Size(5.4f, h))
        drawOval(Iris, Offset(x - 2.1f + look.x, top + 0.3f + look.y), Size(4.2f, (h - 0.6f).coerceAtLeast(0f)))
        drawOval(IrisDark, Offset(x - 1.1f + look.x, top + 1.2f + look.y), Size(2.2f, (h - 2.2f).coerceAtLeast(0f)))
        drawCircle(Color.White, 0.7f, Offset(x - 0.8f + look.x, top + 1.4f))
        // Kelopak atas yang berat: kesan malas khasnya.
        drawLine(Ink, Offset(x - 3.2f, top), Offset(x + 3.2f, top + 0.2f), 1.5f, StrokeCap.Round)
    }

    if (act == FAct.NAP) drawOval(Ink, Offset(31f, 28.4f), Size(2f, 1.6f))
    else drawLine(Ink, Offset(30.9f, 29f), Offset(33.1f, 29f), 0.9f, StrokeCap.Round)

    for (s in listOf(-1f, 1f)) drawCircle(Earring, 1f, Offset(32f + s * 13.8f, 27f))
}

/** Peti mimic yang menelan kepala dan bahu; giginya menggigit, petinya bergetar. */
private fun DrawScope.drawMimic(t: Float, line: Stroke) {
    val chomp = sin(t * 8 * PI).toFloat()
    drawMageSleeve(Offset(24f, 37f), Offset(17f, 40f + chomp * 2f))
    drawMageHand(Offset(16.5f, 40.5f + chomp * 2f))
    drawMageSleeve(Offset(40f, 37f), Offset(47f, 40f - chomp * 2f))
    drawMageHand(Offset(47.5f, 40.5f - chomp * 2f))
    translate(left = chomp * 0.8f) {
        drawRoundRect(Wood, Offset(15f, 12f), Size(34f, 24f), CornerRadius(3f))
        drawRoundRect(Ink, Offset(15f, 12f), Size(34f, 24f), CornerRadius(3f), style = line)
        drawLine(WoodDark, Offset(15.5f, 21f), Offset(48.5f, 21f), 1.2f)
        for (x in listOf(20f, 44f)) drawLine(Gold, Offset(x, 12.5f), Offset(x, 35.5f), 1.6f)
        drawRect(Gold, Offset(30.5f, 19f), Size(3f, 4f))
        drawRect(Ink, Offset(30.5f, 19f), Size(3f, 4f), style = Stroke(0.6f))
        // Gigi di bibir peti.
        var x = 17f
        while (x < 47f) {
            val tooth = Path().apply { moveTo(x, 35.5f); lineTo(x + 1.5f, 38f); lineTo(x + 3f, 35.5f); close() }
            drawPath(tooth, Color.White)
            drawPath(tooth, Ink, style = Stroke(0.5f))
            x += 3f
        }
    }
}

/** Bunga-bunga kecil yang tumbuh satu per satu dari tanah. */
private fun DrawScope.drawFlowers(t: Float) {
    for (i in 0 until 4) {
        val grow = (t * 1.8f - i * 0.18f).coerceIn(0f, 1f)
        if (grow <= 0f) continue
        val x = 6f + i * 5f
        val top = 60f - 5f * grow
        drawLine(Color(0xFF6DAF6A), Offset(x, 60f), Offset(x, top), 0.8f, StrokeCap.Round)
        val r = 1.4f * grow
        for (k in 0 until 4) {
            val a = k * PI / 2
            drawCircle(Petals[i], r, Offset(x + (kotlin.math.cos(a) * r).toFloat(), top + (sin(a) * r).toFloat()))
        }
        drawCircle(Gold, r * 0.6f, Offset(x, top))
    }
}

private fun DrawScope.drawSleepZ(ink: Color, t: Float) {
    for (i in 0 until 3) {
        val phase = (t * 2f + i / 3f) % 1f
        val w = 2.4f + i * 0.6f
        val x = 46f + i * 3f
        val y = 12f - i * 4f - phase * 3f
        val color = ink.copy(alpha = (1f - phase) * 0.8f)
        val z = Path().apply { moveTo(x, y); lineTo(x + w, y); lineTo(x, y + w); lineTo(x + w, y + w) }
        drawPath(z, color, style = Stroke(0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawMageNote(ink: Color, beat: Float) {
    val x = 6f + beat * 2f
    val y = 16f - beat * 10f
    val color = ink.copy(alpha = (1f - beat) * 0.7f)
    drawCircle(color, 2f, Offset(x, y))
    drawLine(color, Offset(x + 1.8f, y), Offset(x + 1.8f, y - 7f), 1.3f, StrokeCap.Round)
}
