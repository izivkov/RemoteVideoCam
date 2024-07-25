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
        val modifiedFrame = motionDetector.process(videoFrame)
        defaultVideoSink?.onFrame(modifiedFrame)
    }

    override fun setSink(videoSink: VideoSink?) {
        defaultVideoSink = videoSink
    }
}