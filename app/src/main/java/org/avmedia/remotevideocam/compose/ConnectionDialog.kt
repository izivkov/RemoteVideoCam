package org.avmedia.remotevideocam.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.avmedia.remotevideocam.camera.ConnectionStrategy

@Composable
fun ConnectionDialog(
        onDismiss: () -> Unit,
        onOptionSelected: (ConnectionStrategy.ConnectionType) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
                shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = "Connection Strategy",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                )

                val options =
                        listOf(
                                ConnectionStrategy.ConnectionType.AUTO,
                                ConnectionStrategy.ConnectionType.NETWORK,
                                ConnectionStrategy.ConnectionType.WIFI_DIRECT,
                                ConnectionStrategy.ConnectionType.WIFI_AWARE
                        )

                // Note: Real state should be passed from ViewModel, simple mock here
                val selectedOption = ConnectionStrategy.getSelectedType()

                options.forEach { option ->
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                                selected = (option == selectedOption),
                                onClick = {
                                    onOptionSelected(option)
                                    onDismiss()
                                }
                        )
                        Text(
                                text = option.name.replace("_", " "),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                ) { TextButton(onClick = onDismiss) { Text("Cancel") } }
            }
        }
    }
}
