package org.avmedia.remotevideocam.camera

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager

object FlashlightHandler {
    private var flashlightOn = false
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraId: String

    fun toggleFlashlight(context: Context) {
        flashlightOn = !flashlightOn
        switchFlashLight(flashlightOn, context)
    }

    private fun switchFlashLight(status: Boolean, context: Context) {
        try {
            getCameraManager(context).setTorchMode(cameraId, status)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun getCameraManager(context: Context): CameraManager {
        if (!this::cameraManager.isInitialized) {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            try {
                cameraId = cameraManager.cameraIdList[0] // back camera
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }

        return cameraManager
    }
}
