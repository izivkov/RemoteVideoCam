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
import android.view.MotionEvent
import android.view.View
import org.avmedia.remotevideocam.customcomponents.LocalEventBus
import org.avmedia.remotevideocam.customcomponents.Button

class FlipCamera @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : Button(context, attrs, defStyleAttr) {

    private var soundStatus:Boolean = true

    init {
        setOnTouchListener(OnTouchListener())
    }

    inner class OnTouchListener() : View.OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
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
