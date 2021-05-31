package org.avmedia.remotevideocam.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import org.avmedia.remotevideocam.R
import org.avmedia.remotevideocam.camera.customcomponents.WebRTCSurfaceView
import org.json.JSONException
import org.json.JSONObject
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import timber.log.Timber

object Camera  {
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

        handleBotEvents()
        connect(context)
        setView(view)
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

        videoServer.setConnected(true, context)
    }

    fun disconnect(context: Context?) {
        connection.stop()
        videoServer.setConnected(false, context)
    }

    private fun send(info: JSONObject) {
        connection.sendMessage(info.toString())
    }

    fun isConnected(): Boolean {
        return connection.isConnected
    }

    @SuppressLint("CheckResult")
    private fun handleBotEvents() {
        CameraToDisplayEventBus.processor
            .subscribe(
                { info: JSONObject -> send(info) },
                { error -> Timber.d("Error occurred in BotToControllerEventBus: %s", error) })
    }

    fun setView(videoView: WebRTCSurfaceView) {
        videoServer.setView(videoView)
    }
}