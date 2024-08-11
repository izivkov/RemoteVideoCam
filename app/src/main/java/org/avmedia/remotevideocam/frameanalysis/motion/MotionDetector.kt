package org.avmedia.remotevideocam.frameanalysis.motion

import android.opengl.GLES20
import androidx.tracing.trace
import org.avmedia.remotevideocam.frameanalysis.motion.backgroundsubtractor.BackgroundSubtractor
import org.avmedia.remotevideocam.frameanalysis.motion.backgroundsubtractor.FrameDiffSubtractor
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.webrtc.TextureBufferImpl
import timber.log.Timber
import java.nio.ByteBuffer

private const val GRAY_SCALE_THRESHOLD = 20.0
private const val MIN_AREA_THRESHOLD = 0
private val SCALAR_GREEN = Scalar(0.0, 255.0, 0.0, 255.0)
private val SCALAR_TRANSPARENT = Scalar(0.0, 0.0, 0.0, 0.0)

private const val TAG = "MotionDetector"

/**
 * Use OpenCV to detect the motion of two consecutive frames. It finds contours of the foreground
 * image to conclude the motion detection result.
 */
class MotionDetector(
    private val backgroundSubtractor: BackgroundSubtractor = FrameDiffSubtractor()
) {

    private val morphKernel by lazy {
        Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE,
            Size(3.0, 3.0),
        )
    }

    private var byteBufferToTexture: ByteBuffer? = null

    fun analyzeMotion(textureBuffer: TextureBufferImpl): List<MatOfPoint> =
        trace("$TAG.analyzeMotion") {
            val foregroundMat =
                backgroundSubtractor.apply(textureBuffer) ?: return@trace emptyList()

            executeImageProcessing(foregroundMat)

            val contours = findContours(foregroundMat, MIN_AREA_THRESHOLD)

            foregroundMat.release()

            return@trace contours
        }

    fun uploadToTexture(
        width: Int,
        height: Int,
        contours: List<MatOfPoint>,
        texId: Int
    ) = trace("$TAG.uploadToTexture") {
        val filtered = contours.filter { it.width() * it.height() > MIN_AREA_THRESHOLD }

        val byteBuffer = getByteBufferForRgbaTexture(width, height)
        val mat = Mat(height, width, CvType.CV_8UC4, byteBuffer)

        // Clear the mat with transparent black.
        Imgproc.rectangle(mat, Rect(0, 0, width, height), SCALAR_TRANSPARENT, Imgproc.FILLED)

        // Draw contours with green outlines.
        Imgproc.drawContours(mat, filtered, -1, SCALAR_GREEN, Imgproc.FILLED)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            width,
            height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            byteBuffer
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        mat.release()
    }

    private fun getByteBufferForRgbaTexture(width: Int, height: Int): ByteBuffer {
        val capacity = width * height * 4
        val byteBuffer = this.byteBufferToTexture?.takeIf {
            it.capacity() == capacity
        } ?: ByteBuffer.allocateDirect(capacity).also {
            byteBufferToTexture = it
        }
        return byteBuffer
    }

    private fun executeImageProcessing(foregroundMat: Mat) =
        trace("executeImageProcessing") {
            trace("binarizeImage") {
                // Create a binary image with a gray scale threshold.
                Imgproc.threshold(
                    foregroundMat,
                    foregroundMat,
                    GRAY_SCALE_THRESHOLD,
                    255.0,
                    Imgproc.THRESH_BINARY,
                )
            }

            trace("reduceNoise") {
                /**
                 * Noise reduction choices
                 * 1. MORPH_OPEN to reduce random noises from the foreground.
                 * 2. MORPH_DILATE to enlarge the white region, reassembling parts back into a
                 * single object. Useful for objects with various backgrounds, like cars.
                 * 3. Optionally, MORPH_CLOSE to closing the small holes in the foreground objects. .
                 * See details https://docs.opencv.org/4.x/d9/d61/tutorial_py_morphological_ops.html
                 */
                Imgproc.morphologyEx(foregroundMat, foregroundMat, Imgproc.MORPH_OPEN, morphKernel)
                Imgproc.morphologyEx(
                    foregroundMat,
                    foregroundMat,
                    Imgproc.MORPH_DILATE,
                    morphKernel
                )
            }
        }

    private fun findContours(
        foregroundMat: Mat,
        minArea: Int = 0,
    ): List<MatOfPoint> = trace("findContours") {
        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(
            foregroundMat,
            contours,
            Mat(),
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE,
        )
        return@trace contours.filter { it.width() * it.height() >= minArea }
    }

    fun release() {
        backgroundSubtractor.release()
    }

    companion object {
        init {
            Timber.tag(TAG).i("Initialize OpenCV")
            check(OpenCVLoader.initLocal()) {
                "Fail to init OpenCV"
            }
        }
    }
}
