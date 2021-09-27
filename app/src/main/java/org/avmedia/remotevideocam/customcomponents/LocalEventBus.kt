package org.avmedia.remotevideocam.customcomponents

import android.util.Log
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor

object LocalEventBus {
    private val eventProcessor: PublishProcessor<ProgressEvents> =
        PublishProcessor.create()

    val connectionEventFlowable = eventProcessor as Flowable<ProgressEvents>

    fun onNext(e: ProgressEvents) {
        if (eventProcessor.hasSubscribers()) {
            return eventProcessor.onNext(e)
        } else {
            Log.d("EventProcessor:onNext", "----------- No subscribers")
        }
    }

    open class ProgressEvents(var payload: Object? = null) {

        object Init : ProgressEvents()

        object ConnectionDisplaySuccessful : ProgressEvents()
        object ConnectionCameraSuccessful : ProgressEvents()
        object DisplayDisconnected : ProgressEvents()
        object CameraDisconnected : ProgressEvents()
        object ShowMainScreen: ProgressEvents()
        object ShowCameraScreen: ProgressEvents()
        object StartCamera: ProgressEvents(payload = null)
        object StartDisplay: ProgressEvents()
    }
}