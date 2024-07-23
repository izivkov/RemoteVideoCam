package org.avmedia.remotevideocam.camera.customcomponents

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.widget.ImageView
import org.avmedia.remotevideocam.frameanalysis.motion.MotionDetector
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame

class WebRTCSurfaceView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) :
    SurfaceViewRenderer(context, attrs), MotionDetector.Listener {
    private val ratioWidth = 0
    private val ratioHeight = 0

    private var motionDetector: MotionDetector? = null
    private var motionDetectorDebugView: ImageView? = null
    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    fun enableMotionDetection(debugView: ImageView?) {
        val enableDebug = debugView != null
        motionDetector = MotionDetector(enableDebug = enableDebug).apply {
            setListener(this@WebRTCSurfaceView)
        }
        motionDetectorDebugView = debugView
    }

    override fun onFrame(frame: VideoFrame) {
        super.onFrame(frame)
        motionDetector?.onFrame(frame)
    }

    override fun onDetectionResult(detected: Boolean, bitmap: Bitmap?) {
        motionDetectorDebugView?.let { debugView ->
            mainHandler.post {
                debugView.setImageBitmap(bitmap)
            }
        }
    }
}

