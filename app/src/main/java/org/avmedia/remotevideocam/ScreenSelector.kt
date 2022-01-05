/*
 * Developed for the OpenBot project (https://openbot.org) by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:59 p.m.
 */

package org.avmedia.remotevideocam

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.avmedia.remotevideocam.customcomponents.LocalEventBus
import java.util.*
import kotlin.system.exitProcess


object ScreenSelector {
    private const val TAG = "ScreenSelector"

    private class NamedScreen(var name: String, var layout: IHideableLayout)
    private var screens: MutableList<NamedScreen> = ArrayList()

    init {
        createAppEventsSubscription()
    }

    fun add(name: String, layout: IHideableLayout) {
        screens.add(NamedScreen(name, layout))
    }

    private fun showScreen(name: String) {
        for (screen in screens) {
            if (screen.name == name)
                screen.layout.show()
            else
                screen.layout.hide()
        }
    }

    @SuppressLint("LogNotTimber")
    private fun createAppEventsSubscription(): Disposable =
        LocalEventBus.connectionEventFlowable
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                Log.i(TAG, "Got $it event")

                when (it) {
                    LocalEventBus.ProgressEvents.CameraDisconnected -> {
                        showScreen("main screen")
                    }
                    LocalEventBus.ProgressEvents.ShowMainScreen -> {
                        showScreen("main screen")
                    }
                    LocalEventBus.ProgressEvents.ShowCameraScreen -> {
                        showScreen("camera screen")
                    }
                    LocalEventBus.ProgressEvents.StartCamera -> {
                        ScreenSelector.showScreen("camera screen")
                    }
                    LocalEventBus.ProgressEvents.StartDisplay -> {
                        ScreenSelector.showScreen("display screen")
                    }
                }
            }
            .subscribe(
                { },
                { throwable ->
                    Log.d(
                        "EventsSubscription",
                        "Got error on subscribe: $throwable"
                    )
                })

}
