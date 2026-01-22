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
    private var stopped = false

    fun isConnected(): Boolean {
        return client != null && !client!!.isClosed
    }

    class ClientInfo(var reader: Scanner, var writer: OutputStream)

    fun connect(host: String?, port: Int): ClientInfo? {
        try {
            client = Socket(host, port)
            return createClientInfo(client!!)
        } catch (e: Exception) {
            Timber.e("Connect failed: ${e.message}")
            return null
        }
    }

    fun waitForConnection(port: Int): ClientInfo? {
        var serverSocket: ServerSocket? = null
        try {
            serverSocket = ServerSocket(port)
            serverSocket.reuseAddress = true
            Timber.d("Waiting for connection on port $port...")
            client = serverSocket.accept()
            return createClientInfo(client!!)
        } catch (e: Exception) {
            Timber.e("Wait for connection failed: ${e.message}")
            return null
        } finally {
            try {
                serverSocket?.close()
            } catch (e: Exception) {}
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
            messageQueue.put(message)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun stop() {
        stopped = true
    }

    fun close() {
        try {
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
