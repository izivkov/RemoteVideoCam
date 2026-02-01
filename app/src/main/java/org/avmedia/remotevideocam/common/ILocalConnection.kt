package org.avmedia.remotevideocam.common

import android.content.Context

interface IDataReceived {
    fun dataReceived(data: String?)
}

interface ILocalConnection {
    fun init(context: Context?)
    fun setDataCallback(dataCallback: IDataReceived?)
    fun connect(context: Context?)
    fun disconnect(context: Context? = null)
    fun isConnected(): Boolean
    fun sendMessage(message: String?)
    fun start()
    fun stop()
    val isVideoCapable: Boolean
    val name: String
}
