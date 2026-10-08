package com.jamalsquad.jamalify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jamalsquad.jamalify.ui.components.PetKind
import com.jamalsquad.jamalify.ui.components.PetManager
import com.jamalsquad.jamalify.ui.components.PlayerIcons
import com.jamalsquad.jamalify.ui.components.VSpace
import com.jamalsquad.jamalify.ui.theme.ThemeManager
import com.jamalsquad.jamalify.ui.theme.ThemeMode

/**
 * Semua setelan aplikasi dikumpulkan di sini. Tiap kelompok berupa kartu
 * dengan judul kecil di atasnya; setelan baru cukup ditambah sebagai
 * [SettingsRow] di kelompok yang cocok.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    contentPadding: PaddingValues
) {
    val themeMode by ThemeManager.mode.collectAsState()
    val pet by PetManager.kind.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(PlayerIcons.ChevronLeft, contentDescription = "Kembali")
                }
                Text(
                    "Pengaturan",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        item {
            SettingsGroup("Tampilan") {
                ThemeMode.entries.forEach { mode ->
                    SettingsRow(
                        title = mode.label,
                        subtitle = when (mode) {
                            ThemeMode.SYSTEM -> "Ikut mode terang/gelap ponsel"
                            ThemeMode.LIGHT -> null
                            ThemeMode.DARK -> null
                        },
                        onClick = { ThemeManager.select(mode) },
                        trailing = { if (mode == themeMode) SelectedMark() }
                    )
                }
            }
        }

        item {
            SettingsGroup("Peliharaan beranda") {
                PetKind.entries.forEach { kind ->
                    SettingsRow(
                        title = kind.label,
                        subtitle = kind.description,
                        onClick = { PetManager.select(kind) },
                        trailing = { if (kind == pet) SelectedMark() }
                    )
                }
            }
        }

        item {
            SettingsGroup("Lainnya") {
                SettingsRow(
                    title = "Tentang Jamalify",
                    onClick = onOpenAbout,
                    trailing = {
                        Icon(
                            PlayerIcons.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) { content() }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                VSpace(2.dp)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing()
    }
}

@Composable
private fun SelectedMark() {
    Icon(
        Icons.Rounded.Check,
        contentDescription = "Terpilih",
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(3.dp)
    )
}
