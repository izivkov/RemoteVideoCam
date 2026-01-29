package org.avmedia.remotevideocam.ui

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.system.exitProcess
import org.avmedia.remotevideocam.camera.customcomponents.WebRTCSurfaceView
import org.avmedia.remotevideocam.display.customcomponents.VideoViewWebRTC
import org.avmedia.remotevideocam.ui.components.ConnectionSettingsDialog
import org.avmedia.remotevideocam.ui.navigation.Screen
import org.avmedia.remotevideocam.ui.screens.*
import org.avmedia.remotevideocam.ui.theme.RemoteVideoCamTheme
import org.avmedia.remotevideocam.ui.viewmodel.MainViewModel
import org.avmedia.remotevideocam.utils.ProgressEvents

/** Main App composable that handles screen navigation and state */
@Composable
fun RemoteVideoCamApp(
        viewModel: MainViewModel = viewModel(),
        webRTCSurfaceView: WebRTCSurfaceView? = null,
        videoViewWebRTC: VideoViewWebRTC? = null,
        onReconnect: () -> Unit = {}
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val connectionType by viewModel.connectionType.collectAsState()
    val showConnectionDialog by viewModel.showConnectionDialog.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isMirrored by viewModel.isMirrored.collectAsState()

    val context = LocalContext.current

    // Handle progress events
    LaunchedEffect(Unit) {
        // Subscribe to progress events and update screen accordingly
        // This connects the existing event system to Compose state
    }

    RemoteVideoCamTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Screen content with animations
                AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            val animationSpec =
                                    spring<Float>(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessLow
                                    )
                            val intAnimationSpec =
                                    spring<IntOffset>(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessLow
                                    )

                            when {
                                targetState == Screen.Camera || targetState == Screen.Display -> {
                                    (slideInHorizontally(intAnimationSpec) { it } +
                                            fadeIn(animationSpec)) togetherWith
                                            (slideOutHorizontally(intAnimationSpec) { -it } +
                                                    fadeOut(animationSpec))
                                }
                                initialState == Screen.Camera || initialState == Screen.Display -> {
                                    (slideInHorizontally(intAnimationSpec) { -it } +
                                            fadeIn(animationSpec)) togetherWith
                                            (slideOutHorizontally(intAnimationSpec) { it } +
                                                    fadeOut(animationSpec))
                                }
                                else -> {
                                    fadeIn(tween(500)) togetherWith fadeOut(tween(500))
                                }
                            }
                        },
                        label = "screen_transition"
                ) { screen ->
                    when (screen) {
                        Screen.Waiting -> {
                            WaitingScreen(
                                    onSettingsClick = { viewModel.showConnectionSettings() },
                                    onExitClick = {
                                        (context as? Activity)?.finish()
                                        exitProcess(0)
                                    }
                            )
                        }
                        Screen.Main -> {
                            MainScreen(
                                    onCameraClick = {
                                        ProgressEvents.onNext(ProgressEvents.Events.StartCamera)
                                        viewModel.showCameraScreen()
                                    },
                                    onDisplayClick = {
                                        ProgressEvents.onNext(ProgressEvents.Events.StartDisplay)
                                        viewModel.showDisplayScreen()
                                    },
                                    onExitClick = {
                                        (context as? Activity)?.finish()
                                        exitProcess(0)
                                    }
                            )
                        }
                        Screen.Camera -> {
                            CameraScreen(
                                    onBackClick = {
                                        ProgressEvents.onNext(ProgressEvents.Events.ShowMainScreen)
                                        viewModel.showMainScreen()
                                    },
                                    onFlipCamera = {
                                        // Handled via ProgressEvents
                                    },
                                    webRTCSurfaceView = webRTCSurfaceView
                            )
                        }
                        Screen.Display -> {
                            DisplayScreen(
                                    onBackClick = {
                                        ProgressEvents.onNext(ProgressEvents.Events.ShowMainScreen)
                                        viewModel.showMainScreen()
                                    },
                                    onToggleSound = { muted -> viewModel.toggleMute() },
                                    onToggleMirror = { viewModel.toggleMirror() },
                                    isMuted = isMuted,
                                    isMirrored = isMirrored,
                                    videoViewWebRTC = videoViewWebRTC
                            )
                        }
                    }
                }

                // Connection settings dialog
                ConnectionSettingsDialog(
                        isVisible = showConnectionDialog,
                        currentConnectionType = connectionType,
                        onConnectionTypeSelected = { type ->
                            viewModel.setConnectionType(type)
                            viewModel.showWaitingScreen()
                            onReconnect()
                        },
                        onDismiss = { viewModel.hideConnectionSettings() }
                )
            }
        }
    }
}

/** Wrapper that integrates with the existing MainActivity */
@Composable
fun MainActivityContent(
        viewModel: MainViewModel,
        webRTCSurfaceView: WebRTCSurfaceView?,
        videoViewWebRTC: VideoViewWebRTC?,
        onReconnect: () -> Unit
) {
    RemoteVideoCamApp(
            viewModel = viewModel,
            webRTCSurfaceView = webRTCSurfaceView,
            videoViewWebRTC = videoViewWebRTC,
            onReconnect = onReconnect
    )
}
