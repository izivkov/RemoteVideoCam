package org.avmedia.remotevideocam.utils

import java.util.*

/*
AndGate will execute some action if all its input conditions are met.
This is useful if we do not know the order of the updates to the conditions.

We can add an arbitrary number of conditions, initially set to false. These conditions can
be updated in the future, and if all become true, the action will be executed.
 */
class AndGate(
    startAction: () -> Unit,
    stopAction: () -> Unit
) {
    private var isRunning = false
    private var conditions: MutableList<Condition> =
        ArrayList()
    private val startAction: () -> Unit
    private val stopAction: () -> Unit
    operator fun set(name: String, value: Boolean) {
        for (condition in conditions) {
            if (condition.name == name) {
                condition.value = value
                break
            }
        }
        // if all conditions match
        for (condition in conditions) {
            if (!condition.value) {
                if (isRunning && stopAction != null) {
                    isRunning = false
                    stopAction()
                }
                return
            }
        }

        // and run the action
        if (!isRunning && startAction != null) {
            isRunning = true
            startAction()
        }
    }

    private inner class Condition(var name: String, var value: Boolean)

    fun reset() {
        conditions.clear()
    }

    fun addCondition(name: String?) {
        conditions.add(Condition(name!!, false))
    }

    init {
        conditions = conditions
        this.startAction = startAction
        this.stopAction = stopAction
    }
}