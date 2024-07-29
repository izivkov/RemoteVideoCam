package org.avmedia.remotevideocam.frameanalysis.motion

import android.graphics.Matrix
import android.opengl.GLES20
import android.os.Handler
import org.avmedia.remotevideocam.camera.VideoProcessorImpl
import org.opencv.android.OpenCVLoader
import org.opencv.core.MatOfPoint
import org.webrtc.GlRectDrawer
import org.webrtc.GlTextureFrameBuffer
import org.webrtc.GlUtil
import org.webrtc.RendererCommon
import org.webrtc.TextureBufferImpl
import org.webrtc.VideoFrame
import org.webrtc.VideoFrame.TextureBuffer
import org.webrtc.VideoFrameDrawer
import org.webrtc.YuvConverter

/**
 * Transform Mat coordinates(top-left) to Texture coordinates(bottom-left) by flipping the y value.
 */
private val matToTextureTransform = Matrix().apply {
    preTranslate(0.5f, 0.5f)
    preScale(1f, -1f)
    preTranslate(-0.5f, -0.5f)
}

/**
 * Used to analyze video frames for motion detection.
 */
class MotionProcessor : VideoProcessorImpl.FrameProcessor {

    /**
     *  Notify the detection result.
     */
    interface Listener {

        fun onDetectionResult(detected: Boolean)
    }

    private val yuvConverter = YuvConverter()
    private val glDrawer = GlRectDrawer()
    private val motionDetector = MotionDetector()

    private var texId: Int? = null
    private var listener: Listener? = null
    private var renderMotion: Boolean = false
    private val enable get() = listener != null

    init {
        check(OpenCVLoader.initLocal()) {
            "Fail to init OpenCV"
        }
    }

    override fun process(frame: VideoFrame): VideoFrame {
        /**
         * Return the original frame if not enabled.
         */
        if (!enable) {
            return frame
        }

        return processInternal(frame)
    }

    private fun processInternal(frame: VideoFrame): VideoFrame {
        val textureBuffer = frame.buffer as TextureBufferImpl
        val contours = motionDetector.analyzeMotion(textureBuffer)

        notifyListener(contours)

        val resultBuffer = if (renderMotion) {
            val modifiedBuffer = modifyTextureBuffer(textureBuffer, contours)
            VideoFrame(modifiedBuffer, frame.rotation, frame.timestampNs)
        } else {
            frame
        }

        contours.forEach {
            it.release()
        }

        return resultBuffer
    }

    fun setMotionListener(listener: Listener?, renderMotion: Boolean = false) {
        this.listener = listener
        this.renderMotion = renderMotion
    }

    private fun notifyListener(contours: List<MatOfPoint>) {
        val motionDetected = contours.isNotEmpty()
        listener?.onDetectionResult(motionDetected)
    }

    private fun modifyTextureBuffer(
        textureFrame: TextureBufferImpl,
        contours: List<MatOfPoint>,
    ): TextureBufferImpl {
        val handler: Handler = textureFrame.toI420Handler
        val texId = this.texId ?: GlUtil.generateTexture(GLES20.GL_TEXTURE_2D).also {
            texId = it
        }
        val width = textureFrame.width
        val height = textureFrame.height

        motionDetector.uploadToTexture(width, height, contours, texId)

        // TODO: optimize perf with a single framebuffer
        val frameBuffer = GlTextureFrameBuffer(GLES20.GL_RGBA)
        renderToTexture(frameBuffer, textureFrame, texId)

        return TextureBufferImpl(
            width,
            height,
            TextureBuffer.Type.RGB,
            frameBuffer.textureId,
            Matrix(),
            handler,
            yuvConverter,
            handler.makeReleaseRunnable(frameBuffer),
        )
    }

    private fun renderToTexture(
        frameBuffer: GlTextureFrameBuffer,
        cameraTextureBuffer: TextureBuffer,
        texId: Int
    ) {
        frameBuffer.setSize(cameraTextureBuffer.width, cameraTextureBuffer.height)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer.frameBufferId)

        // Render camera frame
        VideoFrameDrawer.drawTexture(
            glDrawer,
            cameraTextureBuffer,
            Matrix(),
            cameraTextureBuffer.width,
            cameraTextureBuffer.height,
            0,
            0,
            cameraTextureBuffer.width,
            cameraTextureBuffer.height,
        )

        // Render motion detection
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        val transform = RendererCommon.convertMatrixFromAndroidGraphicsMatrix(matToTextureTransform)
        glDrawer.drawRgb(
            texId,
            transform,
            cameraTextureBuffer.width,
            cameraTextureBuffer.height,
            0,
            0,
            cameraTextureBuffer.width,
            cameraTextureBuffer.height,
        )
        GLES20.glDisable(GLES20.GL_BLEND)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GlUtil.checkNoGLES2Error("Fails to render to texture")

        // TODO: optimize perf via sync object
        GLES20.glFlush()
    }

    fun release() {
        texId?.let {
            val source = IntArray(1) { it }
            GLES20.glDeleteTextures(1, source, 0)
            texId = 0
        }
        glDrawer.release()
        yuvConverter.release()
    }
}
