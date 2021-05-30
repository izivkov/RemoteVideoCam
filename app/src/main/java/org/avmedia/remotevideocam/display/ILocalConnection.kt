package org.avmedia.remotevideocam.display

import android.content.Context

interface ILocalConnection {

    fun init(context: Context)

    fun setDataCallback(dataCallback: IDataReceived?)

    fun connect(context: Context)

    fun disconnect(context: Context? = null)

    fun isConnected(): Boolean

    fun sendMessage(message: String?)

    fun start ()

    fun stop ()
}