package org.avmedia.remotevideocam

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
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
import android.widget.RadioGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import kotlin.system.exitProcess
import org.avmedia.remotevideocam.camera.Camera
import org.avmedia.remotevideocam.camera.ConnectionStrategy
import org.avmedia.remotevideocam.databinding.ActivityMainBinding
import org.avmedia.remotevideocam.display.Display
import org.avmedia.remotevideocam.utils.ProgressEvents
import org.avmedia.remotevideocam.utils.Utils.toast
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import timber.log.Timber

class MainActivity :
        AppCompatActivity(),
        EasyPermissions.PermissionCallbacks,
        EasyPermissions.RationaleCallbacks {

    private lateinit var binding: ActivityMainBinding
    private val TAG = "MainActivity"

    init {
        instance = this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
        } catch (e: Exception) {
            Timber.d("ActivityMainBinding failed : $e")
            val message =
                    "This app requires WiFi connection. Please connect both devices to the same WiFi network and restart..."
            toast(this, message)
            toast(this, message)
            finish()
            return
        }

        setContentView(binding.root)

        // AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setScreenCharacteristics() // this should be called after "setContentView()"
        getPermission()

        ScreenSelector.add("main screen", binding.mainLayout)
        ScreenSelector.add("waiting for connection screen", binding.waitingToConnectLayout)
        ScreenSelector.add("display screen", binding.displayLayout)
        ScreenSelector.add("camera screen", binding.cameraLayout)

        createAppEventsSubscription()
        ProgressEvents.onNext(ProgressEvents.Events.ShowWaitingForConnectionScreen)

        binding.settingsButton.setOnClickListener { showConnectionSelectionDialog() }
    }

    private fun showConnectionSelectionDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_connection_selection, null)
        dialog.setContentView(view)

        val radioGroup = view.findViewById<RadioGroup>(R.id.connection_type_group)
        val currentType = ConnectionStrategy.getSelectedType()

        when (currentType) {
            ConnectionStrategy.ConnectionType.AUTO -> radioGroup.check(R.id.radio_auto)
            ConnectionStrategy.ConnectionType.NETWORK -> radioGroup.check(R.id.radio_network)
            ConnectionStrategy.ConnectionType.WIFI_DIRECT ->
                    radioGroup.check(R.id.radio_wifi_direct)
            ConnectionStrategy.ConnectionType.WIFI_AWARE -> radioGroup.check(R.id.radio_wifi_aware)
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newType =
                    when (checkedId) {
                        R.id.radio_auto -> ConnectionStrategy.ConnectionType.AUTO
                        R.id.radio_network -> ConnectionStrategy.ConnectionType.NETWORK
                        R.id.radio_wifi_direct -> ConnectionStrategy.ConnectionType.WIFI_DIRECT
                        R.id.radio_wifi_aware -> ConnectionStrategy.ConnectionType.WIFI_AWARE
                        else -> ConnectionStrategy.ConnectionType.AUTO
                    }
            ConnectionStrategy.setSelectedType(newType)
            dialog.dismiss()
            restartApp()
        }

        dialog.show()
    }

    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }
    }

    @Override
    override fun onPause() {
        super.onPause()
    }

    @Override
    override fun onResume() {
        super.onResume()

        Display.init(this, binding.videoView, binding.motionDetectionButton)
        Camera.init(this, binding.videoWindow)

        if (!Camera.isConnected()) {
            // Open display first, which waits on 'accept'
            Display.connect(this)
            Camera.connect(this)
        }
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
        if (ScreenSelector.currentScreen?.name == "display screen") {
            ProgressEvents.onNext(ProgressEvents.Events.StartDisplay)
        }

        if (isInPictureInPictureMode) {} else {}
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Forward results to EasyPermissions
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
            // Do not have permissions, request them now
            // EasyPermissions.requestPermissions()
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
                window.navigationBarColor = getColor(R.color.colorPrimaryDark)
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

    override fun onRationaleDenied(requestCode: Int) {
        // Not yet
    }

    override fun onRationaleAccepted(requestCode: Int) {
        // Not yet
    }

    private fun createAppEventsSubscription(): Disposable =
            ProgressEvents.connectionEventFlowable
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnNext {
                        when (it) {
                            ProgressEvents.Events.DisplayDisconnected -> {
                                restartApp()
                            }
                            ProgressEvents.Events.CameraDisconnected -> {
                                restartApp()
                            }
                        }
                    }
                    .subscribe({}, { throwable -> Timber.d("Got error on subscribe: $throwable") })

    private fun restartApp() {
        Handler(Looper.getMainLooper())
                .postDelayed(
                        {
                            val pm: PackageManager = this.packageManager
                            val intent = pm.getLaunchIntentForPackage(this.packageName)
                            this.finishAffinity() // Finishes all activities.
                            this.startActivity(intent) // Start the launch activity
                            exitProcess(0) // System finishes and automatically relaunches us.
                        },
                        100
                )
    }

    companion object {
        private const val RC_ALL_PERMISSIONS = 123
        private var instance: MainActivity? = null

        // Make context available from anywhere in the code (not yet used).
        fun applicationContext(): Context {
            return instance!!.applicationContext
        }
    }
}
