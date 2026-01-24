package org.avmedia.remotevideocam.camera

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ArrayBlockingQueue
import org.avmedia.remotevideocam.common.IDataReceived
import org.avmedia.remotevideocam.common.ILocalConnection
import org.avmedia.remotevideocam.common.LocalConnectionSocketHandler
import org.avmedia.remotevideocam.utils.ProgressEvents
import org.json.JSONObject
import timber.log.Timber

typealias dict<K, V> = Map<K, V>

class WiFiDirectServiceConnection(override val isVideoCapable: Boolean) : ILocalConnection {
    override val name: String = "WiFi Direct"

    companion object {
        private const val PORT = 8888
        private const val QUEUE_CAPACITY = 25
        private const val SERVICE_TYPE = "_rvc._tcp"
        private const val INSTANCE_NAME = "RemoteVideoCam"
        private const val THREAD_NAME = "WiFiDirect Symmetrical Thread"
        private const val CMD_KEY = "command"
        private const val VAL_CONNECTED = "CONNECTED"
        private const val VAL_DISCONNECTED = "DISCONNECTED"
        private const val JITTER_MIN = 500L
        private const val JITTER_MAX = 1500L
        private const val INTENT_CLIENT = 0
    }

    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var socketHandler: LocalConnectionSocketHandler? = null
    private var context: Context? = null
    private var dataReceivedCallback: IDataReceived? = null
    private var receiver: WiFiDirectBroadcastReceiver? = null

    private var isInvokingConnect = false
    private var isSocketStarting = false
    private var discoveryStarted = false

    override fun init(context: Context?) {
        this.context = context
        manager = context?.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = manager?.initialize(context, context?.mainLooper, null)
        socketHandler = LocalConnectionSocketHandler(
            context,
            ArrayBlockingQueue(QUEUE_CAPACITY),
            { dataReceivedCallback },
            { emitConnected() },
            { emitDisconnected() }
        )
    }

    @SuppressLint("MissingPermission")
    override fun connect(context: Context?) {
        if (discoveryStarted) return
        start()

        manager?.clearServiceRequests(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = setupServiceDiscovery()
            override fun onFailure(reason: Int) = setupServiceDiscovery()
        })
    }

    @SuppressLint("MissingPermission")
    private fun setupServiceDiscovery() {
        discoveryStarted = true

        manager?.clearLocalServices(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(INSTANCE_NAME, SERVICE_TYPE, null)
                manager?.addLocalService(channel, serviceInfo, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() = Timber.d("WiFiDirect: Local Service Added")
                    override fun onFailure(reason: Int) {
                        discoveryStarted = false
                        Timber.e("WiFiDirect: Failed to add service: $reason")
                    }
                })
            }
            override fun onFailure(reason: Int) { discoveryStarted = false }
        })

        manager?.setDnsSdResponseListeners(channel, { instanceName, _, srcDevice ->
            if (instanceName.equals(INSTANCE_NAME, ignoreCase = true)) {
                Timber.d("WiFiDirect: Found peer: ${srcDevice.deviceName}")
                val config = WifiP2pConfig().apply {
                    deviceAddress = srcDevice.deviceAddress
                    groupOwnerIntent = INTENT_CLIENT
                }
                connectToDevice(config)
            }
        }, null)

        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        manager?.addServiceRequest(channel, serviceRequest, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                manager?.discoverServices(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() = Timber.d("WiFiDirect: Service Discovery Started")
                    override fun onFailure(reason: Int) {
                        discoveryStarted = false
                        Timber.e("WiFiDirect: Service Discovery Failed: $reason")
                    }
                })
            }
            override fun onFailure(reason: Int) { discoveryStarted = false }
        })
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(config: WifiP2pConfig) {
        if (isInvokingConnect) return
        isInvokingConnect = true

        val randomDelay = (JITTER_MIN..JITTER_MAX).random()
        Handler(Looper.getMainLooper()).postDelayed({
            manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = Timber.d("WiFiDirect: Connection handshake initiated")
                override fun onFailure(reason: Int) {
                    isInvokingConnect = false
                    Timber.e("WiFiDirect: Connection failed: $reason")
                }
            })
        }, randomDelay)
    }

    override fun disconnect(context: Context?) {
        stop()
        socketHandler?.close()
        manager?.removeGroup(channel, null)
        discoveryStarted = false
        isSocketStarting = false
    }

    override fun isConnected(): Boolean = socketHandler?.isConnected() ?: false
    override fun sendMessage(message: String?) { message?.let { socketHandler?.put(it) } }

    override fun stop() {
        socketHandler?.stop()
        try { context?.unregisterReceiver(receiver) } catch (e: Exception) {}
        receiver = null
        discoveryStarted = false
        isSocketStarting = false
    }

    override fun start() {
        if (receiver == null) {
            receiver = WiFiDirectBroadcastReceiver()
            val intentFilter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            }
            context?.registerReceiver(receiver, intentFilter)
        }
    }

    private fun emitConnected() {
        val data: dict<String, String> = mapOf(CMD_KEY to VAL_CONNECTED)
        DisplayToCameraEventBus.emitEvent(JSONObject(data))
    }

    private fun emitDisconnected() {
        ProgressEvents.onNext(ProgressEvents.Events.DisplayDisconnected)
        val data: dict<String, String> = mapOf(CMD_KEY to VAL_DISCONNECTED)
        DisplayToCameraEventBus.emitEvent(JSONObject(data))
        discoveryStarted = false
        isSocketStarting = false
    }

    inner class WiFiDirectBroadcastReceiver : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) {
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)

                if (networkInfo?.isConnected == true) {
                    manager?.requestConnectionInfo(channel) { info ->
                        // Prevent multiple socket threads from starting on the same connection event
                        if (info.groupFormed && !isSocketStarting) {
                            isSocketStarting = true
                            isInvokingConnect = false

                            val host = info.groupOwnerAddress?.hostAddress ?: return@requestConnectionInfo
                            Timber.d("WiFiDirect: Link ready. Host: $host, I am Owner: ${info.isGroupOwner}")

                            Thread({
                                try {
                                    val clientInfo = if (info.isGroupOwner) {
                                        socketHandler?.waitForConnection(PORT)
                                    } else {
                                        socketHandler?.connect(host, PORT)
                                    }
                                    clientInfo?.let { socketHandler?.startCommunication(it) }
                                } catch (e: Exception) {
                                    Timber.e("WiFiDirect: Socket error: ${e.message}")
                                } finally {
                                    // Reset so we can reconnect if the socket drops
                                    isSocketStarting = false
                                }
                            }, THREAD_NAME).start()
                        }
                    }
                } else {
                    isSocketStarting = false
                }
            }
        }
    }

    override fun setDataCallback(dataCallback: IDataReceived?) { this.dataReceivedCallback = dataCallback }
}
