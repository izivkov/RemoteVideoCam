package org.avmedia.remotevideocam.camera

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.fragment.app.Fragment
import org.avmedia.remotevideocam.camera.customcomponents.WebRTCSurfaceView
import org.avmedia.remotevideocam.customcomponents.EventProcessor
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

object Camera : Fragment() {
    private val TAG = "Camera"
    private var connection: ILocalConnection = NetworkServiceConnection()
    private val videoServer: IVideoServer = WebRtcServer()

    fun init(
        context: Context,
        view: WebRTCSurfaceView
    ) {
        connection.init(context)
        connection.setDataCallback(DataReceived())
        videoServer.init(context)
        setView(view)

        handleDisplayEvents()
        handleDisplayCommands()
        connect(context)
    }

    internal class DataReceived : IDataReceived {
        override fun dataReceived(commandStr: String?) {
            try {
                DisplayToCameraEventBus.emitEvent(JSONObject(commandStr))
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    fun connect(context: Context) {
        connection.connect(context)

        if (!connection.isConnected) {
            connection.init(context)
            connection.connect(context)
        } else {
            connection.start()
        }

        videoServer.setConnected(true)
    }

    fun disconnect(context: Context?) {
        connection.stop()
        videoServer.setConnected(false)
    }

    private fun send(info: JSONObject) {
        connection.sendMessage(info.toString())
    }

    fun isConnected(): Boolean {
        return connection.isConnected
    }

    @SuppressLint("CheckResult")
    private fun handleDisplayEvents() {
        CameraToDisplayEventBus.processor
            .subscribe(
                { info: JSONObject -> send(info) },
                { error -> Timber.d("Error occurred in BotToControllerEventBus: %s", error) })
    }

    private fun setView(videoView: WebRTCSurfaceView) {
        videoServer.setView(videoView)
    }

    @SuppressLint("LogNotTimber")
    fun handleDisplayCommands() {
        DisplayToCameraEventBus.subscribe(
            this.javaClass.simpleName,
            { event: JSONObject? ->
                when (event!!.getString("command")) {
                    "CONNECTED" -> {
                        Timber.d("CONNECTED")
                        EventProcessor.onNext(EventProcessor.ProgressEvents.ConnectionCameraSuccessful)
                        videoServer.setConnected(true)
                    }
                    "DISCONNECTED" -> {
                        Timber.d("DISCONNECTED")
                        EventProcessor.onNext(EventProcessor.ProgressEvents.CameraDisconnected)
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