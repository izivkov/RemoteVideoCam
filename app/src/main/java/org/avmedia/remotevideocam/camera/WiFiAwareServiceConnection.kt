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

@RequiresApi(Build.VERSION_CODES.O)
class WiFiAwareServiceConnection(override val isVideoCapable: Boolean) : ILocalConnection {
    override val name: String = "WiFi Aware"

    companion object {
        private const val SERVICE_NAME = "REMOTE_VIDEO_CAM_AWARE"
        private const val DEFAULT_PORT = 8889
        private const val QUEUE_CAPACITY = 25
        private const val IPV6_HOST = "FE80::1"
        private const val THREAD_NAME = "WiFiAware Connection Thread"
        private const val PSK = "RemoteCamSecure123"
        private const val TRANSPORT_PROTOCOL_TCP = 6
    }

    private var awareManager: WifiAwareManager? = null
    private var awareSession: WifiAwareSession? = null
    private var socketHandler: LocalConnectionSocketHandler? = null
    private var connectivityManager: ConnectivityManager? = null
    private var context: Context? = null
    private var dataReceivedCallback: IDataReceived? = null

    private var discoverySession: DiscoverySession? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isConnecting = false

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

        awareManager?.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                awareSession = session
                startDiscovery(session)
            }
        }, Handler(Looper.getMainLooper()))
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery(session: WifiAwareSession) {
        val pubConfig = PublishConfig.Builder().setServiceName(SERVICE_NAME).build()
        session.publish(pubConfig, object : DiscoverySessionCallback() {
            override fun onPublishStarted(session: PublishDiscoverySession) {
                discoverySession = session
            }
        }, Handler(Looper.getMainLooper()))

        val subConfig = SubscribeConfig.Builder().setServiceName(SERVICE_NAME).build()
        session.subscribe(subConfig, object : DiscoverySessionCallback() {
            override fun onServiceDiscovered(peerHandle: PeerHandle, info: ByteArray, matches: List<ByteArray>) {
                if (isConnecting) return
                isConnecting = true
                setupDataPath(peerHandle)
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun setupDataPath(peerHandle: PeerHandle) {
        val session = discoverySession ?: return

        // IMPORTANT: We specify TCP protocol but let the system handle channel selection
        // given the existing Wi-Fi connection.
        val specifier = if (session is SubscribeDiscoverySession) {
            WifiAwareNetworkSpecifier.Builder(session, peerHandle)
                .setPskPassphrase(PSK)
                .build()
        } else {
            WifiAwareNetworkSpecifier.Builder(session as PublishDiscoverySession, peerHandle)
                .setPskPassphrase(PSK)
                .build()
        }

        // We use a broader request to allow the system to negotiate
        // coexistence with the existing Wi-Fi network.
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED) // Broaden search
            .setNetworkSpecifier(specifier)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Timber.d("Network Available despite Infrastructure Wi-Fi!")
                startSocketThread(session is SubscribeDiscoverySession)
            }
            override fun onUnavailable() {
                Timber.e("Factory rejected. Try disconnecting from standard Wi-Fi.")
                isConnecting = false
            }
        }
        connectivityManager?.requestNetwork(request, networkCallback!!)
    }

    private fun startSocketThread(isClient: Boolean) {
        Thread({
            if (isClient) {
                socketHandler?.connect(IPV6_HOST, DEFAULT_PORT)?.let { socketHandler?.startCommunication(it) }
            } else {
                socketHandler?.waitForConnection(DEFAULT_PORT)?.let { socketHandler?.startCommunication(it) }
            }
        }, THREAD_NAME).start()
    }

    private fun cleanup() {
        isConnecting = false
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            discoverySession?.close()
            awareSession?.close()
        } catch (e: Exception) {
            Timber.e(e)
        }
        networkCallback = null
        discoverySession = null
        awareSession = null
    }

    override fun disconnect(context: Context?) = cleanup()
    override fun isConnected(): Boolean = socketHandler?.isConnected() ?: false
    override fun sendMessage(message: String?) { message?.let { socketHandler?.put(it) } }
    override fun stop() { socketHandler?.stop() }
    override fun start() {}

    private fun emitConnected() {
        (context as? Activity)?.runOnUiThread {
            DisplayToCameraEventBus.emitEvent(JSONObject(mapOf("command" to "CONNECTED")))
        }
    }

    private fun emitDisconnected() {
        isConnecting = false
        ProgressEvents.onNext(ProgressEvents.Events.DisplayDisconnected)
        (context as? Activity)?.runOnUiThread {
            DisplayToCameraEventBus.emitEvent(JSONObject(mapOf("command" to "DISCONNECTED")))
        }
    }
}
