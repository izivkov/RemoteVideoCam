package org.avmedia.remotevideocam.frameanalysis.motion

import timber.log.Timber

/**
 * Conclude [MotionDetectionAction.NOT_DETECTED] when no more detected event is received
 * for the [RECOVERY_THRESHOLD_MS] duration.
 */
private const val RECOVERY_THRESHOLD_MS = 3_000L

/**
 * Conclude [MotionDetectionAction.DETECTED] when the time difference between two detected events
 * is larger than [DETECTED_THRESHOLD_MS].
 */
private const val DETECTED_THRESHOLD_MS = 500L

private const val TAG = "MotionDetectionStateMachine"

class MotionDetectionStateMachine {

    /**
     * Used to mark the first detected timestamp when [current] is in not detected state.
     */
    private var firstDetectedMs: Long? = null

    private var current: MotionDetectionData? = null
        set(value) {
            Timber.tag(TAG).d("setCurrent %s", value)
            field = value
            firstDetectedMs = null
        }

    interface Listener {

        fun onStateChanged(detected: Boolean)
    }

    var listener: Listener? = null

    fun process(response: MotionDetectionData) {
        Timber.tag(TAG).d(
            "process response, action %s current %s",
            response.action,
            current?.action
        )

        when (response.action) {
            MotionDetectionAction.ENABLED,
            MotionDetectionAction.DISABLED -> {
                /**
                 * Always reset to NOT_DETECTED for ENABLED / DISABLED actions.
                 */
                current = null
            }

            MotionDetectionAction.DETECTED -> {
                /**
                 * 1. If already detected, extends the timestamp.
                 * 2. Otherwise, transition to detected only if two detected events
                 */
                if (current?.action == MotionDetectionAction.DETECTED) {
                    current = response
                } else if (firstDetectedMs == null) {
                    firstDetectedMs = response.timestampMs
                } else {
                    firstDetectedMs?.takeIf {
                        Timber.tag(TAG).d(
                            "check firstDetected diff %s",
                            response.timestampMs - it
                        )
                        response.timestampMs - it >= DETECTED_THRESHOLD_MS
                    }?.let {
                        current = response
                        listener?.onStateChanged(true)
                    }
                }
            }

            MotionDetectionAction.NOT_DETECTED -> {
                /**
                 * 1. Invalidate [firstDetectedMs] if no motion is detected for
                 * [RECOVERY_THRESHOLD_MS] duration.
                 * 2. Recover to NOT_DETECTED if no motion is detected for
                 * [RECOVERY_THRESHOLD_MS] duration.
                 */
                firstDetectedMs?.takeIf {
                    response.timestampMs - it >= RECOVERY_THRESHOLD_MS
                }?.let {
                    Timber.tag(TAG).d("Invalid firstDetectedMs $firstDetectedMs")
                    firstDetectedMs = null
                }

                current?.takeIf { shouldRecover(it, response) }?.let {
                    current = response
                    listener?.onStateChanged(false)
                }
            }
        }
    }

    private fun shouldRecover(
        current: MotionDetectionData,
        response: MotionDetectionData
    ) = current.action == MotionDetectionAction.DETECTED &&
            response.timestampMs - current.timestampMs > RECOVERY_THRESHOLD_MS
}
