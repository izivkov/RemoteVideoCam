package org.avmedia.remotevideocam.display

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.fragment.app.Fragment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.avmedia.remotevideocam.MainActivity
import org.avmedia.remotevideocam.ScreenSelector
import org.avmedia.remotevideocam.camera.Camera
import org.avmedia.remotevideocam.customcomponents.LocalEventBus
import org.avmedia.remotevideocam.display.customcomponents.VideoViewWebRTC
import timber.log.Timber
import kotlin.system.exitProcess

@SuppressLint("StaticFieldLeak")
object Display : Fragment() {
    private var connection: ILocalConnection = NetworkServiceConnection

    fun init(
        context: Context?,
        videoView: VideoViewWebRTC
    ) {
        if (context != null) {
            connection.init(context)
        }
        videoView.init()

        CameraDataListener.init(connection)
        // createAppEventsSubscription()
    }

    fun connect(context: Context?) {
        connection.connect(context)
    }

    fun disconnect(context: Context?) {
        connection.disconnect(context)
    }

    private fun createAppEventsSubscription(): Disposable =
        LocalEventBus.connectionEventFlowable
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {

                when (it) {
                    LocalEventBus.ProgressEvents.CameraDisconnected -> {
                        Timber.i("CameraDisconnected event")
                        (context as Activity).finish()
                        exitProcess(0)
                    }
                }
            }
            .subscribe(
                { },
                { throwable ->
                    Timber.d(
                        "Got error on subscribe: $throwable"
                    )
                })
}