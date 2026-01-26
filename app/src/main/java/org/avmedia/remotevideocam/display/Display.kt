package org.avmedia.remotevideocam.display

import android.annotation.SuppressLint
import android.content.Context
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import org.avmedia.remotevideocam.common.ILocalConnection
import org.avmedia.remotevideocam.display.customcomponents.VideoViewWebRTC

@SuppressLint("StaticFieldLeak")
object Display : Fragment() {
    private lateinit var connection: ILocalConnection

    fun init(context: Context, videoView: VideoViewWebRTC) {
        connection =
                org.avmedia.remotevideocam.camera.ConnectionStrategy.getDisplayConnection(context)

        if (!connection.isConnected()) {
            connection.init(context)
        }
        connection.setDataCallback(org.avmedia.remotevideocam.common.UnifiedDataRouter())
        videoView.init()
    }

    fun connect(context: Context?) {
        if (::connection.isInitialized) {
            connection.connect(context)
        }
    }

    fun disconnect(context: Context?) {
        if (::connection.isInitialized) {
            connection.disconnect(context)
        }
    }
}
