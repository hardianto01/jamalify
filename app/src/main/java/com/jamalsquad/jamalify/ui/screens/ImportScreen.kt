package com.jamalsquad.jamalify.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.data.ImportState
import com.jamalsquad.jamalify.data.spotify.SpotifyAuth
import com.jamalsquad.jamalify.data.spotify.SpotifyRedirectBus
import com.jamalsquad.jamalify.ui.components.HSpace
import com.jamalsquad.jamalify.ui.components.VSpace
import com.jamalsquad.jamalify.ui.viewmodel.ImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: ImportViewModel = viewModel()
) {
    val link by viewModel.link.collectAsState()
    val state by viewModel.state.collectAsState()
    val clientId by viewModel.clientId.collectAsState()
    val clientSecret by viewModel.clientSecret.collectAsState()
    val loggedIn by viewModel.loggedIn.collectAsState()
    val account by viewModel.account.collectAsState()
    var showSpotifyAdvanced by remember { mutableStateOf(false) }
    var clientIdMissing by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Begitu Activity menerima redirect, tukarkan kodenya jadi token.
    val pendingRedirect by SpotifyRedirectBus.redirect.collectAsState()
    LaunchedEffect(pendingRedirect) {
        pendingRedirect?.let {
            viewModel.onLoginRedirect(it)
            SpotifyRedirectBus.consume()
        }
    }

    val busy = state is ImportState.Loading || state is ImportState.Matching

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Impor playlist") },
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
                .padding(horizontal = 20.dp)
                .padding(bottom = contentPadding.calculateBottomPadding())
        ) {
            Text(
                "Tempel link playlist dari YouTube atau Spotify. Lagu akan masuk sebagai playlist baru di Library.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            VSpace(16.dp)

            SpotifyAccountSection(
                loggedIn = loggedIn,
                account = account,
                clientId = clientId,
                busy = busy,
                onClientIdChange = viewModel::onClientIdChange,
                onLogin = {
                    val url = viewModel.authorizeUrl()
                    if (url == null) {
                        clientIdMissing = true
                    } else {
                        clientIdMissing = false
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                },
                clientIdMissing = clientIdMissing,
                onLogout = viewModel::logout,
                onImportPlaylists = viewModel::importAllPlaylists,
                onImportSaved = viewModel::importSavedTracks
            )

            VSpace(20.dp)

            Text(
                "Atau tempel link",
                style = MaterialTheme.typography.titleMedium
            )
            VSpace(8.dp)

            OutlinedTextField(
                value = link,
                onValueChange = viewModel::onLinkChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Link playlist") },
                placeholder = { Text("https://open.spotify.com/playlist/…") },
                singleLine = true,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp)
            )

            VSpace(16.dp)

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = viewModel::import,
                    enabled = !busy && link.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text(if (busy) "Sedang mengimpor…" else "Impor sekarang") }

                if (busy) {
                    HSpace(10.dp)
                    OutlinedButton(onClick = viewModel::cancel) { Text("Batal") }
                }
            }

            VSpace(20.dp)

            when (val current = state) {
                is ImportState.Loading -> StatusCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(current.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                is ImportState.Matching -> StatusCard {
                    Column {
                        Text(
                            "Mencocokkan ke YouTube — ${current.done} dari ${current.total}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        VSpace(8.dp)
                        LinearProgressIndicator(
                            progress = {
                                if (current.total == 0) 0f
                                else current.done.toFloat() / current.total
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outline
                        )
                        VSpace(8.dp)
                        Text(
                            current.current,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                is ImportState.Success -> StatusCard {
                    Column {
                        Text(
                            "\"${current.playlistName}\" berhasil diimpor",
                            style = MaterialTheme.typography.titleMedium
                        )
                        VSpace(6.dp)
                        Text(
                            buildString {
                                append("${current.imported} lagu masuk ke library")
                                if (current.skipped > 0) {
                                    append(", ${current.skipped} tidak ditemukan di YouTube")
                                }
                                append(".")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        VSpace(10.dp)
                        Row {
                            Button(onClick = onOpenLibrary) { Text("Buka Library") }
                            HSpace(8.dp)
                            TextButton(onClick = viewModel::reset) { Text("Impor lagi") }
                        }
                    }
                }

                is ImportState.Error -> StatusCard(error = true) {
                    Text(
                        current.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                ImportState.Idle -> Unit
            }

            VSpace(28.dp)

            TextButton(onClick = { showSpotifyAdvanced = !showSpotifyAdvanced }) {
                Text(if (showSpotifyAdvanced) "Sembunyikan opsi Spotify" else "Opsi lanjutan Spotify")
            }

            if (showSpotifyAdvanced) {
                Text(
                    "Tanpa kredensial, Spotify hanya memberi sekitar 100 lagu pertama dari sebuah " +
                        "playlist. Isi Client ID dan Secret dari dashboard developer Spotify untuk " +
                        "mengimpor playlist secara penuh. Data ini hanya disimpan di perangkat ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                VSpace(12.dp)
                OutlinedTextField(
                    value = clientId,
                    onValueChange = viewModel::onClientIdChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Spotify Client ID") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                VSpace(10.dp)
                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = viewModel::onClientSecretChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Spotify Client Secret") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            VSpace(32.dp)
        }
    }
}

@Composable
private fun StatusCard(error: Boolean = false, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/**
 * Login Spotify memakai Client ID milik pengguna sendiri.
 *
 * Spotify mengikat redirect URI ke satu aplikasi terdaftar, jadi tidak ada
 * Client ID yang bisa dibundel di dalam APK dan dipakai semua orang — tiap
 * pengguna harus mendaftarkan aplikasinya sendiri di Developer Dashboard.
 */
@Composable
private fun SpotifyAccountSection(
    loggedIn: Boolean,
    account: String,
    clientId: String,
    busy: Boolean,
    clientIdMissing: Boolean,
    onClientIdChange: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onImportPlaylists: () -> Unit,
    onImportSaved: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Akun Spotify", style = MaterialTheme.typography.titleMedium)
            VSpace(6.dp)

            if (loggedIn) {
                Text(
                    if (account.isBlank()) "Sudah masuk." else "Masuk sebagai $account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                VSpace(14.dp)

                Button(
                    onClick = onImportPlaylists,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Impor semua playlist saya") }

                VSpace(8.dp)

                OutlinedButton(
                    onClick = onImportSaved,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Impor lagu yang saya sukai") }

                VSpace(4.dp)

                TextButton(onClick = onLogout, enabled = !busy) { Text("Keluar dari Spotify") }
            } else {
                Text(
                    "Masuk untuk mengimpor playlist dan lagu favoritmu otomatis, " +
                        "dan supaya beranda ikut menyesuaikan seleramu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                VSpace(12.dp)

                OutlinedTextField(
                    value = clientId,
                    onValueChange = onClientIdChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Spotify Client ID") },
                    singleLine = true,
                    isError = clientIdMissing,
                    shape = RoundedCornerShape(12.dp)
                )

                if (clientIdMissing) {
                    VSpace(6.dp)
                    Text(
                        "Client ID belum diisi.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                VSpace(10.dp)

                Button(
                    onClick = onLogin,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Masuk dengan Spotify") }

                VSpace(14.dp)

                Text(
                    "Cara mendapatkannya",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                VSpace(4.dp)
                Text(
                    "1. Buka developer.spotify.com/dashboard, buat aplikasi baru.\n" +
                        "2. Di setelan aplikasi itu, tambahkan Redirect URI persis:\n" +
                        "     ${SpotifyAuth.REDIRECT_URI}\n" +
                        "3. Salin Client ID-nya ke kolom di atas.\n" +
                        "4. Selama aplikasimu masih Development Mode, tambahkan email " +
                        "akun Spotify-mu ke daftar User Management — tanpa itu Spotify " +
                        "akan menolak login.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
