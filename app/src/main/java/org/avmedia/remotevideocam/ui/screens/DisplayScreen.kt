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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import org.avmedia.remotevideocam.display.customcomponents.VideoViewWebRTC
import org.avmedia.remotevideocam.ui.components.*
import org.avmedia.remotevideocam.ui.theme.ConnectedGreen
import org.avmedia.remotevideocam.utils.ProgressEvents

/** Time format for "last update", easy to tweak */
private const val LAST_FRAME_TIME_PATTERN = "HH:mm:ss"

/** Placeholder text shown when no video frame has been received yet */
private const val NO_LAST_FRAME_TIME_TEXT = "--:--:--"

/** Warning color used when the picture is frozen */
private val FreezeAmber = Color(0xFFFFB300)

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
        // Subscribe only to "milliseconds since last frame": 0 while playing (StateFlow deduplicates), updated every 250ms, so 30fps never causes recomposition
        val msSinceLastFrame by
                remember(videoViewWebRTC) {
                        videoViewWebRTC?.msSinceLastFrame ?: MutableStateFlow<Long?>(null)
                }
                        .collectAsState()

        // No new frame beyond the threshold means the picture is frozen; null means no frame yet, so no alert (the screen is black anyway and cannot be mistaken for live video)
        val isFrameStalled = (msSinceLastFrame ?: 0L) >= VideoViewWebRTC.FRAME_FREEZE_TIMEOUT_MS

        // Only a real received frame counts as "has picture": a connection with zero frames must not show LIVE
        val hasVideoFrames = msSinceLastFrame != null

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
                        Box(
                                modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)),
                                contentAlignment = Alignment.Center
                        ) {
                                Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Monitor,
                                                contentDescription = null,
                                                modifier = Modifier.size(80.dp),
                                                tint = Color.White.copy(alpha = 0.1f)
                                        )
                                        Text(
                                                text = "Establishing connection...",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = Color.White.copy(alpha = 0.3f)
                                        )
                                        LoadingDots(
                                                color =
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.3f
                                                        )
                                        )
                                }
                        }
                }

                // Freeze alert (dim the picture and show the time of the last real frame)
                FreezeAlertOverlay(
                        videoViewWebRTC = videoViewWebRTC,
                        isFrameStalled = isFrameStalled
                )

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
                                        ConnectionStatusIndicator(
                                                hasVideoFrames = hasVideoFrames,
                                                isFrameStalled = isFrameStalled
                                        )
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
                                        DisplayControls(
                                                isMuted = isMuted,
                                                isMirrored = isMirrored,
                                                onToggleSound = onToggleSound,
                                                onToggleMirror = onToggleMirror
                                        )
                                }

                                AnimatedEntrance(delay = 800) { DisplayModeIndicator() }
                        }
                }
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
        Row(
                modifier =
                        modifier.clip(RoundedCornerShape(32.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                GlassIconButton(
                        onClick = {
                                val newMuted = !isMuted
                                onToggleSound(newMuted)
                                ProgressEvents.onNext(
                                        if (newMuted) ProgressEvents.Events.Mute
                                        else ProgressEvents.Events.Unmute
                                )
                        },
                        icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        size = 56.dp,
                        iconSize = 28.dp,
                        isActive = !isMuted
                )

                GlassIconButton(
                        onClick = {
                                ProgressEvents.onNext(ProgressEvents.Events.ToggleMirror)
                                onToggleMirror()
                        },
                        icon = Icons.Default.Flip,
                        contentDescription = "Toggle mirror",
                        size = 56.dp,
                        iconSize = 28.dp,
                        isActive = isMirrored
                )
        }
}

@Composable
private fun DisplayModeIndicator(modifier: Modifier = Modifier) {
        Row(
                modifier =
                        modifier.clip(RoundedCornerShape(24.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
                Icon(
                        imageVector = Icons.Default.Monitor,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                        text = "REMOTE VIEW",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                )
        }
}

@Composable
private fun ConnectionStatusIndicator(
        hasVideoFrames: Boolean,
        isFrameStalled: Boolean = false,
        modifier: Modifier = Modifier
) {
        // Only "actually receiving frames" counts as LIVE: never show a green LIVE while the picture is frozen or no frame arrived
        val isLive = hasVideoFrames && !isFrameStalled

        val infiniteTransition = rememberInfiniteTransition(label = "connection")
        val pulseScale by
                infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.25f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(1000, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "pulse"
                )

        val dotColor =
                when {
                        isFrameStalled -> FreezeAmber
                        hasVideoFrames -> ConnectedGreen
                        else -> Color.Red
                }
        val statusText =
                when {
                        isFrameStalled -> "STALLED"
                        hasVideoFrames -> "LIVE"
                        else -> "OFFLINE"
                }

        Row(
                modifier =
                        modifier.clip(RoundedCornerShape(20.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                Box(
                        modifier =
                                Modifier.size(8.dp)
                                        .graphicsLayer {
                                                scaleX = if (isLive) pulseScale else 1f
                                                scaleY = if (isLive) pulseScale else 1f
                                        }
                                        .background(
                                                color = dotColor,
                                                shape =
                                                        androidx.compose.foundation.shape
                                                                .CircleShape
                                        )
                )
                Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                )
        }
}

/**
 * Freeze alert: dim the picture and show the time of the last video frame actually received.
 * The HH:mm:ss here is the wall-clock time of the last frame, not the current time, so a ticking clock can never mask a dead picture.
 */
@Composable
private fun FreezeAlertOverlay(
        videoViewWebRTC: VideoViewWebRTC?,
        isFrameStalled: Boolean,
        modifier: Modifier = Modifier
) {
        // This timestamp is only needed for the alert; the per-second StateFlow triggers at most one recomposition per second
        val lastFrameTimestampMs by
                remember(videoViewWebRTC) {
                        videoViewWebRTC?.lastFrameTimestampMs ?: MutableStateFlow(0L)
                }
                        .collectAsState()

        val lastUpdateText =
                remember(lastFrameTimestampMs) { formatLastFrameTime(lastFrameTimestampMs) }

        AnimatedVisibility(
                visible = isFrameStalled,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
                modifier = modifier
        ) {
                Box(
                        modifier =
                                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                ) {
                        Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = FreezeAmber
                                )
                                Text(
                                        text = "Video paused - last update $lastUpdateText",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                )
                        }
                }
        }
}

private fun formatLastFrameTime(timestampMs: Long): String {
        if (timestampMs <= 0L) return NO_LAST_FRAME_TIME_TEXT
        return SimpleDateFormat(LAST_FRAME_TIME_PATTERN, Locale.getDefault()).format(Date(timestampMs))
}
