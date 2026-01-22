package org.avmedia.remotevideocam.camera

import android.annotation.SuppressLint
import android.content.Context
import io.reactivex.rxjava3.disposables.Disposable
import org.avmedia.remotevideocam.camera.customcomponents.WebRTCSurfaceView
import org.avmedia.remotevideocam.common.ILocalConnection
import org.avmedia.remotevideocam.frameanalysis.motion.MotionDetectionAction
import org.avmedia.remotevideocam.frameanalysis.motion.MotionDetectionData
import org.avmedia.remotevideocam.frameanalysis.motion.toMotionDetectionData
import org.avmedia.remotevideocam.utils.ProgressEvents
import org.json.JSONObject
import timber.log.Timber

@SuppressLint("StaticFieldLeak")
object Camera {
    private const val TAG = "Camera"
    private lateinit var connection: ILocalConnection
    private val videoServer: IVideoServer = WebRtcServer()
    private var context: Context? = null

    private var displayEventsDisposable: Disposable? = null

    fun init(
            context: Context?,
            view: WebRTCSurfaceView,
    ) {
        this.context = context
        this.connection = ConnectionStrategy.getCameraConnection(context!!)

        if (!connection.isConnected()) {
            connection.init(context)
            connection.setDataCallback(org.avmedia.remotevideocam.common.UnifiedDataRouter())
        }

        videoServer.init(context)
        videoServer.setView(view)

        if (isConnected()) {
            videoServer.startClient()
        }

        handleDisplayEvents()
        handleDisplayCommands()
    }

    fun connect(context: Context?) {
        connection.connect(context)
    }

    fun disconnect() {
        connection.disconnect(context)
    }

    fun disconnectHard(context: Context?) {
        connection.disconnect(context)
    }

    private fun send(info: JSONObject) {
        if (connection.isConnected()) {
            connection.sendMessage(info.toString())
        } else if (org.avmedia.remotevideocam.display.NetworkServiceConnection.isConnected()) {
            org.avmedia.remotevideocam.display.NetworkServiceConnection.sendMessage(info.toString())
        } else {
            Timber.tag(TAG).d("No connection available to send message: %s", info)
        }
    }

    fun isConnected(): Boolean {
        return this::connection.isInitialized && connection.isConnected()
    }

    @SuppressLint("CheckResult")
    private fun handleDisplayEvents() {
        if (displayEventsDisposable != null && !displayEventsDisposable!!.isDisposed) {
            return
        }
        displayEventsDisposable =
                CameraToDisplayEventBus.processor.subscribe(
                        // 1st parameter: the onNext lambda
                        { info: JSONObject -> send(info) },
                        // 2nd parameter: the onError lambda
                        { error: Throwable ->
                            Timber.d("Error occurred in CameraToDisplayEventBus: $error")
                        }
                )
    }

    fun handleDisplayCommands() {
        DisplayToCameraEventBus.subscribe(
                this.javaClass.simpleName,
                { event: JSONObject? ->
                    event?.takeIf { it.has("command") }?.let {
                        when (event.getString("command")) {
                            "CONNECTED" -> {
                                Timber.d("CONNECTED")
                                ProgressEvents.onNext(
                                        ProgressEvents.Events.ConnectionCameraSuccessful
                                )
                                videoServer.setConnected(true)
                                videoServer.startClient()
                            }
                            "DISCONNECTED" -> {
                                Timber.d("DISCONNECTED")
                                ProgressEvents.onNext(ProgressEvents.Events.CameraDisconnected)
                                videoServer.setConnected(false)
                            }
                        }
                    }
                    event?.takeIf { it.has(MotionDetectionData.KEY) }?.let {
                        event.getJSONObject(MotionDetectionData.KEY).toMotionDetectionData().let {
                                data ->
                            when (data.action) {
                                MotionDetectionAction.ENABLED -> setMotionDetection(true)
                                MotionDetectionAction.DISABLED -> setMotionDetection(false)
                                MotionDetectionAction.DETECTED,
                                MotionDetectionAction.NOT_DETECTED ->
                                        Timber.tag(TAG)
                                                .e(
                                                        "Unexpected motion detection action %s",
                                                        data.action.name
                                                )
                            }
                        }
                    }
                },
                { error: Throwable? ->
                    Timber.d("Error occurred in handleControllerWebRtcEvents: $error")
                },
                { commandJsn: JSONObject? ->
                    commandJsn!!.has("command") &&
                            ("CONNECTED" == commandJsn.getString("command") ||
                                    "DISCONNECTED" == commandJsn.getString("command")) ||
                            commandJsn.has(MotionDetectionData.KEY)
                    // filter everything else
                }
        )
    }

    private fun setMotionDetection(enable: Boolean) {
        videoServer.setMotionDetection(enable)
    }
}
