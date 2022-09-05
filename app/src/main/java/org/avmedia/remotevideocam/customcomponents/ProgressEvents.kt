package org.avmedia.remotevideocam.customcomponents

import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import timber.log.Timber

object ProgressEvents {
    private val eventProcessor: PublishProcessor<Events> =
        PublishProcessor.create()

    val connectionEventFlowable = eventProcessor as Flowable<Events>

    fun onNext(e: Events) {
        if (eventProcessor.hasSubscribers()) {
            return eventProcessor.onNext(e)
        } else {
            Timber.d("EventProcessor:onNext----------- No subscribers")
        }
    }

    open class Events(var payload: Any? = null) {

        object Init : Events()

        object ShowWaitingForConnectionScreen : Events()
        object Connected : Events()
        object Disconnected : Events()
        object ShowMainScreen: Events()
        object ShowCameraScreen: Events(payload = null)
        object ShowDisplayScreen: Events()
        object ToggleMirror: Events()
        object FlipCamera : Events()
        object Mute : Events()
        object Unmute : Events()
        object ToggleFlashlight : Events()
        object WEBRtcServerFailed : Events()
        object WEBRtcClientFailed : Events()
        object WEBRtcClientDisconnected : Events()
    }
}