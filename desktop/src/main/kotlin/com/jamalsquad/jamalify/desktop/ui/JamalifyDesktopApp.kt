package com.jamalsquad.jamalify.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.jamalsquad.jamalify.desktop.core.AppSettings
import com.jamalsquad.jamalify.desktop.core.LibraryStore
import com.jamalsquad.jamalify.desktop.core.MusicCatalog
import com.jamalsquad.jamalify.desktop.core.PlayerController
import com.jamalsquad.jamalify.ui.components.ambientLight
import com.jamalsquad.jamalify.ui.components.liquidGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch

private val BarHeight = 84.dp
private val BarMargin = 12.dp

/**
 * Akar UI desktop. Hanya bergantung pada kontrak di `desktop.core`, jadi
 * bisa dijalankan dengan implementasi asli maupun palsu (`ui.preview`).
 */
@Composable
fun JamalifyDesktopApp(
    player: PlayerController,
    catalog: MusicCatalog,
    library: LibraryStore,
    settings: AppSettings
) = JamalifyDesktopApp(player, catalog, library, settings, Screen.HOME)

/** Varian dengan keadaan awal, dipakai pengambil tangkapan layar di `ui.preview`. */
@Composable
internal fun JamalifyDesktopApp(
    player: PlayerController,
    catalog: MusicCatalog,
    library: LibraryStore,
    settings: AppSettings,
    initialScreen: Screen,
    initialQueueOpen: Boolean = false,
    initialPlayerOpen: Boolean = false
) {
    val theme by settings.theme.collectAsState()
    val pet by settings.pet.collectAsState()
    val state by player.state.collectAsState()

    var screen by remember { mutableStateOf(initialScreen) }
    var queueOpen by remember { mutableStateOf(initialQueueOpen) }
    var playerOpen by remember { mutableStateOf(initialPlayerOpen) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actions = remember(player, library) {
        SongActions(player, library, scope) { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }
    }
    val hazeState = remember { HazeState() }
    val rootFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    var focusSearch by remember { mutableStateOf(0) }
    val focusManager = LocalFocusManager.current

    // Fokus awal di akar supaya Spasi langsung memutar/menjeda.
    LaunchedEffect(Unit) { rootFocus.requestFocus() }
    LaunchedEffect(focusSearch) { if (focusSearch > 0) runCatching { searchFocus.requestFocus() } }

    val onPlay: PlayList = { songs, index -> if (songs.isNotEmpty()) player.playSongs(songs, index) }
    val setVolume: (Float) -> Unit = { v -> player.setVolume(v); settings.setVolume(v) }
    val content = PaddingValues(bottom = BarHeight + BarMargin * 2)

    JamalifyDesktopTheme(theme) {
        CompositionLocalProvider(LocalSongActions provides actions) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .ambientLight()
                    // Ctrl+… dicegat sebelum sampai ke kolom teks; Spasi baru
                    // ditangani kalau tidak dipakai elemen yang fokus (mis. kolom cari).
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionRight -> { player.next(); true }
                            Key.DirectionLeft -> { player.previous(); true }
                            Key.F -> { playerOpen = false; screen = Screen.SEARCH; focusSearch++; true }
                            else -> false
                        }
                    }
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Spacebar) { player.togglePlay(); true } else false
                    }
                    .focusRequester(rootFocus)
                    .focusable()
                    // Klik di area kosong melepas fokus kolom teks supaya Spasi kembali memutar/menjeda.
                    .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus(); rootFocus.requestFocus() } }
            ) {
                Row(Modifier.fillMaxSize().haze(hazeState)) {
                    Sidebar(screen, { screen = it; playerOpen = false }, Modifier.padding(bottom = BarHeight + BarMargin))
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Crossfade(playerOpen, label = "player") { open ->
                            if (open) {
                                PlayerView(player, catalog, pet, content, onClose = { playerOpen = false })
                            } else when (screen) {
                                Screen.HOME -> HomeView(catalog, library, pet, state.current, state.isPlaying, onPlay, content)
                                Screen.SEARCH -> SearchView(catalog, state.current, onPlay, searchFocus, content)
                                Screen.LIBRARY -> LibraryView(library, state.current, onPlay, content)
                                Screen.SETTINGS -> SettingsView(settings, content)
                            }
                        }
                    }
                    AnimatedVisibility(
                        queueOpen,
                        enter = slideInHorizontally { it } + fadeIn(),
                        exit = slideOutHorizontally { it } + fadeOut()
                    ) {
                        QueuePanel(
                            player, onClose = { queueOpen = false },
                            modifier = Modifier.padding(bottom = BarHeight + BarMargin * 2, end = BarMargin, top = BarMargin)
                                .liquidGlass(RoundedCornerShape(20.dp), null)
                        )
                    }
                }
                NowPlayingBar(
                    player,
                    queueOpen = queueOpen,
                    playerOpen = playerOpen,
                    onToggleQueue = { queueOpen = !queueOpen },
                    onTogglePlayer = { playerOpen = !playerOpen },
                    onVolume = setVolume,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(BarMargin)
                        .liquidGlass(RoundedCornerShape(22.dp), hazeState)
                )
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = BarHeight + BarMargin * 2))
            }
        }
    }
}
