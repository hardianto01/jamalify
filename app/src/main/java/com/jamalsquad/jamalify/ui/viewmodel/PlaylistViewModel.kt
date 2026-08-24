package com.jamalsquad.jamalify.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.JamalifyApp
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.download.DownloadService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
class PlaylistViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as JamalifyApp).repository

    private val playlistId = MutableStateFlow(-1L)

    val songs = playlistId
        .flatMapLatest { id ->
            if (id < 0) kotlinx.coroutines.flow.flowOf(emptyList()) else repository.playlistSongs(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val info = playlistId
        .flatMapLatest { id ->
            if (id < 0) kotlinx.coroutines.flow.flowOf(null) else repository.playlistInfo(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(id: Long) {
        if (playlistId.value != id) playlistId.value = id
    }

    fun remove(songId: String) {
        val id = playlistId.value
        if (id < 0) return
        viewModelScope.launch { repository.removeFromPlaylist(id, songId) }
    }

    fun downloadAll(songs: List<Song>) {
        viewModelScope.launch {
            songs.forEach { repository.enqueueDownload(it) }
            DownloadService.start(getApplication())
        }
    }
}
