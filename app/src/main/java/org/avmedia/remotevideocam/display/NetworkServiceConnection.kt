package org.avmedia.remotevideocam.display

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.RegistrationListener
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.remotevideocam.camera.DisplayToCameraEventBus
import org.avmedia.remotevideocam.customcomponents.LocalEventBus
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import kotlin.concurrent.thread

@SuppressLint("StaticFieldLeak")
object NetworkServiceConnection : ILocalConnection {

    private const val TAG = "NetworkServiceConn"
    private var mNsdManager: NsdManager? = null
    private var SERVICE_NAME = "REMOTE_VIDEO_CAM" + "-" + Utils.getIPAddress(true)
    private const val SERVICE_TYPE = "_org_avmedia_remotevideocam._tcp."

    private var dataReceivedCallback: IDataReceived? = null

    private const val port = 19400
    private lateinit var socketHandler: SocketHandler
    private val messageQueue: BlockingQueue<String> = ArrayBlockingQueue(100)
    private lateinit var context: Context

    override fun init(context: Context) {
        NetworkServiceConnection.context = context
        mNsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager?
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
            if (mNsdManager == null) {
                return
            }
            mNsdManager?.unregisterService(mRegistrationListener)
            socketHandler.close()
        } catch (e: java.lang.Exception) {
            Timber.d("Got exception in disconnect: $e")
        }
    }

    override fun isConnected(): Boolean {
        return this::socketHandler.isInitialized && socketHandler.isConnected()
    }

    override fun sendMessage(message: String?) {
        socketHandler.put(message)
    }

    override fun start() {
    }

    override fun stop() {
    }
    // end of interface

    @SuppressLint("StaticFieldLeak")
    private fun runConnection() {
        socketHandler = SocketHandler(messageQueue)

        thread {
            val client: SocketHandler.ClientInfo = socketHandler.connect(port) ?: return@thread

            thread {
                socketHandler.runSender(client.writer)
            }
            thread {
                socketHandler.runReceiver(client.reader)
            }
        }
    }

    class SocketHandler(private val messageQueue: BlockingQueue<String>) {
        private lateinit var client: Socket
        private lateinit var serverSocket: ServerSocket
        private lateinit var clientInfo: ClientInfo

        class ClientInfo(val reader: Scanner, val writer: OutputStream)

        fun isConnected(): Boolean {
            return this::client.isInitialized && !client.isClosed
        }

        fun connect(port: Int): ClientInfo? {
            try {
                serverSocket = ServerSocket(port)
                serverSocket.reuseAddress = true

                while (true) {
                    client = serverSocket.accept()
                    Timber.i("Connected...")

                    // only connect if the app is NOT running on this device.
                    if (client.inetAddress.hostAddress != Utils.getIPAddress(true)) {
                        LocalEventBus.onNext(LocalEventBus.ProgressEvents.ConnectionDisplaySuccessful)
                        break
                    } else {
                        Timber.i("Trying to connect to myself. Ignore")
                    }
                }

                val reader = Scanner(DataInputStream(BufferedInputStream(client.getInputStream())))
                val writer = client.getOutputStream()

                clientInfo = ClientInfo(reader, writer)

                println("Client connected: ${client.inetAddress.hostAddress}")
            } catch (e: Exception) {
                Timber.i("Got exception: %s", e)
                close()
                return null
            }
            return clientInfo
        }

        fun runReceiver(reader: Scanner?) {
            try {
                while (true) {
                    val payload: String? = reader?.nextLine()
                    if (payload != null) {

                        (context as Activity).runOnUiThread {
                            dataReceivedCallback?.dataReceived(
                                String(
                                    payload.toByteArray(), StandardCharsets.UTF_8
                                )
                            )
                        }
                    }
                }

            } catch (ex: Exception) {
                reader?.close()
                Timber.d("got exception $ex")
                close()
            } finally {
            }
        }

        fun runSender(writer: OutputStream?) {
            Timber.i("runSender started...")
            try {
                while (true) {
                    val message = messageQueue.take() as String
                    Timber.d("Sending command $message")
                    writer?.write((message + '\n').toByteArray(Charset.defaultCharset()))
                }
            } catch (e: Exception) {
                Timber.d("runSender InterruptedException: {e}")
                writer?.close()
            } finally {
            }
            Timber.d("end of runSender thread...")
        }

        fun close() {
            if (this::client.isInitialized && !client.isClosed) {
                LocalEventBus.onNext(LocalEventBus.ProgressEvents.DisplayDisconnected)
                client.close()
            }
            try {
                serverSocket.close()
            } catch (e: Exception) {
            }
        }

        fun put(message: String?) {
            try {
                this.messageQueue.put(message)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    private fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo()
        serviceInfo.serviceName = SERVICE_NAME
        serviceInfo.serviceType = SERVICE_TYPE
        serviceInfo.port = port

        try {
            mNsdManager!!.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                mRegistrationListener
            )
        } catch (e: Exception) {
            Timber.d("Got exception: %s", e)
        }
    }

    private var mRegistrationListener: RegistrationListener = object : RegistrationListener {
        override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
            val mServiceName = NsdServiceInfo.serviceName
            SERVICE_NAME = mServiceName
            Timber.d("Registered name : $mServiceName")
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Timber.d("onRegistrationFailed")
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
            Timber.d("Service Unregistered : %s", serviceInfo.serviceName)
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Timber.d("onUnregistrationFailed : %s", errorCode)
        }
    }
}