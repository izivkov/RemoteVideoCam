package org.avmedia.remotevideocam.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun ComposeScreenSelector(
    modifier: Modifier = Modifier,
    onNavigation: () -> Unit = {}
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Jetpack Compose UI Loading...",
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Preview
@Composable
fun ComposeScreenSelectorPreview() {
    MaterialTheme {
        ComposeScreenSelector()
    }
}
