package org.avmedia.remotevideocam.camera

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Predicate
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.*
import org.json.JSONObject
import timber.log.Timber

object DisplayToCameraEventBus {
    private val subscribers: MutableMap<String, Disposable> = HashMap<String, Disposable>()
    private val subject: PublishSubject<JSONObject> = PublishSubject.create()
    fun emitEvent(event: JSONObject?) {
        event?.let { subject.onNext(it) }
    }

    fun subscribe(
            subscriberName: String,
            // CORRECTED: JSONObject and Throwable are not nullable inside the Consumer
            onNext: Consumer<in JSONObject>?,
            onError: Consumer<in Throwable>?
    ) {
        subscribe(subscriberName, onNext, onError) {
            true
        } // do not filter if no filter passed. Always return true.
    }

    fun subscribe(
            subscriberName: String,
            // CORRECTED: Same change here
            onNext: Consumer<in JSONObject>?,
            onError: Consumer<in Throwable>?,
            filterPredicate: Predicate<in JSONObject>?
    ) {
        if (subscribers.containsKey(subscriberName)) {
            // This name already subscribed, cannot subscribe multiple times;
            return
        }
        // Corrected code
        val subscriber: Disposable =
                subject.filter(filterPredicate ?: Predicate { true }) // Default for filter
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                onNext ?: Consumer { /* Do nothing */}, // Default for onNext
                                onError
                                        ?: Consumer { error ->
                                            Timber.tag("DisplayToCameraEventBus")
                                                    .e(
                                                            error,
                                                            "Error received but no handler was subscribed"
                                                    )
                                        } // Default for onError
                        )

        subscribers[subscriberName] = subscriber
    }

    fun unsubscribe(name: String) {
        val subscriber: Disposable? = subscribers[name]
        if (subscriber != null) {
            subscriber.dispose()
            subscribers.remove(name)
        }
    }

    fun unsubscribeAll() {
        for (subscriber in subscribers.values) {
            subscriber.dispose()
        }
        subscribers.clear()
    }
}
