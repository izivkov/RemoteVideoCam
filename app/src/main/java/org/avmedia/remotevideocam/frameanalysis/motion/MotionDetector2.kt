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

//    private var fenceSyncObject = AtomicReference<Long?>()
//    private val textureFrameBuffer = GlTextureFrameBuffer(GLES20.GL_RGBA)
    private val yuvConverter = MyYuvConverter()
    private val frameDrawer = VideoFrameDrawer()
    private val glDrawer = GlRectDrawer()

    fun process(frame: VideoFrame): VideoFrame {
        val textureFrame = frame.buffer as TextureBufferImpl
        val handler = textureFrame.toI420Handler
        check(handler.looper.isCurrentThread)

        val textureFrameBuffer = GlTextureFrameBuffer(GLES20.GL_RGBA).apply {
            setSize(frame.rotatedWidth, frame.rotatedHeight)
        }
        Log.d("lweijing", "new framebuffer $textureFrameBuffer, ${textureFrameBuffer.frameBufferId}, tex ${textureFrameBuffer.textureId}")

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, textureFrameBuffer.textureId)
        GlUtil.checkNoGLES2Error("$TAG glBindFrameBuffer")
        frameDrawer.drawFrame(frame, glDrawer, Matrix())
        GlUtil.checkNoGLES2Error("$TAG frameDrawer")
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
//        GlUtil.checkNoGLES2Error("$TAG glBindFrameBuffer")
        GLES20.glFlush()

        val newTextureBuffer = TextureBufferImpl(
            textureFrameBuffer.width,
            textureFrameBuffer.height,
            TextureBuffer.Type.RGB,
            textureFrameBuffer.textureId,
            Matrix(),
            textureFrame.toI420Handler,
            yuvConverter,
            makeReleaseRunnable(textureFrameBuffer, textureFrame.toI420Handler),
        ).let {
//            it
            TextureBufferImpl2(textureFrameBuffer, it)
        }
//        return VideoFrame(newTextureBuffer, frame.rotation, frame.timestampNs)
        return VideoFrame(newTextureBuffer, 0, frame.timestampNs)
    }

    class TextureBufferImpl2(
        val textureFrameBuffer: GlTextureFrameBuffer,
        private val delegate: TextureBufferImpl
    ) : TextureBuffer by delegate {

        override fun toI420(): VideoFrame.I420Buffer {
            Log.d("lweijing", "toI420 $this")
//            Log.d("lweijing", "toI420 framebuffer $textureFrameBuffer, ${textureFrameBuffer.frameBufferId}")
            return delegate.toI420().let {
                Log.d("lweijing", "toI420 $this done")
                it
            }
        }

        override fun retain() {
            Log.d("lweijing", "retain $this")
//            Log.d("lweijing", "retain framebuffer $textureFrameBuffer, ${textureFrameBuffer.frameBufferId}")
            delegate.retain()
        }

        override fun release() {
            Log.d("lweijing", "release $this")
//            Log.d("lweijing", "release framebuffer $textureFrameBuffer, ${textureFrameBuffer.frameBufferId}")
            delegate.release()
        }
    }

    override fun onFrame(bitmap: Bitmap?) {
    }

    class MyYuvConverter : YuvConverter() {

        override fun convert(inputTextureBuffer: TextureBuffer?): VideoFrame.I420Buffer {
            try {
                return super.convert(inputTextureBuffer)
            } catch (e: RuntimeException) {
                throw e
            }
        }
    }

    companion object {
        val DEFAULT = MotionDetector2()

        fun makeReleaseRunnable(
            textureFrameBuffer: GlTextureFrameBuffer,
            handler: Handler,
        ): Runnable {
            return Runnable {
                if (handler.getLooper().getThread() == Thread.currentThread()) {
                    textureFrameBuffer.release()
                    Log.d("lweijing", "delete framebuffer $textureFrameBuffer, ${textureFrameBuffer.frameBufferId}")
                    Log.d("lweijing", "new line")
                } else {
                    handler.post {
                        textureFrameBuffer.release()
                        Log.d("lweijing", "delete framebuffer $textureFrameBuffer, ${textureFrameBuffer.frameBufferId}")
                        Log.d("lweijing", "new line")
                    }
                }
            }
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