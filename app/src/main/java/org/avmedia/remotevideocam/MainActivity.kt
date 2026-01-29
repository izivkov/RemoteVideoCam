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
import androidx.activity.result.contract.ActivityResultContracts
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
import timber.log.Timber

class MainActivity : ComponentActivity() { // Removed EasyPermissions interfaces

    private val viewModel: MainViewModel by viewModels()
    private val disposables = CompositeDisposable()

    private var webRTCSurfaceView: WebRTCSurfaceView? = null
    private var videoViewWebRTC: VideoViewWebRTC? = null

    // New Activity Result Launcher for permissions
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                Timber.d("All permissions granted")
                reconnect()
            } else {
                Timber.w("Not all permissions were granted.")
                // Optionally, show a message to the user
                toast(
                    this,
                    "Some permissions were denied. The app may not function correctly."
                )
            }
        }

    init {
        instance = this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Request permissions using the new Activity Result API
        requestPermissions()

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

        setContent {
            MainActivityContent(
                viewModel = viewModel,
                webRTCSurfaceView = webRTCSurfaceView,
                videoViewWebRTC = videoViewWebRTC,
                onReconnect = { reconnect() }
            )
        }

        setScreenCharacteristics()
        createAppEventsSubscription()
        viewModel.showWaitingScreen()
    }

    // New method to request permissions
    private fun requestPermissions() {
        val permissionsToRequest =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE
            )
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                        add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    }
                }
        requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }
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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        if (viewModel.currentScreen.value == Screen.Display) {
            ProgressEvents.onNext(ProgressEvents.Events.StartDisplay)
        }
    }

    private fun setScreenCharacteristics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                // The following two lines are deprecated and no longer needed with edge-to-edge.
                // window.navigationBarColor = android.graphics.Color.TRANSPARENT
                // window.statusBarColor = android.graphics.Color.TRANSPARENT
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

            // This part is also deprecated and generally not needed for immersive mode.
            // @Suppress("DEPRECATION")
            // window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        }
    }

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
        private var instance: MainActivity? = null

        fun applicationContext(): Context {
            return instance!!.applicationContext
        }
    }
}
