package org.avmedia.remotevideocam.frameanalysis.motion.backgroundsubtractor

import org.opencv.core.Mat
import org.webrtc.TextureBufferImpl
import org.webrtc.VideoFrame

/**
 * Used to segment the foreground objects from the background scene.
 */
interface BackgroundSubtractor {

    /**
     * @param buffer the latest video buffer
     * @return the mat of foreground objects
     */
    fun apply(buffer: VideoFrame.Buffer): Mat?

    fun release()
}
