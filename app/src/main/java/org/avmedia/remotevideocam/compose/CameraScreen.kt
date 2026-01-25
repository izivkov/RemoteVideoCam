package org.avmedia.remotevideocam.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.avmedia.remotevideocam.camera.Camera
import org.avmedia.remotevideocam.camera.customcomponents.WebRTCSurfaceView
import org.avmedia.remotevideocam.utils.ProgressEvents

@Composable
fun CameraScreen(onBack: () -> Unit = { /* Handle back navigation globally or via event */}) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
                factory = { context ->
                    WebRTCSurfaceView(context).apply { Camera.init(context, this) }
                },
                modifier = Modifier.fillMaxSize()
        )

        // Controls
        IconButton(
                onClick = {
                    ProgressEvents.onNext(ProgressEvents.Events.ShowMainScreen)
                    // Sending StopCamera event might be needed if supported
                    // But legacy code just hides the screen.
                },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
            )
        }

        IconButton(
                onClick = { ProgressEvents.onNext(ProgressEvents.Events.FlipCamera) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).size(64.dp)
        ) {
            Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Flip Camera",
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize()
            )
        }
    }
}
