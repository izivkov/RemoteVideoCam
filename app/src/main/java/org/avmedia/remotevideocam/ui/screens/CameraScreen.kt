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
            // Placeholder for preview mode
            Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White.copy(alpha = 0.3f)
                )
            }
        }

        // Top bar with back button and recording indicator
        Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedBackButton(onClick = onBackClick)

            // Recording indicator (empty space if needed, indicator is in corner)
            Spacer(modifier = Modifier.weight(1f))
        }

        // Recording indicator - top right corner
        RecordingIndicator(
                isRecording = isRecording,
                modifier =
                        Modifier.align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(16.dp)
                                .padding(top = 56.dp)
        )

        // Camera mode indicator - bottom right
        CameraModeIndicator(
                modifier =
                        Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)
        )

        // Bottom controls
        CameraControls(
                onFlipCamera = onFlipCamera,
                modifier =
                        Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(16.dp)
        )
    }
}

@Composable
private fun CameraControls(onFlipCamera: () -> Unit, modifier: Modifier = Modifier) {
    Column(
            modifier =
                    modifier.clip(RoundedCornerShape(28.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Flip camera button
        GlassIconButton(
                onClick = {
                    ProgressEvents.onNext(ProgressEvents.Events.FlipCamera)
                    onFlipCamera()
                },
                icon = Icons.Default.FlipCameraAndroid,
                contentDescription = "Flip camera",
                size = 50.dp,
                iconSize = 26.dp
        )
    }
}

@Composable
private fun CameraModeIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "camera_indicator")
    val glowAlpha by
            infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(1000),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "glow"
            )

    Row(
            modifier =
                    modifier.clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                modifier = Modifier.size(24.dp).graphicsLayer { alpha = glowAlpha },
                tint = RecordingRed
        )
        Text(text = "CAMERA", style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}
