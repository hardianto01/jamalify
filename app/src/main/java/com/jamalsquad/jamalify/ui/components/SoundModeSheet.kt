package com.jamalsquad.jamalify.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jamalsquad.jamalify.playback.SoundMode
import com.jamalsquad.jamalify.playback.SoundModeManager

private const val COLUMNS = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundModeSheet(accentColor: Color, onDismiss: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { SoundModeManager.init(context) }
    val current by SoundModeManager.mode.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text("Mode Suara", style = MaterialTheme.typography.titleLarge)
            Text(
                "Berlaku untuk semua lagu sampai diganti lagi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            VSpace(16.dp)
            ModeGroup("Vibe", SoundMode.entries.filterNot { it.troll }, current, accentColor)
            VSpace(20.dp)
            ModeGroup("Troll 🤪", SoundMode.entries.filter { it.troll }, current, accentColor)
            VSpace(8.dp)
            Text(
                "Mode troll kembali ke Normal saat aplikasi dibuka lagi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModeGroup(title: String, modes: List<SoundMode>, current: SoundMode, accentColor: Color) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    VSpace(8.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { mode ->
                    ModeTile(
                        mode = mode,
                        selected = mode == current,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ModeTile(mode: SoundMode, selected: Boolean, accentColor: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) accentColor.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .then(
                if (selected) Modifier.border(BorderStroke(1.5.dp, accentColor), shape) else Modifier
            )
            .clickable { SoundModeManager.select(mode) }
            .padding(vertical = 14.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (mode.hiRes) {
                Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) { HiResBadge(height = 26.dp, showAudio = false) }
            } else {
                Text(mode.emoji, fontSize = 24.sp)
            }
            VSpace(4.dp)
            Text(
                mode.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

/**
 * Logo Hi-Res Audio: digambar sebagai vektor, bukan gambar, jadi tetap tajam
 * di ukuran berapa pun — dari tombol kecil sampai label di pemutar.
 */
@Composable
fun HiResBadge(modifier: Modifier = Modifier, height: Dp = 22.dp, showAudio: Boolean = true) {
    val shape = RoundedCornerShape(height * 0.22f)
    val gold = Brush.linearGradient(listOf(Color(0xFFFFE9A3), Color(0xFFE7B53C), Color(0xFFB8860B)))
    Row(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(Color.Black)
            .border(BorderStroke(height * 0.06f, gold), shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .background(gold)
                .padding(horizontal = height * 0.28f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Hi-Res",
                color = Color.Black,
                fontWeight = FontWeight.Black,
                fontSize = (height.value * 0.5f).sp,
                letterSpacing = (-0.3).sp,
                maxLines = 1,
                softWrap = false
            )
        }
        if (showAudio) {
            Text(
                "AUDIO",
                modifier = Modifier.padding(horizontal = height * 0.24f),
                color = Color(0xFFE7B53C),
                fontWeight = FontWeight.Bold,
                fontSize = (height.value * 0.36f).sp,
                letterSpacing = 1.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
