package org.avmedia.remotevideocam.camera

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import java.util.concurrent.ArrayBlockingQueue
import org.avmedia.remotevideocam.common.IDataReceived
import org.avmedia.remotevideocam.common.ILocalConnection
import org.avmedia.remotevideocam.common.LocalConnectionSocketHandler
import org.avmedia.remotevideocam.utils.ProgressEvents
import org.json.JSONObject
import timber.log.Timber
import java.net.Inet6Address

// typealias dict<K, V> = Map<K, V>

@RequiresApi(Build.VERSION_CODES.O)
class WiFiAwareServiceConnection(override val isVideoCapable: Boolean) : ILocalConnection {
    override val name: String = "WiFi Aware"

    companion object {
        private const val SERVICE_NAME = "RVC_AWARE_STREAM"
        private const val DEFAULT_PORT = 8889
        private const val QUEUE_CAPACITY = 25
        private const val IPV6_HOST = "FE80::1"
        private const val THREAD_NAME = "WiFiAware Connection Thread"
        private const val PSK = "RemoteCamSecure123"
        private const val COMMAND_KEY = "command"
        private const val EVENT_CONNECTED = "CONNECTED"
        private const val EVENT_DISCONNECTED = "DISCONNECTED"
        private const val MSG_ID_TIE_BREAK = 0
        private const val SOCKET_DELAY_MS = 1000L
    }

    private var awareManager: WifiAwareManager? = null
    private var awareSession: WifiAwareSession? = null
    private var socketHandler: LocalConnectionSocketHandler? = null
    private var connectivityManager: ConnectivityManager? = null
    private var context: Context? = null
    private var dataReceivedCallback: IDataReceived? = null

    private var publishSession: PublishDiscoverySession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isConnecting = false
    private var didInitiateDataPath = false

    private val localDeviceId = "${Build.MODEL}_${Build.BOARD}_${(100..999).random()}"

    override fun init(context: Context?) {
        this.context = context
        awareManager = context?.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
        connectivityManager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        socketHandler = LocalConnectionSocketHandler(
            context,
            ArrayBlockingQueue(QUEUE_CAPACITY),
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
        cleanup()
        if (awareManager?.isAvailable == false) {
            Timber.e("WiFi Aware unavailable")
            return
        }
        awareManager?.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                awareSession = session
                startDiscovery(session)
            }
        }, Handler(Looper.getMainLooper()))
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery(session: WifiAwareSession) {
        val pubConfig = PublishConfig.Builder()
            .setServiceName(SERVICE_NAME)
            .setServiceSpecificInfo(localDeviceId.toByteArray())
            .build()

        session.publish(pubConfig, object : DiscoverySessionCallback() {
            override fun onPublishStarted(session: PublishDiscoverySession) {
                publishSession = session
                Timber.d("WiFiAware: Publish Started. ID: $localDeviceId")
            }

            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                val remoteId = String(message)
                if (localDeviceId < remoteId && !didInitiateDataPath) {
                    Timber.d("WiFiAware: I am RESPONDER")
                    setupDataPath(peerHandle, isInitiator = false)
                }
            }
        }, Handler(Looper.getMainLooper()))

        val subConfig = SubscribeConfig.Builder().setServiceName(SERVICE_NAME).build()

        session.subscribe(subConfig, object : DiscoverySessionCallback() {
            override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                subscribeSession = session
                Timber.d("WiFiAware: Subscribe Started")
            }

            override fun onServiceDiscovered(peerHandle: PeerHandle, info: ByteArray, matches: List<ByteArray>) {
                val remoteId = String(info)
                if (isConnecting || didInitiateDataPath) return

                if (localDeviceId > remoteId) {
                    isConnecting = true
                    didInitiateDataPath = true
                    Timber.d("WiFiAware: I am INITIATOR")
                    setupDataPath(peerHandle, isInitiator = true)
                } else {
                    publishSession?.sendMessage(peerHandle, MSG_ID_TIE_BREAK, localDeviceId.toByteArray())
                }
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun setupDataPath(peerHandle: PeerHandle, isInitiator: Boolean) {
        val sessionToUse: DiscoverySession? = if (isInitiator) subscribeSession else publishSession

        if (sessionToUse == null) {
            Timber.e("WiFiAware: Session missing for role")
            return
        }

        // Role constants
        val roleInitiator = 1 // WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR
        val roleResponder = 0 // WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER
        val role = if (isInitiator) roleInitiator else roleResponder

        // Create the builder and cast it explicitly to ensure the compiler sees the API 31+ methods
        val builder = WifiAwareNetworkSpecifier.Builder(sessionToUse, peerHandle)
        builder.setPskPassphrase(PSK)

        // Explicit role setting - Since compileSdk is 36, this should resolve.
        // If it red-lines in the IDE, ignore it and try to build, or use 'builder.setDataPathRole(role)'
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            builder.setDataPathRole(role)
//        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(builder.build())
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Timber.d("WiFi Aware Network Ready. Role: ${if (isInitiator) "Initiator" else "Responder"}")
                startSocketThread(network, isInitiator)
            }
            override fun onUnavailable() {
                isConnecting = false
                didInitiateDataPath = false
                Timber.e("WiFi Aware Network Negotiation Timed Out")
            }
        }

        connectivityManager?.requestNetwork(request, networkCallback!!)
    }

    private fun startSocketThread(network: Network, isClient: Boolean) {
        Thread({
            try {
                val linkProperties = connectivityManager?.getLinkProperties(network)
                val ipv6Addr = linkProperties?.linkAddresses
                    ?.firstOrNull { it.address is Inet6Address }
                    ?.address?.hostAddress?.split("%")?.get(0) ?: IPV6_HOST

                if (isClient) {
                    Thread.sleep(SOCKET_DELAY_MS)
                    socketHandler?.connect(ipv6Addr, DEFAULT_PORT)?.let {
                        socketHandler?.startCommunication(it)
                    }
                } else {
                    socketHandler?.waitForConnection(DEFAULT_PORT)?.let {
                        socketHandler?.startCommunication(it)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Socket Error")
                isConnecting = false
                didInitiateDataPath = false
            }
        }, THREAD_NAME).start()
    }

    private fun cleanup() {
        isConnecting = false
        didInitiateDataPath = false
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            publishSession?.close()
            subscribeSession?.close()
            awareSession?.close()
        } catch (e: Exception) {
            Timber.e(e)
        }
        networkCallback = null
        publishSession = null
        subscribeSession = null
        awareSession = null
    }

    override fun disconnect(context: Context?) = cleanup()
    override fun isConnected(): Boolean = socketHandler?.isConnected() ?: false
    override fun sendMessage(message: String?) { message?.let { socketHandler?.put(it) } }
    override fun stop() { socketHandler?.stop() }
    override fun start() {}

    private fun emitConnected() {
        (context as? Activity)?.runOnUiThread {
            val eventData: Map<String, String> = mapOf(COMMAND_KEY to EVENT_CONNECTED)
            DisplayToCameraEventBus.emitEvent(JSONObject(eventData))
        }
    }

    private fun emitDisconnected() {
        isConnecting = false
        didInitiateDataPath = false
        ProgressEvents.onNext(ProgressEvents.Events.DisplayDisconnected)
        (context as? Activity)?.runOnUiThread {
            val eventData: Map<String, String> = mapOf(COMMAND_KEY to EVENT_DISCONNECTED)
            DisplayToCameraEventBus.emitEvent(JSONObject(eventData))
        }
    }
}
