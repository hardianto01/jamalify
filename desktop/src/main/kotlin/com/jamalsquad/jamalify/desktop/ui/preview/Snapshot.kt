package com.jamalsquad.jamalify.desktop.ui.preview

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.jamalsquad.jamalify.desktop.core.DesktopTheme
import com.jamalsquad.jamalify.desktop.ui.JamalifyDesktopApp
import com.jamalsquad.jamalify.desktop.ui.Screen
import org.jetbrains.skia.EncodedImageFormat
import java.io.File


/**
 * Merender layar-layar utama ke PNG tanpa membuka jendela, untuk memeriksa
 * tata letak (`java -cp … SnapshotKt <folder>`). Memakai data palsu.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "snapshots").apply { mkdirs() }
    data class Shot(
        val name: String, val screen: Screen, val queue: Boolean = false, val player: Boolean = false,
        val light: Boolean = false, val act: (ImageComposeScene) -> Unit = {}
    )
    val shots = listOf(
        Shot("home", Screen.HOME),
        Shot("home-queue", Screen.HOME, queue = true),
        Shot("player", Screen.HOME, player = true),
        Shot("search", Screen.SEARCH),
        Shot("library", Screen.LIBRARY),
        Shot("settings", Screen.SETTINGS),
        Shot("home-light", Screen.HOME, queue = true, light = true),
        Shot("player-small", Screen.HOME, player = true),
        // Klik kanan baris kedua di Pustaka → menu konteks.
        Shot("context-menu", Screen.LIBRARY) { sc ->
            val at = Offset(600f, 207f)
            sc.sendPointerEvent(PointerEventType.Move, at)
            sc.sendPointerEvent(
                PointerEventType.Press, at,
                buttons = PointerButtons(isSecondaryPressed = true), button = PointerButton.Secondary
            )
            sc.sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Secondary)
        },
        // Ctrl+F dari Beranda harus pindah ke Cari dan memfokuskan kolomnya.
        Shot("ctrl-f", Screen.HOME) { sc ->
            sc.sendKeyEvent(KeyEvent(Key.F, KeyEventType.KeyDown, isCtrlPressed = true))
            sc.sendKeyEvent(KeyEvent(Key.F, KeyEventType.KeyUp, isCtrlPressed = true))
        },
    )
    for (shot in shots) {
        val player = FakePlayer()
        player.playSongs(FakeSongs, 0)
        player.seekTo(13_000)
        val settings = FakeSettings().apply { if (shot.light) setTheme(DesktopTheme.LIGHT) }
        val (w, h) = if (shot.name.endsWith("small")) 900 to 600 else 1280 to 800
        val scene = ImageComposeScene(w, h, Density(1f)) {
            JamalifyDesktopApp(player, FakeCatalog(), FakeLibrary(), settings, shot.screen, shot.queue, shot.player)
        }
        // Beberapa frame dengan jeda nyata supaya data palsu dan sampul sempat dimuat.
        var t = 0L
        repeat(10) { scene.render(t); t += 100_000_000; Thread.sleep(100) }
        shot.act(scene)
        repeat(20) { scene.render(t); t += 100_000_000; Thread.sleep(100) }
        val png = scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes
        File(out, "${shot.name}.png").writeBytes(png)
        scene.close()
        player.release()
        println("ditulis ${shot.name}.png")
    }
    System.exit(0)
}
