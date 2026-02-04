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
import org.avmedia.remotevideocam.customcomponents.Button
import org.avmedia.remotevideocam.utils.ProgressEvents

class MirrorImage
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        Button(context, attrs, defStyleAttr) {

    private var rotationAngle = 0f

    init {
        setOnTouchListener(OnTouchListener())
        if (loadState()) {
            rotationAngle = 180f
            rotation = rotationAngle
        }
    }

    private fun saveState(isMirrored: Boolean) {
        val sharedPref = context.getSharedPreferences("MirrorPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("MirrorState", isMirrored)
            apply()
        }
    }

    private fun loadState(): Boolean {
        val sharedPref = context.getSharedPreferences("MirrorPrefs", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("MirrorState", false)
    }

    inner class OnTouchListener() : View.OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    ProgressEvents.onNext(ProgressEvents.Events.ToggleMirror)
                    rotationAngle = if (rotationAngle == 0f) 180f else 0f
                    animate().rotation(rotationAngle).start()
                    saveState(rotationAngle == 180f)
                }
            }
            return true
        }
    }
}
