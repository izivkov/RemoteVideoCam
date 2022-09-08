package org.avmedia.remotevideocam.camera

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.avmedia.remotevideocam.camera.customcomponents.WebRTCSurfaceView
import org.avmedia.remotevideocam.customcomponents.ProgressEvents
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

@SuppressLint("StaticFieldLeak")
object Camera {
    private const val TAG = "Camera"
    private var connection: ILocalConnection = NetworkServiceConnection()
    private val videoServer: IVideoServer = WebRtcServer()
    private var context: Context? = null

    fun init(
        context: Context?,
        view: WebRTCSurfaceView
    ) {
        this.context = context

        connection.init(context)
        connection.setDataCallback(DataReceived())

        videoServer.init(context)
        videoServer.setView(view)

        createAppEventsSubscription()
        handleDisplayEvents()
    }

    private fun createAppEventsSubscription(): Disposable =
        ProgressEvents.connectionEventFlowable
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                when (it) {
                    ProgressEvents.Events.Connected -> {
                        videoServer.setConnected(true)
                    }
                    ProgressEvents.Events.Disconnected -> {
                        videoServer.setConnected(false)
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

    internal class DataReceived : IDataReceived {
        override fun dataReceived(commandStr: String?) {
            try {
                DisplayToCameraEventBus.emitEvent(JSONObject(commandStr as String))
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    fun connect(context: Context?) {
        connection.connect(context)
    }

    fun disconnect() {
        connection.stop()
    }

    fun disconnectHard(context: Context?) {
        connection.disconnect(context)
    }

    private fun send(info: JSONObject) {
        connection.sendMessage(info.toString())
    }

    fun isConnected(): Boolean {
        return connection.isConnected()
    }

    @SuppressLint("CheckResult")
    private fun handleDisplayEvents() {
        CameraToDisplayEventBus.processor
            .subscribe(
                { info -> send(info) }
            ) { error -> Timber.d("Error occurred in CameraToDisplayEventBus: %s", error) }
    }
}