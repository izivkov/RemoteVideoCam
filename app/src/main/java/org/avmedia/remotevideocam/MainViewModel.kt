package org.avmedia.remotevideocam

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.avmedia.remotevideocam.camera.ConnectionStrategy
import org.avmedia.remotevideocam.utils.ProgressEvents

class MainViewModel : ViewModel() {
    private val _currentScreen = MutableStateFlow("waiting")
    val currentScreen = _currentScreen.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog = _showSettingsDialog.asStateFlow()

    private var eventsDisposable: Disposable? = null

    init {
        eventsDisposable =
                ProgressEvents.connectionEventFlowable.subscribe { event ->
                    when (event) {
                        ProgressEvents.Events.ShowWaitingForConnectionScreen ->
                                _currentScreen.value = "waiting"

                        // Connection successful usually leads to Main Screen selection
                        ProgressEvents.Events.ConnectionCameraSuccessful ->
                                _currentScreen.value = "main"
                        ProgressEvents.Events.ConnectionDisplaySuccessful ->
                                _currentScreen.value = "main"
                        ProgressEvents.Events.ShowMainScreen -> _currentScreen.value = "main"
                        ProgressEvents.Events.StartDisplay -> _currentScreen.value = "display"
                        ProgressEvents.Events.StartCamera -> _currentScreen.value = "camera"
                        ProgressEvents.Events.DisplayDisconnected ->
                                _currentScreen.value = "waiting"
                        ProgressEvents.Events.CameraDisconnected -> _currentScreen.value = "waiting"
                    }
                }
    }

    fun showSettings() {
        _showSettingsDialog.value = true
    }
    fun hideSettings() {
        _showSettingsDialog.value = false
    }

    fun setConnectionStrategy(type: ConnectionStrategy.ConnectionType) {
        ConnectionStrategy.setSelectedType(type)
        ProgressEvents.onNext(ProgressEvents.Events.ShowWaitingForConnectionScreen)
    }

    fun startCamera() {
        ProgressEvents.onNext(ProgressEvents.Events.StartCamera)
    }

    fun startDisplay() {
        ProgressEvents.onNext(ProgressEvents.Events.StartDisplay)
    }

    override fun onCleared() {
        super.onCleared()
        eventsDisposable?.dispose()
    }
}
