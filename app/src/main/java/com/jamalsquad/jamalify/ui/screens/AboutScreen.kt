package com.jamalsquad.jamalify.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jamalsquad.jamalify.BuildConfig
import com.jamalsquad.jamalify.R
import com.jamalsquad.jamalify.ui.components.VSpace

private data class Dependency(
    val name: String,
    val role: String,
    val license: String
)

private val DEPENDENCIES = listOf(
    Dependency("NewPipeExtractor", "Ekstraksi katalog dan stream YouTube", "GPL-3.0"),
    Dependency("AndroidX Media3 / ExoPlayer", "Mesin pemutar dan MediaSession", "Apache-2.0"),
    Dependency("Jetpack Compose", "Seluruh antarmuka", "Apache-2.0"),
    Dependency("Room", "Playlist, favorit, riwayat, unduhan", "Apache-2.0"),
    Dependency("OkHttp", "Lapisan jaringan", "Apache-2.0"),
    Dependency("Coil", "Pemuat sampul album", "Apache-2.0")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Tentang") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 24.dp)
                .padding(bottom = contentPadding.calculateBottomPadding())
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = "Logo Jamalify",
                    modifier = Modifier.size(96.dp)
                )
                VSpace(8.dp)
                Text("Jamalify", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Versi ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                VSpace(10.dp)
                Text(
                    "by Hardianto",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            CreditCard(
                title = "Tentang logonya",
                body = "Dua kepala not yang diikat satu balok — not seperdelapan " +
                    "berpasangan. Bentuknya dipilih karena maknanya sudah ada di " +
                    "notasi musik itu sendiri: dua nada yang terikat jadi satu frasa, " +
                    "dan tidak lagi berbunyi sendiri-sendiri."
            )

            VSpace(12.dp)

            CreditCard(
                title = "Buat didengar bareng",
                body = "Playlist paling enak bukan yang paling rapi, tapi yang isinya " +
                    "ketahuan siapa yang menambahkan. Aplikasi ini dibuat supaya lagu " +
                    "gampang dibagi dan didengar bareng — sisanya urusan kalian."
            )

            VSpace(12.dp)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Dibangun di atas",
                        style = MaterialTheme.typography.titleMedium
                    )
                    VSpace(4.dp)
                    Text(
                        "Aplikasi ini berdiri di atas pekerjaan orang lain. Ini daftarnya.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    VSpace(14.dp)

                    DEPENDENCIES.forEachIndexed { index, dependency ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    dependency.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    dependency.role,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                dependency.license,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            VSpace(12.dp)

            CreditCard(
                title = "Catatan lisensi",
                body = "NewPipeExtractor berlisensi GPL-3.0. Selama pustaka itu dipakai, " +
                    "aplikasi ini harus ikut GPL-3.0 kalau sampai disebarkan ke orang lain — " +
                    "termasuk kewajiban membuka kode sumbernya. Untuk pemakaian sendiri, tidak " +
                    "ada kewajiban apa pun."
            )

            VSpace(12.dp)

            CreditCard(
                title = "Bukan produk resmi",
                body = "Jamalify tidak berafiliasi dengan YouTube, Google, maupun Spotify. " +
                    "Lagu diambil dari YouTube dan daftar lagu Spotify hanya dibaca judulnya. " +
                    "Cara kerja ini melanggar Persyaratan Layanan YouTube, jadi aplikasi ini " +
                    "ditujukan untuk pemakaian pribadi dan tidak untuk diedarkan di Play Store."
            )

            VSpace(32.dp)

            Text(
                "Dengarkan bareng-bareng.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            VSpace(32.dp)
        }
    }
}

@Composable
private fun CreditCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            VSpace(6.dp)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
