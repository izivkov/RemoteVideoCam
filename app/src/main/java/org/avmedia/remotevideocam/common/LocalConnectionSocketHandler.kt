package org.avmedia.remotevideocam.common

import android.app.Activity
import android.content.Context
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.BlockingQueue
import timber.log.Timber

class LocalConnectionSocketHandler(
        private val context: Context?,
        private val messageQueue: BlockingQueue<String>,
        private val dataReceivedCallback: (() -> IDataReceived?),
        private val onConnected: () -> Unit,
        private val onDisconnected: () -> Unit
) {
    private var client: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var stopped = false

    fun isConnected(): Boolean {
        return client != null && !client!!.isClosed
    }

    class ClientInfo(var reader: Scanner, var writer: OutputStream)

    fun connect(host: String?, port: Int): ClientInfo? {
        try {
            Timber.d("LocalConnectionSocketHandler: Connecting to $host:$port...")
            client = Socket()
            client!!.connect(java.net.InetSocketAddress(host, port), 5000) // 5s timeout
            Timber.d("LocalConnectionSocketHandler: Successfully connected to $host:$port")
            return createClientInfo(client!!)
        } catch (e: Exception) {
            Timber.e("LocalConnectionSocketHandler: Connect to $host:$port failed: ${e.message}")
            return null
        }
    }

    fun waitForConnection(port: Int): ClientInfo? {
        try {
            serverSocket = ServerSocket()
            serverSocket?.reuseAddress = true
            // Force binding to IPv4 to avoid the "::" issue
            serverSocket?.bind(
                    java.net.InetSocketAddress(java.net.InetAddress.getByName("0.0.0.0"), port)
            )

            Timber.i("ServerSocket listening on ${serverSocket?.localSocketAddress} (port $port)")
            Timber.i("Waiting for connection...")

            client = serverSocket?.accept()
            Timber.i("Accepted connection from ${client?.remoteSocketAddress}")

            return createClientInfo(client!!)
        } catch (e: Exception) {
            if (!stopped) {
                Timber.e("Wait for connection failed: ${e.message}")
            }
            return null
        } finally {
            try {
                serverSocket?.close()
                serverSocket = null
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun createClientInfo(socket: Socket): ClientInfo {
        return ClientInfo(
                Scanner(DataInputStream(BufferedInputStream(socket.getInputStream()))),
                socket.getOutputStream()
        )
    }

    fun startCommunication(clientInfo: ClientInfo) {
        stopped = false
        Timber.d("LocalConnectionSocketHandler: Starting communication threads")
        onConnected()

        Thread({ runReceiver(clientInfo.reader) }, "Receiver Thread").start()

        Thread(
                        {
                            try {
                                runSender(clientInfo.writer)
                            } catch (e: Exception) {
                                Timber.e("Sender thread error: ${e.message}")
                            }
                        },
                        "Sender Thread"
                )
                .start()
        Timber.d("LocalConnectionSocketHandler: Communication threads started")
    }

    private fun runReceiver(reader: Scanner) {
        try {
            while (reader.hasNextLine()) {
                val msg = reader.nextLine().trim { it <= ' ' }
                if (!stopped) {
                    (context as? Activity)?.runOnUiThread {
                        dataReceivedCallback()?.dataReceived(msg)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e("Receiver error: ${e.message}")
        } finally {
            close()
        }
    }

    private fun runSender(writer: OutputStream) {
        try {
            while (!stopped) {
                val message = messageQueue.take()
                writer.write((message + "\n").toByteArray(StandardCharsets.UTF_8))
                writer.flush()
            }
        } catch (e: InterruptedException) {
            Timber.i("Sender interrupted")
        } catch (e: IOException) {
            Timber.e("Sender error: ${e.message}")
        } finally {
            close()
        }
    }

    fun put(message: String) {
        try {
            Timber.d("LocalConnectionSocketHandler: Queuing message (${message.take(100)}...)")
            messageQueue.put(message)
            Timber.d("LocalConnectionSocketHandler: Message queued successfully")
        } catch (e: InterruptedException) {
            Timber.e("LocalConnectionSocketHandler: Failed to queue message: ${e.message}")
            e.printStackTrace()
        }
    }

    fun stop() {
        stopped = true
    }

    fun close() {
        try {
            stopped = true
            serverSocket?.close()
            serverSocket = null

            if (client == null || client!!.isClosed) {
                return
            }
            client!!.close()
            client = null
            onDisconnected()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
