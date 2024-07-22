package org.avmedia.remotevideocam.frameanalysis.motion

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

class VideoFrameInterceptor : VideoSink {

    init {
        check(OpenCVLoader.initDebug()) {
            "Failed to init OpenCV."
        }
    }

    private val backgroundSubtractor by lazy {
        Video.createBackgroundSubtractorMOG2()
    }

    private var debugBitmap: Bitmap? = null

    override fun onFrame(videoFrame: VideoFrame) {
        Log.d("lweijing", "width ${videoFrame.rotatedWidth}, height ${videoFrame.rotatedHeight}")
        val greyBuffer = videoFrame.buffer.toI420().dataY

        // Use buffer's width and height over video frame's rotated specs.
        val imageMat = Mat(
            videoFrame.buffer.height,
            videoFrame.buffer.width,
            CvType.CV_8UC1,
            greyBuffer,
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

        debug(foregroundMat)

        foregroundMat.release()
        imageMat.release()
    }


    var count = 0;
    private fun debug(foreground: Mat) {
        if (++count % 30 == 0) {
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
            Log.d("lweijing", "$bitmap")
        }
    }
}