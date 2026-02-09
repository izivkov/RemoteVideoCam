package org.avmedia.remotevideocam.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.avmedia.remotevideocam.ui.theme.*

/** Entrance animation for components */
@Composable
fun AnimatedEntrance(delay: Int = 0, content: @Composable () -> Unit) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(delay.toLong())
                visible = true
        }

        androidx.compose.animation.AnimatedVisibility(
                visible = visible,
                enter =
                        fadeIn(animationSpec = tween(600)) +
                                slideInVertically(
                                        initialOffsetY = { it / 2 },
                                        animationSpec = tween(600, easing = EaseOutExpo)
                                ),
                exit = fadeOut()
        ) { content() }
}

/** Modern Header component with title and optional icon */
@Composable
fun ModernHeader(
        title: String,
        modifier: Modifier = Modifier,
        subtitle: String? = null,
        icon: ImageVector? = null
) {
        Column(
                modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                if (icon != null) {
                        Box(
                                modifier =
                                        Modifier.size(64.dp)
                                                .clip(CircleShape)
                                                .background(
                                                        MaterialTheme.colorScheme.primaryContainer
                                                                .copy(alpha = 0.3f)
                                                )
                                                .border(
                                                        1.dp,
                                                        Color.White.copy(alpha = 0.2f),
                                                        CircleShape
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                        text = title,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                )

                if (subtitle != null) {
                        Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                }
        }
}

/** Glassmorphism card with blurred background effect */
@Composable
fun GlassCard(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        content: @Composable BoxScope.() -> Unit
) {
        val haptic = LocalHapticFeedback.current
        Box(
                modifier =
                        modifier.clip(RoundedCornerShape(24.dp))
                                .background(
                                        brush =
                                                Brush.verticalGradient(
                                                        colors =
                                                                listOf(
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                                                .copy(alpha = 0.7f),
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                                                .copy(alpha = 0.4f)
                                                                )
                                                )
                                )
                                .border(
                                        width = 1.dp,
                                        brush =
                                                Brush.verticalGradient(
                                                        colors =
                                                                listOf(
                                                                        Color.White.copy(
                                                                                alpha = 0.3f
                                                                        ),
                                                                        Color.White.copy(
                                                                                alpha = 0.1f
                                                                        )
                                                                )
                                                ),
                                        shape = RoundedCornerShape(24.dp)
                                )
                                .then(
                                        if (onClick != null) {
                                                Modifier.clickable(
                                                        interactionSource =
                                                                remember {
                                                                        MutableInteractionSource()
                                                                },
                                                        indication = ripple(bounded = true)
                                                ) {
                                                        haptic.performHapticFeedback(
                                                                HapticFeedbackType.LongPress
                                                        )
                                                        onClick()
                                                }
                                        } else Modifier
                                ),
                content = content
        )
}

/** Animated floating action button with pulse effect */
@Composable
fun PulsingFab(
        onClick: () -> Unit,
        icon: ImageVector,
        modifier: Modifier = Modifier,
        containerColor: Color = MaterialTheme.colorScheme.primary,
        contentColor: Color = MaterialTheme.colorScheme.onPrimary,
        size: Dp = 64.dp,
        showPulse: Boolean = true
) {
        val haptic = LocalHapticFeedback.current
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseAnimation by
                infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.2f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(1000, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "pulse_scale"
                )

        val pulseAlpha by
                infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 0f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(1000, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "pulse_alpha"
                )

        Box(modifier = modifier, contentAlignment = Alignment.Center) {
                // Pulse ring
                if (showPulse) {
                        Box(
                                modifier =
                                        Modifier.size(size)
                                                .scale(pulseAnimation)
                                                .background(
                                                        color =
                                                                containerColor.copy(
                                                                        alpha = pulseAlpha
                                                                ),
                                                        shape = CircleShape
                                                )
                        )
                }

                // Main FAB
                FloatingActionButton(
                        onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onClick()
                        },
                        modifier = Modifier.size(size),
                        containerColor = containerColor,
                        contentColor = contentColor,
                        shape = CircleShape,
                        elevation =
                                FloatingActionButtonDefaults.elevation(
                                        defaultElevation = 8.dp,
                                        pressedElevation = 12.dp
                                )
                ) {
                        Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(size * 0.45f)
                        )
                }
        }
}

/** Icon button with glassmorphism effect */
@Composable
fun GlassIconButton(
        onClick: () -> Unit,
        icon: ImageVector,
        modifier: Modifier = Modifier,
        contentDescription: String? = null,
        size: Dp = 56.dp,
        iconSize: Dp = 28.dp,
        isActive: Boolean = false
) {
        val haptic = LocalHapticFeedback.current
        val scale by
                animateFloatAsState(
                        targetValue = if (isActive) 1.1f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "button_scale"
                )

        val backgroundColor =
                if (isActive) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }

        val contentColor =
                if (isActive) {
                        MaterialTheme.colorScheme.onPrimary
                } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                }

        Box(
                modifier =
                        modifier.size(size)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(backgroundColor)
                                .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.2f),
                                        shape = CircleShape
                                )
                                .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = true)
                                ) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onClick()
                                },
                contentAlignment = Alignment.Center
        ) {
                Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        modifier = Modifier.size(iconSize),
                        tint = contentColor
                )
        }
}

/** Back button with animation */
@Composable
fun AnimatedBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
        GlassIconButton(
                onClick = onClick,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                modifier = modifier,
                contentDescription = "Go back",
                size = 48.dp,
                iconSize = 24.dp
        )
}

/** Exit button */
@Composable
fun ExitButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
        GlassIconButton(
                onClick = onClick,
                icon = Icons.Default.Close,
                modifier = modifier,
                contentDescription = "Exit",
                size = 48.dp,
                iconSize = 24.dp
        )
}

/** Settings button with gear icon */
@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
        val infiniteTransition = rememberInfiniteTransition(label = "settings")
        val rotation by
                infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(8000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                ),
                        label = "settings_rotation"
                )

        GlassIconButton(
                onClick = onClick,
                icon = Icons.Default.Settings,
                modifier = modifier.graphicsLayer { rotationZ = rotation },
                contentDescription = "Settings",
                size = 48.dp,
                iconSize = 24.dp
        )
}

/** Recording indicator with animated pulse */
@Composable
fun RecordingIndicator(isRecording: Boolean, modifier: Modifier = Modifier) {
        val infiniteTransition = rememberInfiniteTransition(label = "recording")
        val alpha by
                infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.3f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(500),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "recording_alpha"
                )

        if (isRecording) {
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
                                        Modifier.size(12.dp)
                                                .graphicsLayer { this.alpha = alpha }
                                                .background(RecordingRed, CircleShape)
                        )
                        Text(
                                text = "REC",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                        )
                }
        }
}

/** Loading indicator with animated dots */
@Composable
fun LoadingDots(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
        val infiniteTransition = rememberInfiniteTransition(label = "loading")

        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { index ->
                        val delay = index * 200
                        val offsetY by
                                infiniteTransition.animateFloat(
                                        initialValue = 0f,
                                        targetValue = -10f,
                                        animationSpec =
                                                infiniteRepeatable(
                                                        animation =
                                                                tween(
                                                                        durationMillis = 400,
                                                                        delayMillis = delay,
                                                                        easing = FastOutSlowInEasing
                                                                ),
                                                        repeatMode = RepeatMode.Reverse
                                                ),
                                        label = "dot_$index"
                                )

                        Box(
                                modifier =
                                        Modifier.size(12.dp)
                                                .graphicsLayer { translationY = offsetY }
                                                .background(color, CircleShape)
                        )
                }
        }
}

/** Gradient background for full screen */
@Composable
fun GradientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
        Box(
                modifier =
                        modifier.fillMaxSize()
                                .background(
                                        brush =
                                                Brush.radialGradient(
                                                        colors =
                                                                listOf(
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.15f
                                                                        ),
                                                                        MaterialTheme.colorScheme
                                                                                .background
                                                                ),
                                                        radius = 1000f
                                                )
                                ),
                content = content
        )
}
