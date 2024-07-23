package org.avmedia.remotevideocam.frameanalysis.motion

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import org.webrtc.VideoFrame

private const val GRAY_SCALE_THRESHOLD = 40.0

private const val TAG = "MotionDetectionInterceptor"

class MotionDetector(
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {

    private val analysisThread: HandlerThread
    private val analysisHandler: Handler

    init {
        check(OpenCVLoader.initLocal()) {
            "Fail to init OpenCV"
        }

        analysisThread = HandlerThread(TAG).apply {
            start()
        }
        analysisHandler = Handler(analysisThread.looper)
    }

    private val backgroundSubtractor by lazy {
        Video.createBackgroundSubtractorMOG2()
    }

    private var debugBitmap: Bitmap? = null

    interface Listener {

        fun onDetectionResult(detected: Boolean, bitmap: Bitmap?)
    }

    @Volatile
    private var listener: Listener? = null

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun onFrame(videoFrame: VideoFrame) {
        // Retains the frame for motion detection.
        videoFrame.retain()

        // Process the frame on a dedicated thread to avoid blocking the incoming video stream.
        analysisHandler.post {
            process(videoFrame)

            // Release the frame.
            videoFrame.release()
        }
    }

    private fun process(videoFrame: VideoFrame) {
        val i420Buffer = videoFrame.buffer.toI420()

        val imageMat = Mat(
            videoFrame.buffer.height,
            videoFrame.buffer.width,
            CvType.CV_8UC1,
            i420Buffer.dataY,
        )

        // Extract the foreground by comparing the diff between current and last frame.
        val foregroundMat = Mat()
        backgroundSubtractor.apply(imageMat, foregroundMat)

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


        val detected = Core.hasNonZero(foregroundMat)
        val bitmap = showDebugView(foregroundMat, imageMat, videoFrame.rotation)
        notifyListener(detected, bitmap)

        // Release frames.
        foregroundMat.release()
        imageMat.release()
        i420Buffer.release()
    }

    private fun notifyListener(detected: Boolean, bitmap: Bitmap?) {
        listener?.let {
            mainHandler.post {
                it.onDetectionResult(detected, bitmap)
            }
        }
    }

    private fun showDebugView(
        foregroundMat: Mat,
        image: Mat,
        rotation: Int,
    ): Bitmap? {
        val rotationCode = when (rotation) {
            90 -> Core.ROTATE_90_CLOCKWISE
            270 -> Core.ROTATE_90_COUNTERCLOCKWISE
            180 -> Core.ROTATE_180
            else -> null
        }
        rotationCode?.let {
            Core.rotate(foregroundMat, foregroundMat, it)
        }

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(
            foregroundMat,
            contours,
            Mat(),
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE,
        )

        val colorMat = Mat()
        Imgproc.cvtColor(image, colorMat, Imgproc.COLOR_GRAY2RGBA)
        rotationCode?.let {
            Core.rotate(colorMat, colorMat, it)
        }

        Imgproc.drawContours(
            colorMat,
            contours,
            -1,
            Scalar(
                0.0,
                255.0,
                0.0,
            ),
            3,
        )
        contours.forEach {
            val rect = Imgproc.boundingRect(it)
            Imgproc.rectangle(colorMat, rect, Scalar(255.0, 0.0, 0.0), 3)
        }

        val bitmap = this.debugBitmap?.takeIf {
            colorMat.width() == it.width && colorMat.height() == it.height
        } ?: Bitmap.createBitmap(
            colorMat.width(),
            colorMat.height(),
            Bitmap.Config.ARGB_8888,
        ).also {
            this.debugBitmap = it
        }

        Utils.matToBitmap(colorMat, bitmap)

        colorMat.release()

        return bitmap
    }
}
