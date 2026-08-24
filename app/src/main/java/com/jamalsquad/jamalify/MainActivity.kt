package com.jamalsquad.jamalify

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.spotify.SpotifyRedirectBus
import com.jamalsquad.jamalify.data.spotify.SpotifyAuth
import com.jamalsquad.jamalify.ui.components.MiniPlayer
import com.jamalsquad.jamalify.ui.components.SongSheetHost
import com.jamalsquad.jamalify.ui.components.rememberSongSheetController
import com.jamalsquad.jamalify.ui.screens.AboutScreen
import com.jamalsquad.jamalify.ui.screens.HomeScreen
import com.jamalsquad.jamalify.ui.screens.ImportScreen
import com.jamalsquad.jamalify.ui.screens.LibraryScreen
import com.jamalsquad.jamalify.ui.screens.PlayerScreen
import com.jamalsquad.jamalify.ui.screens.PlaylistScreen
import com.jamalsquad.jamalify.ui.screens.SearchScreen
import com.jamalsquad.jamalify.ui.screens.SplashScreen
import com.jamalsquad.jamalify.ui.theme.JamalifyTheme
import kotlinx.coroutines.delay

@UnstableApi
class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as JamalifyApp
        app.playerConnection.connect()
        requestNotificationPermissionIfNeeded()

        handleSpotifyRedirect(intent)

        setContent {
            JamalifyTheme {
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(SPLASH_MILLIS)
                    showSplash = false
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Aplikasi digambar sejak awal di belakang splash, jadi
                    // saat splash memudar isinya sudah siap — tidak ada
                    // kedipan atau layar kosong sesaat.
                    JamalifyRoot()

                    AnimatedVisibility(
                        visible = showSplash,
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(520, easing = FastOutSlowInEasing))
                    ) {
                        SplashScreen()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSpotifyRedirect(intent)
    }

    /** Tangkap kembalian dari halaman izin Spotify. */
    private fun handleSpotifyRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (SpotifyAuth.isRedirect(data)) SpotifyRedirectBus.post(data)
    }

    // Ticker posisi hanya berguna selama ada layar yang menampilkannya.
    override fun onStart() {
        super.onStart()
        (application as JamalifyApp).playerConnection.onUiVisible()
    }

    override fun onStop() {
        super.onStop()
        (application as JamalifyApp).playerConnection.onUiHidden()
    }

    /** Tanpa izin ini, notifikasi pemutar tidak muncul di Android 13 ke atas. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Destination("home", "Beranda", Icons.Rounded.Home)
    data object Search : Destination("search", "Cari", Icons.Rounded.Search)
    data object Library : Destination("library", "Library", Icons.Rounded.LibraryMusic)
}

private const val SPLASH_MILLIS = 1700L

private val BOTTOM_ITEMS = listOf(Destination.Home, Destination.Search, Destination.Library)

@UnstableApi
@Composable
private fun JamalifyRoot() {
    val navController = rememberNavController()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as JamalifyApp
    val player = app.playerConnection

    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.position.collectAsState()
    val duration by player.duration.collectAsState()
    val playbackError by player.error.collectAsState()

    val sheet = rememberSongSheetController()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPlayer by remember { mutableStateOf(false) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in BOTTOM_ITEMS.map { it.route }

    // Redirect login harus mendarat di layar Impor, di mana ViewModel-nya
    // menunggu untuk menukar kode jadi token.
    val pendingRedirect by SpotifyRedirectBus.redirect.collectAsState()
    LaunchedEffect(pendingRedirect) {
        if (pendingRedirect != null && currentRoute != "import") {
            navController.navigate("import") { launchSingleTop = true }
        }
    }

    LaunchedEffect(playbackError) {
        playbackError?.let {
            snackbarHostState.showSnackbar(it)
            player.clearError()
        }
    }

    val play: (List<Song>, Int) -> Unit = { songs, index -> player.play(songs, index) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                MiniPlayer(
                    song = currentSong,
                    isPlaying = isPlaying,
                    progress = if (duration > 0) position.toFloat() / duration else 0f,
                    onExpand = { showPlayer = true },
                    onPlayPause = player::togglePlayPause,
                    onNext = player::next
                )
                if (showBottomBar) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        BOTTOM_ITEMS.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(Destination.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        val contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 12.dp
        )

        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.fillMaxSize(),
            // Perpindahan layar dihaluskan; durasinya sengaja pendek supaya
            // terasa responsif, bukan seperti menunggu animasi selesai.
            // Antar tab: fade-through — yang lama meredup lebih cepat daripada
            // yang baru menyala, jadi tidak ada momen dua layar sama terangnya.
            enterTransition = {
                fadeIn(tween(260, delayMillis = 60, easing = FastOutSlowInEasing)) +
                    scaleIn(tween(260, delayMillis = 60, easing = FastOutSlowInEasing), 0.97f)
            },
            exitTransition = { fadeOut(tween(140, easing = FastOutSlowInEasing)) },
            popEnterTransition = {
                fadeIn(tween(260, delayMillis = 60, easing = FastOutSlowInEasing))
            },
            popExitTransition = {
                fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.97f)
            }
        ) {
            composable(Destination.Home.route) {
                HomeScreen(
                    currentSongId = currentSong?.id,
                    onPlay = play,
                    sheet = sheet,
                    contentPadding = contentPadding
                )
            }
            composable(Destination.Search.route) {
                SearchScreen(
                    currentSongId = currentSong?.id,
                    onPlay = play,
                    sheet = sheet,
                    contentPadding = contentPadding
                )
            }
            composable(Destination.Library.route) {
                LibraryScreen(
                    currentSongId = currentSong?.id,
                    onPlay = play,
                    onOpenPlaylist = { navController.navigate("playlist/$it") },
                    onOpenImport = { navController.navigate("import") },
                    onOpenAbout = { navController.navigate("about") },
                    sheet = sheet,
                    contentPadding = contentPadding
                )
            }
            composable(
                route = "playlist/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
                enterTransition = {
                    slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 4 } +
                        fadeIn(tween(240))
                },
                popExitTransition = {
                    slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 4 } +
                        fadeOut(tween(200))
                },
            ) { entry ->
                PlaylistScreen(
                    playlistId = entry.arguments?.getLong("playlistId") ?: -1L,
                    currentSongId = currentSong?.id,
                    onPlay = play,
                    onShuffle = { songs -> player.play(songs.shuffled(), 0) },
                    onBack = { navController.popBackStack() },
                    sheet = sheet,
                    contentPadding = contentPadding
                )
            }
            composable(
                route = "about",
                enterTransition = {
                    slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 4 } +
                        fadeIn(tween(240))
                },
                popExitTransition = {
                    slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 4 } +
                        fadeOut(tween(200))
                },
            ) {
                AboutScreen(
                    onBack = { navController.popBackStack() },
                    contentPadding = contentPadding
                )
            }
            composable(
                route = "import",
                enterTransition = {
                    slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 4 } +
                        fadeIn(tween(240))
                },
                popExitTransition = {
                    slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 4 } +
                        fadeOut(tween(200))
                },
            ) {
                ImportScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLibrary = {
                        navController.popBackStack()
                        navController.navigate(Destination.Library.route) {
                            launchSingleTop = true
                        }
                    },
                    contentPadding = contentPadding
                )
            }
        }
    }

    SongSheetHost(controller = sheet)

    AnimatedVisibility(
        visible = showPlayer,
        enter = slideInVertically(tween(360, easing = FastOutSlowInEasing)) { it } +
            fadeIn(tween(220)),
        exit = slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it } +
            fadeOut(tween(180))
    ) {
        /*
         * Harus Surface, bukan Box.
         *
         * Scaffold menyediakan Surface yang menyetel LocalContentColor untuk
         * seluruh isinya. Overlay ini digambar DI LUAR Scaffold, jadi dengan
         * Box biasa LocalContentColor jatuh ke nilai bawaan Material 3 —
         * Color.Black. Akibatnya setiap Text dan Icon di layar pemutar yang
         * tidak menyebut warnanya sendiri (judul lagu, panah tutup, ikon
         * antrean, tombol prev/next) tergambar hitam di atas latar hitam.
         */
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            Box(
                modifier = Modifier.fillMaxSize().systemBarsPadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                PlayerScreen(onCollapse = { showPlayer = false })
            }
        }
    }
}
