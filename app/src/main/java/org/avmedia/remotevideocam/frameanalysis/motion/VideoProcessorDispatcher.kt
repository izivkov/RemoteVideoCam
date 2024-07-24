package org.avmedia.remotevideocam.frameanalysis.motion

import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink

class VideoProcessorDispatcher : VideoProcessor {

    private var defaultVideoSink: VideoSink? = null;

    override fun onCapturerStarted(success: Boolean) {
    }

    override fun onCapturerStopped() {
    }

    override fun onFrameCaptured(videoFrame: VideoFrame) {

        defaultVideoSink?.onFrame(videoFrame)
    }

    override fun setSink(videoSink: VideoSink?) {
        defaultVideoSink = videoSink
    }
}