/*
 * Developed by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:58 p.m.
 */

package org.avmedia.remotevideocam.utils

import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.wifi.WifiManager
import android.widget.Toast
import java.math.BigInteger
import java.net.InetAddress
import java.util.*
import org.avmedia.remotevideocam.MainActivity
import timber.log.Timber

object Utils {

    enum class TONE {
        PIP,
        ALERT,
        INTERCEPT
    }

    fun beep(tone: TONE = TONE.PIP, duration: Int = 150) {
        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        val tgTone =
                when (tone) {
                    TONE.INTERCEPT -> {
                        ToneGenerator.TONE_CDMA_ABBR_INTERCEPT
                    }
                    TONE.ALERT -> {
                        ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
                    }
                    else -> {
                        ToneGenerator.TONE_CDMA_PIP
                    }
                }
        toneGen.startTone(tgTone, duration)
    }

    fun isMe(otherIP: String, otherServiceName: String? = null): Boolean {
        if (otherIP == "127.0.0.1" ||
                        otherIP == "0.0.0.0" ||
                        otherIP == "localhost" ||
                        otherIP == "::1"
        )
                return true

        // Check by IP
        val myIPs = getAllMyIPs()
        if (myIPs.contains(otherIP)) {
            Timber.i("Same IP address detected: $otherIP is one of mine: $myIPs")
            return true
        }

        // Check by Service Name (as a fallback)
        otherServiceName?.let {
            val myName = "REMOTE_VIDEO_CAM-${android.os.Build.MODEL}"
            if (it.startsWith(myName)) {
                Timber.i("Same Service Name detected: $it starts with $myName")
                return true
            }
        }

        return false
    }

    fun getMyIP(): String? {
        val ips = getAllMyIPs()
        return ips.firstOrNull()
    }

    fun getAllMyIPs(): List<String> {
        val ips = mutableListOf<String>()
        try {
            // Priority 1: WifiManager
            val context: Context = MainActivity.applicationContext()
            val wm = context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val ipAddress = wm.connectionInfo.ipAddress
            if (ipAddress != 0) {
                val longIp = ipAddress.toLong()
                val byteIp = BigInteger.valueOf(longIp).toByteArray().reversedArray()
                val ipAddr = InetAddress.getByAddress(byteIp).hostAddress
                if (ipAddr != null) {
                    ips.add(ipAddr)
                }
            }

            // Priority 2: Network interfaces (more robust)
            val networkInterfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (iface in networkInterfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is java.net.Inet4Address) {
                        addr.hostAddress?.let { host ->
                            if (host != "127.0.0.1" && !ips.contains(host) && host.startsWith("192.0.0.").not()) {
                                ips.add(host)
                            }
                        }
                    }
                }
            }
        } catch (e: java.lang.Exception) {
            Timber.e(e, "Error getting IP addresses")
        }
        return ips
    }

    fun replaceInvalidIp(candidate: String, validIp: String?): String {
        if (validIp.isNullOrEmpty()) return candidate

        // Regex to capture an IPv4 address
        val ipV4Regex = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")

        return ipV4Regex.replace(candidate) { matchResult ->
            val ip = matchResult.value
            if (isIpInvalid(ip)) {
                Timber.d("Replaced invalid IP $ip with $validIp")
                validIp
            } else {
                ip
            }
        }
    }

    private fun isIpInvalid(ip: String): Boolean {
        return ip.startsWith("192.0.0.") || ip.startsWith("127.") || ip == "0.0.0.0"
    }

    fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
