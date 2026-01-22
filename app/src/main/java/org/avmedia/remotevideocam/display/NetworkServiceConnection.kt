package org.avmedia.remotevideocam.display

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.RegistrationListener
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.ArrayBlockingQueue
import org.avmedia.remotevideocam.common.IDataReceived
import org.avmedia.remotevideocam.common.ILocalConnection
import org.avmedia.remotevideocam.common.LocalConnectionSocketHandler
import org.avmedia.remotevideocam.utils.ProgressEvents
import timber.log.Timber

@SuppressLint("StaticFieldLeak")
object NetworkServiceConnection : ILocalConnection {

    private const val TAG = "NetworkServiceConnDisplay"
    private var mNsdManager: NsdManager? = null
    private var SERVICE_NAME: String? = null
    private const val SERVICE_TYPE = "_org_avmedia_remotevideocam._tcp."

    private var dataReceivedCallback: IDataReceived? = null

    private const val port = 19400
    private var socketHandler: LocalConnectionSocketHandler? = null
    private var context: Context? = null

    override fun init(context: Context?) {
        this.context = context
        SERVICE_NAME =
                "REMOTE_VIDEO_CAM-${android.os.Build.MODEL}-${org.avmedia.remotevideocam.utils.Utils.getMyIP() ?: "unknown"}"
        Timber.d("Initialised SERVICE_NAME: $SERVICE_NAME")

        mNsdManager = context?.getSystemService(Context.NSD_SERVICE) as NsdManager?
        socketHandler =
                LocalConnectionSocketHandler(
                        context,
                        ArrayBlockingQueue(100),
                        { dataReceivedCallback },
                        {
                            Timber.i("Display Connected.")
                            ProgressEvents.onNext(ProgressEvents.Events.ConnectionDisplaySuccessful)
                            try {
                                org.avmedia.remotevideocam.camera.DisplayToCameraEventBus.emitEvent(
                                        org.json.JSONObject("{\"command\": \"CONNECTED\"}")
                                )
                            } catch (e: org.json.JSONException) {
                                e.printStackTrace()
                            }
                        },
                        {
                            Timber.i("Display Disconnected.")
                            ProgressEvents.onNext(ProgressEvents.Events.CameraDisconnected)
                            try {
                                org.avmedia.remotevideocam.camera.DisplayToCameraEventBus.emitEvent(
                                        org.json.JSONObject("{\"command\": \"DISCONNECTED\"}")
                                )
                            } catch (e: org.json.JSONException) {
                                e.printStackTrace()
                            }
                        }
                )
        registerService(port)
    }

    override fun connect(context: Context?) {
        runConnection()
    }

    override fun setDataCallback(dataCallback: IDataReceived?) {
        dataReceivedCallback = dataCallback
    }

    override fun disconnect(context: Context?) {
        try {
            mNsdManager?.unregisterService(mRegistrationListener)
            socketHandler?.close()
        } catch (e: Exception) {
            Timber.d("Got exception in disconnect: $e")
        }
    }

    override fun isConnected(): Boolean {
        return socketHandler?.isConnected() ?: false
    }

    override fun sendMessage(message: String?) {
        message?.let { socketHandler?.put(it) }
    }

    override fun start() {}

    override fun stop() {
        socketHandler?.stop()
    }

    override val isVideoCapable: Boolean = true
    override val name: String = "Network (NSD) Display"

    private fun runConnection() {
        Thread(
                        {
                            while (context != null) {
                                if (socketHandler?.isConnected() != true) {
                                    Timber.d("Waiting for a connection from Camera...")
                                    val clientInfo = socketHandler?.waitForConnection(port)
                                    clientInfo?.let { socketHandler?.startCommunication(it) }
                                }
                                try {
                                    Thread.sleep(1000)
                                } catch (e: Exception) {}
                            }
                        },
                        "NSD Display Server Thread"
                )
                .start()
    }

    private fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo()
        serviceInfo.serviceName = SERVICE_NAME
        serviceInfo.serviceType = SERVICE_TYPE
        serviceInfo.port = port

        try {
            mNsdManager?.registerService(
                    serviceInfo,
                    NsdManager.PROTOCOL_DNS_SD,
                    mRegistrationListener
            )
        } catch (e: Exception) {
            Timber.e("Registration failed: $e")
        }
    }

    private var mRegistrationListener: RegistrationListener =
            object : RegistrationListener {
                override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                    SERVICE_NAME = nsdServiceInfo.serviceName
                    Timber.d("Registered name : $SERVICE_NAME")
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Timber.e("onRegistrationFailed: $errorCode")
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                    Timber.d("Service Unregistered : ${serviceInfo.serviceName}")
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Timber.e("onUnregistrationFailed : $errorCode")
                }
            }
}
