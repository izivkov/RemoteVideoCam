package org.avmedia.remotevideocam.common

import org.avmedia.remotevideocam.camera.DisplayToCameraEventBus
import org.avmedia.remotevideocam.display.CameraStatusEventBus
import org.avmedia.remotevideocam.frameanalysis.motion.MotionDetectionData
import org.json.JSONObject

class UnifiedDataRouter : IDataReceived {
    override fun dataReceived(data: String?) {
        if (data == null) return
        val dataJson = JSONObject(data)

        // 1. Process Status/Commands for the Camera core
        if (dataJson.has("status") ||
                        dataJson.has(MotionDetectionData.KEY) ||
                        dataJson.has("command")
        ) {
            DisplayToCameraEventBus.emitEvent(dataJson)
        }

        // 2. Process WebRTC signaling for the Video Server (Camera side)
        if (dataJson.has("to_camera_webrtc")) {
            DisplayToCameraEventBus.emitEvent(dataJson)
        }

        // 3. Process WebRTC signaling for the Video Viewer (Display side)
        if (dataJson.has("to_display_webrtc")) {
            CameraStatusEventBus.emitEvent(
                    "WEB_RTC_EVENT",
                    dataJson.getJSONObject("to_display_webrtc").toString()
            )
        }

        // 4. Process Status values for UI components (Display side)
        if (dataJson.has("status")) {
            val statusValues = dataJson.getJSONObject("status")
            for (key in statusValues.keys()) {
                val value: String = statusValues.getString(key)
                CameraStatusEventBus.emitEvent(key, value)
            }
        }

        // 5. Process Motion Detection for UI (Display side)
        if (dataJson.has(MotionDetectionData.KEY)) {
            CameraStatusEventBus.emitEvent(
                    MotionDetectionData.KEY,
                    dataJson.getString(MotionDetectionData.KEY)
            )
        }
    }
}
