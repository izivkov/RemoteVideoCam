package org.avmedia.remotevideocam.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.avmedia.remotevideocam.R
import org.avmedia.remotevideocam.ui.components.ExitButton
import org.avmedia.remotevideocam.ui.components.GradientBackground

/** Main selection screen with Camera and Display options */
@Composable
fun MainScreen(
        onCameraClick: () -> Unit,
        onDisplayClick: () -> Unit,
        onExitClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    GradientBackground(modifier = modifier.fillMaxSize()) {
        Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Camera panel - top half
            ModeSelectionCard(
                    title = stringResource(R.string.camera),
                    subtitle = "Capture & stream video",
                    icon = Icons.Default.Videocam,
                    gradientColors =
                            listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                            ),
                    onClick = onCameraClick,
                    modifier = Modifier.weight(1f).fillMaxWidth()
            )

            // Display panel - bottom half
            ModeSelectionCard(
                    title = stringResource(R.string.display),
                    subtitle = "View remote camera feed",
                    icon = Icons.Default.Monitor,
                    gradientColors =
                            listOf(
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.primary
                            ),
                    onClick = onDisplayClick,
                    modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }

        // Exit button - bottom right corner
        ExitButton(
                onClick = onExitClick,
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        )
    }
}

@Composable
private fun ModeSelectionCard(
        title: String,
        subtitle: String,
        icon: ImageVector,
        gradientColors: List<Color>,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    val scale by
            animateFloatAsState(
                    targetValue = if (isPressed) 0.97f else 1f,
                    animationSpec =
                            spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                            ),
                    label = "card_scale"
            )

    val infiniteTransition = rememberInfiniteTransition(label = "icon_animation")
    val iconScale by
            infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.05f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(1500, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "icon_scale"
            )

    val shimmerProgress by
            infiniteTransition.animateFloat(
                    initialValue = -1f,
                    targetValue = 2f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(2500, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                            ),
                    label = "shimmer"
            )

    Box(
            modifier =
                    modifier.scale(scale)
                            .clip(RoundedCornerShape(32.dp))
                            .background(
                                    brush =
                                            Brush.linearGradient(
                                                    colors =
                                                            gradientColors.map {
                                                                it.copy(alpha = 0.85f)
                                                            }
                                            )
                            )
                            .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, color = Color.White)
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onClick()
                            }
    ) {
        // Shimmer overlay
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .graphicsLayer { translationX = shimmerProgress * 500f }
                                .background(
                                        brush =
                                                Brush.horizontalGradient(
                                                        colors =
                                                                listOf(
                                                                        Color.Transparent,
                                                                        Color.White.copy(
                                                                                alpha = 0.15f
                                                                        ),
                                                                        Color.Transparent
                                                                ),
                                                        startX = 0f,
                                                        endX = 200f
                                                )
                                )
        )

        // Content
        Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon with glow effect
            Box(contentAlignment = Alignment.Center) {
                // Glow
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier =
                                Modifier.size(100.dp).graphicsLayer {
                                    scaleX = iconScale * 1.3f
                                    scaleY = iconScale * 1.3f
                                    alpha = 0.3f
                                },
                        tint = Color.White
                )

                // Main icon
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier =
                                Modifier.size(80.dp).graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                        tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
