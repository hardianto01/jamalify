package com.jamalsquad.jamalify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

private fun glassEdge(light: Boolean) = Brush.linearGradient(
    if (light) listOf(
        Color.White.copy(alpha = 0.95f),
        Color.Black.copy(alpha = 0.06f),
        Color.White.copy(alpha = 0.7f)
    ) else listOf(
        Color.White.copy(alpha = 0.42f),
        Color.White.copy(alpha = 0.06f),
        Color.White.copy(alpha = 0.18f)
    )
)

private fun glassSheen(light: Boolean) = Brush.verticalGradient(
    0f to Color.White.copy(alpha = if (light) 0.55f else 0.13f),
    0.5f to Color.Transparent
)

private fun glassStyle(background: Color, tint: Color, light: Boolean) = HazeStyle(
    backgroundColor = background,
    tint = HazeTint(tint.copy(alpha = if (light) 0.55f else 0.42f)),
    blurRadius = 24.dp,
    noiseFactor = if (light) 0.03f else 0.06f,
    fallbackTint = HazeTint(tint.copy(alpha = 0.92f))
)

/**
 * Kaca ala iOS: isi di belakangnya diburamkan, diberi rona tipis, lalu
 * disapu kilau di bagian atas dan garis tepi bergradasi — supaya terbaca
 * sebagai lempeng kaca tebal yang menangkap cahaya, bukan panel transparan.
 * Di tema terang ditambah bayangan lembut, karena tepi putih saja hilang
 * di atas latar putih.
 *
 * Blur butuh Android 12 ke atas. Di bawah itu Haze memakai rona pekat
 * sebagai gantinya, jadi tetap rapi walau tidak buram.
 *
 * [hazeState] harus dipasang ([dev.chrisbanes.haze.haze]) pada saudara
 * elemen ini, bukan leluhurnya; kaca tidak bisa memburamkan dirinya sendiri.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    hazeState: HazeState?,
    tint: Color? = null
): Modifier = composed {
    val colors = MaterialTheme.colorScheme
    val light = colors.background.luminance() > 0.5f
    val glassTint = tint ?: colors.surface
    this
        .shadow(
            18.dp, shape,
            ambientColor = if (light) GlassShadow else Color.Black,
            spotColor = if (light) GlassShadow else Color.Black
        )
        .clip(shape)
        .then(
            if (hazeState != null) Modifier.hazeChild(hazeState, style = glassStyle(colors.background, glassTint, light))
            else Modifier.background(glassTint.copy(alpha = 0.92f))
        )
        .drawBehind { drawRect(glassSheen(light)) }
        .border(1.dp, glassEdge(light), shape)
}

private val GlassShadow = Color(0x33222838)

/**
 * Kaca untuk kartu di dalam daftar yang bergulir. Tanpa blur — Haze tidak
 * bisa memburamkan sumbernya sendiri — jadi di tema gelap kacanya dibangun
 * dari isian putih tipis yang memudar ke bawah plus tepi bergradasi. Di tema
 * terang cukup permukaan biasa, karena kaca putih di atas putih tak terlihat.
 */
fun Modifier.glassCard(shape: Shape): Modifier = composed {
    val colors = MaterialTheme.colorScheme
    if (colors.background.luminance() > 0.5f) {
        this.clip(shape).background(colors.surfaceContainerHigh)
    } else {
        this
            .clip(shape)
            .background(GlassCardFill)
            .border(1.dp, glassEdge(light = false), shape)
    }
}

private val GlassCardFill = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.035f))
)

/**
 * Cahaya samar di pojok atas layar gelap. Tanpa ini latar datar dan kaca di
 * atasnya tidak punya apa pun untuk dibiaskan.
 */
fun Modifier.ambientLight(): Modifier = composed {
    val colors = MaterialTheme.colorScheme
    if (colors.background.luminance() > 0.5f) this
    else drawBehind {
        drawRect(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
                center = Offset(size.width * 0.15f, 0f),
                radius = size.width * 1.1f
            )
        )
    }
}

/**
 * Pendar lembut di belakang elemen. Di tema gelap berupa cahaya putih yang
 * bocor dari tepinya; di tema terang jadi bayangan halus berwarna teks.
 */
fun Modifier.glow(alpha: Float = 0.22f, spread: Float = 0.85f): Modifier = composed {
    val colors = MaterialTheme.colorScheme
    val light = colors.background.luminance() > 0.5f
    val color = if (light) colors.onBackground else Color.White
    val strength = if (light) alpha * 0.6f else alpha
    drawBehind {
        val radius = size.maxDimension * spread
        drawCircle(
            brush = Brush.radialGradient(
                listOf(color.copy(alpha = strength), Color.Transparent),
                center = Offset(size.width / 2, size.height / 2),
                radius = radius
            ),
            radius = radius
        )
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    hazeState: HazeState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .liquidGlass(CircleShape, hazeState)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(size * 0.5f))
    }
}
