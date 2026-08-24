package com.jamalsquad.jamalify.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.JamalifyApp
import com.jamalsquad.jamalify.data.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@UnstableApi
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as JamalifyApp).repository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<Song>>(emptyList())
    val results: StateFlow<List<Song>> = _results.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        _query.value = value
        searchJob?.cancel()
        if (value.isBlank()) {
            _results.value = emptyList()
            _loading.value = false
            _error.value = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(400) // tunggu ketikan berhenti sebelum menembak jaringan
            runSearch(value)
        }
    }

    fun submit() {
        searchJob?.cancel()
        val value = _query.value
        if (value.isNotBlank()) {
            searchJob = viewModelScope.launch { runSearch(value) }
        }
    }

    private suspend fun runSearch(value: String) {
        _loading.value = true
        _error.value = null
        runCatching { repository.search(value) }
            .onSuccess { songs ->
                _results.value = songs
                if (songs.isEmpty()) _error.value = "Tidak ada hasil untuk \"$value\""
            }
            .onFailure {
                _results.value = emptyList()
                _error.value = "Pencarian gagal: ${it.message ?: "kesalahan jaringan"}"
            }
        _loading.value = false
    }
}
