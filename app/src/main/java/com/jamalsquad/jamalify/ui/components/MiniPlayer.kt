package com.jamalsquad.jamalify.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.ui.theme.JamalifyTheme

private val DockShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val RowHeight = 68.dp

/**
 * Mini player berupa kartu yang "menancap" di belakang bottom bar, seperti
 * desain acuan: warnanya dibalik dari tema (putih di tema gelap) supaya
 * langsung terbaca sebagai lagu yang sedang diputar. [tuck] adalah tinggi
 * bagian bawah kartu yang tertutup bottom bar.
 */
@Composable
fun MiniPlayer(
    song: Song?,
    isPlaying: Boolean,
    isFavorite: Boolean,
    progress: () -> Float,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleFavorite: () -> Unit,
    tuck: Dp,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = song != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier
    ) {
        if (song == null) return@AnimatedVisibility
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        JamalifyTheme(darkTheme = !dark) {
            val colors = MaterialTheme.colorScheme
            CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(24.dp, DockShape)
                        .background(colors.surface, DockShape)
                        .clip(DockShape)
                        .clickable(onClick = onExpand)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(RowHeight),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Artwork(url = song.thumbnail, size = RowHeight, corner = 0.dp)
                        HSpace(14.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (isFavorite) "Hapus dari favorit" else "Favoritkan"
                            )
                        }
                        HSpace(4.dp)
                        PlayPauseRing(isPlaying, progress, onPlayPause)
                        HSpace(14.dp)
                    }
                    Spacer(Modifier.height(tuck))
                }
            }
        }
    }
}

/**
 * Tombol putar bulat dengan cincin progres. Progres dibaca saat menggambar,
 * jadi tiap detik hanya cincinnya yang digambar ulang.
 */
@Composable
private fun PlayPauseRing(isPlaying: Boolean, progress: () -> Float, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(50.dp)
            .drawBehind {
                val stroke = 2.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    colors.primary.copy(alpha = 0.15f), 0f, 360f, false,
                    Offset(inset, inset), arcSize, style = Stroke(stroke)
                )
                drawArc(
                    colors.primary, -90f, 360f * progress().coerceIn(0f, 1f), false,
                    Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            .padding(4.dp)
            .clip(CircleShape)
            .background(colors.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.6f))
                    .togetherWith(fadeOut(tween(140)))
            },
            label = "mini_play_pause"
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Jeda" else "Putar",
                tint = colors.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
