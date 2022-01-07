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

    fun getStatus(
        loggingEnabled: Boolean,
        noiseEnabled: Boolean,
        networkEnabled: Boolean,
        driveMode: String?,
        indicator: Int
    ): JSONObject {
        val status = JSONObject()
        try {
            val statusValue = JSONObject()
            statusValue.put("LOGS", loggingEnabled)
            statusValue.put("NOISE", noiseEnabled)
            statusValue.put("NETWORK", networkEnabled)
            statusValue.put("DRIVE_MODE", driveMode)

            // Possibly can only send the value of the indicator here, but this makes it clearer.
            // Also, the controller need not have to know implementation details.
            statusValue.put("INDICATOR_LEFT", indicator == -1)
            statusValue.put("INDICATOR_RIGHT", indicator == 1)
            statusValue.put("INDICATOR_STOP", indicator == 0)
            status.put("status", statusValue)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return status
    }

    fun createStatusBulk(nameValues: List<Pair<String?, String?>>): JSONObject {
        val status = JSONObject()
        try {
            val statusValue = JSONObject()
            for (nameValue in nameValues) {
                statusValue.put(nameValue.first.toString(), nameValue.second)
            }
            status.put("status", statusValue)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return status
    }

    fun getIPAddress(useIPv4: Boolean): String {
        try {
            val interfaces: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        // boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (useIPv4) {
                            if (isIPv4) return sAddr
                        } else {
                            if (!isIPv4) {
                                val delim = sAddr.indexOf('%') // drop ip6 zone suffix
                                return if (delim < 0) sAddr.toUpperCase() else sAddr.substring(
                                    0,
                                    delim
                                ).toUpperCase()
                            }
                        }
                    }
                }
            }
        } catch (ignored: Exception) {
        } // for now eat exceptions
        return ""
    }
}