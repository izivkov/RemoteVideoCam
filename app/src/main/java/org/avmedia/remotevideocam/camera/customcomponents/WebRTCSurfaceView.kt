package org.avmedia.remotevideocam.camera.customcomponents

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintSet.Motion
import org.avmedia.remotevideocam.frameanalysis.motion.MotionDetector
import org.avmedia.remotevideocam.frameanalysis.motion.MotionDetector2
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame

class WebRTCSurfaceView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) :
    SurfaceViewRenderer(context, attrs) {
    private val ratioWidth = 0
    private val ratioHeight = 0

    private var motionDetectorDebugView: ImageView? = null
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
}
