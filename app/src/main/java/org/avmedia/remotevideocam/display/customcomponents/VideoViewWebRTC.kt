/*
 * Developed by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2021-05-08, 10:56 p.m.
 */

package org.avmedia.remotevideocam.display.customcomponents

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import org.avmedia.remotevideocam.common.ILocalConnection
import org.avmedia.remotevideocam.display.CameraStatusEventBus
import org.avmedia.remotevideocam.display.NetworkServiceConnection
import org.avmedia.remotevideocam.utils.ProgressEvents
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection.IceServer
import timber.log.Timber

class VideoViewWebRTC @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        org.webrtc.SurfaceViewRenderer(context, attrs), SdpObserver, PeerConnection.Observer {

    private var peerConnection: PeerConnection? = null
    private var rootEglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private val connection: ILocalConnection = NetworkServiceConnection
    private var mirrorState = false
    private val disposables = CompositeDisposable()

    companion object {
        private const val TAG = "VideoViewWebRTC"
        private const val RC_CALL = 111
        const val VIDEO_TRACK_ID = "ARDAMSv0"
        const val VIDEO_RESOLUTION_WIDTH = 640
        const val VIDEO_RESOLUTION_HEIGHT = 360
        const val FPS = 30
    }

    init {}

    private var isInitialized = false
    @SuppressLint("CheckResult")
    fun init() {
        if (isInitialized) return
        isInitialized = true

        CameraStatusEventBus.addSubject("WEB_RTC_EVENT")
        CameraStatusEventBus.subscribe(
                        this.javaClass.simpleName,
                        "WEB_RTC_EVENT",
                        onNext = { SignalingHandler().handleWebRtcEvent(JSONObject(it as String)) },
                        onError = { Timber.i("Failed to send...") }
                )
                ?.let { disposables.add(it) }

        CameraStatusEventBus.addSubject("VIDEO_COMMAND")
        CameraStatusEventBus.subscribe(
                        this.javaClass.simpleName,
                        "VIDEO_COMMAND",
                        onNext = { processVideoCommand(it as String) }
                )
                ?.let { disposables.add(it) }

        disposables.add(createAppEventsSubscription())

        if (rootEglBase == null) {
            rootEglBase = EglBase.create()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        disposables.clear()
        isInitialized = false
    }

    private fun processVideoCommand(command: String) {

        when (command) {
            "STOP" -> {
                stop()
            }
            "START" -> {
                start()
            }
        }
    }

    private fun start() {
        initializeSurfaceViews()
        initializePeerConnectionFactory()
        initializePeerConnections()

        show()
    }

    private fun stop() {
        hide()
        release()
    }

    fun show() {
        visibility = VISIBLE
    }

    fun hide() {
        visibility = GONE
    }

    private fun initializeSurfaceViews() {
        release() // just in case

        init(rootEglBase?.eglBaseContext, null)
        setEnableHardwareScaler(true)
        mirrorState = false
        setMirror(mirrorState)
    }

    private fun mute() {
        peerConnection!!.receivers[0].track()?.setEnabled(false)
    }

    private fun unmute() {
        peerConnection!!.receivers[0].track()?.setEnabled(true)
    }

    private fun toggleMirror() {
        mirrorState = !mirrorState
        setMirror(mirrorState)
    }

    private fun createAppEventsSubscription(): Disposable =
            ProgressEvents.connectionEventFlowable
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnNext {
                        when (it) {
                            ProgressEvents.Events.ToggleMirror -> {
                                toggleMirror()
                            }
                            ProgressEvents.Events.Mute -> {
                                mute()
                            }
                            ProgressEvents.Events.Unmute -> {
                                unmute()
                            }
                        }
                    }
                    .subscribe({}, { throwable -> Timber.d("Got error on subscribe: $throwable") })

    private fun initializePeerConnectionFactory() {
        // New
        try {
            // Force load the native binary manually
            System.loadLibrary("jingle_peerconnection_so")
        } catch (e: UnsatisfiedLinkError) {
            // If it fails here, the .so is definitely missing from the APK
            Log.e("WebRtcServer", "Native library failed to load: ${e.message}")
        }

        // Now proceed with initialization
        val options =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        // End new

        val encoderFactory: VideoEncoderFactory =
                DefaultVideoEncoderFactory(rootEglBase!!.eglBaseContext, true, true)
        val decoderFactory: VideoDecoderFactory =
                DefaultVideoDecoderFactory(rootEglBase!!.eglBaseContext)

        val initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)
        factory =
                PeerConnectionFactory.builder()
                        .setVideoEncoderFactory(encoderFactory)
                        .setVideoDecoderFactory(decoderFactory)
                        .createPeerConnectionFactory()
    }

    private fun initializePeerConnections() {
        peerConnection = createPeerConnection(factory)
    }

    private fun createPeerConnection(factory: PeerConnectionFactory?): PeerConnection? {
        val iceServers = ArrayList<IceServer>()
        iceServers.add(IceServer("stun:stun.l.google.com:19302"))
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        val pcConstraints = MediaConstraints()
        val pcObserver: PeerConnection.Observer =
                object : PeerConnection.Observer {
                    override fun onSignalingChange(signalingState: PeerConnection.SignalingState) {}
                    override fun onIceConnectionChange(
                            iceConnectionState: PeerConnection.IceConnectionState
                    ) {}
                    override fun onStandardizedIceConnectionChange(
                            newState: PeerConnection.IceConnectionState
                    ) {}
                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {}
                    override fun onIceConnectionReceivingChange(b: Boolean) {}
                    override fun onIceGatheringChange(
                            iceGatheringState: PeerConnection.IceGatheringState
                    ) {}

                    override fun onIceCandidate(iceCandidate: IceCandidate) {
                        val message = JSONObject()
                        try {
                            message.put("type", "candidate")
                            message.put("label", iceCandidate.sdpMLineIndex)
                            message.put("id", iceCandidate.sdpMid)
                            message.put("candidate", iceCandidate.sdp)
                            sendMessage(message)
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {}

                    override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent) {}
                    override fun onAddStream(mediaStream: MediaStream) {
                        val remoteVideoTrack = mediaStream.videoTracks[0]
                        remoteVideoTrack.setEnabled(true)

                        val remoteAudioTrack = mediaStream.audioTracks[0]
                        remoteAudioTrack.setEnabled(false) // start mute

                        remoteVideoTrack.addSink(this@VideoViewWebRTC)
                    }

                    override fun onRemoveStream(mediaStream: MediaStream) {}
                    override fun onDataChannel(dataChannel: DataChannel) {}
                    override fun onRenegotiationNeeded() {}
                    override fun onAddTrack(
                            rtpReceiver: RtpReceiver,
                            mediaStreams: Array<MediaStream>
                    ) {}
                    override fun onTrack(transceiver: RtpTransceiver) {}
                }
        return factory!!.createPeerConnection(rtcConfig, pcConstraints, pcObserver)
    }

    private fun doAnswer() {
        peerConnection!!.createAnswer(
                object : SimpleSdpObserver() {
                    override fun onCreateSuccess(sessionDescription: SessionDescription) {
                        peerConnection!!.setLocalDescription(
                                SimpleSdpObserver(),
                                sessionDescription
                        )
                        val message = JSONObject()
                        try {
                            message.put("type", "answer")
                            message.put("sdp", sessionDescription.description)
                            sendMessage(message)
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    }
                },
                MediaConstraints()
        )
    }

    private fun sendMessage(message: JSONObject) {
        val eventMessage = JSONObject()
        eventMessage.put("webrtc_event", message)
        connection.sendMessage(eventMessage.toString())
    }

    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(p0: Boolean) {}
    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidate(p0: IceCandidate?) {}
    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
    override fun onAddStream(p0: MediaStream?) {}
    override fun onRemoveStream(p0: MediaStream?) {}
    override fun onDataChannel(p0: DataChannel?) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}

    open inner class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sessionDescription: SessionDescription) {}

        override fun onSetSuccess() {}
        override fun onCreateFailure(s: String) {}
        override fun onSetFailure(s: String) {
            Timber.i("Got error: $s")
        }
    }

    inner class SignalingHandler {
        fun handleWebRtcEvent(webRtcEvent: JSONObject) {
            when (webRtcEvent.getString("type")) {
                "offer" -> {
                    peerConnection!!.setRemoteDescription(
                            SimpleSdpObserver(),
                            SessionDescription(
                                    SessionDescription.Type.OFFER,
                                    webRtcEvent.getString("sdp")
                            )
                    )
                    doAnswer()
                }
                "candidate" -> {
                    val candidate =
                            IceCandidate(
                                    webRtcEvent.getString("id"),
                                    webRtcEvent.getInt("label"),
                                    webRtcEvent.getString("candidate")
                            )
                    peerConnection!!.addIceCandidate(candidate)
                }
                "bye" -> {
                    // Not yet used.
                    Timber.i("got bye")
                }
            }
        }
    }
}
