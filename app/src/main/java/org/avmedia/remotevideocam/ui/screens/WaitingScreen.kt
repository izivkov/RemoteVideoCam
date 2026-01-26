package org.avmedia.remotevideocam.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.avmedia.remotevideocam.R
import org.avmedia.remotevideocam.ui.components.*
import org.avmedia.remotevideocam.ui.theme.WaitingBlue

/** Waiting screen displayed while connecting to another device */
@Composable
fun WaitingScreen(
        onSettingsClick: () -> Unit,
        onExitClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    // Animation for the pulsing radar effect
    val infiniteTransition = rememberInfiniteTransition(label = "waiting")

    val radarScale1 by
            infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.8f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(2000, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Restart
                            ),
                    label = "radar1"
            )

    val radarAlpha1 by
            infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 0f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(2000, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Restart
                            ),
                    label = "radar_alpha1"
            )

    val radarScale2 by
            infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.8f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation =
                                            tween(
                                                    2000,
                                                    delayMillis = 500,
                                                    easing = FastOutSlowInEasing
                                            ),
                                    repeatMode = RepeatMode.Restart
                            ),
                    label = "radar2"
            )

    val radarAlpha2 by
            infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 0f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation =
                                            tween(
                                                    2000,
                                                    delayMillis = 500,
                                                    easing = FastOutSlowInEasing
                                            ),
                                    repeatMode = RepeatMode.Restart
                            ),
                    label = "radar_alpha2"
            )

    val radarScale3 by
            infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.8f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation =
                                            tween(
                                                    2000,
                                                    delayMillis = 1000,
                                                    easing = FastOutSlowInEasing
                                            ),
                                    repeatMode = RepeatMode.Restart
                            ),
                    label = "radar3"
            )

    val radarAlpha3 by
            infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 0f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation =
                                            tween(
                                                    2000,
                                                    delayMillis = 1000,
                                                    easing = FastOutSlowInEasing
                                            ),
                                    repeatMode = RepeatMode.Restart
                            ),
                    label = "radar_alpha3"
            )

    val iconPulse by
            infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.1f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(800, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "icon_pulse"
            )

    GradientBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // Radar animation center
            Box(
                    modifier = Modifier.align(Alignment.Center).size(300.dp),
                    contentAlignment = Alignment.Center
            ) {
                // Radar rings
                Box(
                        modifier =
                                Modifier.size(200.dp)
                                        .graphicsLayer {
                                            scaleX = radarScale1
                                            scaleY = radarScale1
                                            alpha = radarAlpha1
                                        }
                                        .background(
                                                brush =
                                                        Brush.radialGradient(
                                                                colors =
                                                                        listOf(
                                                                                WaitingBlue.copy(
                                                                                        alpha = 0.4f
                                                                                ),
                                                                                WaitingBlue.copy(
                                                                                        alpha = 0f
                                                                                )
                                                                        )
                                                        ),
                                                shape =
                                                        androidx.compose.foundation.shape
                                                                .CircleShape
                                        )
                )

                Box(
                        modifier =
                                Modifier.size(200.dp)
                                        .graphicsLayer {
                                            scaleX = radarScale2
                                            scaleY = radarScale2
                                            alpha = radarAlpha2
                                        }
                                        .background(
                                                brush =
                                                        Brush.radialGradient(
                                                                colors =
                                                                        listOf(
                                                                                WaitingBlue.copy(
                                                                                        alpha = 0.4f
                                                                                ),
                                                                                WaitingBlue.copy(
                                                                                        alpha = 0f
                                                                                )
                                                                        )
                                                        ),
                                                shape =
                                                        androidx.compose.foundation.shape
                                                                .CircleShape
                                        )
                )

                Box(
                        modifier =
                                Modifier.size(200.dp)
                                        .graphicsLayer {
                                            scaleX = radarScale3
                                            scaleY = radarScale3
                                            alpha = radarAlpha3
                                        }
                                        .background(
                                                brush =
                                                        Brush.radialGradient(
                                                                colors =
                                                                        listOf(
                                                                                WaitingBlue.copy(
                                                                                        alpha = 0.4f
                                                                                ),
                                                                                WaitingBlue.copy(
                                                                                        alpha = 0f
                                                                                )
                                                                        )
                                                        ),
                                                shape =
                                                        androidx.compose.foundation.shape
                                                                .CircleShape
                                        )
                )

                // Center icon
                GlassCard(
                        modifier =
                                Modifier.size(100.dp).graphicsLayer {
                                    scaleX = iconPulse
                                    scaleY = iconPulse
                                }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Text content below radar
            Column(
                    modifier = Modifier.align(Alignment.Center).padding(top = 200.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                        text = stringResource(R.string.waiting_got_other_device_to_connect),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                LoadingDots(color = MaterialTheme.colorScheme.primary)
            }

            // Settings button - bottom left
            SettingsButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.align(Alignment.BottomStart)
            )

            // Exit button - bottom right
            ExitButton(onClick = onExitClick, modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}
