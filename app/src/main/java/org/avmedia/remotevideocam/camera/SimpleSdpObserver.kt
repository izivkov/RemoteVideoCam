package org.avmedia.remotevideocam.camera

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import timber.log.Timber

internal open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sessionDescription: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(s: String) {}
    override fun onSetFailure(s: String) {
        Timber.i("Got error: $s")
    }
}
