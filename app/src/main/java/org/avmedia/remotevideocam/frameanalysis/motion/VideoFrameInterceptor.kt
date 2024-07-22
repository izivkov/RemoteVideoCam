package org.avmedia.remotevideocam.frameanalysis.motion

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.ImageView
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
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
            40.toDouble(),
            255.toDouble(),
            Imgproc.THRESH_BINARY,
        )



        debug(foregroundMat, motionDetectorView)

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