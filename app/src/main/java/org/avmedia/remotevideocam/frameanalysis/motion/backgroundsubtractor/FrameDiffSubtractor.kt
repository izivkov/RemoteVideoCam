package org.avmedia.remotevideocam.frameanalysis.motion.backgroundsubtractor

import androidx.tracing.trace
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.webrtc.VideoFrame

private const val TAG = "FrameDiffSubtractor"

class FrameDiffSubtractor : BackgroundSubtractor {
    class Frame(
        private val buffer: VideoFrame.I420Buffer,
    ) {
        val mat = Mat(buffer.height, buffer.width, CvType.CV_8UC1, buffer.dataY)

        val width get() = mat.width()

        val height get() = mat.height()

        fun release() = buffer.release()
    }

    private var lastFrame: Frame? = null
    private val foreground = Mat()

    override fun apply(buffer: VideoFrame.Buffer): Mat? = trace("$TAG.apply") {
        val frame = trace("toI420") {
            Frame(buffer.toI420())
        }

        if (lastFrame == null ||
            lastFrame?.width != frame.width ||
            lastFrame?.height != frame.height) {
            lastFrame?.release()
            lastFrame = frame
            return@trace null
        }

        val lastFrame = checkNotNull(this.lastFrame)
        trace("extractForeground") {
            Core.absdiff(lastFrame.mat, frame.mat, foreground)
        }

        lastFrame.release()
        this.lastFrame = frame

        return@trace foreground
    }

    override fun release() {
        lastFrame?.release()
        lastFrame = null

        foreground.release()
    }
}
