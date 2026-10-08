package com.jamalsquad.jamalify.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Peliharaan yang gentayangan bebas di seluruh area [modifier], di atas
 * komponen lain. Bergantian diam dan berjalan ke titik acak; saat musik
 * diputar lebih sering diam bergoyang. Bisa diseret, dan setelah diketuk atau
 * diseret ia diam dulu sebentar. Saat lagu berganti ia mengomentari
 * [songTitle]; pada [PetKind.DUO] Light dan L sesekali saling menghampiri
 * dan mengobrol.
 *
 * Hanya badan peliharaannya yang menerima sentuhan, jadi daftar di bawahnya
 * tetap bisa digulir dan diketuk seperti biasa.
 */
@Composable
fun PetRoam(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    kind: PetKind = PetKind.CAT,
    size: Dp = 52.dp,
    songTitle: String? = null
) = PetStage(isPlaying, modifier, kind, size, songTitle, roam = true)

/**
 * Peliharaan yang duduk diam di pojok kanan bawah [modifier], misalnya di
 * atas sampul lagu di layar Player. Tetap bisa diketuk dan tetap
 * mengomentari lagu, tapi tidak berjalan dan tidak bisa diseret.
 */
@Composable
fun PetPerch(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    kind: PetKind = PetKind.CAT,
    size: Dp = 52.dp,
    songTitle: String? = null
) = PetStage(isPlaying, modifier, kind, size, songTitle, roam = false)

@Stable
private class PetActor(val kind: PetKind) {
    val position = Animatable(Offset.Zero, Offset.VectorConverter)
    var walking by mutableStateOf(false)
    var facingRight by mutableStateOf(false)
    var pokes by mutableIntStateOf(0)
    var speech by mutableStateOf<String?>(null)
    /** Sedang menjalankan adegan duo; jalan-jalan acaknya berhenti dulu. */
    var scripted by mutableStateOf(false)

    suspend fun say(text: String) {
        speech = text
        delay(1400L + text.length * 55L)
        speech = null
    }
}

@Composable
private fun PetStage(
    isPlaying: Boolean,
    modifier: Modifier,
    kind: PetKind,
    size: Dp,
    songTitle: String?,
    roam: Boolean
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val sizePx = with(density) { size.toPx() }
        val maxX = (constraints.maxWidth - sizePx).coerceAtLeast(0f)
        val maxY = (constraints.maxHeight - sizePx).coerceAtLeast(0f)
        val pxPerSec = with(density) { 44.dp.toPx() }
        val actors = remember(kind) {
            val cast = if (kind == PetKind.DUO) listOf(PetKind.GENIUS, PetKind.DETECTIVE) else listOf(kind)
            cast.map { PetActor(it) }
        }
        val scope = rememberCoroutineScope()

        // Posisi awal: berdampingan di kanan atas saat gentayangan, di pojok
        // kanan bawah saat duduk. Yang kedua menghadap yang pertama.
        LaunchedEffect(actors, maxX, maxY) {
            actors.forEachIndexed { i, actor ->
                val x = (maxX - i * sizePx * 0.85f).coerceAtLeast(0f)
                val start = if (roam) Offset(x * 0.8f, 0f) else Offset(x, maxY)
                if (!roam || actor.position.value == Offset.Zero) actor.position.snapTo(start)
                if (i > 0) actor.facingRight = true
            }
        }

        if (roam) actors.forEach { actor ->
            val speaking = actor.speech != null
            LaunchedEffect(actor, maxX, maxY, isPlaying, actor.pokes, speaking, actor.scripted) {
                if (actor.scripted) return@LaunchedEffect
                actor.walking = false
                val now = actor.position.value
                actor.position.snapTo(Offset(now.x.coerceIn(0f, maxX), now.y.coerceIn(0f, maxY)))
                // Diam selama bicara, supaya balonnya tidak ikut berlari.
                if (speaking) return@LaunchedEffect
                if (actor.pokes > 0) delay(2000)
                while (true) {
                    delay(if (isPlaying) 4000L + Random.nextLong(4000) else 1200L + Random.nextLong(2800))
                    val from = actor.position.value
                    // Langkah pendek-pendek ke sekitar, bukan melompat ke ujung layar.
                    val reach = maxOf(maxX, maxY) * 0.45f
                    val target = Offset(
                        (from.x + (Random.nextFloat() * 2 - 1) * reach).coerceIn(0f, maxX),
                        (from.y + (Random.nextFloat() * 2 - 1) * reach).coerceIn(0f, maxY)
                    )
                    actor.walkTo(target, pxPerSec)
                }
            }
        }

        // Adegan duo: L menghampiri Light, saling menghadap, lalu mengobrol.
        if (roam && actors.size == 2) LaunchedEffect(actors, maxX, maxY) {
            val (light, l) = actors
            while (true) {
                delay(25_000L + Random.nextLong(20_000))
                if (actors.any { it.speech != null }) continue
                actors.forEach { it.scripted = true }
                try {
                    // Beri waktu jalan-jalan acaknya berhenti dulu.
                    delay(300)
                    val base = light.position.value
                    val gap = sizePx * 1.05f
                    val side = if (base.x + gap <= maxX) gap else -gap
                    l.walkTo(Offset((base.x + side).coerceIn(0f, maxX), base.y), pxPerSec)
                    light.facingRight = l.position.value.x > light.position.value.x
                    l.facingRight = !light.facingRight
                    delay(400)
                    for ((who, line) in DuoDialogues.random()) {
                        (if (who == 0) light else l).say(line)
                        delay(250)
                    }
                } finally {
                    actors.forEach { it.scripted = false; it.speech = null }
                }
            }
        }

        // Komentar saat lagu berganti; lagu yang sudah diputar saat layar
        // dibuka tidak dikomentari.
        var lastTitle by remember { mutableStateOf(songTitle) }
        LaunchedEffect(songTitle) {
            if (songTitle == null || songTitle == lastTitle) {
                lastTitle = songTitle
                return@LaunchedEffect
            }
            lastTitle = songTitle
            delay(700)
            val actor = actors.filter { !it.scripted && it.speech == null }.randomOrNull()
                ?: return@LaunchedEffect
            actor.say(songComment(actor.kind, songTitle))
        }

        actors.forEach { actor ->
            val petModifier = Modifier
                .offset { actor.position.value.let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                .then(
                    if (!roam) Modifier else Modifier.pointerInput(actor, maxX, maxY) {
                        detectDragGestures(
                            onDragStart = { actor.pokes++ },
                            onDragEnd = { actor.pokes++ }
                        ) { change, drag ->
                            change.consume()
                            if (abs(drag.x) > 0.5f) actor.facingRight = drag.x > 0
                            scope.launch {
                                val next = actor.position.value + drag
                                actor.position.snapTo(Offset(next.x.coerceIn(0f, maxX), next.y.coerceIn(0f, maxY)))
                            }
                        }
                    }
                )
            val onTap = { actor.pokes++; Unit }
            val onSpeech = { text: String? -> actor.speech = text }
            when (actor.kind) {
                PetKind.CAT -> PetCat(isPlaying, petModifier, size, actor.walking, actor.facingRight, onTap)
                PetKind.GENIUS -> PetGenius(isPlaying, petModifier, size, actor.walking, actor.facingRight, onTap, onSpeech)
                PetKind.DETECTIVE -> PetDetective(isPlaying, petModifier, size, actor.walking, actor.facingRight, onTap, onSpeech)
                PetKind.MAGE -> PetMage(isPlaying, petModifier, size, actor.walking, actor.facingRight, onTap, onSpeech)
                PetKind.DUO, PetKind.NONE -> Unit
            }
        }

        val gap = with(density) { 4.dp.roundToPx() }
        actors.forEach { actor ->
            AnimatedContent(
                targetState = actor.speech,
                transitionSpec = {
                    (fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.8f))
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "pet_speech",
                modifier = Modifier.layout { measurable, constraints ->
                    // Balon di atas kepala, digeser supaya tetap di dalam area;
                    // saat gentayangan pindah ke bawah kaki kalau di atas sempit.
                    val placeable = measurable.measure(
                        Constraints(maxWidth = minOf(constraints.maxWidth, 220.dp.roundToPx()))
                    )
                    val pet = actor.position.value
                    val x = (pet.x + sizePx / 2 - placeable.width / 2)
                        .coerceIn(0f, (constraints.maxWidth - placeable.width).toFloat().coerceAtLeast(0f))
                    val above = pet.y - placeable.height - gap
                    val y = if (above >= 0f || !roam) above else pet.y + sizePx + gap
                    layout(0, 0) { placeable.place(x.roundToInt(), y.roundToInt()) }
                }
            ) { text ->
                if (text != null) SpeechBubble(text)
            }
        }
    }
}

/** Berjalan lurus ke [target] dengan kecepatan tetap, menghadap arah jalan. */
private suspend fun PetActor.walkTo(target: Offset, pxPerSec: Float) {
    val from = position.value
    val distance = (target - from).getDistance()
    if (distance < pxPerSec * 0.5f) return
    if (abs(target.x - from.x) > 1f) facingRight = target.x > from.x
    walking = true
    try {
        position.animateTo(target, tween((distance / pxPerSec * 1000).toInt(), easing = LinearEasing))
    } catch (e: CancellationException) {
        // Terpotong seretan jari: berhenti di tempat, kecuali memang dibatalkan.
        coroutineContext.ensureActive()
    } finally {
        walking = false
    }
}

/** Obrolan Light (0) dan L (1) saat mereka berpapasan. */
private val DuoDialogues = listOf(
    listOf(1 to "Kamu Kira, kan?", 0 to "…Bukan.", 1 to "Kemungkinannya naik 2%."),
    listOf(1 to "Mau kue?", 0 to "Nggak, makasih.", 1 to "Bagus. Memang bukan buat kamu."),
    listOf(0 to "Kenapa kamu duduk begitu?", 1 to "Kalau duduk biasa, daya deduksiku turun 40%."),
    listOf(0 to "(Dia mencurigaiku…)", 1 to "Aku dengar itu."),
    listOf(1 to "Lagu ini kamu yang pilih?", 0 to "Semua sesuai rencana.", 1 to "…Mencurigakan."),
    listOf(0 to "Aku akan jadi dewa dunia baru!", 1 to "Pelan-pelan. Aku lagi dengar lagu.")
)

private fun songComment(kind: PetKind, title: String): String {
    val song = "\"${if (title.length > 22) title.take(21).trimEnd() + "…" else title}\""
    return when (kind) {
        PetKind.CAT -> listOf("Meong~ $song ♪", "Purr… $song enak.", "Nyaa! Lagu baru!").random()
        PetKind.GENIUS -> listOf(
            "$song… sesuai rencana.",
            "Pilihan bagus. Seperti yang kuduga.",
            "$song? Tidak akan kutulis di Death Note."
        ).random()
        PetKind.DETECTIVE -> listOf(
            "$song… kemungkinan kamu suka: ${Random.nextInt(70, 100)}%.",
            "Lagu ini… mencurigakan.",
            "Kemungkinan kamu Kira turun ${Random.nextInt(1, 10)}%."
        ).random()
        PetKind.MAGE -> listOf(
            "$song… Himmel pasti suka.",
            "Lagu ini akan kuingat. Mungkin seratus tahun.",
            "Hmm. Sihir yang bagus."
        ).random()
        PetKind.DUO, PetKind.NONE -> ""
    }
}

@Composable
private fun SpeechBubble(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .shadow(6.dp, RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}
