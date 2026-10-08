package com.jamalsquad.jamalify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamalsquad.jamalify.data.Song

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    trailingLabel: String? = null,
    number: Int? = null,
    onMore: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isPlaying) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (number != null) {
            Box(Modifier.width(30.dp)) {
                if (isPlaying) {
                    AudioVisualizer(isPlaying = true, barCount = 4, barWidth = 2.dp, height = 14.dp)
                } else {
                    Text(
                        text = "%02d".format(number),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Artwork(url = song.thumbnail, size = 52.dp)
        HSpace(14.dp)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = if (isPlaying) FontWeight.SemiBold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = trailingLabel ?: listOfNotNull(
                    song.artist.takeIf { it.isNotBlank() },
                    song.durationLabel.takeIf { song.durationSec > 0 }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isPlaying && number == null) {
            AudioVisualizer(
                isPlaying = true,
                barCount = 3,
                barWidth = 2.5.dp,
                height = 16.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        trailing?.invoke()

        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Opsi lainnya",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
