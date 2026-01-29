package org.avmedia.remotevideocam.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.avmedia.remotevideocam.R
import org.avmedia.remotevideocam.ui.components.*

/** Waiting screen displayed while connecting to another device */
@Composable
fun WaitingScreen(
        onSettingsClick: () -> Unit,
        onExitClick: () -> Unit,
        modifier: Modifier = Modifier
) {
        val infiniteTransition = rememberInfiniteTransition(label = "waiting")

        GradientBackground(modifier = modifier.fillMaxSize()) {
                Column(
                        modifier =
                                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        AnimatedEntrance(delay = 100) {
                                ModernHeader(
                                        title = "Connecting",
                                        subtitle = "Searching for nearby devices...",
                                        icon = Icons.Default.Wifi
                                )
                        }

                        Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                        ) {
                                // Radar rings
                                repeat(3) { index ->
                                        val delay = index * 600
                                        val scale by
                                                infiniteTransition.animateFloat(
                                                        initialValue = 0.2f,
                                                        targetValue = 2.5f,
                                                        animationSpec =
                                                                infiniteRepeatable(
                                                                        animation =
                                                                                tween(
                                                                                        1800,
                                                                                        delayMillis =
                                                                                                delay,
                                                                                        easing =
                                                                                                LinearEasing
                                                                                ),
                                                                        repeatMode =
                                                                                RepeatMode.Restart
                                                                ),
                                                        label = "ring_$index"
                                                )
                                        val alpha by
                                                infiniteTransition.animateFloat(
                                                        initialValue = 0.6f,
                                                        targetValue = 0f,
                                                        animationSpec =
                                                                infiniteRepeatable(
                                                                        animation =
                                                                                tween(
                                                                                        1800,
                                                                                        delayMillis =
                                                                                                delay,
                                                                                        easing =
                                                                                                LinearEasing
                                                                                ),
                                                                        repeatMode =
                                                                                RepeatMode.Restart
                                                                ),
                                                        label = "alpha_$index"
                                                )

                                        Box(
                                                modifier =
                                                        Modifier.size(200.dp)
                                                                .graphicsLayer {
                                                                        scaleX = scale
                                                                        scaleY = scale
                                                                        this.alpha = alpha
                                                                }
                                                                .background(
                                                                        brush =
                                                                                Brush.radialGradient(
                                                                                        colors =
                                                                                                listOf(
                                                                                                        MaterialTheme
                                                                                                                .colorScheme
                                                                                                                .primary
                                                                                                                .copy(
                                                                                                                        alpha =
                                                                                                                                0.3f
                                                                                                                ),
                                                                                                        Color.Transparent
                                                                                                )
                                                                                ),
                                                                        shape = CircleShape
                                                                )
                                        )
                                }

                                // Center animated icon
                                val centerPulse by
                                        infiniteTransition.animateFloat(
                                                initialValue = 1f,
                                                targetValue = 1.15f,
                                                animationSpec =
                                                        infiniteRepeatable(
                                                                animation =
                                                                        tween(
                                                                                1000,
                                                                                easing =
                                                                                        FastOutSlowInEasing
                                                                        ),
                                                                repeatMode = RepeatMode.Reverse
                                                        ),
                                                label = "center_pulse"
                                        )

                                Box(
                                        modifier =
                                                Modifier.size(120.dp)
                                                        .graphicsLayer {
                                                                scaleX = centerPulse
                                                                scaleY = centerPulse
                                                        }
                                                        .clip(CircleShape)
                                                        .background(
                                                                brush =
                                                                        Brush.linearGradient(
                                                                                colors =
                                                                                        listOf(
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .primary,
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .secondary
                                                                                        )
                                                                        )
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.BluetoothSearching,
                                                contentDescription = null,
                                                modifier = Modifier.size(48.dp),
                                                tint = Color.White
                                        )
                                }
                        }

                        AnimatedEntrance(delay = 400) {
                                Column(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(
                                                                horizontal = 40.dp,
                                                                vertical = 32.dp
                                                        ),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                        Text(
                                                text =
                                                        stringResource(
                                                                R.string
                                                                        .waiting_got_other_device_to_connect
                                                        ),
                                                style = MaterialTheme.typography.bodyLarge,
                                                textAlign = TextAlign.Center,
                                                color =
                                                        MaterialTheme.colorScheme.onBackground.copy(
                                                                alpha = 0.7f
                                                        )
                                        )

                                        Spacer(modifier = Modifier.height(32.dp))

                                        LoadingDots(color = MaterialTheme.colorScheme.primary)
                                }
                        }

                        Row(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                // Hide the setting menu for now
                                // AnimatedEntrance(delay = 600) {
                                //      SettingsButton(onClick = onSettingsClick)
                                // }
                                AnimatedEntrance(delay = 700) { ExitButton(onClick = onExitClick) }
                        }
                }
        }
}
