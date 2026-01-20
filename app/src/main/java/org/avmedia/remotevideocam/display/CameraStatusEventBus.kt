package org.avmedia.remotevideocam.display

import android.annotation.SuppressLint
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.subjects.PublishSubject

object CameraStatusEventBus {
    // Note: The PublishSubject itself can handle a nullable type if needed,
    // but the Consumer in the subscribe method cannot.
    // For consistency, let's make the subject non-nullable too.
    private val subjects = HashMap<String, PublishSubject<String>>()
    private val subscribers = HashMap<String, LinkedHashSet<String>> ()

    fun addSubject(name: String) {
        if (subjects[name] != null) {
            return
        }
        // Create a subject that emits non-nullable Strings
        val subject: PublishSubject<String> = PublishSubject.create()
        subjects[name] = subject
    }

    private fun addSubscriberAndSubject(subscriber: String, subject: String) {
        if (!subscribers.containsKey(subscriber)) {
            val subjectsForThisSubscriber = LinkedHashSet<String>()
            subscribers[subscriber] = subjectsForThisSubscriber
        }

        val subjectsForThisSubscriber =  subscribers[subscriber]
        subjectsForThisSubscriber?.add(subject)
    }

    private fun subscriberAlreadySubscribed(subscriber: String, subject: String): Boolean {
        val subjectsForThisSubscriber = subscribers[subscriber]
        return subjectsForThisSubscriber?.contains(subject) ?: false
    }

    @SuppressLint("CheckResult")
    fun subscribe(subscriberName: String, subject: String, onNext: Consumer<in String>) { // CORRECTED
        if (!subscriberAlreadySubscribed(subscriberName, subject)) {
            getProcessor(subject)?.subscribe(onNext)
            addSubscriberAndSubject(subscriberName, subject)
        }
    }

    @SuppressLint("CheckResult")
    fun subscribe(subscriberName: String, subject: String, onNext: Consumer<in String>, onError: Consumer<in Throwable>) { // CORRECTED
        if (!subscriberAlreadySubscribed(subscriberName, subject)) {
            getProcessor(subject)?.subscribe(onNext, onError)
            addSubscriberAndSubject(subscriberName, subject)
        }
    }

    private fun getProcessor(name: String): PublishSubject<String>? { // Return type is now PublishSubject<String>
        return subjects[name]
    }

    fun emitEvent(name: String, event: String) {
        subjects[name]?.onNext(event)
    }
}
