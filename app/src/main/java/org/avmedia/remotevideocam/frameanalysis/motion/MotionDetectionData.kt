package org.avmedia.remotevideocam.frameanalysis.motion

import android.os.SystemClock
import org.avmedia.remotevideocam.display.CameraStatusEventBus
import org.avmedia.remotevideocam.frameanalysis.motion.MotionDetectionAction.*
import org.json.JSONObject

data class MotionDetectionData(
    val action: MotionDetectionAction,
    val timestampMs: Long = SystemClock.elapsedRealtime(),
) {

    companion object {

        const val KEY = "MotionDetectionData"

        const val ACTION = "ACTION"

        const val TIMESTAMP = "TIMESTAMP"

        init {
            CameraStatusEventBus.addSubject(KEY)
        }
    }
}

enum class MotionDetectionAction {

    ENABLED,

    DISABLED,

    DETECTED,

    NOT_DETECTED,

    ;
}

fun JSONObject.toMotionDetectionData(): MotionDetectionData {
    val action = getString(MotionDetectionData.ACTION).toMotionDetectionAction()
    val timestamp = getLong(MotionDetectionData.TIMESTAMP)
    return MotionDetectionData(action, timestamp)
}

fun String.toMotionDetectionData(): MotionDetectionData {
    return JSONObject(this).toMotionDetectionData()
}

fun MotionDetectionData.toJsonResponse(): JSONObject {
    val json = JSONObject()
        .put(MotionDetectionData.ACTION, action)
        .put(MotionDetectionData.TIMESTAMP, timestampMs)
    return JSONObject().put(MotionDetectionData.KEY, json)
}

fun MotionDetectionAction.toJsonResponse(): JSONObject {
    return MotionDetectionData(this).toJsonResponse()
}

fun String.toMotionDetectionAction(): MotionDetectionAction {
    return when (this) {
        ENABLED.name -> ENABLED
        DISABLED.name -> DISABLED
        DETECTED.name -> DETECTED
        NOT_DETECTED.name -> NOT_DETECTED
        else -> throw IllegalArgumentException("$this fails to map")
    }
}
