package org.avmedia.remotevideocam

import android.app.Application
import timber.log.Timber
import timber.log.Timber.DebugTree

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Hook up timber for debug build only
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
    }
}
