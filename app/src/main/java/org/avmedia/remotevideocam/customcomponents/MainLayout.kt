/*
 * Developed by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:58 p.m.
 */

package org.avmedia.remotevideocam.customcomponents

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.avmedia.remotevideocam.IHideableLayout
import timber.log.Timber

class MainLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) , IHideableLayout {

    init {
        hide()
        createAppEventsSubscription()
    }

    private fun createAppEventsSubscription(): Disposable =
        ProgressEvents.connectionEventFlowable
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                when (it) {
                    ProgressEvents.Events.Connected -> {
                        show()
                    }
                    ProgressEvents.Events.ShowMainScreen -> {
                        show()
                    }
                    else -> {
                        hide()
                    }
                }
            }
            .subscribe(
                { }
            ) { throwable ->
                Timber.d(
                    "Got error on subscribe: $throwable"
                )
            }

    override fun show() {
        visibility = View.VISIBLE
    }

    override fun hide() {
        visibility = View.GONE
    }
}
