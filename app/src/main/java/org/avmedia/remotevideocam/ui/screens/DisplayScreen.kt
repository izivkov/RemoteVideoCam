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
import org.avmedia.remotevideocam.display.customcomponents.VideoViewWebRTC
import org.avmedia.remotevideocam.ui.components.*
import org.avmedia.remotevideocam.ui.theme.ConnectedGreen
import org.avmedia.remotevideocam.utils.ProgressEvents

/** Display screen showing the remote camera feed with controls */
@Composable
fun DisplayScreen(
        onBackClick: () -> Unit,
        onToggleSound: (Boolean) -> Unit,
        onToggleMirror: () -> Unit,
        modifier: Modifier = Modifier,
        isMuted: Boolean = true,
        isMirrored: Boolean = false,
        videoViewWebRTC: VideoViewWebRTC? = null
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // WebRTC Video View
        if (videoViewWebRTC != null) {
            AndroidView(
                    factory = { videoViewWebRTC },
                    modifier =
                            Modifier.fillMaxSize().graphicsLayer {
                                scaleX = if (isMirrored) -1f else 1f
                            },
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
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.Monitor,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.White.copy(alpha = 0.3f)
                    )
                    Text(
                            text = "Waiting for video...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Top bar with back button
        Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedBackButton(onClick = onBackClick)

            Spacer(modifier = Modifier.weight(1f))
        }

        // Connection status indicator - top right
        ConnectionStatusIndicator(
                isConnected = true,
                modifier =
                        Modifier.align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(16.dp)
                                .padding(top = 56.dp)
        )

        // Display mode indicator - bottom right
        DisplayModeIndicator(
                modifier =
                        Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)
        )

        // Bottom controls
        DisplayControls(
                isMuted = isMuted,
                isMirrored = isMirrored,
                onToggleSound = onToggleSound,
                onToggleMirror = onToggleMirror,
                modifier =
                        Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(16.dp)
        )
    }
}

@Composable
private fun DisplayControls(
        isMuted: Boolean,
        isMirrored: Boolean,
        onToggleSound: (Boolean) -> Unit,
        onToggleMirror: () -> Unit,
        modifier: Modifier = Modifier
) {
    val mirrorRotation by
            animateFloatAsState(
                    targetValue = if (isMirrored) 180f else 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "mirror_rotation"
            )

    Column(
            modifier =
                    modifier.clip(RoundedCornerShape(28.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sound toggle button
        GlassIconButton(
                onClick = {
                    val newMuted = !isMuted
                    onToggleSound(newMuted)
                    if (newMuted) {
                        ProgressEvents.onNext(ProgressEvents.Events.Mute)
                    } else {
                        ProgressEvents.onNext(ProgressEvents.Events.Unmute)
                    }
                },
                icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                size = 50.dp,
                iconSize = 26.dp,
                isActive = !isMuted
        )

        // Mirror toggle button
        GlassIconButton(
                onClick = {
                    ProgressEvents.onNext(ProgressEvents.Events.ToggleMirror)
                    onToggleMirror()
                },
                icon = Icons.Default.Flip,
                contentDescription = "Toggle mirror",
                size = 50.dp,
                iconSize = 26.dp,
                isActive = isMirrored
        )
    }
}

@Composable
private fun DisplayModeIndicator(modifier: Modifier = Modifier) {
    Row(
            modifier =
                    modifier.clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
                imageVector = Icons.Default.Monitor,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
        )
        Text(text = "DISPLAY", style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun ConnectionStatusIndicator(isConnected: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "connection")
    val pulseScale by
            infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.2f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(800),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "pulse"
            )

    Row(
            modifier =
                    modifier.clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
                modifier =
                        Modifier.size(10.dp)
                                .graphicsLayer {
                                    scaleX = if (isConnected) pulseScale else 1f
                                    scaleY = if (isConnected) pulseScale else 1f
                                }
                                .background(
                                        color = if (isConnected) ConnectedGreen else Color.Red,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                )
        )
        Text(
                text = if (isConnected) "CONNECTED" else "DISCONNECTED",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
        )
    }
}
