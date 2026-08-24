package com.jamalsquad.jamalify.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.JamalifyApp
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.db.DownloadState
import com.jamalsquad.jamalify.data.download.DownloadService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State tambahan untuk lagu yang sedang diputar: favorit, unduhan, radio. */
@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as JamalifyApp).repository
    private val player = (app as JamalifyApp).playerConnection

    val isFavorite = player.currentSong
        .flatMapLatest { song ->
            if (song == null) flowOf(false) else repository.isFavorite(song.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val downloadState = player.currentSong
        .flatMapLatest { song ->
            if (song == null) flowOf(null) else repository.downloadState(song.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleFavorite() {
        val song = player.currentSong.value ?: return
        viewModelScope.launch { repository.toggleFavorite(song) }
    }

    fun download() {
        val song = player.currentSong.value ?: return
        viewModelScope.launch {
            repository.enqueueDownload(song)
            DownloadService.start(getApplication())
        }
    }

    fun isDownloaded(state: DownloadState?) = state == DownloadState.COMPLETED

    /** Lanjutkan antrean dengan lagu terkait, seperti autoplay YouTube. */
    fun startRadio(song: Song) {
        viewModelScope.launch {
            val related = runCatching { repository.related(song.id) }.getOrDefault(emptyList())
            if (related.isNotEmpty()) player.addToQueue(related)
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch { repository.toggleFavorite(song) }
    }

    fun addToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch { repository.addToPlaylist(playlistId, song) }
    }

    fun download(song: Song) {
        viewModelScope.launch {
            repository.enqueueDownload(song)
            DownloadService.start(getApplication())
        }
    }

    val playlists = repository.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
