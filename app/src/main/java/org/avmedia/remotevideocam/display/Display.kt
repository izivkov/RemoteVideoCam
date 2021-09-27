package org.avmedia.remotevideocam.display

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.fragment.app.Fragment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.avmedia.remotevideocam.MainActivity
import org.avmedia.remotevideocam.camera.Camera
import org.avmedia.remotevideocam.customcomponents.LocalEventBus
import org.avmedia.remotevideocam.display.customcomponents.VideoViewWebRTC

@SuppressLint("StaticFieldLeak")
object Display : Fragment() {
    private val TAG = "Display"
    private var connection: ILocalConnection = NetworkServiceConnection

    fun init(
        context: Context?,
        videoView: VideoViewWebRTC
    ) {
        if (context != null) {
            connection.init(context)
        }
        videoView.init()

        subscribeToStatusInfo()

        CameraDataListener.init(connection)
        connect(context)
    }

    fun connect(context: Context?) {
        connection.connect(context)
    }

    @SuppressLint("CheckResult", "LogNotTimber")
    private fun subscribeToStatusInfo() {
        StatusEventBus.addSubject("CONNECTION_ACTIVE")
        StatusEventBus.subscribe(this.javaClass.simpleName, "CONNECTION_ACTIVE", onNext = {
            if (it.toBoolean()) {
            } else {
            }
        })
    }
}