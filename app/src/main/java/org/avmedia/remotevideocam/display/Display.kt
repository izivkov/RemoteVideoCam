package org.avmedia.remotevideocam.display

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.avmedia.remotevideocam.customcomponents.EventProcessor
import org.avmedia.remotevideocam.display.customcomponents.VideoViewWebRTC

@SuppressLint("StaticFieldLeak")
object Display {
    private val TAG = "Display"
    private var connection: ILocalConnection = NetworkServiceConnection
    private lateinit var context: Context

    fun init(
        context: Context,
        videoWindow: VideoViewWebRTC
    ) {
        this.context = context
        connection.init(context)
        videoWindow.init()

        createAppEventsSubscription()
        subscribeToStatusInfo()

        CameraDataListener.init(connection)
    }

    fun connect(context: Context) {
        connection.connect(context)
    }

    @SuppressLint("CheckResult", "LogNotTimber")
    private fun subscribeToStatusInfo() {
        StatusEventBus.addSubject("CONNECTION_ACTIVE")
        StatusEventBus.getProcessor("CONNECTION_ACTIVE")?.subscribe {

            if (it.toBoolean()) Log.i(TAG, "Got CONNECTION_ACTIVE: true") else Log.i(
                TAG,
                "Got CONNECTION_ACTIVE: false"
            )
        }
    }

    @SuppressLint("LogNotTimber")
    private fun createAppEventsSubscription(): Disposable =
        EventProcessor.connectionEventFlowable
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                Log.i(TAG, "Got $it event")

                when (it) {
                    EventProcessor.ProgressEvents.ConnectionDisplaySuccessful -> {
                        Utils.beep()
                    }
                    EventProcessor.ProgressEvents.ConnectionFailed -> {
                    }
                    EventProcessor.ProgressEvents.DisplayDisconnected -> {
                    }
                }
            }
            .subscribe(
                { },
                { throwable ->
                    Log.d(
                        "EventsSubscription",
                        "Got error on subscribe: $throwable"
                    )
                })
}