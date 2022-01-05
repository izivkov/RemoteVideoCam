/*
 * Developed for the OpenBot project (https://openbot.org) by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:56 p.m.
 */

package org.avmedia.remotevideocam.customcomponents

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.avmedia.remotevideocam.display.ILocalConnection
import org.avmedia.remotevideocam.display.NetworkServiceConnection
import org.avmedia.remotevideocam.display.StatusEventBus

open class Button @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : com.google.android.material.button.MaterialButton(context, attrs, defStyleAttr) {

    private val connection: ILocalConnection = NetworkServiceConnection

    open fun show() {
        visibility = VISIBLE
    }

    fun hide() {
        visibility = INVISIBLE
    }

    protected fun sendMessage(message: String) {
        connection.sendMessage(message)
    }

    inner class OnTouchListener(private val command: String) : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            when (event?.action) {
                MotionEvent.ACTION_UP -> {
                    sendMessage(command)
                }
            }
            return false
        }
    }

    @SuppressLint("CheckResult")
    protected fun subscribe(subject: String, onDataReceived: (String) -> Unit) {
        StatusEventBus.addSubject(subject)
        StatusEventBus.subscribe(this.javaClass.simpleName, subject, onNext = {
            onDataReceived(it as String)
        })
    }

    protected open fun setOnOffStateConditions(value: String) {
        if (value == "true") onState() else offState()
    }

    protected open fun offState() {
        backgroundTintList = ColorStateList.valueOf(Color.rgb(53, 53, 53)) // darkslategray
        setTextColor(Color.rgb(189, 189, 189)) // silver
    }

    protected open fun onState() {
        backgroundTintList = ColorStateList.valueOf(Color.rgb(128, 203, 196)) // mediumaquamarine
        setTextColor(Color.rgb(33, 33, 33)) // black
    }
}
