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
import org.avmedia.remotevideocam.ui.components.*

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
                        modifier =
                                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                ) {
                        AnimatedEntrance(delay = 100) {
                                ModernHeader(
                                        title = "Remote Cam",
                                        subtitle = "High-quality remote video streaming",
                                        icon = Icons.Default.Camera
                                )
                        }

                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(horizontal = 24.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                                AnimatedEntrance(delay = 300) {
                                        ModernActionCard(
                                                title = stringResource(R.string.camera),
                                                subtitle =
                                                        "Stream your device's camera to another device securely",
                                                icon = Icons.Default.Videocam,
                                                primaryColor = MaterialTheme.colorScheme.primary,
                                                secondaryColor = MaterialTheme.colorScheme.tertiary,
                                                onClick = onCameraClick
                                        )
                                }

                                AnimatedEntrance(delay = 500) {
                                        ModernActionCard(
                                                title = stringResource(R.string.display),
                                                subtitle =
                                                        "View and record feeds from remote devices in real-time",
                                                icon = Icons.Default.Monitor,
                                                primaryColor = MaterialTheme.colorScheme.secondary,
                                                secondaryColor = MaterialTheme.colorScheme.primary,
                                                onClick = onDisplayClick
                                        )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                AnimatedEntrance(delay = 700) {
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(bottom = 16.dp),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                ExitButton(
                                                        onClick = onExitClick,
                                                        modifier = Modifier.scale(1.2f)
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
private fun ModernActionCard(
        title: String,
        subtitle: String,
        icon: ImageVector,
        primaryColor: Color,
        secondaryColor: Color,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
        val haptic = LocalHapticFeedback.current
        var isPressed by remember { mutableStateOf(false) }

        val ICON_ALPHA_LOW = 0.2f
        val ICON_SCALE_FACTOR = 1.5f

        val scale by
                animateFloatAsState(
                        targetValue = if (isPressed) 0.96f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "card_scale"
                )

        val elevation by
                animateDpAsState(
                        targetValue = if (isPressed) 2.dp else 8.dp,
                        label = "card_elevation"
                )

        Surface(
                modifier =
                        modifier.fillMaxWidth()
                                .scale(scale)
                                .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                                haptic.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                )
                                                onClick()
                                        }
                                ),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = elevation,
                shadowElevation = elevation
        ) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(
                                                brush =
                                                        Brush.linearGradient(
                                                                colors =
                                                                        listOf(
                                                                                primaryColor.copy(
                                                                                        alpha =
                                                                                                0.15f
                                                                                ),
                                                                                secondaryColor.copy(
                                                                                        alpha =
                                                                                                0.05f
                                                                                )
                                                                        )
                                                        )
                                        )
                                        .padding(24.dp)
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                                // Icon with layered background
                                Box(
                                        modifier =
                                                Modifier.size(80.dp)
                                                        .clip(RoundedCornerShape(24.dp))
                                                        .background(
                                                                brush =
                                                                        Brush.linearGradient(
                                                                                colors =
                                                                                        listOf(
                                                                                                primaryColor,
                                                                                                secondaryColor
                                                                                        )
                                                                        )
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        // Subtle background icon
                                        Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier
                                                        .size(60.dp)
                                                        .graphicsLayer {
                                                                this.alpha = ICON_ALPHA_LOW
                                                                this.scaleX = ICON_SCALE_FACTOR
                                                                this.scaleY = ICON_SCALE_FACTOR
                                                        },
                                                tint = Color.White
                                        )
                                        // Foreground icon
                                        Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(36.dp),
                                                tint = Color.White
                                        )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                text = title,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }

                                Icon(
                                        imageVector = Icons.Default.ArrowForwardIos,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint =
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.5f
                                                )
                                )
                        }
                }
        }
}
