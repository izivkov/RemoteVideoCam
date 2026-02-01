package org.avmedia.remotevideocam.ui.screens

import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.avmedia.remotevideocam.camera.customcomponents.WebRTCSurfaceView
import org.avmedia.remotevideocam.ui.components.*
import org.avmedia.remotevideocam.ui.theme.RecordingRed
import org.avmedia.remotevideocam.utils.ProgressEvents

/** Camera screen showing the camera preview with controls */
@Composable
fun CameraScreen(
        onBackClick: () -> Unit,
        onFlipCamera: () -> Unit,
        modifier: Modifier = Modifier,
        webRTCSurfaceView: WebRTCSurfaceView? = null
) {
        var isRecording by remember { mutableStateOf(true) }

        Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
                // WebRTC Surface View
                if (webRTCSurfaceView != null) {
                        AndroidView(
                                factory = { webRTCSurfaceView },
                                modifier = Modifier.fillMaxSize(),
                                update = { view ->
                                        view.layoutParams =
                                                ViewGroup.LayoutParams(
                                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                                        ViewGroup.LayoutParams.MATCH_PARENT
                                                )
                                }
                        )
                } else {
                        Box(
                                modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        modifier = Modifier.size(80.dp),
                                        tint = Color.White.copy(alpha = 0.1f)
                                )
                        }
                }

                // Top UI Overlay
                Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                        ) {
                                AnimatedEntrance(delay = 200) {
                                        AnimatedBackButton(onClick = onBackClick)
                                }

                                AnimatedEntrance(delay = 400) {
                                        RecordingIndicator(isRecording = isRecording)
                                }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Bottom UI Overlay
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .navigationBarsPadding()
                                                .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                        ) {
                                AnimatedEntrance(delay = 600) {
                                        CameraControls(onFlipCamera = onFlipCamera)
                                }

                                AnimatedEntrance(delay = 800) { CameraModeIndicator() }
                        }
                }
        }
}

@Composable
private fun CameraControls(onFlipCamera: () -> Unit, modifier: Modifier = Modifier) {
        Box(
                modifier =
                        modifier.clip(RoundedCornerShape(32.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .padding(8.dp)
        ) {
                GlassIconButton(
                        onClick = {
                                ProgressEvents.onNext(ProgressEvents.Events.FlipCamera)
                                onFlipCamera()
                        },
                        icon = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Flip camera",
                        size = 64.dp,
                        iconSize = 32.dp
                )
        }
}

@Composable
private fun CameraModeIndicator(modifier: Modifier = Modifier) {
        val infiniteTransition = rememberInfiniteTransition(label = "camera_indicator")
        val glowAlpha by
                infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(1200),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "glow"
                )

        Row(
                modifier =
                        modifier.clip(RoundedCornerShape(24.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
                Box(
                        modifier =
                                Modifier.size(10.dp)
                                        .graphicsLayer { alpha = glowAlpha }
                                        .background(
                                                RecordingRed,
                                                androidx.compose.foundation.shape.CircleShape
                                        )
                )
                Text(
                        text = "CAMERA MODE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                )
        }
}
