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
import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.avmedia.remotevideocam.customcomponents.EventProcessor
import java.util.*


object ScreenSelector {
    private val TAG = "ScreenSelector"

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
        EventProcessor.connectionEventFlowable
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                Log.i(TAG, "Got $it event")

                when (it) {
                    EventProcessor.ProgressEvents.ConnectionCameraSuccessful -> {
                        showScreen("camera screen")
                    }
                    EventProcessor.ProgressEvents.ConnectionDisplaySuccessful -> {
                        showScreen("display screen")
                    }
                    EventProcessor.ProgressEvents.CameraDisconnected -> {
                        showScreen("main screen")
                    }
                    EventProcessor.ProgressEvents.ShowMainScreen -> {
                        showScreen("main screen")
                    }
                    EventProcessor.ProgressEvents.StartCameraConnect -> {
                    }
                    EventProcessor.ProgressEvents.StartDisplayConnect -> {
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
