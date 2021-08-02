/*
 * Developed for the OpenBot project (https://openbot.org) by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:57 p.m.
 */

package org.avmedia.remotevideocam.display.customcomponents

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.avmedia.remotevideocam.customcomponents.Button
import org.avmedia.remotevideocam.customcomponents.EventProcessor
import org.avmedia.remotevideocam.display.NetworkServiceConnection

class BackButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : Button(context, attrs, defStyleAttr) {

    init {
        setOnTouchListener(OnTouchListener())
        show()
    }

    inner class OnTouchListener() : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            when (event?.action) {
                MotionEvent.ACTION_UP -> {
                    NetworkServiceConnection.disconnect()
                    EventProcessor.onNext(EventProcessor.ProgressEvents.ShowMainScreen)
                }
            }
            return false
        }
    }
}
