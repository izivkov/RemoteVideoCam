package org.avmedia.remotevideocam.frameanalysis.motion

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.ImageView
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

private const val DEBUG_UPDATE_INTERVAL = 300L

class VideoFrameInterceptor(
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {

    init {
        check(OpenCVLoader.initDebug()) {
            "Failed to init OpenCV."
        }
    }

    private val backgroundSubtractor by lazy {
        Video.createBackgroundSubtractorMOG2()
    }

    private var debugBitmap: Bitmap? = null

    private var currentTimeMs: Long = SystemClock.elapsedRealtime()

    fun onFrame(videoFrame: VideoFrame, motionDetectorView: ImageView? = null) {
        val i420Buffer = videoFrame.buffer.toI420()

        // Use buffer's width and height over video frame's rotated specs.
        val imageMat = Mat(
            videoFrame.buffer.height,
            videoFrame.buffer.width,
            CvType.CV_8UC1,
            i420Buffer.dataY,
        )

        val foregroundMat = Mat()
        backgroundSubtractor.apply(imageMat, foregroundMat)

        Imgproc.threshold(
            foregroundMat,
            foregroundMat,
            40.0,
            255.0,
            Imgproc.THRESH_BINARY,
        )

        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE,
            Size(5.0, 5.0),
        )
        Imgproc.erode(foregroundMat, foregroundMat, kernel)
        Imgproc.dilate(foregroundMat, foregroundMat, kernel)

        val colorMat = Mat()
        Imgproc.cvtColor(foregroundMat, colorMat, Imgproc.COLOR_GRAY2RGBA)

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(
            foregroundMat,
            contours,
            Mat(),
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE,
        )

//        Imgproc.drawContours(foregroundMat, contours, -1, Scalar(1.0, 0.0, 0.0), 3)

        contours.mapNotNull {
            if (Imgproc.contourArea(it) > 1000) {
                it
            } else {
                null
            }
        }.forEach {
            val rect = Imgproc.boundingRect(it)
            Imgproc.rectangle(colorMat, rect, Scalar(1.0, 0.0, 0.0), 4)
        }

//        debug(foregroundMat, motionDetectorView)
        debug(colorMat, motionDetectorView)

        foregroundMat.release()
        imageMat.release()
        i420Buffer.release()
    }


    private fun debug(foreground: Mat, motionDetectorView: ImageView?) {
        val timeMs = SystemClock.elapsedRealtime()

        if (timeMs - currentTimeMs > DEBUG_UPDATE_INTERVAL) {
            currentTimeMs = timeMs

            val bitmap = this.debugBitmap?.takeIf {
                foreground.width() == it.width && foreground.height() == it.height
            } ?: Bitmap.createBitmap(
                foreground.width(),
                foreground.height(),
                Bitmap.Config.ARGB_8888,
            ).also {
                this.debugBitmap = it
            }

            Utils.matToBitmap(foreground, bitmap)
            mainHandler.post {
                motionDetectorView?.setImageBitmap(bitmap)
            }
        }
    }
}