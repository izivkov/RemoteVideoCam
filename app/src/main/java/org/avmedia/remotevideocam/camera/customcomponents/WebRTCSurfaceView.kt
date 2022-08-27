package org.avmedia.remotevideocam.camera.customcomponents

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.avmedia.remotevideocam.customcomponents.LocalEventBus
import org.webrtc.SurfaceViewRenderer

class WebRTCSurfaceView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) :
    SurfaceViewRenderer(context, attrs) {
    private val ratioWidth = 0
    private val ratioHeight = 0
}

