package org.avmedia.remotevideocam.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.avmedia.remotevideocam.theme.ElectricViolet
import org.avmedia.remotevideocam.theme.NeonBlue

@Composable
fun HomeScreen(onSettingsClick: () -> Unit) {
    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(
                                    Brush.verticalGradient(
                                            colors = listOf(Color(0xFF0F0F1A), Color(0xFF1C1C2E))
                                    )
                            )
    ) {
        IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
            )
        }

        Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PulsingCircleAnimation()
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                    text = "Waiting for Connection...",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                    text = "Ensure devices are on the same network",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun PulsingCircleAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by
            infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.2f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(1500),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "scale"
            )
    val alpha by
            infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 0.1f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(1500),
                                    repeatMode = RepeatMode.Restart
                            ),
                    label = "alpha"
            )

    Canvas(modifier = Modifier.size(200.dp)) {
        drawCircle(color = NeonBlue.copy(alpha = alpha), radius = size.minDimension / 2 * scale)
        drawCircle(
                color = ElectricViolet,
                radius = size.minDimension / 4,
                style = Stroke(width = 4.dp.toPx())
        )
    }
}
