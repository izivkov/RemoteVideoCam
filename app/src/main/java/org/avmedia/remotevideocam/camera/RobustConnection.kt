package org.avmedia.remotevideocam.camera

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import org.avmedia.remotevideocam.common.IDataReceived
import org.avmedia.remotevideocam.common.ILocalConnection
import timber.log.Timber

class RobustConnection(
        override val isVideoCapable: Boolean,
        private val networkConnection: ILocalConnection
) : ILocalConnection {
    override val name: String = "Robust Auto Connection"
    private var context: Context? = null
    private var dataReceivedCallback: IDataReceived? = null

    private val wifiAwareConnection =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                WiFiAwareServiceConnection(isVideoCapable)
            } else {
                null
            }
    private val wifiDirectConnection = WiFiDirectServiceConnection(isVideoCapable)

    private var activeConnection: ILocalConnection? = null
    private val handler = Handler(Looper.getMainLooper())
    private val FALLBACK_DELAY_MS = 5000L

    private val checkConnectionRunnable =
            object : Runnable {
                override fun run() {
                    if (!isConnected()) {
                        Timber.d(
                                "RobustConnection: Still not connected, engaging WiFi Direct fallback..."
                        )
                        startWiFiDirect()
                    }
                }
            }

    private val reconnectionWatchdog =
            object : Runnable {
                override fun run() {
                    if (!isConnected()) {
                        Timber.d("RobustConnection: Watchdog detected no connection, retrying...")
                        connect(context)
                    }
                    handler.postDelayed(this, 10000L) // Check every 10 seconds
                }
            }

    override fun init(context: Context?) {
        this.context = context
        networkConnection.init(context)
        wifiAwareConnection?.init(context)
        wifiDirectConnection.init(context)

        handler.postDelayed(reconnectionWatchdog, 5000L)
    }

    override fun setDataCallback(dataCallback: IDataReceived?) {
        this.dataReceivedCallback = dataCallback
        networkConnection.setDataCallback(dataCallback)
        wifiAwareConnection?.setDataCallback(dataCallback)
        wifiDirectConnection.setDataCallback(dataCallback)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun connect(context: Context?) {
        Timber.d("RobustConnection: Attempting connection/discovery...")

        if (!networkConnection.isConnected()) networkConnection.connect(context)
        if (wifiAwareConnection?.isConnected() == false) wifiAwareConnection.connect(context)

        // Schedule WiFi Direct fallback
        handler.removeCallbacks(checkConnectionRunnable)
        handler.postDelayed(checkConnectionRunnable, FALLBACK_DELAY_MS)
    }

    private fun startWiFiDirect() {
        if (!isConnected()) {
            wifiDirectConnection.connect(context)
        }
    }

    override fun disconnect(context: Context?) {
        handler.removeCallbacks(checkConnectionRunnable)
        networkConnection.disconnect(context)
        wifiAwareConnection?.disconnect(context)
        wifiDirectConnection.disconnect(context)
        activeConnection = null
    }

    override fun isConnected(): Boolean {
        val connected =
                when {
                    networkConnection.isConnected() -> {
                        activeConnection = networkConnection
                        true
                    }
                    wifiAwareConnection?.isConnected() == true -> {
                        activeConnection = wifiAwareConnection
                        true
                    }
                    wifiDirectConnection.isConnected() -> {
                        activeConnection = wifiDirectConnection
                        true
                    }
                    else -> false
                }

        if (connected) {
            handler.removeCallbacks(checkConnectionRunnable)
        }
        return connected
    }

    override fun sendMessage(message: String?) {
        // If we don't know who is active, try all or just the last known active
        if (activeConnection?.isConnected() == true) {
            activeConnection?.sendMessage(message)
        } else {
            // Broadcast to all potentially connected
            networkConnection.sendMessage(message)
            wifiAwareConnection?.sendMessage(message)
            wifiDirectConnection.sendMessage(message)
        }
    }

    override fun start() {
        networkConnection.start()
        wifiAwareConnection?.start()
        wifiDirectConnection.start()
    }

    override fun stop() {
        handler.removeCallbacks(checkConnectionRunnable)
        handler.removeCallbacks(reconnectionWatchdog)
        networkConnection.stop()
        wifiAwareConnection?.stop()
        wifiDirectConnection.stop()
    }
}
