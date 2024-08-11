/*
 * Developed by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:58 p.m.
 */

package org.avmedia.remotevideocam.display

import org.avmedia.remotevideocam.frameanalysis.motion.MotionDetectionAction
import org.avmedia.remotevideocam.frameanalysis.motion.MotionDetectionData
import org.avmedia.remotevideocam.utils.ConnectionUtils
import org.json.JSONObject

/*
This class listens for status data from the Camera and emits events.
 */
object CameraDataListener {

    fun init(connection: ILocalConnection) {
        val dataReceived: IDataReceived = object : IDataReceived {
            override fun dataReceived(command: String?) {

                val dataJson = JSONObject(command as String)
                if (dataJson.has("status")) {
                    processStatus(dataJson.getJSONObject("status"))
                }

                if (dataJson.has(MotionDetectionData.KEY)) {
                    processMotionDetection(dataJson.getString(MotionDetectionData.KEY))
                }
            }

            private fun processStatus(statusValues: JSONObject) {
                for (key in statusValues.keys()) {
                    val value: String = statusValues.getString(key)

                    /*
                    Send an event on a particular subject.
                    The custom components are listening on their subject.
                    */
                    CameraStatusEventBus.emitEvent(key, value)
                }
            }

            private fun processMotionDetection(dataJson: String) {
                CameraStatusEventBus.emitEvent(MotionDetectionData.KEY, dataJson)
            }
        }

        connection.setDataCallback(dataReceived)
    }
}