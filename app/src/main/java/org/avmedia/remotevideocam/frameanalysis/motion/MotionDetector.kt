package org.avmedia.remotevideocam.frameanalysis.motion

import android.opengl.GLES20
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import org.webrtc.TextureBufferImpl
import java.nio.ByteBuffer

private const val GRAY_SCALE_THRESHOLD = 20.0
private const val MIN_AREA_THRESHOLD = 5
private val SCALAR_GREEN = Scalar(0.0, 255.0, 0.0, 255.0)

/**
 * Use OpenCV to detect the motion of two consecutive frames. It finds contours of the foreground
 * image to conclude the motion detection result.
 */
class MotionDetector {

    private val backgroundSubtractor by lazy {
        Video.createBackgroundSubtractorMOG2()
    }

    init {
        check(OpenCVLoader.initLocal()) {
            "Fail to init OpenCV"
        }
    }

    fun analyzeMotion(textureBuffer: TextureBufferImpl): List<MatOfPoint> {
        val i420Buffer = textureBuffer.toI420()
        val yBuffer = i420Buffer.dataY
        val imageMat = Mat(i420Buffer.height, i420Buffer.width, CvType.CV_8UC1, yBuffer)
        val foregroundMat = Mat()

        executeImageProcessing(imageMat, foregroundMat)

        val contours = findContours(foregroundMat, MIN_AREA_THRESHOLD)

        foregroundMat.release()
        imageMat.release()
        i420Buffer.release()

        return contours
    }

    fun uploadToTexture(
        width: Int,
        height: Int,
        contours: List<MatOfPoint>,
        texId: Int
    ) {
        val filtered = contours.filter { it.width() * it.height() > MIN_AREA_THRESHOLD }

        // TODO: reuse bytebuffer for better perf.
        val byteBuffer = ByteBuffer.allocateDirect(
            width * height * 4
        )
        val mat = Mat(height, width, CvType.CV_8UC4, byteBuffer)

        Imgproc.drawContours(mat, filtered, -1, SCALAR_GREEN)

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

    private fun executeImageProcessing(image: Mat, foregroundMat: Mat) {
        backgroundSubtractor.apply(image, foregroundMat)

        // Create a binary image with a gray scale threshold.
        Imgproc.threshold(
            foregroundMat,
            foregroundMat,
            GRAY_SCALE_THRESHOLD,
            255.0,
            Imgproc.THRESH_BINARY,
        )

        // Noise reduction on the binary image.
        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE,
            Size(3.0, 3.0),
        )

        /**
         * Noise reduction choices
         * 1. MORPH_OPEN to reduce random noises from the foreground.
         * 2. MORPH_DILATE to enlarge the white region, reassembling parts back into a
         * single object. Useful for objects with various backgrounds, like cars.
         * 3. Optionally, MORPH_CLOSE to closing the small holes in the foreground objects. .
         * See details https://docs.opencv.org/4.x/d9/d61/tutorial_py_morphological_ops.html
         */
        Imgproc.morphologyEx(foregroundMat, foregroundMat, Imgproc.MORPH_OPEN, kernel)
        Imgproc.morphologyEx(foregroundMat, foregroundMat, Imgproc.MORPH_DILATE, kernel)
    }

    private fun findContours(
        foregroundMat: Mat,
        minArea: Int = 0,
    ): List<MatOfPoint> {
        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(
            foregroundMat,
            contours,
            Mat(),
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE,
        )
        return contours.filter { it.width() * it.height() >= minArea }
    }
}
