/*
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:59 p.m.
 */

package org.avmedia.remotevideocam

import android.annotation.SuppressLint
import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.avmedia.remotevideocam.utils.ProgressEvents
import java.util.*


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
        ProgressEvents.connectionEventFlowable
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                Log.i(TAG, "Got $it event")

                when (it) {
                    ProgressEvents.Events.CameraDisconnected -> {
                        showScreen("waiting for connection screen")
                    }
                    ProgressEvents.Events.DisplayDisconnected -> {
                        showScreen("waiting for connection screen")
                    }
                    ProgressEvents.Events.ConnectionDisplaySuccessful -> {
                        showScreen("main screen")
                    }
                    ProgressEvents.Events.ConnectionCameraSuccessful -> {
                        showScreen("main screen")
                    }
                    ProgressEvents.Events.ShowWaitingForConnectionScreen -> {
                        showScreen("waiting for connection screen")
                    }
                    ProgressEvents.Events.ShowMainScreen -> {
                        showScreen("main screen")
                    }
                    ProgressEvents.Events.StartCamera -> {
                        ScreenSelector.showScreen("camera screen")
                    }
                    ProgressEvents.Events.StartDisplay -> {
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
