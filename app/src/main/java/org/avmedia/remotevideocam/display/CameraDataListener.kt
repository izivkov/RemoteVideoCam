/*
 * Developed by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:58 p.m.
 */

package org.avmedia.remotevideocam.display

import org.json.JSONObject

/*
This class listens for status data from the Camera and emits events.
 */
object CameraDataListener {

    fun init(connection: ILocalConnection) {
        val dataReceived: IDataReceived = object : IDataReceived {
            override fun dataReceived(command: String?) {

                val dataJson = JSONObject(command as String)
                val statusValues = dataJson.getJSONObject("status")

                for (key in statusValues.keys()) {
                    val value: String = statusValues.getString(key)

                    /*
                    Send an event on a particular subject.
                    The custom components are listening on their subject.
                    */
                    CameraStatusEventBus.emitEvent(key, value)
                }
            }
        }

        connection.setDataCallback(dataReceived)
    }
}