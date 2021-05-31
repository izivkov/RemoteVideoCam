/*
 * Developed by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:59 p.m.
 */

package org.avmedia.remotevideocam.display

import io.reactivex.subjects.PublishSubject

object StatusEventBus {
    private val subjects = HashMap<String, PublishSubject<String?>>()

    fun addSubject(name: String) {
        if (subjects[name] != null) {
            return
        }
        val subject: PublishSubject<String?> = PublishSubject.create()
        subjects[name] = subject
    }

    fun getProcessor(name: String): PublishSubject<String?>? {
        return subjects[name]
    }

    fun emitEvent(name: String, event: String) {
        subjects[name]?.onNext(event)
    }
}