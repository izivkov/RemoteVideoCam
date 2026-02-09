package org.avmedia.remotevideocam.utils

import java.net.NetworkInterface
import java.util.*
import org.json.JSONException
import org.json.JSONObject

object ConnectionUtils {
    fun createStatus(name: String?, value: Boolean): JSONObject {
        return createStatus(name, if (value) "true" else "false")
    }

    fun createStatus(name: String?, value: String?): JSONObject {
        try {
            return JSONObject().put("status", JSONObject().put(name ?: "", value))
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return JSONObject()
    }

    fun createStatus(name: String?, value: JSONObject): JSONObject {
        try {
            return JSONObject().put("status", JSONObject().put(name ?: "", value.toString()))
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return JSONObject()
    }

    fun getIPAddress(useIPv4: Boolean): String {
        try {
            val interfaces: List<NetworkInterface> =
                    Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs: List<java.net.InetAddress> = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (useIPv4) {
                            if (isIPv4) return sAddr
                        } else {
                            if (!isIPv4) {
                                val delim = sAddr.indexOf('%') // drop ip6 zone suffix
                                return if (delim < 0) sAddr.toUpperCase()
                                else sAddr.substring(0, delim).toUpperCase()
                            }
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}
        return ""
    }
}
