package org.avmedia.remotevideocam.camera.customcomponents

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

class WebRTCSurfaceView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) :
    SurfaceViewRenderer(context, attrs) {
    private val ratioWidth = 0
    private val ratioHeight = 0

    /**
     * SurfaceViewRenderer creates its EGL Surface only in the surfaceCreated() callback;
     * init() only recreates the EGL thread. If the view is already visible at that moment
     * (surfaceCreated has already fired), the renderer can never obtain a Surface again,
     * which shows up in logcat as "EglRenderer: Dropping frame - No surface" and a black preview.
     * So toggle visibility once after init to force surfaceDestroyed -> surfaceCreated.
     */
    override fun init(
        sharedContext: EglBase.Context?,
        rendererEvents: RendererCommon.RendererEvents?
    ) {
        super.init(sharedContext, rendererEvents)
        recreateSurfaceIfAlreadyVisible()
    }

    private fun recreateSurfaceIfAlreadyVisible() {
        if (!isAttachedToWindow || visibility != VISIBLE) return
        Log.d("RVCDIAG", "[cameraView@${System.identityHashCode(this)}] force surface recreation")
        visibility = INVISIBLE
        postDelayed(
            {
                if (isAttachedToWindow) {
                    visibility = VISIBLE
                }
            },
            80L
        )
    }
}
