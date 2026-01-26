package org.avmedia.remotevideocam.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
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
                contentColor = MaterialTheme.colorScheme.onSurface,
                dragHandle = {
                    Column(
                            modifier = Modifier.padding(top = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                                modifier =
                                        Modifier.width(40.dp)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                                .copy(alpha = 0.4f)
                                                )
                        )
                    }
                }
        ) {
            ConnectionSettingsContent(
                    currentConnectionType = currentConnectionType,
                    onConnectionTypeSelected = { type ->
                        onConnectionTypeSelected(type)
                        onDismiss()
                    },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Text(
                text = stringResource(R.string.connection_type),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Connection options
        ConnectionOption(
                title = stringResource(R.string.connection_auto),
                description = "Automatically select the best connection",
                icon = Icons.Default.AutoAwesome,
                isSelected = currentConnectionType == ConnectionStrategy.ConnectionType.AUTO,
                onClick = { onConnectionTypeSelected(ConnectionStrategy.ConnectionType.AUTO) }
        )

        ConnectionOption(
                title = stringResource(R.string.connection_network),
                description = "Connect via local WiFi network",
                icon = Icons.Default.Wifi,
                isSelected = currentConnectionType == ConnectionStrategy.ConnectionType.NETWORK,
                onClick = { onConnectionTypeSelected(ConnectionStrategy.ConnectionType.NETWORK) }
        )

        ConnectionOption(
                title = stringResource(R.string.connection_wifi_direct),
                description = "Direct device-to-device connection",
                icon = Icons.Default.WifiTethering,
                isSelected = currentConnectionType == ConnectionStrategy.ConnectionType.WIFI_DIRECT,
                onClick = {
                    onConnectionTypeSelected(ConnectionStrategy.ConnectionType.WIFI_DIRECT)
                }
        )

        ConnectionOption(
                title = stringResource(R.string.connection_wifi_aware),
                description = "Nearby device discovery (Android 8+)",
                icon = Icons.Default.NetworkWifi,
                isSelected = currentConnectionType == ConnectionStrategy.ConnectionType.WIFI_AWARE,
                onClick = { onConnectionTypeSelected(ConnectionStrategy.ConnectionType.WIFI_AWARE) }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ConnectionOption(
        title: String,
        description: String,
        icon: ImageVector,
        isSelected: Boolean,
        onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val backgroundColor =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }

    val borderColor =
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            }

    val contentColor =
            if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(backgroundColor)
                            .border(
                                    width = 2.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(16.dp)
                            )
                            .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onClick()
                                    },
                                    role = Role.RadioButton
                            )
                            .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Icon
        Box(
                modifier =
                        Modifier.size(48.dp)
                                .clip(CircleShape)
                                .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                ),
                contentAlignment = Alignment.Center
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
            )
        }

        // Text content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
            )
            Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
            )
        }

        // Radio indicator
        RadioButton(
                selected = isSelected,
                onClick = null,
                colors =
                        RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor =
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.6f
                                        )
                        )
        )
    }
}
