package org.avmedia.remotevideocam.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.avmedia.remotevideocam.R
import org.avmedia.remotevideocam.camera.ConnectionStrategy

/** Modern bottom sheet dialog for connection type selection */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsDialog(
        isVisible: Boolean,
        currentConnectionType: ConnectionStrategy.ConnectionType,
        onConnectionTypeSelected: (ConnectionStrategy.ConnectionType) -> Unit,
        onDismiss: () -> Unit
) {
        if (isVisible) {
                ModalBottomSheet(
                        onDismissRequest = onDismiss,
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        dragHandle = {
                                Box(
                                        modifier =
                                                Modifier.padding(vertical = 12.dp)
                                                        .width(48.dp)
                                                        .height(6.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant.copy(
                                                                        alpha = 0.2f
                                                                )
                                                        )
                                )
                        }
                ) {
                        ConnectionSettingsContent(
                                currentConnectionType = currentConnectionType,
                                onConnectionTypeSelected = { type ->
                                        onConnectionTypeSelected(type)
                                        onDismiss()
                                },
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 24.dp)
                                                .padding(bottom = 40.dp)
                        )
                }
        }
}

@Composable
private fun ConnectionSettingsContent(
        currentConnectionType: ConnectionStrategy.ConnectionType,
        onConnectionTypeSelected: (ConnectionStrategy.ConnectionType) -> Unit,
        modifier: Modifier = Modifier
) {
        Column(
                modifier = modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                ModernHeader(
                        title = stringResource(R.string.connection_type),
                        subtitle = "Choose how devices will find each other",
                        icon = Icons.Default.SettingsEthernet
                )

                Spacer(modifier = Modifier.height(8.dp))

                ConnectionOptionCard(
                        title = stringResource(R.string.connection_auto),
                        description = "Intelligent selection based on network conditions",
                        icon = Icons.Default.AutoAwesome,
                        isSelected =
                                currentConnectionType == ConnectionStrategy.ConnectionType.AUTO,
                        onClick = {
                                onConnectionTypeSelected(ConnectionStrategy.ConnectionType.AUTO)
                        }
                )

                ConnectionOptionCard(
                        title = stringResource(R.string.connection_network),
                        description = "Standard connection via your local WiFi network",
                        icon = Icons.Default.Wifi,
                        isSelected =
                                currentConnectionType == ConnectionStrategy.ConnectionType.NETWORK,
                        onClick = {
                                onConnectionTypeSelected(ConnectionStrategy.ConnectionType.NETWORK)
                        }
                )

                ConnectionOptionCard(
                        title = stringResource(R.string.connection_wifi_direct),
                        description = "Direct peer-to-peer connection without a router",
                        icon = Icons.Default.WifiTethering,
                        isSelected =
                                currentConnectionType ==
                                        ConnectionStrategy.ConnectionType.WIFI_DIRECT,
                        onClick = {
                                onConnectionTypeSelected(
                                        ConnectionStrategy.ConnectionType.WIFI_DIRECT
                                )
                        }
                )

                ConnectionOptionCard(
                        title = stringResource(R.string.connection_wifi_aware),
                        description = "Advanced nearby discovery for Android 8+ devices",
                        icon = Icons.Default.NetworkWifi,
                        isSelected =
                                currentConnectionType ==
                                        ConnectionStrategy.ConnectionType.WIFI_AWARE,
                        onClick = {
                                onConnectionTypeSelected(
                                        ConnectionStrategy.ConnectionType.WIFI_AWARE
                                )
                        }
                )
        }
}

@Composable
private fun ConnectionOptionCard(
        title: String,
        description: String,
        icon: ImageVector,
        isSelected: Boolean,
        onClick: () -> Unit
) {
        val haptic = LocalHapticFeedback.current

        Surface(
                modifier =
                        Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .clickable(
                                        onClick = {
                                                haptic.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                )
                                                onClick()
                                        }
                                ),
                shape = RoundedCornerShape(24.dp),
                color =
                        if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        },
                border =
                        if (isSelected) {
                                borderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else {
                                null
                        }
        ) {
                Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                        Box(
                                modifier =
                                        Modifier.size(56.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                        if (isSelected) {
                                                                MaterialTheme.colorScheme.primary
                                                        } else {
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant
                                                        }
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint =
                                                if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color =
                                                if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                }
                                )
                                Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color =
                                                if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                                .copy(alpha = 0.8f)
                                                } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                )
                        }

                        if (isSelected) {
                                Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                )
                        }
                }
        }
}

@Composable
private fun borderStroke(width: androidx.compose.ui.unit.Dp, color: Color) =
        androidx.compose.foundation.BorderStroke(width, color)
