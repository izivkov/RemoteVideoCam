package org.avmedia.remotevideocam.frameanalysis.motion

import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.GLES20
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.GlTextureFrameBuffer
import org.webrtc.TextureBufferImpl
import org.webrtc.VideoFrame
import org.webrtc.VideoFrame.TextureBuffer
import org.webrtc.VideoFrameDrawer

class MotionDetector2 : EglRenderer.FrameListener {

//    private var fenceSyncObject = AtomicReference<Long?>()
//    private val textureFrameBuffer = GlTextureFrameBuffer(GLES20.GL_RGBA)
    private val frameDrawer = VideoFrameDrawer()
    private val glDrawer = GlRectDrawer()

    fun process(frame: VideoFrame): VideoFrame {

        val textureFrame = frame.buffer as TextureBufferImpl
        val textureFrameBuffer = GlTextureFrameBuffer(GLES20.GL_RGBA).apply {
            setSize(frame.rotatedWidth, frame.rotatedHeight)
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, textureFrameBuffer.textureId)
        frameDrawer.drawFrame(frame, glDrawer, Matrix())
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        val newTextureBuffer = TextureBufferImpl(
            textureFrameBuffer.width,
            textureFrameBuffer.height,
            TextureBuffer.Type.RGB,
            textureFrameBuffer.textureId,
            Matrix(),
            textureFrame.toI420Handler,
            textureFrame.yuvConverter,
            makeReleaseRunnable(textureFrameBuffer),
        )
//        return VideoFrame(newTextureBuffer, frame.rotation, frame.timestampNs)
        return VideoFrame(newTextureBuffer, 0, frame.timestampNs)
    }

    override fun onFrame(bitmap: Bitmap?) {
    }

    companion object {
        val DEFAULT = MotionDetector2()

        fun makeReleaseRunnable(textureFrameBuffer: GlTextureFrameBuffer): Runnable {
            return Runnable { textureFrameBuffer.release() }
        }
    }

//        fenceSyncObject.getAndSet(null)?.let {
//            GLES30.glWaitSync(it, 0, GLES30.GL_TIMEOUT_IGNORED)
//            GLES30.glDeleteSync(it)
//        }

//        GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0).also {
//            fenceSyncObject.getAndSet(it)?.let { old ->
//                // delete old fence
//                GLES30.glDeleteSync(old)
//            }
//        }
}