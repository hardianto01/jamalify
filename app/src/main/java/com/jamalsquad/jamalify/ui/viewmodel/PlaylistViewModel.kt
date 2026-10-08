package com.jamalsquad.jamalify.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.JamalifyApp
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.db.PlaylistEntity
import com.jamalsquad.jamalify.data.download.DownloadService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Id semu untuk membuka "Lagu yang Disukai" lewat layar playlist yang sama. */
const val FAVORITES_PLAYLIST_ID = -2L

private const val NO_PLAYLIST = -1L

@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
class PlaylistViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as JamalifyApp).repository

    private val playlistId = MutableStateFlow(NO_PLAYLIST)

    val isFavorites: Boolean get() = playlistId.value == FAVORITES_PLAYLIST_ID

    val songs = playlistId
        .flatMapLatest { id -> source(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val info = playlistId
        .flatMapLatest { id ->
            when (id) {
                NO_PLAYLIST -> flowOf(null)
                FAVORITES_PLAYLIST_ID -> flowOf(PlaylistEntity(id = id, name = "Lagu yang Disukai"))
                else -> repository.playlistInfo(id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _suggestions = MutableStateFlow<List<Song>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private val _suggesting = MutableStateFlow(false)
    val suggesting = _suggesting.asStateFlow()

    private var suggestJob: Job? = null

    fun load(id: Long) {
        if (playlistId.value == id) return
        playlistId.value = id
        _suggestions.value = emptyList()
        refreshSuggestions()
    }

    private fun source(id: Long): Flow<List<Song>> = when (id) {
        NO_PLAYLIST -> flowOf(emptyList())
        FAVORITES_PLAYLIST_ID -> repository.favorites()
        else -> repository.playlistSongs(id)
    }

    /** Saran dari algoritma rekomendasi, berbenih isi playlist saat ini. */
    fun refreshSuggestions() {
        val id = playlistId.value
        if (id == NO_PLAYLIST) return
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            _suggesting.value = true
            val current = source(id).first()
            _suggestions.value = runCatching { repository.recommendations.suggestionsFor(current) }
                .getOrDefault(emptyList())
            _suggesting.value = false
        }
    }

    fun add(song: Song) {
        val id = playlistId.value
        if (id == NO_PLAYLIST) return
        _suggestions.value = _suggestions.value.filterNot { it.id == song.id }
        viewModelScope.launch {
            if (id == FAVORITES_PLAYLIST_ID) {
                if (!repository.isFavorite(song.id).first()) repository.toggleFavorite(song)
            } else {
                repository.addToPlaylist(id, song)
            }
        }
    }

    fun remove(song: Song) {
        val id = playlistId.value
        if (id == NO_PLAYLIST) return
        viewModelScope.launch {
            if (id == FAVORITES_PLAYLIST_ID) {
                if (repository.isFavorite(song.id).first()) repository.toggleFavorite(song)
            } else {
                repository.removeFromPlaylist(id, song.id)
            }
        }
    }

    fun rename(name: String) {
        val id = playlistId.value
        if (id < 0 || name.isBlank()) return
        viewModelScope.launch { repository.renamePlaylist(id, name.trim()) }
    }

    fun delete() {
        val id = playlistId.value
        if (id < 0) return
        viewModelScope.launch { repository.deletePlaylist(id) }
    }

    fun downloadAll(songs: List<Song>) {
        viewModelScope.launch {
            songs.forEach { repository.enqueueDownload(it) }
            DownloadService.start(getApplication())
        }
    }
}
