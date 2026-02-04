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
import org.avmedia.remotevideocam.customcomponents.Button
import org.avmedia.remotevideocam.utils.ProgressEvents

class Sound
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        Button(context, attrs, defStyleAttr) {

    enum class State {
        ON,
        OFF
    }
    var state: State = State.OFF

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
                saveState(state)
            }
            return true
        }
    }

    private fun saveState(state: State) {
        val sharedPref = context.getSharedPreferences("SoundPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("SoundState", state.name)
            apply()
        }
    }

    private fun loadState(): State {
        val sharedPref = context.getSharedPreferences("SoundPrefs", Context.MODE_PRIVATE)
        val stateName = sharedPref.getString("SoundState", State.OFF.name)
        return State.valueOf(stateName ?: State.OFF.name)
    }

    init {
        setOnTouchListener(OnTouchListener())
        state = loadState()
        if (state == State.ON) onState() else offState()
    }

    override fun offState() {
        setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.volume_off_24, 0, 0)

        val event: ProgressEvents.Events = ProgressEvents.Events.Mute
        ProgressEvents.onNext(event)
    }

    override fun onState() {
        setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.volume_up_24, 0, 0)

        val event: ProgressEvents.Events = ProgressEvents.Events.Unmute
        ProgressEvents.onNext(event)
    }
}
