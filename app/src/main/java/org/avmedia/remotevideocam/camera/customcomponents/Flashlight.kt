/*
 * Developed for the OpenBot project (https://openbot.org) by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:57 p.m.
 */

package org.avmedia.remotevideocam.camera.customcomponents

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import org.avmedia.remotevideocam.R
import org.avmedia.remotevideocam.camera.CameraToDisplayEventBus
import org.avmedia.remotevideocam.camera.DisplayToCameraEventBus
import org.avmedia.remotevideocam.customcomponents.LocalEventBus
import org.avmedia.remotevideocam.display.customcomponents.Button
import org.avmedia.remotevideocam.utils.ConnectionUtils
import org.json.JSONObject

import io.reactivex.functions.Consumer
import io.reactivex.functions.Predicate

class Flashlight @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : Button(context, attrs, defStyleAttr) {

    private var soundStatus:Boolean = true;

    init {
        setOnTouchListener(OnTouchListener())
    }

    inner class OnTouchListener() : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    LocalEventBus.onNext(LocalEventBus.ProgressEvents.FlipCamera)
                }
            }
            return false
        }
    }
}
