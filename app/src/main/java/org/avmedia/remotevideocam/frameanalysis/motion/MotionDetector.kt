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
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import org.webrtc.VideoFrame

private const val DEBUG_UPDATE_INTERVAL = 100L
private const val CONTOUR_AREA_THRESHOLD = 100
private const val GREY_SCALE_THRESHOLD = 180.0

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

        val foregroundMat = Mat()
        backgroundSubtractor.apply(imageMat, foregroundMat)

        // Note that the object can't be detected if its color is close to the background color.
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
        Imgproc.erode(foregroundMat, foregroundMat, kernel)
        Imgproc.dilate(foregroundMat, foregroundMat, kernel)

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(
            foregroundMat,
            contours,
            Mat(),
            Imgproc.RETR_TREE,
            Imgproc.CHAIN_APPROX_SIMPLE,
        )

        val largeContours = contours.filter { Imgproc.contourArea(it) > CONTOUR_AREA_THRESHOLD }

        val result = if (largeContours.isNotEmpty()) {
            Log.d(TAG, "motion detected. Contour size ${largeContours.size}.")
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
//        contours.forEach {
//            val rect = Imgproc.boundingRect(it)
//            Imgproc.rectangle(colorMat, rect, Scalar(255.0, 0.0, 0.0), 4)
//        }

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
