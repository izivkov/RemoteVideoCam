/*
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:57 p.m.
 */

package org.avmedia.remotevideocam.display.customcomponents

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.avmedia.remotevideocam.R
import org.avmedia.remotevideocam.utils.ProgressEvents
import org.avmedia.remotevideocam.customcomponents.Button

class Sound @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : Button(context, attrs, defStyleAttr) {

    enum class State { ON, OFF }
    var state: State = State.OFF

    init {
        setOnTouchListener(OnTouchListener())
        offState()
    }

    inner class OnTouchListener : View.OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if (event?.action == MotionEvent.ACTION_DOWN) {
                if (state == State.OFF) {
                    state = State.ON
                    onState()
                } else {
                    state = State.OFF
                    offState()
                }
            }
            return true
        }
    }

    override fun offState() {
        setCompoundDrawablesWithIntrinsicBounds( 0, R.drawable.volume_off_24, 0,0)

        val event: ProgressEvents.Events = ProgressEvents.Events.Mute
        ProgressEvents.onNext(event)
    }

    override fun onState() {
        setCompoundDrawablesWithIntrinsicBounds(  0,R.drawable.volume_up_24, 0, 0)

        val event: ProgressEvents.Events = ProgressEvents.Events.Unmute
        ProgressEvents.onNext(event)
    }
}
