package org.avmedia.remotevideocam.camera

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import org.avmedia.remotevideocam.camera.customcomponents.WebRTCSurfaceView
import org.avmedia.remotevideocam.customcomponents.LocalEventBus
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

        handleDisplayEvents()
        handleDisplayCommands()

        // connect(context)
    }

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
                { info: JSONObject -> send(info) }
            ) { error -> Timber.d("Error occurred in BotToControllerEventBus: %s", error) }
    }

    @SuppressLint("LogNotTimber")
    fun handleDisplayCommands() {
        DisplayToCameraEventBus.subscribe(
            this.javaClass.simpleName,
            { event: JSONObject? ->
                when (event!!.getString("command")) {
                    "CONNECTED" -> {
                        Timber.d("CONNECTED")
                        LocalEventBus.onNext(LocalEventBus.ProgressEvents.ConnectionCameraSuccessful)
                        videoServer.setConnected(true)
                    }
                    "DISCONNECTED" -> {
                        Timber.d("DISCONNECTED")
                        LocalEventBus.onNext(LocalEventBus.ProgressEvents.CameraDisconnected)
                        videoServer.setConnected(false)
                    }
                }
            },
            { error: Throwable? ->
                Log.d(
                    TAG,
                    "Error occurred in handleControllerWebRtcEvents: $error"
                )
            },
            { commandJsn: JSONObject? ->
                commandJsn!!.has(
                    "command"
                ) && ("CONNECTED" == commandJsn.getString("command") || "DISCONNECTED" == commandJsn.getString(
                    "command"
                )) // filter everything else
            }
        )
    }

}