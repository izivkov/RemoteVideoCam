package org.avmedia.remotevideocam.frameanalysis.motion

import android.graphics.Matrix
import android.opengl.GLES20
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.webrtc.GlRectDrawer
import org.webrtc.GlTextureFrameBuffer
import org.webrtc.RendererCommon.GlDrawer
import org.webrtc.TextureBufferImpl
import org.webrtc.VideoFrame
import org.webrtc.VideoFrame.TextureBuffer
import org.webrtc.VideoFrameDrawer

class MotionDetector2 {

    private val textureFrameBuffer = GlTextureFrameBuffer(GLES20.GL_RGBA)
    private val frameDrawer = VideoFrameDrawer()
    private val glDrawer = GlRectDrawer()

    fun process(frame: VideoFrame): VideoFrame {
        val textureFrame = frame.buffer as TextureBufferImpl
        textureFrameBuffer.setSize(textureFrame.width, textureFrame.height)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, textureFrameBuffer.textureId)

        frameDrawer.drawFrame(frame, glDrawer, Matrix())

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        return TextureBufferImpl(
            textureFrame.width,
            textureFrame.height,
            TextureBuffer.Type.RGB,
            textureFrameBuffer.textureId,
            Matrix(),
            textureFrame.toI420Handler,
            textureFrame.yuvConverter,
            null,
        ).let {
            VideoFrame(it, frame.rotation, frame.timestampNs)
        }
    }
}