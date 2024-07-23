package org.avmedia.remotevideocam.frameanalysis.motion

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import org.webrtc.VideoFrame

private const val DEBUG_UPDATE_INTERVAL = 100L / 99
private const val GREY_SCALE_THRESHOLD = 40.0

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

    private var currentTimeMs: Long = SystemClock.elapsedRealtime()

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

        // Create a binary image with a grey scale threshold.
        Imgproc.threshold(
            foregroundMat,
            foregroundMat,
            GREY_SCALE_THRESHOLD,
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

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(
            foregroundMat,
            contours,
            Mat(),
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE,
        )

        val result = if (contours.isNotEmpty()) {
            Log.d(TAG, "motion detected. Contour size ${contours.size}.")
            true
        } else {
            Log.d(TAG, "motion not detected")
            false
        }

        val bitmap = showDebugView(foregroundMat, imageMat, contours)

        notifyListener(result, bitmap)

        // Release frames.
        foregroundMat.release()
        imageMat.release()
        i420Buffer.release()
    }

    private fun notifyListener(result: Boolean, bitmap: Bitmap?) {
        listener?.let {
            mainHandler.post {
                it.onDetectionResult(result, bitmap)
            }
        }
    }

    private fun showDebugView(
        foreground: Mat,
        image: Mat,
        contours: List<MatOfPoint>,
    ): Bitmap? {
        if (SystemClock.elapsedRealtime() - currentTimeMs < DEBUG_UPDATE_INTERVAL) {
            return null
        }

        currentTimeMs = SystemClock.elapsedRealtime()

        val colorMat = Mat()
        Imgproc.cvtColor(image, colorMat, Imgproc.COLOR_GRAY2RGBA)

        Imgproc.drawContours(colorMat, contours, -1, Scalar(0.0, 255.0, 0.0), 4)
        contours.forEach {
            val rect = Imgproc.boundingRect(it)
            Imgproc.rectangle(colorMat, rect, Scalar(255.0, 0.0, 0.0), 4)
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
