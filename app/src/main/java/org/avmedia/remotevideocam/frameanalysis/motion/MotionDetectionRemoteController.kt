package org.avmedia.remotevideocam.frameanalysis.motion

import android.content.Context
import org.avmedia.remotevideocam.common.ILocalConnection
import org.avmedia.remotevideocam.display.CameraStatusEventBus
import org.avmedia.remotevideocam.frameanalysis.motion.MotionDetectionStateMachine.*
import timber.log.Timber

private const val TAG = "MotionDetectionRemoteController"

/**
 * Represents the remote control of the motion detection feature.
 * 1. remotely toggle the motion detection in the Camera side.
 * 2. shows a local notification when motion is detected.
 */
class MotionDetectionRemoteController(
        private val connection: ILocalConnection,
) : Listener {
    private var notificationController: MotionNotificationController? = null
    private val motionDetectionStateMachine = MotionDetectionStateMachine()

    fun init(context: Context) {
        if (notificationController == null) {
            notificationController = MotionNotificationController(context)
            subscribe()
        }
        motionDetectionStateMachine.listener = this
    }

    fun toggleMotionDetection(enable: Boolean) {
        val value =
                if (enable) {
                    MotionDetectionAction.ENABLED
                } else {
                    MotionDetectionAction.DISABLED
                }
        val data = value.toData()
        connection.sendMessage(data.toJsonResponse().toString())
        motionDetectionStateMachine.process(data)
        notificationController?.resetCooldown()
    }

    fun subscribe() {
        CameraStatusEventBus.addSubject(MotionDetectionData.KEY)
        CameraStatusEventBus.subscribe(
                this.javaClass.simpleName,
                MotionDetectionData.KEY,
        ) { it?.toMotionDetectionData()?.let { data -> motionDetectionStateMachine.process(data) } }
    }

    override fun onStateChanged(detected: Boolean) {
        Timber.tag(TAG).d("onStateChanged %s", detected)
        if (detected) {
            notificationController?.showNotification()
        }
    }
}
