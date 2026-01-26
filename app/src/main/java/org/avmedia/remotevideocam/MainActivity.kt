package org.avmedia.remotevideocam

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import org.avmedia.remotevideocam.camera.Camera
import org.avmedia.remotevideocam.camera.customcomponents.WebRTCSurfaceView
import org.avmedia.remotevideocam.display.Display
import org.avmedia.remotevideocam.display.customcomponents.VideoViewWebRTC
import org.avmedia.remotevideocam.ui.MainActivityContent
import org.avmedia.remotevideocam.ui.navigation.Screen
import org.avmedia.remotevideocam.ui.viewmodel.MainViewModel
import org.avmedia.remotevideocam.utils.ProgressEvents
import org.avmedia.remotevideocam.utils.Utils.toast
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import timber.log.Timber

class MainActivity :
        ComponentActivity(),
        EasyPermissions.PermissionCallbacks,
        EasyPermissions.RationaleCallbacks {

    private val viewModel: MainViewModel by viewModels()
    private val disposables = CompositeDisposable()

    // WebRTC views - created lazily for integration with Compose
    private var webRTCSurfaceView: WebRTCSurfaceView? = null
    private var videoViewWebRTC: VideoViewWebRTC? = null

    init {
        instance = this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        getPermission()

        // Create WebRTC views
        try {
            webRTCSurfaceView = WebRTCSurfaceView(this)
            videoViewWebRTC = VideoViewWebRTC(this)
        } catch (e: Exception) {
            Timber.e("Failed to create WebRTC views: $e")
            val message =
                    "This app requires WiFi connection. Please connect both devices to the same WiFi network and restart..."
            toast(this, message)
            finish()
            return
        }

        // Setup Compose UI
        setContent {
            MainActivityContent(
                    viewModel = viewModel,
                    webRTCSurfaceView = webRTCSurfaceView,
                    videoViewWebRTC = videoViewWebRTC,
                    onReconnect = { reconnect() }
            )
        }

        setScreenCharacteristics()

        // Subscribe to progress events
        createAppEventsSubscription()

        // Show waiting screen initially
        viewModel.showWaitingScreen()
    }

    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        reconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    private var isReconnecting = false
    private fun reconnect() {
        if (isReconnecting) return
        isReconnecting = true

        Handler(Looper.getMainLooper())
                .postDelayed(
                        {
                            Display.disconnect(this)
                            Camera.disconnect()

                            videoViewWebRTC?.let { video -> Display.init(this, video) }
                            webRTCSurfaceView?.let { surface -> Camera.init(this, surface) }

                            if (!Camera.isConnected()) {
                                Display.connect(this)
                                Camera.connect(this)
                            }
                            isReconnecting = false
                        },
                        500
                )
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this).build().show()
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {}

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
            isInPictureInPictureMode: Boolean,
            newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        // Handle PiP mode changes
        if (viewModel.currentScreen.value == Screen.Display) {
            ProgressEvents.onNext(ProgressEvents.Events.StartDisplay)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    @AfterPermissionGranted(RC_ALL_PERMISSIONS)
    private fun getPermission() {
        var perms =
                arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.INTERNET,
                        Manifest.permission.ACCESS_NETWORK_STATE
                )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
            perms += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        if (!EasyPermissions.hasPermissions(this, *perms)) {
            EasyPermissions.requestPermissions(
                    this,
                    getString(R.string.camera_and_location_rationale),
                    RC_ALL_PERMISSIONS,
                    *perms
            )
        }
    }

    private fun setScreenCharacteristics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                it.hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                    (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        }
    }

    override fun onRationaleDenied(requestCode: Int) {}

    override fun onRationaleAccepted(requestCode: Int) {}

    private fun createAppEventsSubscription(): Disposable {
        val disposable =
                ProgressEvents.connectionEventFlowable
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnNext { event ->
                            Timber.d("MainActivity received event: $event")
                            when (event) {
                                ProgressEvents.Events.DisplayDisconnected -> {
                                    viewModel.showWaitingScreen()
                                    reconnect()
                                }
                                ProgressEvents.Events.CameraDisconnected -> {
                                    viewModel.showWaitingScreen()
                                    reconnect()
                                }
                                ProgressEvents.Events.ConnectionDisplaySuccessful -> {
                                    viewModel.showMainScreen()
                                }
                                ProgressEvents.Events.ConnectionCameraSuccessful -> {
                                    viewModel.showMainScreen()
                                }
                                ProgressEvents.Events.ShowWaitingForConnectionScreen -> {
                                    viewModel.showWaitingScreen()
                                }
                                ProgressEvents.Events.ShowMainScreen -> {
                                    viewModel.showMainScreen()
                                }
                                ProgressEvents.Events.StartCamera -> {
                                    viewModel.showCameraScreen()
                                }
                                ProgressEvents.Events.StartDisplay -> {
                                    viewModel.showDisplayScreen()
                                }
                                else -> {}
                            }
                        }
                        .subscribe(
                                {},
                                { throwable -> Timber.e("Got error on subscribe: $throwable") }
                        )

        disposables.add(disposable)
        return disposable
    }

    companion object {
        private const val RC_ALL_PERMISSIONS = 123
        private var instance: MainActivity? = null

        fun applicationContext(): Context {
            return instance!!.applicationContext
        }
    }
}
