package org.avmedia.remotevideocam.camera

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.ArrayBlockingQueue
import org.avmedia.remotevideocam.common.IDataReceived
import org.avmedia.remotevideocam.common.ILocalConnection
import org.avmedia.remotevideocam.common.LocalConnectionSocketHandler
import org.avmedia.remotevideocam.utils.ProgressEvents
import org.avmedia.remotevideocam.utils.Utils
import timber.log.Timber

class NetworkServiceConnection : ILocalConnection {
    override val name: String = "Network (NSD) Camera"
    private var context: Context? = null
    private var MY_SERVICE_NAME: String? = null
    private val SERVICE_TYPE = "_org_avmedia_remotevideocam._tcp."

    private var mNsdManager: NsdManager? = null
    private var dataReceivedCallback: IDataReceived? = null
    private var socketHandler: LocalConnectionSocketHandler? = null

    @SuppressLint("ServiceCast")
    override fun init(context: Context?) {
        this.context = context
        MY_SERVICE_NAME =
                "REMOTE_VIDEO_CAM-${android.os.Build.MODEL}-${Utils.getMyIP() ?: "unknown"}"
        Timber.d("Initialised MY_SERVICE_NAME: $MY_SERVICE_NAME")

        mNsdManager = context?.getSystemService(Context.NSD_SERVICE) as NsdManager
        socketHandler =
                LocalConnectionSocketHandler(
                        context,
                        ArrayBlockingQueue(25),
                        { dataReceivedCallback },
                        { emitConnected() },
                        { emitDisconnected() }
                )
    }

    private fun emitConnected() {
        try {
            ProgressEvents.onNext(ProgressEvents.Events.ConnectionCameraSuccessful)
            DisplayToCameraEventBus.emitEvent(org.json.JSONObject("{\"command\": \"CONNECTED\"}"))
        } catch (e: org.json.JSONException) {
            e.printStackTrace()
        }
    }

    private fun emitDisconnected() {
        try {
            DisplayToCameraEventBus.emitEvent(
                    org.json.JSONObject("{\"command\": \"DISCONNECTED\"}")
            )
        } catch (e: org.json.JSONException) {
            e.printStackTrace()
        }
    }

    override fun setDataCallback(dataCallback: IDataReceived?) {
        dataReceivedCallback = dataCallback
    }

    override fun connect(context: Context?) {
        start()
        runConnection()
    }

    override fun disconnect(context: Context?) {
        stop()
        socketHandler?.close()
        try {
            mNsdManager?.stopServiceDiscovery(mDiscoveryListener)
        } catch (e: Exception) {
            Timber.e("disconnect: Exception: $e")
        }
    }

    override fun isConnected(): Boolean {
        return socketHandler?.isConnected() ?: false
    }

    override fun stop() {
        socketHandler?.stop()
    }

    override fun start() {
        // No-op for now as we don't have a receiver to register like WiFiDirect
    }

    override val isVideoCapable: Boolean
        get() = true

    override fun sendMessage(message: String?) {
        message?.let { socketHandler?.put(it) }
    }

    private lateinit var mDiscoveryListener: NsdManager.DiscoveryListener

    private fun runConnection() {
        try {
            mDiscoveryListener = createDiscoveryListener()
            mNsdManager?.discoverServices(
                    SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    mDiscoveryListener
            )
        } catch (e: Exception) {
            Timber.e("runConnection: Exception: $e")
        }
    }

    private fun createDiscoveryListener(): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Timber.d("Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Timber.d("Service discovery success : %s", service)
                try {
                    if (service.serviceType.contains(SERVICE_TYPE.trimEnd('.'))) {
                        val isSelf = service.serviceName == MY_SERVICE_NAME
                        Timber.d(
                                "Service found: ${service.serviceName}, type: ${service.serviceType}. isSelf: $isSelf"
                        )
                        if (!isSelf) {
                            Timber.d("Resolving service: ${service.serviceName}")
                            mNsdManager?.resolveService(service, mResolveListener)
                        } else {
                            Timber.d("Ignoring our own service: ${service.serviceName}")
                        }
                    } else {
                        Timber.d("Ignoring unknown service type: ${service.serviceType}")
                    }
                } catch (e: Exception) {
                    Timber.e("Got exception in onServiceFound: $e")
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Timber.i("onServiceLost")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Timber.i("Discovery stopped: %s", serviceType)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.e("Discovery failed: Error code: %s", errorCode)
                try {
                    mNsdManager?.stopServiceDiscovery(this)
                    runConnection()
                } catch (e: Exception) {
                    Timber.d("Got exception $e")
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.e("Discovery failed: Error code:%s", errorCode)
                try {
                    mNsdManager?.stopServiceDiscovery(this)
                } catch (e: Exception) {}
            }
        }
    }

    private var mResolveListener: NsdManager.ResolveListener =
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Timber.e("Resolve failed %s", errorCode)
                    runConnection()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    Timber.d("Resolve Succeeded. %s", serviceInfo)

                    val port: Int = serviceInfo.port
                    val host: String = serviceInfo.host.hostAddress

                    if (Utils.isMe(host, serviceInfo.serviceName)) {
                        Timber.d("Same device (IP or Name matching). Ignoring connection to self.")
                        return
                    }

                    Thread(
                                    {
                                        socketHandler?.connect(host, port)?.run {
                                            socketHandler!!.startCommunication(this)
                                            Timber.tag(TAG).i("Connected....")
                                        }
                                                ?: Timber.d("Could not get a connection")
                                    },
                                    "NetworkServiceConnection Connect Thread"
                            )
                            .start()
                }
            }

    companion object {
        private const val TAG = "NetworkServiceConnCamera"
    }
}
