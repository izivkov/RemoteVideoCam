package org.avmedia.remotevideocam.utils

import android.util.Pair
import org.json.JSONException
import org.json.JSONObject
import java.net.NetworkInterface
import java.util.*

object ConnectionUtils {
    fun createStatus(name: String?, value: Boolean): JSONObject {
        return createStatus(name, if (value) "true" else "false")
    }

    fun createStatus(name: String?, value: String?): JSONObject {
        try {
            return JSONObject().put("status", JSONObject().put(name, value))
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return JSONObject()
    }

    fun createStatus(name: String?, value: JSONObject): JSONObject {
        try {
            return JSONObject().put("status", JSONObject().put(name, value.toString()))
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return JSONObject()
    }
}