package org.avmedia.remotevideocam.camera

import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.functions.Predicate
import io.reactivex.subjects.PublishSubject
import org.json.JSONObject
import java.util.*

object DisplayToCameraEventBus {
    private val subscribers: MutableMap<String, Disposable> = HashMap<String, Disposable>()
    private val subject: PublishSubject<JSONObject> = PublishSubject.create()
    fun emitEvent(event: JSONObject?) {
        event?.let { subject.onNext(it) }
    }

    fun subscribe(
        subscriberName: String,
        onNext: Consumer<in JSONObject?>?,
        onError: Consumer<in Throwable?>?
    ) {
        subscribe(
            subscriberName,
            onNext,
            onError
        ) { true } // do not filter if no filter passed. Always return true.
    }

    fun subscribe(
        subscriberName: String,
        onNext: Consumer<in JSONObject?>?,
        onError: Consumer<in Throwable?>?,
        filterPredicate: Predicate<in JSONObject?>?
    ) {
        if (subscribers.containsKey(subscriberName)) {
            // This name already subscribed, cannot subscribe multiple times;
            return
        }
        val subscriber: Disposable =
            subject.filter(filterPredicate).subscribe(onNext, onError)
        subscribers[subscriberName] = subscriber
    }

    fun unsubscribe(name: String) {
        val subscriber: Disposable? = subscribers[name]
        if (subscriber != null) {
            subscriber.dispose()
            subscribers.remove(name)
        }
    }

    fun unsubscribeAll () {
        for (subscriber in subscribers.values) {
            subscriber.dispose()
        }
        subscribers.clear()
    }
}