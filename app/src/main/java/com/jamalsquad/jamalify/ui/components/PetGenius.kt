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

private val Outline = Color(0xFF3A2620)
private val Skin = Color(0xFFFFE4D2)
private val SkinShade = Color(0xFFF3C9B0)
private val Hair = Color(0xFFC8773E)
private val HairLight = Color(0xFFE6995C)
private val HairDark = Color(0xFF94532A)
private val Iris = Color(0xFF7B4526)
private val Blazer = Color(0xFFEBCFA6)
private val BlazerDark = Color(0xFFCBA87C)
private val Shirt = Color(0xFFFAFAFA)
private val Tie = Color(0xFFC62828)
private val Pants = Color(0xFFB9A37F)
private val Shoe = Color(0xFF5A3A29)
private val Book = Color(0xFF4A2F27)
private val Page = Color(0xFFF1E6DA)
private val Pen = Color(0xFFF2F2F2)
private val Chip = Color(0xFFF2C14E)
private val Glint = Color(0xFFE53935)

/**
 * Aksi kecil yang dilakukan bergantian saat tidak berjalan. Sisanya pose
 * khusus untuk dialog saat diketuk: SCHEME menulis dengan wajah licik,
 * BOOYAH merayakan kemenangan, CONFUSED bingung, PROUD bangga.
 */
private enum class Act { READ, WRITE, CHIP, SMIRK, SCHEME, BOOYAH, CONFUSED, PROUD }

private class Line(val text: String, val act: Act)

/** Dialog meme saat diketuk; tiap skit bisa lebih dari satu balon. */
private val Skits = listOf(
    listOf(Line("Near, aku booyah!", Act.BOOYAH)),
    listOf(Line("Biar kutulis namamu di Death Note-ku…", Act.SCHEME)),
    listOf(Line("Namaku Kira.", Act.SMIRK)),
    listOf(
        Line("Kenapa ada aku di sini?", Act.CONFUSED),
        Line("Karena creator app ini, char favoritnya Light Yagami!", Act.PROUD)
    )
)

/**
 * Peliharaan chibi bergaya figur: kepala besar, rambut oranye-cokelat yang
 * bervolume, blazer krem, dasi merah, pena di satu tangan dan buku catatan
 * terbuka di tangan lain — penghormatan untuk Light Yagami, digambar sendiri.
 * Saat berjalan ia tetap membawa buku; saat diam ia bergantian membaca,
 * menulis, makan keripik, atau tersenyum licik dengan mata berkilat.
 * Diketuk, ia berpose meme sambil bicara lewat [onSpeech]; balonnya
 * digambar pemanggil supaya bisa dijaga tetap di dalam layar.
 */
@Composable
fun PetGenius(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    walking: Boolean = false,
    facingRight: Boolean = false,
    onTap: () -> Unit = {},
    onSpeech: (String?) -> Unit = {}
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val loop = rememberInfiniteTransition(label = "genius")
    val step by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(460, easing = LinearEasing)),
        label = "step"
    )
    val beat by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing)),
        label = "beat"
    )

    var act by remember { mutableStateOf(Act.READ) }
    val progress = remember { Animatable(0f) }
    val jump = remember { Animatable(0f) }
    val blink = remember { Animatable(1f) }
    var requested by remember { mutableStateOf<List<Line>?>(null) }
    var taps by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var lastSkit by remember { mutableStateOf<List<Line>?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2500L + Random.nextLong(3000))
            blink.animateTo(0.1f, tween(70))
            blink.animateTo(1f, tween(110))
        }
    }

    // Aksi dijalankan satu per satu; berjalan selalu kembali ke membaca.
    LaunchedEffect(walking, taps) {
        if (walking) {
            act = Act.READ
            return@LaunchedEffect
        }
        // Skit baru dihapus setelah selesai, jadi kalau efek ini sempat
        // dimulai ulang (misalnya karena berhenti berjalan) skitnya diulang.
        requested?.let { skit ->
            try {
                for (line in skit) {
                    onSpeech(line.text)
                    play(line.act, progress, speaking = line.text.length) { a -> act = a }
                }
            } finally {
                onSpeech(null)
            }
            requested = null
        }
        while (true) {
            delay(1800L + Random.nextLong(2500))
            val next = listOf(Act.WRITE, Act.CHIP, Act.SMIRK, Act.READ).random()
            play(next, progress) { a -> act = a }
        }
    }

    Canvas(
        modifier = modifier
            .size(size)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onTap()
                // Skit berikutnya diacak tapi tidak mengulang yang barusan.
                requested = Skits.filter { it !== lastSkit }.random().also { lastSkit = it }
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
            val bob = if (isPlaying && !walking) sin(beat * 2 * PI).toFloat() * 1.4f else 0f
            val lift = -jump.value * 7f - abs(stride) * 1.5f
            scale(if (facingRight) -1f else 1f, 1f, pivot = Offset(32f, 32f)) {
                translate(top = lift) {
                    drawGenius(act, progress.value, stride, bob, blink.value)
                }
            }
            // Tanda tanya di luar pencerminan supaya tidak terbalik.
            if (act == Act.CONFUSED) {
                translate(top = lift) { drawQuestionMark(sin(progress.value * PI).toFloat()) }
            }
            if (isPlaying && !walking) drawFloatingNote(ink, beat)
        }
    }
}

private suspend fun play(
    next: Act,
    progress: Animatable<Float, *>,
    speaking: Int = 0,
    setAct: (Act) -> Unit
) {
    setAct(next)
    progress.snapTo(0f)
    // Saat bicara, lamanya pose mengikuti panjang kalimat supaya sempat dibaca.
    val duration = if (speaking > 0) 1200 + speaking * 55 else when (next) {
        Act.READ -> 2200
        Act.WRITE, Act.SCHEME -> 2600
        Act.CHIP -> 1800
        else -> 1600
    }
    progress.animateTo(1f, tween(duration, easing = LinearEasing))
    setAct(Act.READ)
    progress.snapTo(0f)
}

private fun DrawScope.drawGenius(act: Act, t: Float, stride: Float, bob: Float, eyeOpen: Float) {
    val line = Stroke(width = 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Kaki celana khaki dan sepatu cokelat, bergantian terangkat saat berjalan.
    val leftLift = stride.coerceAtLeast(0f) * 3f
    val rightLift = (-stride).coerceAtLeast(0f) * 3f
    for ((x, up) in listOf(28.8f to leftLift, 35.2f to rightLift)) {
        drawLine(Outline, Offset(x, 50f), Offset(x, 58.5f - up), 6f, StrokeCap.Round)
        drawLine(Pants, Offset(x, 50f), Offset(x, 58.5f - up), 4.4f, StrokeCap.Round)
        drawOval(Shoe, Offset(x - 3.2f, 58f - up), Size(6.4f, 3.4f))
    }

    // Blazer krem dengan kerah, kemeja putih, dan dasi merah.
    val body = Path().apply {
        moveTo(23.5f, 53f)
        lineTo(24f, 44f)
        quadraticTo(24.5f, 39.5f, 28.5f, 39f)
        lineTo(35.5f, 39f)
        quadraticTo(39.5f, 39.5f, 40f, 44f)
        lineTo(40.5f, 53f)
        close()
    }
    drawPath(body, Blazer)
    drawPath(body, Outline, style = line)
    val shirt = Path().apply { moveTo(28.6f, 39.1f); lineTo(32f, 48f); lineTo(35.4f, 39.1f); close() }
    drawPath(shirt, Shirt)
    drawLine(BlazerDark, Offset(28.6f, 39.3f), Offset(31.4f, 48.5f), 1f, StrokeCap.Round)
    drawLine(BlazerDark, Offset(35.4f, 39.3f), Offset(32.6f, 48.5f), 1f, StrokeCap.Round)
    val tie = Path().apply {
        moveTo(31.2f, 40f); lineTo(32.8f, 40f); lineTo(33.3f, 46.5f); lineTo(32f, 48f); lineTo(30.7f, 46.5f); close()
    }
    drawPath(tie, Tie)
    drawLine(BlazerDark, Offset(32f, 49f), Offset(32f, 53f), 0.8f)

    // Pose dasar seperti figur: pena terangkat di kiri, buku terbuka di kanan.
    when (act) {
        Act.BOOYAH -> {
            // Kedua tangan terangkat merayakan kemenangan, buku diacungkan.
            val wave = sin(t * 6 * PI).toFloat() * 1.5f
            drawArm(Offset(25f, 42f), Offset(17f, 31f + wave))
            drawArm(Offset(39f, 42f), Offset(47f, 31f - wave))
            rotate(20f, pivot = Offset(49f, 28f)) {
                drawRoundRect(Outline, Offset(44f, 22f), Size(10f, 12f), CornerRadius(1f))
                drawRoundRect(Book, Offset(44.7f, 22.7f), Size(8.6f, 10.6f), CornerRadius(0.8f))
            }
            drawHand(Offset(47f, 31f - wave))
            drawPen(Offset(16f, 32f + wave), angle = -25f)
            drawHand(Offset(17f, 31f + wave))
        }
        Act.CONFUSED -> {
            // Satu tangan menggaruk kepala, buku terkulai di tangan lain.
            val scratch = sin(t * 10 * PI).toFloat() * 1.2f
            drawSideBook(tilt = 0f)
            drawArm(Offset(25f, 42f), Offset(15f + scratch, 22f))
            drawHand(Offset(15f + scratch, 22f))
        }
        Act.PROUD -> {
            // Buku didekap di dada, tangan satunya menunjuk diri sendiri.
            drawArm(Offset(25f, 42f), Offset(28f, 46f))
            drawArm(Offset(39f, 42f), Offset(35f, 46f))
            drawRoundRect(Outline, Offset(26f, 41f), Size(11f, 13f), CornerRadius(1.2f))
            drawRoundRect(Book, Offset(26.7f, 41.7f), Size(9.6f, 11.6f), CornerRadius(1f))
            drawHand(Offset(28f, 46f))
            drawHand(Offset(35f, 46f))
        }
        Act.WRITE, Act.SCHEME -> {
            drawFrontBook(written = t)
            val px = 33.5f + sin(t * 20f).toFloat() * 2.5f
            val py = 45f + t * 2.5f
            drawArm(Offset(25f, 42f), Offset(26f, 48f))
            drawArm(Offset(39f, 42f), Offset(px + 2f, py - 2f))
            drawPen(Offset(px, py), angle = 30f)
            drawHand(Offset(px + 2f, py - 2f))
        }
        Act.CHIP -> {
            drawSideBook(tilt = 0f)
            val reach = if (t < 0.5f) t / 0.5f else (1f - t) / 0.5f
            val hand = Offset(19f + reach * 9f, 44f - reach * 8f)
            drawArm(Offset(25f, 42f), hand)
            if (t < 0.5f) {
                drawOval(Chip, Offset(hand.x - 2.5f, hand.y - 5.5f), Size(5.5f, 4f))
                drawOval(Outline, Offset(hand.x - 2.5f, hand.y - 5.5f), Size(5.5f, 4f), style = Stroke(0.6f))
            }
            drawHand(hand)
        }
        else -> {
            val raise = if (act == Act.SMIRK) sin(t * PI).toFloat() * 3f else 0f
            drawSideBook(tilt = if (act == Act.READ && t in 0.45f..0.6f) (t - 0.45f) / 0.15f else 0f)
            val hand = Offset(18.5f, 44f - raise)
            drawArm(Offset(25f, 42f), hand)
            drawPen(Offset(hand.x - 1f, hand.y + 2f), angle = -15f)
            drawHand(hand)
        }
    }

    // Kepala besar ala figur chibi, menoleh ke buku saat membaca.
    val headTilt = when (act) {
        Act.SMIRK -> -5f
        Act.WRITE, Act.SCHEME -> 6f
        Act.BOOYAH -> -8f + sin(t * 6 * PI).toFloat() * 3f
        Act.CONFUSED -> 12f
        Act.PROUD -> -3f
        else -> 3f
    }
    translate(top = bob) {
        rotate(headTilt, pivot = Offset(32f, 38f)) { drawHead(act, t, eyeOpen, line) }
    }
    if (act == Act.CHIP && t in 0.45f..0.8f) drawCrumbs(t)
    if (act == Act.BOOYAH) {
        val a = sin(t * PI).toFloat()
        drawSparkle(Offset(10f, 16f), 3.5f * a)
        drawSparkle(Offset(55f, 18f), 3f * a)
    }
}

private fun DrawScope.drawArm(shoulder: Offset, hand: Offset) {
    drawLine(Outline, shoulder, hand, 5.4f, StrokeCap.Round)
    drawLine(Blazer, shoulder, hand, 3.8f, StrokeCap.Round)
}

private fun DrawScope.drawHand(at: Offset) {
    drawCircle(Skin, 2.2f, at)
    drawCircle(Outline, 2.2f, at, style = Stroke(0.9f))
}

private fun DrawScope.drawPen(grip: Offset, angle: Float) {
    rotate(angle, pivot = grip) {
        drawLine(Outline, Offset(grip.x, grip.y + 2.5f), Offset(grip.x, grip.y - 8f), 2.4f, StrokeCap.Round)
        drawLine(Pen, Offset(grip.x, grip.y + 2.5f), Offset(grip.x, grip.y - 8f), 1.4f, StrokeCap.Round)
        drawLine(Outline, Offset(grip.x, grip.y - 8f), Offset(grip.x, grip.y - 5.5f), 1.4f, StrokeCap.Round)
    }
}

/** Buku terbuka dipegang di samping, seperti pose figur acuan. */
private fun DrawScope.drawSideBook(tilt: Float) {
    drawArm(Offset(39f, 42f), Offset(44.5f, 46f))
    rotate(-14f, pivot = Offset(48f, 43f)) {
        drawRoundRect(Outline, Offset(40.5f, 38.5f), Size(15f, 9f), CornerRadius(1.2f))
        drawRoundRect(Book, Offset(41.2f, 39.2f), Size(13.6f, 7.6f), CornerRadius(1f))
        drawLine(Page.copy(alpha = 0.35f), Offset(48f, 39.2f), Offset(48f, 46.8f), 0.7f)
        if (tilt > 0f) {
            val w = 6.5f * (1f - 2 * abs(tilt - 0.5f))
            drawRect(Page, Offset(48f, 39.6f), Size(w, 6.8f))
        }
    }
    drawHand(Offset(44.5f, 46f))
}

/** Buku dibuka di depan dada untuk ditulisi; garis tulisan bertambah. */
private fun DrawScope.drawFrontBook(written: Float) {
    drawRoundRect(Outline, Offset(22.5f, 42f), Size(19f, 10.5f), CornerRadius(1.2f))
    drawRoundRect(Page, Offset(23.3f, 42.8f), Size(8.3f, 8.9f), CornerRadius(0.8f))
    drawRoundRect(Page, Offset(32.4f, 42.8f), Size(8.3f, 8.9f), CornerRadius(0.8f))
    for (i in 0 until 3) {
        drawLine(Outline.copy(alpha = 0.35f), Offset(24.5f, 45f + i * 2.2f), Offset(30.5f, 45f + i * 2.2f), 0.6f)
    }
    val lines = (written * 4).toInt().coerceAtMost(3)
    for (i in 0 until lines) {
        drawLine(Outline.copy(alpha = 0.6f), Offset(33.5f, 45f + i * 2.2f), Offset(39.5f, 45f + i * 2.2f), 0.6f)
    }
}

private fun DrawScope.drawHead(act: Act, t: Float, eyeOpen: Float, line: Stroke) {
    // Rambut belakang yang bervolume, melebihi lebar wajah.
    val back = Path().apply {
        moveTo(13f, 32f)
        cubicTo(8f, 12f, 20f, 1.5f, 32f, 1.5f)
        cubicTo(44f, 1.5f, 56f, 12f, 51f, 32f)
        lineTo(47f, 36f)
        lineTo(17f, 36f)
        close()
    }
    drawPath(back, HairDark)
    drawPath(back, Outline, style = line)

    // Wajah.
    drawOval(Skin, Offset(16.5f, 11f), Size(31f, 28.5f))
    drawOval(Outline, Offset(16.5f, 11f), Size(31f, 28.5f), style = line)

    // Rambut depan: poni tebal yang menyapu ke kanan, ujungnya runcing.
    val front = Path().apply {
        moveTo(14f, 27f)
        cubicTo(11f, 9f, 22f, 2.5f, 33f, 2.5f)
        cubicTo(45f, 2.5f, 54f, 11f, 50.5f, 28f)
        lineTo(49f, 34f)
        lineTo(46.5f, 24f)
        lineTo(44f, 29.5f)
        lineTo(40.5f, 19f)
        lineTo(37f, 24.5f)
        lineTo(33.5f, 16.5f)
        lineTo(29.5f, 22.5f)
        lineTo(27f, 16.5f)
        lineTo(23f, 23f)
        lineTo(21f, 18.5f)
        lineTo(17.5f, 31f)
        lineTo(15.5f, 34f)
        close()
    }
    drawPath(front, Hair)
    drawPath(front, Outline, style = line)
    // Helai rambut dan kilau.
    val strands = Path().apply {
        moveTo(24f, 6f); quadraticTo(22f, 12f, 23f, 20f)
        moveTo(31f, 4f); quadraticTo(31f, 10f, 29f, 19f)
        moveTo(38f, 5f); quadraticTo(40f, 11f, 40f, 17f)
        moveTo(45f, 9f); quadraticTo(47f, 16f, 46.5f, 22f)
        moveTo(18f, 14f); quadraticTo(16.5f, 20f, 17.5f, 27f)
    }
    drawPath(strands, HairDark, style = Stroke(0.8f, cap = StrokeCap.Round))
    val shine = Path().apply { moveTo(20f, 9.5f); quadraticTo(30f, 4.5f, 41f, 8f) }
    drawPath(shine, HairLight, style = Stroke(2f, cap = StrokeCap.Round))

    when (act) {
        Act.BOOYAH -> {
            // Mata terpejam gembira "> <" dan mulut tertawa lebar.
            for ((x, dir) in listOf(25f to 1f, 39f to -1f)) {
                val eye = Path().apply {
                    moveTo(x - 3f * dir, 27f); lineTo(x + 2.5f * dir, 29.3f); lineTo(x - 3f * dir, 31.5f)
                }
                drawPath(eye, Outline, style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val mouth = Path().apply {
                moveTo(28.5f, 33.5f); lineTo(35.5f, 33.5f); quadraticTo(32f, 39.5f, 28.5f, 33.5f); close()
            }
            drawPath(mouth, Outline)
            drawOval(Glint.copy(alpha = 0.7f), Offset(30.5f, 35.6f), Size(3f, 1.6f))
        }
        Act.CONFUSED -> {
            drawEyes(eyeOpen, look = Offset(-1f, -0.6f))
            drawOval(Outline, Offset(31f, 34f), Size(2.4f, 2.4f), style = Stroke(1f))
            // Tetes keringat bingung.
            val drop = Path().apply {
                moveTo(47f, 20f); quadraticTo(49.5f, 24f, 47f, 25.5f); quadraticTo(44.5f, 24f, 47f, 20f); close()
            }
            drawPath(drop, Color(0xFF8EC9FF))
        }
        Act.PROUD -> {
            // Mata senyum "^ ^".
            for (x in listOf(25f, 39f)) {
                val arc = Path().apply { moveTo(x - 3f, 30f); quadraticTo(x, 26.5f, x + 3f, 30f) }
                drawPath(arc, Outline, style = Stroke(1.5f, cap = StrokeCap.Round))
            }
            val smile = Path().apply { moveTo(29.5f, 33.8f); quadraticTo(32f, 36.8f, 34.5f, 33.8f) }
            drawPath(smile, Outline, style = Stroke(1.2f, cap = StrokeCap.Round))
        }
        Act.SMIRK, Act.SCHEME -> {
            // Mata menyipit licik dengan kilatan merah.
            val glow = sin(t * PI).toFloat()
            for ((x, dir) in listOf(25f to 1f, 39f to -1f)) {
                drawLine(Outline, Offset(x - 3.5f, 28.5f - dir * 1f), Offset(x + 3.5f, 28.5f + dir * 1f), 1.6f, StrokeCap.Round)
                drawOval(Iris, Offset(x - 2.2f, 28.8f), Size(4.4f, 1.8f))
                drawCircle(Glint.copy(alpha = glow), 1f, Offset(x, 29.6f))
            }
            val smirk = Path().apply { moveTo(29.5f, 35f); quadraticTo(33f, 36.4f, 35.5f, 33.6f) }
            drawPath(smirk, Outline, style = Stroke(1.1f, cap = StrokeCap.Round))
            if (act == Act.SMIRK) drawSparkle(Offset(53f, 8f), 4f * glow)
        }
        Act.CHIP -> {
            drawEyes(eyeOpen, look = Offset(0f, 0f))
            val open = if (t in 0.4f..0.8f) 1.6f + sin(t * 40f).toFloat() * 0.7f else 0.5f
            drawOval(Outline, Offset(30.6f, 34f), Size(2.8f, open))
        }
        else -> {
            drawEyes(eyeOpen, look = if (act == Act.WRITE) Offset(0f, 1f) else Offset(1f, 0.4f))
            val smile = Path().apply { moveTo(30.5f, 34.5f); quadraticTo(32f, 35.6f, 33.5f, 34.5f) }
            drawPath(smile, Outline, style = Stroke(1f, cap = StrokeCap.Round))
        }
    }
    drawOval(SkinShade, Offset(19.5f, 31f), Size(5f, 2.4f))
    drawOval(SkinShade, Offset(39.5f, 31f), Size(5f, 2.4f))
}

/** Mata anime besar: iris cokelat, pupil, dua titik kilau, dan garis bulu mata tebal. */
private fun DrawScope.drawEyes(eyeOpen: Float, look: Offset) {
    for (x in listOf(25f, 39f)) {
        val h = 7.5f * eyeOpen
        val top = 29.5f - h / 2
        if (eyeOpen < 0.35f) {
            drawLine(Outline, Offset(x - 3f, 29.5f), Offset(x + 3f, 29.5f), 1.4f, StrokeCap.Round)
            continue
        }
        drawOval(Color.White, Offset(x - 3.2f, top), Size(6.4f, h))
        drawOval(Iris, Offset(x - 2.6f + look.x, top + 0.6f + look.y), Size(5.2f, h - 0.8f))
        drawOval(Outline, Offset(x - 1.3f + look.x, top + 2.2f + look.y), Size(2.6f, h - 3.6f))
        drawCircle(Color.White, 1.1f, Offset(x - 0.9f + look.x, top + 2.2f + look.y))
        drawCircle(Color.White, 0.5f, Offset(x + 1.2f + look.x, top + h - 2f + look.y))
        // Bulu mata atas, sedikit melebar ke luar.
        val lash = Path().apply {
            moveTo(x - 3.8f, top + 1.2f)
            quadraticTo(x, top - 1.2f, x + 3.8f, top + 1.2f)
        }
        drawPath(lash, Outline, style = Stroke(1.6f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawQuestionMark(alpha: Float) {
    val color = Outline.copy(alpha = alpha.coerceIn(0f, 1f))
    val q = Path().apply {
        moveTo(50f, 6f)
        quadraticTo(50f, 2f, 54f, 2f)
        quadraticTo(58f, 2f, 58f, 5.5f)
        quadraticTo(58f, 8f, 54.5f, 9.5f)
        lineTo(54.5f, 12f)
    }
    drawPath(q, color, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(color, 1.1f, Offset(54.5f, 15f))
}

private fun DrawScope.drawCrumbs(t: Float) {
    val k = (t - 0.45f) / 0.35f
    val alpha = 1f - k
    drawCircle(Chip.copy(alpha = alpha), 0.9f, Offset(28f - k * 4f, 34f + k * 6f))
    drawCircle(Chip.copy(alpha = alpha), 0.7f, Offset(36f + k * 4f, 34f + k * 7f))
    drawCircle(Chip.copy(alpha = alpha), 0.6f, Offset(32f, 35f + k * 9f))
}

private fun DrawScope.drawSparkle(at: Offset, r: Float) {
    if (r <= 0.2f) return
    val path = Path().apply {
        moveTo(at.x, at.y - r)
        quadraticTo(at.x, at.y, at.x + r, at.y)
        quadraticTo(at.x, at.y, at.x, at.y + r)
        quadraticTo(at.x, at.y, at.x - r, at.y)
        quadraticTo(at.x, at.y, at.x, at.y - r)
        close()
    }
    drawPath(path, Glint)
}

private fun DrawScope.drawFloatingNote(ink: Color, beat: Float) {
    val x = 6f + beat * 2f
    val y = 16f - beat * 10f
    val color = ink.copy(alpha = (1f - beat) * 0.7f)
    drawCircle(color, 2f, Offset(x, y))
    drawLine(color, Offset(x + 1.8f, y), Offset(x + 1.8f, y - 7f), 1.3f, StrokeCap.Round)
}
