package org.avmedia.remotevideocam.camera

import android.content.Context
import android.view.SurfaceView
import android.view.TextureView
import org.webrtc.SurfaceViewRenderer

interface IVideoServer {
    fun setResolution(w: Int, h: Int)
    fun setConnected(connected: Boolean, context: Context?)
    fun init(context: Context?)
    val isRunning: Boolean
    fun startClient()
    fun sendServerUrl()
    fun sendVideoStoppedStatus()
    fun setView(view: SurfaceView?)
    fun setView(view: TextureView?)
    fun setView(view: SurfaceViewRenderer?)
}