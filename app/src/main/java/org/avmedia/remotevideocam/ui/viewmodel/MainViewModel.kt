package org.avmedia.remotevideocam.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.avmedia.remotevideocam.camera.ConnectionStrategy
import org.avmedia.remotevideocam.ui.navigation.Screen

/** Main ViewModel managing UI state and screen navigation */
class MainViewModel : ViewModel() {

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Waiting)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _connectionType = MutableStateFlow(ConnectionStrategy.getSelectedType())
    val connectionType: StateFlow<ConnectionStrategy.ConnectionType> = _connectionType.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _showConnectionDialog = MutableStateFlow(false)
    val showConnectionDialog: StateFlow<Boolean> = _showConnectionDialog.asStateFlow()

    // Camera screen state
    private val _isMuted = MutableStateFlow(true)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isMirrored = MutableStateFlow(false)
    val isMirrored: StateFlow<Boolean> = _isMirrored.asStateFlow()

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun showWaitingScreen() {
        _currentScreen.value = Screen.Waiting
    }

    fun showMainScreen() {
        _currentScreen.value = Screen.Main
    }

    fun showCameraScreen() {
        _currentScreen.value = Screen.Camera
    }

    fun showDisplayScreen() {
        _currentScreen.value = Screen.Display
    }

    fun setConnectionType(type: ConnectionStrategy.ConnectionType) {
        _connectionType.value = type
        ConnectionStrategy.setSelectedType(type)
    }

    fun showConnectionSettings() {
        _showConnectionDialog.value = true
    }

    fun hideConnectionSettings() {
        _showConnectionDialog.value = false
    }

    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun toggleMirror() {
        _isMirrored.value = !_isMirrored.value
    }
}
