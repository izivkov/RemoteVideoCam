package org.avmedia.remotevideocam.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.avmedia.remotevideocam.theme.ElectricViolet
import org.avmedia.remotevideocam.theme.NeonBlue

@Composable
fun MainSelectionScreen(onStartCamera: () -> Unit, onStartDisplay: () -> Unit) {
    Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SelectionCard(
                text = "Camera",
                icon = Icons.Default.Videocam,
                color = NeonBlue,
                onClick = onStartCamera
        )
        Spacer(modifier = Modifier.height(24.dp))
        SelectionCard(
                text = "Display",
                icon = Icons.Default.Monitor,
                color = ElectricViolet,
                onClick = onStartDisplay
        )
    }
}

@Composable
fun SelectionCard(
        text: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        color: Color,
        onClick: () -> Unit
) {
    Card(
            modifier = Modifier.fillMaxWidth().height(160.dp).clickable(onClick = onClick),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))
    ) {
        Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = color,
                    modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                    text = text,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
            )
        }
    }
}
