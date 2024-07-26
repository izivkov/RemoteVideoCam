package org.avmedia.remotevideocam.frameanalysis.motion

import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.GLES20
import android.os.Handler
import android.util.Log
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.GlTextureFrameBuffer
import org.webrtc.GlUtil
import org.webrtc.TextureBufferImpl
import org.webrtc.ThreadUtils
import org.webrtc.VideoFrame
import org.webrtc.VideoFrame.TextureBuffer
import org.webrtc.VideoFrameDrawer
import org.webrtc.YuvConverter

private const val TAG = "MotionDetector2"
class MotionDetector2 : EglRenderer.FrameListener {

    private val yuvConverter = YuvConverter()
    private val frameDrawer = VideoFrameDrawer()
    private val glDrawer = GlRectDrawer()

    fun process(frame: VideoFrame): VideoFrame {
        val textureFrame = frame.buffer as TextureBufferImpl
        val handler = textureFrame.toI420Handler
        check(handler.looper.isCurrentThread)

        val textureFrameBuffer = GlTextureFrameBuffer(GLES20.GL_RGBA).apply {
            setSize(frame.rotatedWidth, frame.rotatedHeight)
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, textureFrameBuffer.frameBufferId)
        frameDrawer.drawFrame(frame, glDrawer, Matrix())
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GlUtil.checkNoGLES2Error("$TAG custom framebuffer")

        GLES20.glFlush()

        val newTextureBuffer = TextureBufferImpl(
            textureFrameBuffer.width,
            textureFrameBuffer.height,
            TextureBuffer.Type.RGB,
            textureFrameBuffer.textureId,
            Matrix(),
            handler,
            yuvConverter,
            handler.makeReleaseRunnable(textureFrameBuffer),
        )
        return VideoFrame(newTextureBuffer, 0, frame.timestampNs)
    }

    override fun onFrame(bitmap: Bitmap?) {
    }

    companion object {
        val DEFAULT = MotionDetector2()
    }
}