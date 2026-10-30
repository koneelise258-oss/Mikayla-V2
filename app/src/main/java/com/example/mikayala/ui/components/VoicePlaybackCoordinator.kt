package com.example.mikayala.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinateur global de lecture audio pour l'application Mikayala.
 * Garantit qu'une seule note vocale ou prévisualisation audio joue à la fois
 * dans toute l'application.
 */
object VoicePlaybackCoordinator {
    private val _currentlyPlayingUrl = MutableStateFlow<String?>(null)
    val currentlyPlayingUrl: StateFlow<String?> = _currentlyPlayingUrl.asStateFlow()

    fun requestPlay(url: String) {
        _currentlyPlayingUrl.value = url
    }

    fun notifyStopped(url: String) {
        if (_currentlyPlayingUrl.value == url) {
            _currentlyPlayingUrl.value = null
        }
    }

    fun stopAll() {
        _currentlyPlayingUrl.value = null
    }
}
