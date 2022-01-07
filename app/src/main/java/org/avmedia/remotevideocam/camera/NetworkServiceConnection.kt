package org.avmedia.remotevideocam.camera

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import org.avmedia.remotevideocam.display.Utils
import org.avmedia.remotevideocam.utils.ConnectionUtils
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

class NetworkServiceConnection : ILocalConnection {
    private var context: Context? = null
    private val REMOTE_SERVICE_NAME = "REMOTE_VIDEO_CAM"
    private val MY_SERVICE_NAME = "REMOTE_VIDEO_CAM" + "-" + Utils.getIPAddress(true)
    private val ALL_SERVICE_TYPES = "_services._dns-sd._udp"
    private val SERVICE_TYPE = "_org_avmedia_remotevideocam._tcp."

    private val port = 19400
    private val hostAddress: InetAddress? = null
    private val hostPort = 0
    private var mNsdManager: NsdManager? = null
    private var dataReceivedCallback: IDataReceived? = null
    private var socketHandler: SocketHandler? = null
    private val messageQueue: BlockingQueue<String> = ArrayBlockingQueue(25)
    private var stopped = true

    @SuppressLint("ServiceCast")
    override fun init(context: Context?) {
        this.context = context
        mNsdManager = context?.getSystemService(Context.NSD_SERVICE) as NsdManager
        socketHandler = SocketHandler(messageQueue)
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
        if (socketHandler == null) {
            return
        }
        socketHandler!!.close()
        try {
            mNsdManager?.stopServiceDiscovery(mDiscoveryListener)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "disconnect: Already discovering: $e")
        }
    }

    override fun isConnected (): Boolean {
        return socketHandler != null && socketHandler!!.isConnected()
    }

    override fun stop() {
        stopped = true
        CameraToDisplayEventBus.emitEvent(ConnectionUtils.createStatus("CONNECTION_ACTIVE", false))
    }

    override fun start() {
        stopped = false
        CameraToDisplayEventBus.emitEvent(ConnectionUtils.createStatus("CONNECTION_ACTIVE", true))
    }

    override val isVideoCapable: Boolean
        get() = true

    override fun sendMessage(message: String?) {
        if (socketHandler != null) {
            message?.let { socketHandler?.put(it) }
        }
    }

    // end of interface
    private lateinit var mDiscoveryListener: NsdManager.DiscoveryListener

    private fun runConnection() {
        try {
            mDiscoveryListener = createDiscoveryListener()

            mNsdManager?.discoverServices( /*ALL_SERVICE_TYPES*/
                SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener
            )
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "runConnection: Already discovering: $e")
        }
    }

    private fun createDiscoveryListener() : NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            // Called as soon as service discovery begins.
            override fun onDiscoveryStarted(regType: String) {
                Timber.d("Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                // A service was found! Do something with it.
                Timber.d("Service discovery success : %s", service)
                try {
                    if (service.serviceType == SERVICE_TYPE && service.serviceName != MY_SERVICE_NAME) {
                        mNsdManager?.resolveService(service, mResolveListener)
                    }
                } catch (e: IllegalArgumentException) {
                    Log.d(TAG, "Got exception: $e")
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.

                Timber.i("onServiceLost")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Timber.i("Discovery stopped: %s", serviceType)
            }

            @SuppressLint("TimberArgCount")
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.e(TAG, "Discovery failed: Error code: %s", errorCode)
                try {
                    mNsdManager?.stopServiceDiscovery(this)
                    // re-try connecting
                    runConnection()
                } catch (e:Exception) {
                    Timber.d("Got exception $e")
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.e("Discovery failed: Error code:%s", errorCode)
                mNsdManager?.stopServiceDiscovery(this)
            }
        }
    }
    var mResolveListener: NsdManager.ResolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Called when the resolve fails. Use the error code to debug.
            Timber.e("Resolve failed %s", errorCode)
            Timber.e("service = %s", serviceInfo)

            // re-try connecting
            runConnection()
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Timber.d("Resolve Succeeded. %s", serviceInfo)

            // Obtain port and IP
            val port: Int = serviceInfo.port
            val host: String = serviceInfo.host.hostAddress
            Timber.d("PORT: $port, address: $host")

            if (host == Utils.getIPAddress(true)) {
                Timber.d("Same IP.")
                return
            }

            (context as Activity?)
                ?.runOnUiThread(
                    Runnable {
                        try {
                            DisplayToCameraEventBus.emitEvent(JSONObject("{command: \"CONNECTED\"}"))
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    })
            object : Thread("Receiver Thread") {
                override fun run() {
                    val clientInfo = socketHandler!!.connect(host, port)
                    if (clientInfo == null) {
                        Timber.d("Could not get a connection")
                        return
                    }
                    startReceiver(socketHandler, clientInfo.reader)
                    startSender(socketHandler, clientInfo.writer)
                    Log.i(TAG, "Connected....")
                }
            }.start()
        }
    }

    private fun startReceiver(socketHandler: SocketHandler?, reader: Scanner) {
        object : Thread("startReceiver Thread") {
            override fun run() {
                socketHandler!!.runReceiver(reader)
            }
        }.start()
    }

    private fun startSender(socketHandler: SocketHandler?, writer: OutputStream) {
        object : Thread("startSender Thread") {
            override fun run() {
                try {
                    socketHandler!!.runSender(writer)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.start()
    }

    internal inner class SocketHandler(private var messageQueue: BlockingQueue<String>) {
        private var client: Socket? = null
        fun isConnected(): Boolean {
            return client != null && !client!!.isClosed
        }

        internal inner class ClientInfo(var reader: Scanner, var writer: OutputStream)

        fun connect(host: String?, port: Int): ClientInfo? {
            val clientInfo: ClientInfo
            try {
                client = Socket(host, port)
                clientInfo = ClientInfo(
                    Scanner(DataInputStream(BufferedInputStream(client!!.getInputStream()))),
                    client!!.getOutputStream()
                )
            } catch (e: Exception) {
                return null
            }
            return clientInfo
        }

        fun runReceiver(reader: Scanner) {
            try {
                while (true) {
                    val msg = reader.nextLine().trim { it <= ' ' }
                    if (!stopped) {
                        (context as Activity?)?.runOnUiThread(Runnable {
                            dataReceivedCallback?.dataReceived(
                                msg
                            )
                        })
                    }
                }
            } catch (e: Exception) {
                close()
            }
        }

        fun put(message: String) {
            try {
                this.messageQueue.put(message)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        @SuppressLint("TimberArgCount")
        fun runSender(writer: OutputStream) {
            while (true) {
                try {
                    val message = messageQueue.take()
                    Timber.i(TAG, "queue capacity: " + messageQueue.remainingCapacity())
                    writer.write((message+"\n").toByteArray(StandardCharsets.UTF_8))

                } catch (e: InterruptedException) {
                    Timber.i(TAG, "runSender got exception: $e")
                    close()

                    // reconnect again
                    if (isConnected()) {
                        runConnection()
                    }
                    break
                } catch (e: IOException) {
                    Timber.i(TAG, "runSender got exception: $e")
                    close()
                    if (isConnected()) {
                        runConnection()
                    }
                    break
                }
            }
        }

        fun close() {
            try {
                if (client == null || client!!.isClosed) {
                    return
                }
                client!!.close()
                (context as Activity?)
                    ?.runOnUiThread(
                        Runnable {
                            try {
                                DisplayToCameraEventBus.emitEvent(
                                    JSONObject("{command: \"DISCONNECTED\"}")
                                )
                            } catch (e: JSONException) {
                                e.printStackTrace()
                            }
                        })
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private const val TAG = "NetworkServiceConn"
    }
}