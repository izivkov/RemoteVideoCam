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
import org.avmedia.remotevideocam.R
import org.avmedia.remotevideocam.display.customcomponents.Button

class Sound @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : Button(context, attrs, defStyleAttr) {

    init {
        setOnTouchListener(OnTouchListener("{command: TOGGLE_SOUND}"))
        subscribe("TOGGLE_SOUND", ::onDataReceived)
    }

    private fun onDataReceived(data: String) {
        setOnOffStateConditions(data)
    }

    override fun offState() {
        setCompoundDrawablesWithIntrinsicBounds(R.drawable.volume_off_24, 0, 0, 0)
    }

    override fun onState() {
        setCompoundDrawablesWithIntrinsicBounds(R.drawable.volume_up_24, 0, 0, 0)
    }
}
