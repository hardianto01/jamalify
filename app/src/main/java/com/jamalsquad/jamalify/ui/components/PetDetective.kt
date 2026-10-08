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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val Ink = Color(0xFF101014)
private val Hair = Color(0xFF24242C)
private val HairShine = Color(0xFF4C4C5C)
private val Skin = Color(0xFFFBF1EA)
private val EyeBag = Color(0xFFB6A2B4)
private val Shirt = Color(0xFFF4F4F7)
private val ShirtShade = Color(0xFFD6D8E0)
private val Jeans = Color(0xFF5877AD)
private val JeansDark = Color(0xFF3F5B8C)
private val Cream = Color(0xFFFFF4E4)
private val Sponge = Color(0xFFF6D9A0)
private val Strawberry = Color(0xFFE5394B)
private val Sugar = Color(0xFFFFFFFF)
private val Cup = Color(0xFFE9EEF5)
private val Tea = Color(0xFFB9763F)
private val Phone = Color(0xFF3A3D46)

/**
 * Aksi kecil L. Diam: jongkok dengan ibu jari di bibir (THINK), makan kue
 * stroberi (CAKE), menjatuhkan gula ke teh (SUGAR). Pose dialog: STARE
 * menatap dengan kepala miring, PHONE memegang ponsel di ujung jari.
 */
private enum class DAct { CROUCH, THINK, CAKE, SUGAR, STARE, PHONE }

private class DLine(val text: String, val act: DAct)

private val DetectiveSkits = listOf(
    listOf(DLine("Kemungkinan kamu Kira… 5%.", DAct.THINK)),
    listOf(DLine("Aku L.", DAct.STARE)),
    listOf(DLine("Lagu ini… mencurigakan.", DAct.PHONE)),
    listOf(DLine("Mau kue?", DAct.CAKE), DLine("…Nggak jadi, ini punyaku.", DAct.CAKE))
)

/**
 * Peliharaan chibi detektif: rambut hitam acak-acakan, mata bulat berkantung
 * hitam, kaus putih, jins, tanpa alas kaki — penghormatan untuk L dari Death
 * Note, digambar sendiri. Saat diam ia jongkok khasnya dan bergantian
 * berpikir, makan kue, atau menjatuhkan gula ke teh; berjalan dengan
 * punggung membungkuk dan tangan di saku. Diketuk, ia bicara lewat
 * [onSpeech] seperti [PetGenius].
 */
@Composable
fun PetDetective(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    walking: Boolean = false,
    facingRight: Boolean = false,
    onTap: () -> Unit = {},
    onSpeech: (String?) -> Unit = {}
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val loop = rememberInfiniteTransition(label = "detective")
    val step by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing)),
        label = "step"
    )
    val beat by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing)),
        label = "beat"
    )

    var act by remember { mutableStateOf(DAct.CROUCH) }
    val progress = remember { Animatable(0f) }
    val jump = remember { Animatable(0f) }
    val blink = remember { Animatable(1f) }
    var requested by remember { mutableStateOf<List<DLine>?>(null) }
    var taps by remember { mutableIntStateOf(0) }
    var lastSkit by remember { mutableStateOf<List<DLine>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            // L jarang berkedip; tatapannya memang terkenal kosong.
            delay(4500L + Random.nextLong(4000))
            blink.animateTo(0.1f, tween(80))
            blink.animateTo(1f, tween(130))
        }
    }

    LaunchedEffect(walking, taps) {
        if (walking) {
            act = DAct.CROUCH
            return@LaunchedEffect
        }
        requested?.let { skit ->
            try {
                for (line in skit) {
                    onSpeech(line.text)
                    playDetective(line.act, progress, speaking = line.text.length) { act = it }
                }
            } finally {
                onSpeech(null)
            }
            requested = null
        }
        while (true) {
            delay(1800L + Random.nextLong(2500))
            val next = listOf(DAct.THINK, DAct.CAKE, DAct.SUGAR, DAct.CROUCH).random()
            playDetective(next, progress) { act = it }
        }
    }

    Canvas(
        modifier = modifier
            .size(size)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onTap()
                requested = DetectiveSkits.filter { it !== lastSkit }.random().also { lastSkit = it }
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
                    drawDetective(act, progress.value, walking, stride, sway, blink.value)
                }
            }
            if (isPlaying && !walking) drawDetectiveNote(ink, beat)
        }
    }
}

private suspend fun playDetective(
    next: DAct,
    progress: Animatable<Float, *>,
    speaking: Int = 0,
    setAct: (DAct) -> Unit
) {
    setAct(next)
    progress.snapTo(0f)
    val duration = if (speaking > 0) 1200 + speaking * 55 else when (next) {
        DAct.CAKE, DAct.SUGAR -> 2800
        DAct.THINK -> 2400
        else -> 1800
    }
    progress.animateTo(1f, tween(duration, easing = LinearEasing))
    setAct(DAct.CROUCH)
    progress.snapTo(0f)
}

private fun DrawScope.drawDetective(
    act: DAct,
    t: Float,
    walking: Boolean,
    stride: Float,
    sway: Float,
    eyeOpen: Float
) {
    val line = Stroke(width = 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    if (walking) drawStanding(stride, line) else drawCrouching(act, t, line)

    // Kepala miring khas L; lebih miring saat menatap, condong saat berjalan.
    val tilt = when {
        walking -> 8f
        act == DAct.STARE -> 14f
        act == DAct.PHONE -> -8f
        else -> 5f
    } + sway * 5f
    rotate(tilt, pivot = Offset(32f, 36f)) { drawDetectiveHead(act, t, eyeOpen, line) }

    if (!walking) drawFrontHands(act, t)
}

/** Jongkok: lutut di depan dada, kaki telanjang, tangan bertumpu di lutut. */
private fun DrawScope.drawCrouching(act: DAct, t: Float, line: Stroke) {
    // Badan kaus putih, sebagian tertutup lutut.
    val body = Path().apply {
        moveTo(21.5f, 54f)
        lineTo(22.5f, 41f)
        quadraticTo(23.5f, 36.5f, 28f, 36f)
        lineTo(36f, 36f)
        quadraticTo(40.5f, 36.5f, 41.5f, 41f)
        lineTo(42.5f, 54f)
        close()
    }
    drawPath(body, Shirt)
    drawPath(body, Ink, style = line)
    drawLine(ShirtShade, Offset(28f, 37f), Offset(32f, 39f), 1f, StrokeCap.Round)
    drawLine(ShirtShade, Offset(36f, 37f), Offset(32f, 39f), 1f, StrokeCap.Round)

    // Tulang kering dan lutut berbalut jins.
    for (x in listOf(20.5f, 34.5f)) {
        drawRoundRect(Ink, Offset(x - 0.6f, 41.4f), Size(10.2f, 15.6f), CornerRadius(5f))
        drawRoundRect(Jeans, Offset(x, 42f), Size(9f, 14.5f), CornerRadius(4.5f))
        drawLine(JeansDark, Offset(x + 4.5f, 46f), Offset(x + 4.5f, 55f), 0.7f)
    }
    // Kaki telanjang.
    for (x in listOf(19f, 35f)) {
        drawOval(Skin, Offset(x, 55f), Size(10f, 4.4f))
        drawOval(Ink, Offset(x, 55f), Size(10f, 4.4f), style = Stroke(1f))
    }

    // Tangan yang bertumpu di lutut, kecuali yang sedang dipakai.
    val leftBusy = act == DAct.THINK || act == DAct.CAKE
    val rightBusy = act == DAct.CAKE || act == DAct.SUGAR || act == DAct.PHONE
    if (!leftBusy) {
        drawSleeve(Offset(23f, 40f), Offset(25f, 43f))
        drawDetectiveHand(Offset(25f, 43f))
    }
    if (!rightBusy) {
        drawSleeve(Offset(41f, 40f), Offset(39f, 43f))
        drawDetectiveHand(Offset(39f, 43f))
    }

    if (act == DAct.SUGAR) drawTeaCup(t)
}

/** Berdiri membungkuk dengan tangan di saku, kaki melangkah bergantian. */
private fun DrawScope.drawStanding(stride: Float, line: Stroke) {
    val leftLift = stride.coerceAtLeast(0f) * 3f
    val rightLift = (-stride).coerceAtLeast(0f) * 3f
    for ((x, up) in listOf(28f to leftLift, 36f to rightLift)) {
        drawLine(Ink, Offset(x, 48f), Offset(x, 57.5f - up), 6.2f, StrokeCap.Round)
        drawLine(Jeans, Offset(x, 48f), Offset(x, 57.5f - up), 4.6f, StrokeCap.Round)
        drawOval(Skin, Offset(x - 3.5f, 57f - up), Size(7f, 3.4f))
        drawOval(Ink, Offset(x - 3.5f, 57f - up), Size(7f, 3.4f), style = Stroke(0.9f))
    }
    val body = Path().apply {
        moveTo(23.5f, 51f)
        lineTo(23.5f, 41f)
        quadraticTo(24f, 36.5f, 28.5f, 36f)
        lineTo(35.5f, 36f)
        quadraticTo(40f, 36.5f, 40.5f, 41f)
        lineTo(40.5f, 51f)
        close()
    }
    drawPath(body, Shirt)
    drawPath(body, Ink, style = line)
    // Lengan lurus ke bawah, tangan masuk saku.
    drawSleeve(Offset(24f, 39f), Offset(24.5f, 48f))
    drawSleeve(Offset(40f, 39f), Offset(39.5f, 48f))
}

private fun DrawScope.drawSleeve(from: Offset, to: Offset) {
    drawLine(Ink, from, to, 5.2f, StrokeCap.Round)
    drawLine(Shirt, from, to, 3.7f, StrokeCap.Round)
}

private fun DrawScope.drawDetectiveHand(at: Offset, r: Float = 2.1f) {
    drawCircle(Skin, r, at)
    drawCircle(Ink, r, at, style = Stroke(0.9f))
}

/** Tangan dan benda yang berada di depan wajah, digambar setelah kepala. */
private fun DrawScope.drawFrontHands(act: DAct, t: Float) {
    when (act) {
        DAct.THINK -> {
            // Ibu jari menempel di bibir bawah, sesekali mengetuk.
            val tap = sin(t * 8 * PI).toFloat().coerceAtLeast(0f) * 0.8f
            drawSleeve(Offset(23f, 40f), Offset(28.5f, 34f + tap))
            drawDetectiveHand(Offset(29.5f, 33f + tap))
            drawLine(Skin, Offset(29.5f, 33f + tap), Offset(31f, 31f + tap), 1.6f, StrokeCap.Round)
        }
        DAct.CAKE -> {
            // Piring kue dijepit ujung jari; garpu bolak-balik ke mulut.
            drawSleeve(Offset(23f, 40f), Offset(18f, 42f))
            drawLine(Ink, Offset(10f, 41f), Offset(21f, 41f), 1.6f, StrokeCap.Round)
            val eaten = t > 0.55f
            val slice = Path().apply { moveTo(11.5f, 40.2f); lineTo(20f, 40.2f); lineTo(20f, 35f); close() }
            drawPath(slice, Cream)
            drawLine(Sponge, Offset(14f, 38.6f), Offset(20f, 38.6f), 1.2f)
            drawPath(slice, Ink, style = Stroke(0.8f, join = StrokeJoin.Round))
            if (!eaten) drawCircle(Strawberry, 1.6f, Offset(18.6f, 34f))
            drawDetectiveHand(Offset(18f, 42.2f), 1.7f)

            val bite = abs(sin(t * 3 * PI).toFloat())
            val hand = Offset(39f - bite * 5f, 42f - bite * 9f)
            drawSleeve(Offset(41f, 40f), hand)
            drawLine(Color(0xFF9EA4AE), hand, Offset(hand.x - 3.5f, hand.y - 3f), 1f, StrokeCap.Round)
            drawDetectiveHand(hand, 1.8f)
        }
        DAct.SUGAR -> {
            // Tangan menjepit gula tepat di atas cangkir.
            val cycle = (t * 3f) % 1f
            drawSleeve(Offset(41f, 40f), Offset(47f, 41f))
            drawDetectiveHand(Offset(48f, 41f), 1.7f)
            if (cycle < 0.3f) drawRect(Sugar, Offset(47f, 42.5f), Size(2.4f, 2.4f))
        }
        DAct.PHONE -> {
            // Ponsel dijepit di ujungnya, digantung di dekat telinga.
            drawSleeve(Offset(41f, 40f), Offset(46f, 27f))
            val swing = sin(t * 4 * PI).toFloat() * 4f
            rotate(swing, pivot = Offset(46f, 25.5f)) {
                drawRoundRect(Ink, Offset(43.5f, 25.5f), Size(5f, 9f), CornerRadius(1f))
                drawRoundRect(Phone, Offset(44.1f, 26.1f), Size(3.8f, 7.8f), CornerRadius(0.8f))
            }
            drawDetectiveHand(Offset(46f, 25f), 1.7f)
        }
        else -> Unit
    }
}

/** Cangkir teh di samping kaki; gula berjatuhan dan tumpukannya bertambah. */
private fun DrawScope.drawTeaCup(t: Float) {
    val cup = Path().apply {
        moveTo(44f, 50f); lineTo(53f, 50f); lineTo(52f, 57f)
        quadraticTo(51.5f, 58.5f, 50f, 58.5f); lineTo(47f, 58.5f)
        quadraticTo(45.5f, 58.5f, 45f, 57f); close()
    }
    drawPath(cup, Cup)
    drawPath(cup, Ink, style = Stroke(1f, join = StrokeJoin.Round))
    drawOval(Tea, Offset(44.6f, 49.4f), Size(7.8f, 1.8f))
    drawArc(Ink, -90f, 180f, false, Offset(51.5f, 52f), Size(3.5f, 3.5f), style = Stroke(1f))

    val cycle = (t * 3f) % 1f
    if (cycle in 0.3f..0.85f) {
        val y = 42.5f + (cycle - 0.3f) / 0.55f * 6f
        drawRect(Sugar, Offset(47f, y), Size(2.4f, 2.4f))
        drawRect(Ink, Offset(47f, y), Size(2.4f, 2.4f), style = Stroke(0.5f))
    }
    // Tumpukan gula yang menyembul dari cangkir, bertambah tiap jatuhan.
    val stacked = (t * 3f).toInt().coerceAtMost(3)
    for (i in 0 until stacked) {
        val o = Offset(45.5f + i * 2.2f, 47.6f - (i % 2) * 1.2f)
        drawRect(Sugar, o, Size(2.2f, 2.2f))
        drawRect(Ink, o, Size(2.2f, 2.2f), style = Stroke(0.5f))
    }
}

private fun DrawScope.drawDetectiveHead(act: DAct, t: Float, eyeOpen: Float, line: Stroke) {
    val cx = 32f
    val cy = 20f

    // Rambut belakang: gumpalan kusut yang menjuntai ke bawah, bukan duri melingkar.
    val back = Path().apply {
        moveTo(17f, 33f)
        lineTo(11.5f, 36f); lineTo(13.5f, 29f)
        lineTo(7f, 28.5f); lineTo(12f, 22f)
        lineTo(6.5f, 16f); lineTo(12.5f, 12.5f)
        lineTo(10f, 5.5f); lineTo(17.5f, 6f)
        lineTo(19.5f, 0.8f); lineTo(25.5f, 3f)
        lineTo(30f, 0.4f); lineTo(34f, 2.5f)
        lineTo(39f, 0.6f); lineTo(42f, 3.8f)
        lineTo(48f, 2f); lineTo(48f, 7.5f)
        lineTo(55.5f, 6.5f); lineTo(52f, 13f)
        lineTo(58f, 17f); lineTo(52.5f, 21.5f)
        lineTo(57.5f, 28.5f); lineTo(50.5f, 29f)
        lineTo(52.5f, 36f); lineTo(47f, 33f)
        lineTo(cx, cy + 14f)
        close()
    }
    drawPath(back, Hair)
    drawPath(back, Ink, style = line)

    // Wajah pucat.
    drawOval(Skin, Offset(17.5f, 9f), Size(29f, 26.5f))
    drawOval(Ink, Offset(17.5f, 9f), Size(29f, 26.5f), style = line)

    // Poni menggumpal tak rata sampai ke mata, jambang membingkai pipi.
    val bangs = Path().apply {
        moveTo(15f, 31f)
        cubicTo(11f, 8f, 23f, 2.5f, 32f, 2.5f)
        cubicTo(41f, 2.5f, 53f, 8f, 49f, 31f)
        lineTo(46.5f, 19f)
        lineTo(44.5f, 23.5f)
        lineTo(42f, 13.5f)
        lineTo(38.5f, 21.5f)
        lineTo(36.5f, 13f)
        lineTo(33.5f, 20f)
        lineTo(30.5f, 12f)
        lineTo(27.5f, 21.5f)
        lineTo(25f, 14f)
        lineTo(21f, 23f)
        lineTo(19.5f, 17f)
        close()
    }
    drawPath(bangs, Hair)
    drawPath(bangs, Ink, style = line)
    // Garis helai dan kilap tipis supaya gumpalannya terbaca.
    for ((from, to) in listOf(
        Offset(24f, 7f) to Offset(22.5f, 14f),
        Offset(32f, 5.5f) to Offset(31.5f, 12f),
        Offset(40f, 7f) to Offset(41f, 13f)
    )) drawLine(HairShine, from, to, 0.9f, StrokeCap.Round)
    val shine = Path().apply { moveTo(20.5f, 8f); quadraticTo(30f, 4f, 41f, 7f) }
    drawPath(shine, HairShine, style = Stroke(1.6f, cap = StrokeCap.Round))

    // Mata bulat besar dengan kantung mata hitam yang khas.
    val wide = if (act == DAct.STARE) 1.18f else 1f
    val look = when (act) {
        DAct.PHONE -> Offset(1.2f, -0.3f)
        DAct.CAKE, DAct.SUGAR -> Offset(-0.6f, 0.8f)
        else -> Offset.Zero
    }
    for (x in listOf(25.5f, 38.5f)) {
        val r = 3.6f * wide
        val h = r * 2 * eyeOpen
        // Kantung mata dua lapis.
        val bag = Path().apply { moveTo(x - 3.8f, 26.6f); quadraticTo(x, 29.4f, x + 3.8f, 26.6f) }
        drawPath(bag, EyeBag, style = Stroke(1.3f, cap = StrokeCap.Round))
        val bag2 = Path().apply { moveTo(x - 2.8f, 28f); quadraticTo(x, 30f, x + 2.8f, 28f) }
        drawPath(bag2, EyeBag.copy(alpha = 0.5f), style = Stroke(0.9f, cap = StrokeCap.Round))
        if (eyeOpen < 0.35f) {
            drawLine(Ink, Offset(x - 3.2f, 23f), Offset(x + 3.2f, 23f), 1.4f, StrokeCap.Round)
            continue
        }
        drawOval(Color.White, Offset(x - r, 23f - h / 2), Size(r * 2, h))
        drawOval(Ink, Offset(x - r, 23f - h / 2), Size(r * 2, h), style = Stroke(1.1f))
        val ir = 2.5f * wide
        drawOval(Ink, Offset(x - ir + look.x, 23f - ir * eyeOpen + look.y), Size(ir * 2, ir * 2 * eyeOpen))
        drawCircle(Color.White, 0.8f, Offset(x - 0.9f + look.x, 22f + look.y))
    }

    // Mulut: garis datar; terbuka saat makan.
    val chewing = act == DAct.CAKE && abs(sin(t * 3 * PI).toFloat()) > 0.75f
    if (chewing) {
        drawOval(Ink, Offset(30.6f, 29.8f), Size(2.8f, 2f))
    } else {
        drawLine(Ink, Offset(30.8f, 31f), Offset(33.4f, 30.8f), 1.1f, StrokeCap.Round)
    }
}

private fun DrawScope.drawDetectiveNote(ink: Color, beat: Float) {
    val x = 6f + beat * 2f
    val y = 16f - beat * 10f
    val color = ink.copy(alpha = (1f - beat) * 0.7f)
    drawCircle(color, 2f, Offset(x, y))
    drawLine(color, Offset(x + 1.8f, y), Offset(x + 1.8f, y - 7f), 1.3f, StrokeCap.Round)
}
