package org.avmedia.remotevideocam.camera

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import java.util.concurrent.ArrayBlockingQueue
import org.avmedia.remotevideocam.common.IDataReceived
import org.avmedia.remotevideocam.common.ILocalConnection
import org.avmedia.remotevideocam.common.LocalConnectionSocketHandler
import org.avmedia.remotevideocam.utils.ProgressEvents
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.O)
class WiFiAwareServiceConnection(override val isVideoCapable: Boolean) : ILocalConnection {
    override val name: String = "WiFi Aware"
    private var awareManager: WifiAwareManager? = null
    private var awareSession: WifiAwareSession? = null
    private var socketHandler: LocalConnectionSocketHandler? = null
    private var context: Context? = null
    private var dataReceivedCallback: IDataReceived? = null
    private var connectivityManager: ConnectivityManager? = null
    private val SERVICE_NAME = "REMOTE_VIDEO_CAM_AWARE"
    private val port = 8889

    private var subscribeDiscoverySession: SubscribeDiscoverySession? = null // Add this line

    override fun init(context: Context?) {
        this.context = context
        awareManager = context?.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
        connectivityManager =
                context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
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
        if (awareManager == null || awareManager?.isAvailable == false) {
            Timber.e("WiFi Aware is not available")
            return
        }

        awareManager?.attach(
                object : AttachCallback() {
                    override fun onAttached(session: WifiAwareSession) {
                        awareSession = session
                        // In this implementation, we both publish and subscribe to find each other
                        publishService(session)
                        subscribeService(session)
                    }

                    override fun onAttachFailed() {
                        Timber.e("WiFi Aware attach failed")
                    }
                },
                Handler(Looper.getMainLooper())
        )
    }

    @SuppressLint("MissingPermission")
    private fun publishService(session: WifiAwareSession) {
        val config = PublishConfig.Builder().setServiceName(SERVICE_NAME).build()
        session.publish(
                config,
                object : DiscoverySessionCallback() {
                    override fun onPublishStarted(session: PublishDiscoverySession) {
                        Timber.d("WiFi Aware publish started")
                    }

                    override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                        Timber.d("WiFi Aware message received from peer")
                    }
                },
                Handler(Looper.getMainLooper())
        )
    }

    @SuppressLint("MissingPermission")
    private fun subscribeService(session: WifiAwareSession) {
        val config = SubscribeConfig.Builder().setServiceName(SERVICE_NAME).build()
        session.subscribe(
                config,
                object : DiscoverySessionCallback() {
                    // Add this onSubscribeStarted callback
                    override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                        Timber.d("WiFi Aware subscribe started")
                        subscribeDiscoverySession = session // Save the session here
                    }

                    override fun onServiceDiscovered(
                            peerHandle: PeerHandle,
                            serviceSpecificInfo: ByteArray,
                            matchFilter: List<ByteArray>
                    ) {
                        Timber.d("WiFi Aware service discovered")
                        // Now, when you call establishDataPath, the session will be ready
                        establishDataPath(peerHandle)
                    }
                },
                Handler(Looper.getMainLooper())
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun establishDataPath(peerHandle: PeerHandle) {

        val discoverySession =
                subscribeDiscoverySession
                        ?: run {
                            Timber.e("Cannot establish data path, subscribe session is null")
                            return
                        }

        // Use the correct session here
        val networkSpecifier =
                WifiAwareNetworkSpecifier.Builder(discoverySession, peerHandle)
                        .setPort(port)
                        .build()

        val networkRequest =
                NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                        .setNetworkSpecifier(networkSpecifier)
                        .build()

        connectivityManager?.requestNetwork(
                networkRequest,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Timber.d("WiFi Aware network available")

                        Thread(
                                        {
                                            // For WiFi Aware, we need to decide who is server and
                                            // who is client.
                                            // A simple way is to try to connect, and if it fails,
                                            // wait for connection.
                                            // Here we try to connect first.

                                            val host = "FE80::1" // Placeholder

                                            val connectionInfo = socketHandler?.connect(host, port)
                                            if (connectionInfo != null) {
                                                socketHandler?.startCommunication(connectionInfo)
                                            } else {
                                                val serverInfo =
                                                        socketHandler?.waitForConnection(port)
                                                serverInfo?.let { info ->
                                                    socketHandler?.startCommunication(info)
                                                }
                                            }
                                        },
                                        "WiFiAware Connection Thread"
                                )
                                .start()
                    }

                    override fun onLost(network: Network) {
                        Timber.d("WiFi Aware network lost")
                        socketHandler?.close()
                    }
                }
        )
    }

    override fun disconnect(context: Context?) {
        stop()
        socketHandler?.close()
        awareSession?.close()
        awareSession = null
    }

    override fun isConnected(): Boolean {
        return socketHandler?.isConnected() ?: false
    }

    override fun sendMessage(message: String?) {
        message?.let { socketHandler?.put(it) }
    }

    override fun stop() {
        socketHandler?.stop()
    }

    override fun start() {}

    private fun emitConnected() {
        (context as? Activity)?.runOnUiThread {
            try {
                DisplayToCameraEventBus.emitEvent(JSONObject("{\"command\": \"CONNECTED\"}"))
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    private fun emitDisconnected() {
        ProgressEvents.onNext(ProgressEvents.Events.DisplayDisconnected)
        (context as? Activity)?.runOnUiThread {
            try {
                DisplayToCameraEventBus.emitEvent(JSONObject("{\"command\": \"DISCONNECTED\"}"))
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }
}
