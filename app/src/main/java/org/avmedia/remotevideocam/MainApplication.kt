package org.avmedia.remotevideocam

import android.app.Application
import android.content.Context

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    companion object {
        private lateinit var appContext: Context

        fun applicationContext(): Context {
            return appContext
        }
    }
}
