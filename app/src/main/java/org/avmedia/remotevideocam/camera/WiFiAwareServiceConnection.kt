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
import java.net.Inet6Address
import java.util.concurrent.ArrayBlockingQueue
import org.avmedia.remotevideocam.common.IDataReceived
import org.avmedia.remotevideocam.common.ILocalConnection
import org.avmedia.remotevideocam.common.LocalConnectionSocketHandler
import org.avmedia.remotevideocam.utils.ProgressEvents
import org.json.JSONObject
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.Q)
class WiFiAwareServiceConnection(override val isVideoCapable: Boolean) : ILocalConnection {
    override val name: String = "WiFi Aware"

    companion object {
        private const val SERVICE_NAME = "RVC_AWARE_STREAM"
        private const val DEFAULT_PORT = 8889
        private const val QUEUE_CAPACITY = 25
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
    @Volatile private var context: Context? = null // Made context volatile
    private var dataReceivedCallback: IDataReceived? = null

    private var publishSession: PublishDiscoverySession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Using AtomicBoolean for thread-safe state management
    private val isConnecting = java.util.concurrent.atomic.AtomicBoolean(false)
    private val didInitiateDataPath = java.util.concurrent.atomic.AtomicBoolean(false)

    // Using a more robust unique ID
    private val localDeviceId = "${Build.MANUFACTURER}_${Build.MODEL}_${(1000..9999).random()}"

    override fun init(context: Context?) {
        this.context = context
        awareManager = context?.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
        connectivityManager =
                context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        socketHandler =
                LocalConnectionSocketHandler(
                        this.context, // CORRECTED: Pass the context value directly
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
        awareManager?.attach(
                object : AttachCallback() {
                    override fun onAttached(session: WifiAwareSession) {
                        awareSession = session
                        startDiscovery(session)
                    }
                    override fun onAttachFailed() {
                        Timber.e("WiFi Aware attach failed")
                    }
                },
                Handler(Looper.getMainLooper())
        )
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery(session: WifiAwareSession) {
        val pubConfig =
                PublishConfig.Builder()
                        .setServiceName(SERVICE_NAME)
                        .setServiceSpecificInfo(localDeviceId.toByteArray())
                        .build()

        session.publish(
                pubConfig,
                object : DiscoverySessionCallback() {
                    override fun onPublishStarted(session: PublishDiscoverySession) {
                        publishSession = session
                        Timber.d("WiFiAware: Publish Started. ID: $localDeviceId")
                    }

                    override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                        val remoteId = String(message)
                        // Tie-breaking logic: lower ID initiates connection.
                        if (localDeviceId < remoteId && !didInitiateDataPath.get()) {
                            Timber.d("WiFiAware: Tie-break lost. I am RESPONDER.")
                            // Attempt to set up data path as Responder.
                            if (didInitiateDataPath.compareAndSet(false, true)) {
                                setupDataPath(peerHandle, isInitiator = false)
                            }
                        }
                    }
                },
                Handler(Looper.getMainLooper())
        )

        val subConfig = SubscribeConfig.Builder().setServiceName(SERVICE_NAME).build()

        session.subscribe(
                subConfig,
                object : DiscoverySessionCallback() {
                    override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                        subscribeSession = session
                        Timber.d("WiFiAware: Subscribe Started")
                    }

                    override fun onServiceDiscovered(
                            peerHandle: PeerHandle,
                            info: ByteArray,
                            matches: List<ByteArray>
                    ) {
                        if (isConnecting.get() || didInitiateDataPath.get()) return

                        val remoteId = String(info)
                        if (localDeviceId > remoteId) {
                            // This device has priority, initiate the connection.
                            if (isConnecting.compareAndSet(false, true)) {
                                didInitiateDataPath.set(true)
                                Timber.d("WiFiAware: Tie-break won. I am INITIATOR.")
                                setupDataPath(peerHandle, isInitiator = true)
                            }
                        } else {
                            // This device has lower priority, send a message to the other peer to
                            // start the connection.
                            Timber.d(
                                    "WiFiAware: Service discovered, but remote has priority. Sending message."
                            )
                            publishSession?.sendMessage(
                                    peerHandle,
                                    MSG_ID_TIE_BREAK,
                                    localDeviceId.toByteArray()
                            )
                        }
                    }
                },
                Handler(Looper.getMainLooper())
        )
    }

    @SuppressLint("MissingPermission")
    private fun setupDataPath(peerHandle: PeerHandle, isInitiator: Boolean) {
        val sessionToUse: DiscoverySession? = if (isInitiator) subscribeSession else publishSession

        if (sessionToUse == null) {
            Timber.e("WiFiAware: Session missing for role. Cannot setup data path.")
            resetState()
            return
        }

        val builder =
                WifiAwareNetworkSpecifier.Builder(sessionToUse, peerHandle).setPskPassphrase(PSK)

        val requestBuilder =
                NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(builder.build())

        val request = requestBuilder.build() // Build the request after setting the role

        networkCallback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Timber.d(
                                "WiFi Aware Network Ready. Role: ${if (isInitiator) "Initiator" else "Responder"}"
                        )
                        startSocketThread(network, isInitiator)
                    }

                    override fun onUnavailable() {
                        Timber.e("WiFi Aware Network Negotiation Timed Out or Failed.")
                        resetState()
                    }
                    override fun onLost(network: Network) {
                        Timber.w("WiFi Aware Network Lost.")
                        resetState()
                        emitDisconnected()
                    }
                }
        connectivityManager?.requestNetwork(request, networkCallback!!)
    }

    private fun startSocketThread(network: Network, isClient: Boolean) {
        Thread(
                        {
                            try {
                                val linkProperties = connectivityManager?.getLinkProperties(network)
                                val ipv6Addr =
                                        linkProperties
                                                ?.linkAddresses
                                                ?.firstOrNull { it.address is Inet6Address }
                                                ?.address
                                                ?.hostAddress
                                                ?.split("%")
                                                ?.get(0)

                                if (ipv6Addr == null) {
                                    Timber.e(
                                            "Could not find IPv6 address. Aborting socket connection."
                                    )
                                    resetState()
                                    return@Thread
                                }

                                if (isClient) {
                                    Thread.sleep(
                                            SOCKET_DELAY_MS
                                    ) // Give server socket time to start
                                    socketHandler?.connect(ipv6Addr, DEFAULT_PORT)?.let {
                                        socketHandler?.startCommunication(it)
                                    }
                                } else {
                                    socketHandler?.waitForConnection(DEFAULT_PORT)?.let {
                                        socketHandler?.startCommunication(it)
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Socket Error in WiFi Aware connection thread")
                                resetState()
                            }
                        },
                        THREAD_NAME
                )
                .start()
    }

    private fun resetState() {
        isConnecting.set(false)
        didInitiateDataPath.set(false)
    }

    private fun cleanup() {
        resetState()
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            publishSession?.close()
            subscribeSession?.close()
            awareSession?.close()
            socketHandler?.stop()
        } catch (e: Exception) {
            Timber.e(e, "Error during cleanup")
        }
        networkCallback = null
        publishSession = null
        subscribeSession = null
        awareSession = null
    }

    override fun disconnect(context: Context?) = cleanup()
    override fun isConnected(): Boolean = socketHandler?.isConnected() ?: false
    override fun sendMessage(message: String?) {
        message?.let { socketHandler?.put(it) }
    }
    override fun stop() {
        socketHandler?.stop()
    }
    override fun start() {}

    private fun emitConnected() {
        // Use the volatile context variable in a thread-safe way
        (this.context as? Activity)?.runOnUiThread {
            val eventData: Map<String, String> = mapOf(COMMAND_KEY to EVENT_CONNECTED)
            DisplayToCameraEventBus.emitEvent(JSONObject(eventData))
        }
    }

    private fun emitDisconnected() {
        resetState() // Ensure state is reset on disconnect
        ProgressEvents.onNext(ProgressEvents.Events.DisplayDisconnected)
        (this.context as? Activity)?.runOnUiThread {
            val eventData: Map<String, String> = mapOf(COMMAND_KEY to EVENT_DISCONNECTED)
            DisplayToCameraEventBus.emitEvent(JSONObject(eventData))
        }
    }
}
