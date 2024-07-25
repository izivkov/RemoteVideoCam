package org.avmedia.remotevideocam.frameanalysis.motion

import android.opengl.GLES30
import android.util.Log
import androidx.constraintlayout.widget.ConstraintSet.Motion
import org.webrtc.TextureBufferImpl
import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink

class VideoProcessorDispatcher : VideoProcessor {
    private val motionDetector = MotionDetector2.DEFAULT
    private var defaultVideoSink: VideoSink? = null;

    override fun onCapturerStarted(success: Boolean) {
    }

    override fun onCapturerStopped() {
    }

    override fun onFrameCaptured(videoFrame: VideoFrame) {
//        val modifiedFrame =
//            VideoFrame(
//            MotionDetector2.TextureBufferImpl2(videoFrame.buffer as TextureBufferImpl),
//                videoFrame.rotation,
//                videoFrame.timestampNs
//            )

        val modifiedFrame = motionDetector.process(videoFrame)
//        modifiedFrame.retain()
//        defaultVideoSink?.let {
//           it.onFrame(modifiedFrame)
//            Log.d("lweijing",
//                "defaultVideoSink ${(modifiedFrame.buffer as MotionDetector2.TextureBufferImpl2).textureFrameBuffer}")
//        }
        defaultVideoSink?.onFrame(modifiedFrame)
//        modifiedFrame.release()
    }

    override fun setSink(videoSink: VideoSink?) {
        defaultVideoSink = videoSink
    }
}