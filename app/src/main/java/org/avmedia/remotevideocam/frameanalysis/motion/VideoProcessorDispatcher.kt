package org.avmedia.remotevideocam.frameanalysis.motion

import android.opengl.GLES30
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
//        val modifiedFrame = videoFrame
        val modifiedFrame = motionDetector.process(videoFrame)
//        modifiedFrame.retain()
        defaultVideoSink?.onFrame(modifiedFrame)
        modifiedFrame.release()
    }

    override fun setSink(videoSink: VideoSink?) {
        defaultVideoSink = videoSink
    }
}