package org.avmedia.remotevideocam.compose

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import org.avmedia.remotevideocam.MainViewModel
import org.avmedia.remotevideocam.theme.RemoteVideoCamTheme

@Composable
fun RemoteVideoCamApp(mainViewModel: MainViewModel = viewModel()) {
    val currentScreen by mainViewModel.currentScreen.collectAsState()

    RemoteVideoCamTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                when (screen) {
                    "waiting" -> HomeScreen(onSettingsClick = { mainViewModel.showSettings() })
                    "main" ->
                            MainSelectionScreen(
                                    onStartCamera = { mainViewModel.startCamera() },
                                    onStartDisplay = { mainViewModel.startDisplay() }
                            )
                    "camera" -> CameraScreen()
                    "display" -> DisplayScreen()
                    else -> HomeScreen(onSettingsClick = { mainViewModel.showSettings() })
                }
            }

            if (mainViewModel.showSettingsDialog.collectAsState().value) {
                ConnectionDialog(
                        onDismiss = { mainViewModel.hideSettings() },
                        onOptionSelected = { type -> mainViewModel.setConnectionStrategy(type) }
                )
            }
        }
    }
}
