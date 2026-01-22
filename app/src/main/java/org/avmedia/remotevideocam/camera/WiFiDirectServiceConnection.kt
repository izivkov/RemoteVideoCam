package org.avmedia.remotevideocam.camera

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pManager
import java.util.concurrent.ArrayBlockingQueue
import org.avmedia.remotevideocam.common.IDataReceived
import org.avmedia.remotevideocam.common.ILocalConnection
import org.avmedia.remotevideocam.common.LocalConnectionSocketHandler
import org.avmedia.remotevideocam.utils.ProgressEvents
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

class WiFiDirectServiceConnection(override val isVideoCapable: Boolean) : ILocalConnection {
    override val name: String = "WiFi Direct"
    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var socketHandler: LocalConnectionSocketHandler? = null
    private var context: Context? = null
    private var dataReceivedCallback: IDataReceived? = null
    private val port = 8888

    private var receiver: WiFiDirectBroadcastReceiver? = null
    private val intentFilter =
            IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }

    override fun init(context: Context?) {
        this.context = context
        manager = context?.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = manager?.initialize(context, context?.mainLooper, null)
        socketHandler =
                LocalConnectionSocketHandler(
                        context,
                        ArrayBlockingQueue(25),
                        { dataReceivedCallback },
                        { emitConnected() },
                        { emitDisconnected() }
                )
    }

    override fun setDataCallback(dataCallback: IDataReceived?) {
        this.dataReceivedCallback = dataCallback
    }

    @SuppressLint("MissingPermission")
    override fun connect(context: Context?) {
        start()
        manager?.discoverPeers(
                channel,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Timber.d("Peer discovery started")
                    }

                    override fun onFailure(reason: Int) {
                        Timber.e("Peer discovery failed: $reason")
                    }
                }
        )
    }

    override fun disconnect(context: Context?) {
        stop()
        socketHandler?.close()
        manager?.removeGroup(
                channel,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Timber.d("Group removed")
                    }

                    override fun onFailure(reason: Int) {
                        Timber.e("Failed to remove group: $reason")
                    }
                }
        )
    }

    override fun isConnected(): Boolean {
        return socketHandler?.isConnected() ?: false
    }

    override fun sendMessage(message: String?) {
        message?.let { socketHandler?.put(it) }
    }

    override fun stop() {
        socketHandler?.stop()
        context?.let {
            if (receiver != null) {
                try {
                    it.unregisterReceiver(receiver)
                } catch (e: IllegalArgumentException) {
                    Timber.w("Receiver not registered")
                }
                receiver = null
            }
        }
    }

    override fun start() {
        if (receiver == null) {
            receiver = WiFiDirectBroadcastReceiver()
            context?.registerReceiver(receiver, intentFilter)
        }
    }

    private fun emitConnected() {
        try {
            DisplayToCameraEventBus.emitEvent(JSONObject("{\"command\": \"CONNECTED\"}"))
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun emitDisconnected() {
        ProgressEvents.onNext(ProgressEvents.Events.DisplayDisconnected)
        try {
            DisplayToCameraEventBus.emitEvent(JSONObject("{\"command\": \"DISCONNECTED\"}"))
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    inner class WiFiDirectBroadcastReceiver : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        Timber.d("Wi-Fi P2P is enabled")
                    } else {
                        Timber.d("Wi-Fi P2P is not enabled")
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    // Logic to handle peer list changes
                    Timber.d("Peers changed")
                    // In a more advanced implementation, we might want to automatically connect to
                    // a specific peer here
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo: NetworkInfo? =
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        manager?.requestConnectionInfo(channel) { info ->
                            val host = info.groupOwnerAddress.hostAddress
                            val isGroupOwner = info.isGroupOwner

                            Thread(
                                            {
                                                val clientInfo =
                                                        if (!isGroupOwner) {
                                                            socketHandler?.connect(host, port)
                                                        } else {
                                                            socketHandler?.waitForConnection(port)
                                                        }
                                                clientInfo?.let { info ->
                                                    socketHandler?.startCommunication(info)
                                                }
                                            },
                                            "WiFiDirect Connection Thread"
                                    )
                                    .start()
                        }
                    } else {
                        socketHandler?.close()
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // Respond to this device's wifi state changing
                }
            }
        }
    }
}
