package org.avmedia.remotevideocam.camera

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.widget.ImageButton
import org.avmedia.remotevideocam.camera.customcomponents.WebRTCSurfaceView
import org.avmedia.remotevideocam.frameanalysis.motion.MotionDetectionAction
import org.avmedia.remotevideocam.frameanalysis.motion.MotionDetectionData
import org.avmedia.remotevideocam.frameanalysis.motion.toMotionDetectionData
import org.avmedia.remotevideocam.utils.ProgressEvents
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
        view: WebRTCSurfaceView,
    ) {
        this.context = context

        connection.init(context)
        connection.setDataCallback(DataReceived())

        videoServer.init(context)
        videoServer.setView(view)

        handleDisplayEvents()
        handleDisplayCommands()
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

    @SuppressLint("LogNotTimber")
    fun handleDisplayCommands() {
        DisplayToCameraEventBus.subscribe(
            this.javaClass.simpleName,
            { event: JSONObject? ->
                event?.takeIf { it.has("command") }?.let {
                    when (event.getString("command")) {
                        "CONNECTED" -> {
                            Timber.d("CONNECTED")
                            ProgressEvents.onNext(ProgressEvents.Events.ConnectionCameraSuccessful)
                            videoServer.setConnected(true)
                        }

                        "DISCONNECTED" -> {
                            Timber.d("DISCONNECTED")
                            ProgressEvents.onNext(ProgressEvents.Events.CameraDisconnected)
                            videoServer.setConnected(false)
                        }
                    }
                }
                event?.takeIf { it.has(MotionDetectionData.KEY) }?.let {
                    event.getJSONObject(MotionDetectionData.KEY)
                        .toMotionDetectionData()
                        .let { data ->
                        when (data.action) {
                            MotionDetectionAction.ENABLED ->
                                setMotionDetection(true)

                            MotionDetectionAction.DISABLED ->
                                setMotionDetection(false)

                            MotionDetectionAction.DETECTED,
                            MotionDetectionAction.NOT_DETECTED ->
                                Timber.tag(TAG).e(
                                    "Unexpected motion detection action %s",
                                    data.action.name
                                )
                        }
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
                )) ||
                commandJsn.has(MotionDetectionData.KEY)
                // filter everything else
            }
        )
    }

    private fun setMotionDetection(enable: Boolean) {
        videoServer.setMotionDetection(enable)
    }
}