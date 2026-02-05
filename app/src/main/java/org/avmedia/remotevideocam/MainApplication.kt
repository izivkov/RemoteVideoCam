package org.avmedia.remotevideocam

import android.app.Application
import android.content.Context
import timber.log.Timber

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    companion object {
        private lateinit var appContext: Context

        fun applicationContext(): Context {
            return appContext
        }
    }
}
