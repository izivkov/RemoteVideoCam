package org.avmedia.remotevideocam.utils

import android.content.Context
import androidx.core.content.edit
import org.avmedia.remotevideocam.MainApplication

object LocalDataStorage {
    private const val PREFS_NAME = "RemoteVideoCamPrefs"
    private const val CONNECTION_TYPE_KEY = "connection_type"
    private const val IS_MUTED_KEY = "is_muted"
    private const val IS_MIRRORED_KEY = "is_mirrored"

    fun saveConnectionType(type: String) {
        val context = MainApplication.applicationContext()
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit { putString(CONNECTION_TYPE_KEY, type) }
    }

    fun getConnectionType(): String? {
        val context = MainApplication.applicationContext()
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(CONNECTION_TYPE_KEY, null)
    }

    fun saveMuted(muted: Boolean) {
        val context = MainApplication.applicationContext()
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit { putBoolean(IS_MUTED_KEY, muted) }
    }

    fun isMuted(): Boolean {
        val context = MainApplication.applicationContext()
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(IS_MUTED_KEY, true) // Default to muted
    }

    fun saveMirrored(mirrored: Boolean) {
        val context = MainApplication.applicationContext()
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit { putBoolean(IS_MIRRORED_KEY, mirrored) }
    }

    fun isMirrored(): Boolean {
        val context = MainApplication.applicationContext()
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(IS_MIRRORED_KEY, false)
    }
}
