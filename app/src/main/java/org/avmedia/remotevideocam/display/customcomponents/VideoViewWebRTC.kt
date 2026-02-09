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
import org.avmedia.remotevideocam.utils.Utils
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
    private var remoteValidIp: String? = null

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
        sendDeviceInfo()

        show()
    }

    private fun sendDeviceInfo() {
        // val ipAddress = ConnectionUtils.getIPAddress(true)
        val ipAddress = Utils.getMyIP()
        val message =
                JSONObject().apply {
                    put("type", "DEVICE_INFO")
                    put("ip_address", ipAddress)
                }
        sendMessage(message)
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
                        if (isValidCandidate(iceCandidate, remoteValidIp)) {
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
                        } else {
                            Timber.d("Filtering out irrelevant candidate: ${iceCandidate.sdp}")
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
        println(">>> Sending message: $message")
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
            Timber.d("Received WebRTC Event: $type")
            when (type) {
                "offer" -> {
                    if (peerConnection == null) {
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
                    val sdp =
                            Utils.replaceInvalidIp(
                                    webRtcEvent.getString("candidate"),
                                    remoteValidIp
                            )
                    val candidate =
                            IceCandidate(
                                    webRtcEvent.getString("id"),
                                    webRtcEvent.getInt("label"),
                                    sdp
                            )
                    peerConnection?.addIceCandidate(candidate)
                }
                "bye" -> {
                    Timber.i("got bye")
                    stop()
                }
                "DEVICE_INFO" -> {
                    if (webRtcEvent.has("ip_address")) {
                        remoteValidIp = webRtcEvent.getString("ip_address")
                        Timber.d(">>>>>>>>>>>>>>>>> Received Remote IP: $remoteValidIp")
                    }
                }
            }
        }
    }

    private fun isValidCandidate(candidate: IceCandidate, targetIp: String?): Boolean {
        if (targetIp == null || targetIp.isEmpty()) return true
        val lastDot = targetIp.lastIndexOf('.')
        if (lastDot > 0) {
            val prefix = targetIp.substring(0, lastDot)
            return candidate.sdp.contains(prefix)
        }
        return true
    }
}
