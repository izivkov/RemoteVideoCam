package org.avmedia.remotevideocam.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.avmedia.remotevideocam.display.Display
import org.avmedia.remotevideocam.display.customcomponents.VideoViewWebRTC
import org.avmedia.remotevideocam.utils.ProgressEvents

@Composable
fun DisplayScreen() {
    var isMuted by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
                factory = { context ->
                    VideoViewWebRTC(context).apply {
                        Display.init(context, this)
                        Display.connect(context)
                    }
                },
                modifier = Modifier.fillMaxSize()
        )

        // Top Controls
        IconButton(
                onClick = { ProgressEvents.onNext(ProgressEvents.Events.ShowMainScreen) },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
            )
        }

        // Bottom Controls
        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            IconButton(
                    onClick = { ProgressEvents.onNext(ProgressEvents.Events.ToggleMirror) },
                    modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                        imageVector = Icons.Default.Flip,
                        contentDescription = "Mirror",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                    onClick = {
                        isMuted = !isMuted
                        ProgressEvents.onNext(
                                if (isMuted) ProgressEvents.Events.Mute
                                else ProgressEvents.Events.Unmute
                        )
                    }
            ) {
                Icon(
                        imageVector =
                                if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Sound",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
