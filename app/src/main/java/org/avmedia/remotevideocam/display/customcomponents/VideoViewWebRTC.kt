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

        disposables.add(createAppEventsSubscription())

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

        if (rootEglBase == null) {
            rootEglBase = EglBase.create()
        }
        if (factory == null) {
            initializePeerConnectionFactory()
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
        if (peerConnection != null) {
            show()
            return
        }
        stop()
        initializeSurfaceViews()
        initializePeerConnectionFactory()
        initializePeerConnections()

        show()
    }

    private fun stop() {
        hide()

        try {
            peerConnection?.close()
        } catch (e: Exception) {
            Timber.e(e, "Error closing peer connection")
        }
        peerConnection?.dispose()
        peerConnection = null

        factory?.dispose()
        factory = null

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
        mirrorState = loadMirrorState()
        setMirror(mirrorState)
    }

    private fun loadMirrorState(): Boolean {
        val sharedPref = context.getSharedPreferences("MirrorPrefs", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("MirrorState", false)
    }

    private fun isSoundOn(): Boolean {
        val sharedPref = context.getSharedPreferences("SoundPrefs", Context.MODE_PRIVATE)
        val stateName = sharedPref.getString("SoundState", "OFF")
        return stateName == "ON"
    }

    private fun applyAudioMuteState(enabled: Boolean) {
        peerConnection?.receivers?.forEach { receiver ->
            receiver.track()?.let { track ->
                if (track.kind() == "audio") {
                    track.setEnabled(enabled)
                }
            }
        }
    }

    private fun mute() {
        applyAudioMuteState(false)
    }

    private fun unmute() {
        applyAudioMuteState(true)
    }

    private fun toggleMirror() {
        mirrorState = !mirrorState
        setMirror(mirrorState)
        saveMirrorState(mirrorState)
    }

    private fun saveMirrorState(state: Boolean) {
        val sharedPref = context.getSharedPreferences("MirrorPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("MirrorState", state)
            apply()
        }
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

        factory =
                PeerConnectionFactory.builder()
                        .setVideoEncoderFactory(encoderFactory)
                        .setVideoDecoderFactory(decoderFactory)
                        .createPeerConnectionFactory()
    }

    private fun initializePeerConnections() {
        if (peerConnection == null) {
            peerConnection = createPeerConnection(factory)
        }
    }

    private fun createPeerConnection(factory: PeerConnectionFactory?): PeerConnection? {
        val iceServers = ArrayList<IceServer>()
        val rtcConfig =
                PeerConnection.RTCConfiguration(iceServers).apply {
                    // Enable all ICE candidates including host candidates for WiFi Direct
                    iceTransportsType = PeerConnection.IceTransportsType.ALL
                    // Use continual gathering to discover all network interfaces
                    continualGatheringPolicy =
                            PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                    // Enable TCP candidates as fallback
                    tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
                }
        val pcConstraints = MediaConstraints()
        val pcObserver: PeerConnection.Observer =
                object : PeerConnection.Observer {
                    override fun onSignalingChange(signalingState: PeerConnection.SignalingState) {
                        Timber.d("Signaling state changed: $signalingState")
                    }
                    override fun onIceConnectionChange(
                            iceConnectionState: PeerConnection.IceConnectionState
                    ) {
                        Timber.d("ICE connection state changed: $iceConnectionState")
                    }
                    override fun onStandardizedIceConnectionChange(
                            newState: PeerConnection.IceConnectionState
                    ) {
                        Timber.d("Standardized ICE connection state changed: $newState")
                    }
                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                        Timber.d("Peer connection state changed: $newState")
                    }
                    override fun onIceConnectionReceivingChange(b: Boolean) {
                        Timber.d("ICE connection receiving change: $b")
                    }
                    override fun onIceGatheringChange(
                            iceGatheringState: PeerConnection.IceGatheringState
                    ) {
                        Timber.d("ICE gathering state changed: $iceGatheringState")
                    }

                    override fun onIceCandidate(iceCandidate: IceCandidate) {
                        Timber.d("Local ICE Candidate: ${iceCandidate.sdpMid}")
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
                        Timber.d("onAddStream: ${mediaStream.id}")
                        if (mediaStream.videoTracks.isNotEmpty()) {
                            val remoteVideoTrack = mediaStream.videoTracks[0]
                            remoteVideoTrack.setEnabled(true)
                            remoteVideoTrack.addSink(this@VideoViewWebRTC)
                            Timber.d("Attached video track from onAddStream")
                        }
                        if (mediaStream.audioTracks.isNotEmpty()) {
                            val remoteAudioTrack = mediaStream.audioTracks[0]
                            remoteAudioTrack.setEnabled(isSoundOn())
                            Timber.d("Attached audio track from onAddStream")
                        }
                    }

                    override fun onRemoveStream(mediaStream: MediaStream) {}
                    override fun onDataChannel(dataChannel: DataChannel) {}
                    override fun onRenegotiationNeeded() {}
                    override fun onAddTrack(
                            rtpReceiver: RtpReceiver,
                            mediaStreams: Array<MediaStream>
                    ) {
                        Timber.d("onAddTrack: ${rtpReceiver.id()}")
                        val track = rtpReceiver.track()
                        if (track is VideoTrack) {
                            track.setEnabled(true)
                            track.addSink(this@VideoViewWebRTC)
                            Timber.d("Attached video track from onAddTrack")
                        }
                        if (track is AudioTrack) {
                            track.setEnabled(isSoundOn())
                            Timber.d("Attached audio track from onAddTrack")
                        }
                    }
                    override fun onTrack(transceiver: RtpTransceiver) {
                        Timber.d("onTrack: ${transceiver.mid}")
                        val track = transceiver.receiver.track()
                        if (track is VideoTrack) {
                            track.setEnabled(true)
                            track.addSink(this@VideoViewWebRTC)
                            Timber.d("Attached video track from onTrack")
                        }
                        if (track is AudioTrack) {
                            track.setEnabled(isSoundOn())
                            Timber.d("Attached audio track from onTrack")
                        }
                    }
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
        eventMessage.put("to_camera_webrtc", message)
        org.avmedia.remotevideocam.camera.CameraToDisplayEventBus.emitEvent(eventMessage)
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
            val type = webRtcEvent.getString("type")
            Timber.d("VideoViewWebRTC received WebRTC Event: $type")
            when (type) {
                "offer" -> {
                    Timber.d("Processing OFFER from camera")
                    if (peerConnection == null) {
                        Timber.d("Initializing peer connection for offer")
                        show() // Make the view visible BEFORE initializing surface
                        initializeSurfaceViews()
                        initializePeerConnections()
                    }
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
                    Timber.d("Processing ICE candidate from camera")
                    val candidate =
                            IceCandidate(
                                    webRtcEvent.getString("id"),
                                    webRtcEvent.getInt("label"),
                                    webRtcEvent.getString("candidate")
                            )
                    peerConnection?.addIceCandidate(candidate)
                }
                "bye" -> {
                    Timber.i("got bye")
                    stop()
                }
            }
        }
    }
}
