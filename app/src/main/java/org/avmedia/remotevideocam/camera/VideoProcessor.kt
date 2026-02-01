package org.avmedia.remotevideocam.camera

import android.graphics.Matrix
import android.opengl.GLES20
import androidx.tracing.trace
import org.webrtc.GlRectDrawer
import org.webrtc.VideoFrame
import org.webrtc.YuvConverter
import timber.log.Timber

/**
 * Transform Mat coordinates(top-left) to Texture coordinates(bottom-left) by flipping the y value.
 */
private val matToTextureTransform = Matrix().apply {
    preTranslate(0.5f, 0.5f)
    preScale(1f, -1f)
    preTranslate(-0.5f, -0.5f)
}

private const val TAG = "MotionProcessor"

/**
 * Used to analyze video frames for motion detection.
 */
class VideoProcessor : VideoProcessorImpl.FrameProcessor {

    /**
     *  Notify the detection result.
     */
    interface Listener {

        fun onDetectionResult(detected: Boolean)
    }

    private val yuvConverter = YuvConverter()
    private val glDrawer = GlRectDrawer()

    private var texId: Int? = null
    private var listener: Listener? = null
    private var renderMotion: Boolean = false
    private val enable get() = listener != null

    override fun process(frame: VideoFrame): VideoFrame = trace("$TAG.process") {
        /**
         * Return the original frame if not enabled.
         */
        if (!enable) {
            return@trace frame
        }

        return@trace frame
    }

    fun setMotionListener(listener: Listener?, renderMotion: Boolean = false) {
        Timber.tag(TAG).i(
            "setMotionListener, listener %s, renderMotion %s",
            listener,
            renderMotion
        )
        this.listener = listener
        this.renderMotion = renderMotion
    }

    fun release() {
        texId?.let {
            val source = IntArray(1) { it }
            GLES20.glDeleteTextures(1, source, 0)
            texId = 0
        }
        glDrawer.release()
        yuvConverter.release()
    }
}
