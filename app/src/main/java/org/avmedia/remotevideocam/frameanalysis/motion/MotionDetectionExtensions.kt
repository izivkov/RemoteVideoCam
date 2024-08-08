package org.avmedia.remotevideocam.frameanalysis.motion

import android.os.Handler
import org.webrtc.GlTextureFrameBuffer

fun Handler.runOrPost(runnable: Runnable) {
    if (looper.isCurrentThread) {
        runnable.run()
    } else {
        post(runnable)
    }
}

fun Handler.makeReleaseRunnable(textureFrameBuffer: GlTextureFrameBuffer): Runnable {
    return Runnable {
        runOrPost {
            textureFrameBuffer.release()
        }
    }
}
