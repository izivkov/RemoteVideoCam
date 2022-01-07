package org.avmedia.remotevideocam.camera

import io.reactivex.subjects.PublishSubject
import org.json.JSONObject

object CameraToDisplayEventBus {
    private val subject: PublishSubject<JSONObject> = PublishSubject.create()
    val processor: PublishSubject<JSONObject>
        get() = subject

    fun emitEvent(event: JSONObject?) {
        event?.let { subject.onNext(it) }
    }
}