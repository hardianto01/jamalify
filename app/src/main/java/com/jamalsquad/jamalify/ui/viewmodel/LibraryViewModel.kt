package com.jamalsquad.jamalify.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.JamalifyApp
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.download.DownloadService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@UnstableApi
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as JamalifyApp).repository

    val playlists = repository.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites = repository.favorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloads = repository.downloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history = repository.recentlyPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createPlaylist(name.trim()) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { repository.deletePlaylist(id) }
    }

    fun renamePlaylist(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.renamePlaylist(id, name.trim()) }
    }

    fun download(song: Song) {
        viewModelScope.launch {
            repository.enqueueDownload(song)
            DownloadService.start(getApplication())
        }
    }

    fun removeDownload(songId: String) {
        viewModelScope.launch { repository.removeDownload(songId) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }
}
