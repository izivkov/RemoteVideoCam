package org.avmedia.remotevideocam.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import android.view.SurfaceView
import android.view.TextureView
import androidx.core.content.ContextCompat
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import org.avmedia.remotevideocam.camera.CameraToDisplayEventBus.emitEvent
import org.avmedia.remotevideocam.camera.DisplayToCameraEventBus.subscribe
import org.avmedia.remotevideocam.utils.AndGate
import org.avmedia.remotevideocam.utils.ConnectionUtils
import org.avmedia.remotevideocam.utils.ProgressEvents
import org.avmedia.remotevideocam.utils.Utils
import org.json.JSONObject
import org.webrtc.*
import timber.log.Timber

class WebRtcServer : IVideoServer, VideoProcessor.Listener {
    private val TAG = "WebRtcServer"

    // Constants
    private val STREAM_ID = "ARDAMS"
    private val AUDIO_TRACK_ID = "101"
    private val CAPTURE_THREAD = "CaptureThread"
    private val NATIVE_LIB = "jingle_peerconnection_so"

    // Event Keys
    private val CMD_VIDEO_PROTOCOL = "VIDEO_PROTOCOL"
    private val CMD_VIDEO_COMMAND = "VIDEO_COMMAND"
    private val TYPE_OFFER = "offer"
    private val TYPE_ANSWER = "answer"
    private val TYPE_CANDIDATE = "candidate"

    private val TO_DISPLAY = "to_display_webrtc"
    private val TO_CAMERA = "to_camera_webrtc"

    private var view: SurfaceViewRenderer? = null
    private var resolution = Size(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT)

    private var rootEglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var videoTrackFromCamera: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var peerConnection: PeerConnection? = null
    private var mediaStream: MediaStream? = null
    private var andGate: AndGate? = null
    private var context: Context? = null
    private val signalingHandler = SignalingHandler()

    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var motionProcessor: VideoProcessor? = null

    private var isInitialized = false

    override fun init(context: Context?) {
        if (isInitialized) return
        isInitialized = true

        this.context = context
        andGate = AndGate({ startServer() }) { stopServer() }

        andGate!!.addCondition("connected")
        andGate!!.addCondition("view set")
        andGate!!.addCondition("camera permission")

        val camera = ContextCompat.checkSelfPermission(context!!, Manifest.permission.CAMERA)
        andGate!!["camera permission"] = camera == PackageManager.PERMISSION_GRANTED

        if (rootEglBase == null) {
            rootEglBase = EglBase.create()
        }

        // Subscribe to connection events
        signalingHandler.handleConnectionEvents()
        // Subscribe to WebRTC signaling events
        signalingHandler.handleWebRtcEvents()

        createAppEventsSubscription()
    }

    override val isRunning: Boolean
        get() = isInitialized

    override fun startClient() {
        emitEvent(ConnectionUtils.createStatus(CMD_VIDEO_PROTOCOL, "WEBRTC"))
        sendServerUrl()
        emitEvent(ConnectionUtils.createStatus(CMD_VIDEO_COMMAND, "START"))
    }

    override fun sendServerUrl() {
        emitEvent(ConnectionUtils.createStatus("VIDEO_SERVER_URL", ""))
    }

    override fun sendVideoStoppedStatus() {
        emitEvent(ConnectionUtils.createStatus(CMD_VIDEO_COMMAND, "STOP"))
    }

    override fun setView(view: SurfaceView?) {}
    override fun setView(view: TextureView?) {}
    override fun setView(view: SurfaceViewRenderer?) {
        this.view = view
        this.view?.isEnabled = false
        andGate?.set("view set", true)
    }

    override fun setConnected(connected: Boolean) {
        andGate?.set("connected", connected)
    }

    override fun setResolution(w: Int, h: Int) {
        resolution = Size(w, h)
        andGate?.set("resolution set", true)
    }

    override fun setMotionDetection(enabled: Boolean) {
        motionProcessor?.setMotionListener(if (enabled) this else null)
    }

    private fun startServer() {
        initializeSurfaceViews()
        initializePeerConnectionFactory()
        createVideoTrackFromCameraAndShowIt()
        initializePeerConnections()
        startStreamingVideo()
        doCall()
        startClient()
    }

    private fun startStreamingVideo() {
        val factory = factory ?: return
        val pc = peerConnection ?: return

        val stream = factory.createLocalMediaStream(STREAM_ID)
        mediaStream = stream

        videoTrackFromCamera?.let { track ->
            stream.addTrack(track)
            pc.addTrack(track, listOf(stream.id))
        }

        localAudioTrack?.let { track ->
            stream.addTrack(track)
            pc.addTrack(track, listOf(stream.id))
        }
    }

    private fun stopServer() {
        motionProcessor?.release()
        motionProcessor = null

        try {
            videoCapturer?.stopCapture()
        } catch (e: InterruptedException) {
            Log.e(TAG, "Failed to stop video capturer", e)
        }
        videoCapturer?.dispose()
        videoCapturer = null

        videoSource?.dispose()
        videoSource = null

        videoTrackFromCamera?.dispose()
        videoTrackFromCamera = null

        localAudioTrack?.dispose()
        localAudioTrack = null

        audioSource?.dispose()
        audioSource = null

        mediaStream = null

        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null

        factory?.dispose()
        factory = null

        view?.release()
        stopClient()
    }

    private fun stopClient() {
        emitEvent(ConnectionUtils.createStatus(CMD_VIDEO_COMMAND, "STOP"))
    }

    private fun doCall() {
        val sdpMediaConstraints = MediaConstraints()
        sdpMediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false")
        )
        sdpMediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false")
        )

        peerConnection?.createOffer(
            object : SimpleSdpObserver() {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                    val message = JSONObject().apply {
                        put("type", TYPE_OFFER)
                        put("sdp", sessionDescription.description)
                    }
                    sendMessage(message)
                }
            },
            sdpMediaConstraints
        )
    }

    private fun doAnswer() {
        peerConnection?.createAnswer(
            object : SimpleSdpObserver() {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                    val message = JSONObject().apply {
                        put("type", TYPE_ANSWER)
                        put("sdp", sessionDescription.description)
                    }
                    sendMessage(message)
                }
            },
            MediaConstraints()
        )
    }

    private fun initializePeerConnections() {
        // NO ICE servers - strictly local P2P connection
        val iceServers = ArrayList<PeerConnection.IceServer>()
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    val candidateIp = extractIpFromCandidate(candidate.sdp)

                    Timber.d("Generated ICE Candidate - IP: $candidateIp, SDP: ${candidate.sdp}")

                    if (isValidLocalCandidate(candidateIp)) {
                        Timber.d("✓ Sending valid local candidate IP: $candidateIp")
                        val message = JSONObject().apply {
                            put("type", TYPE_CANDIDATE)
                            put("label", candidate.sdpMLineIndex)
                            put("id", candidate.sdpMid)
                            put("candidate", candidate.sdp)
                        }
                        sendMessage(message)
                    } else {
                        Timber.d("✗ Filtering out non-local candidate IP: $candidateIp")
                    }
                }

                override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                    Timber.d("Signaling state: $state")
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Timber.d("ICE connection state: $state")
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    Timber.d("ICE receiving: $receiving")
                }

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    Timber.d("ICE gathering state: $state")
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                    Timber.d("ICE candidates removed: ${candidates?.size}")
                }

                override fun onAddStream(stream: MediaStream?) {
                    Timber.d("Stream added: ${stream?.id}")
                }

                override fun onRemoveStream(stream: MediaStream?) {
                    Timber.d("Stream removed: ${stream?.id}")
                }

                override fun onDataChannel(dataChannel: DataChannel?) {}

                override fun onRenegotiationNeeded() {
                    Timber.d("Renegotiation needed")
                }

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    Timber.d("Track added: ${receiver?.track()?.kind()}")
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    Timber.d("Track: ${transceiver?.receiver?.track()?.kind()}")
                }
            }
        )
    }

    private fun createVideoTrackFromCameraAndShowIt() {
        videoCapturer = createVideoCapturer()
        val source = factory!!.createVideoSource(videoCapturer!!.isScreencast)
        videoSource = source

        val newMotionProcessor = VideoProcessor().also {
            this.motionProcessor?.release()
            this.motionProcessor = it
        }
        source.setVideoProcessor(VideoProcessorImpl(newMotionProcessor))

        surfaceTextureHelper = SurfaceTextureHelper.create(CAPTURE_THREAD, rootEglBase!!.eglBaseContext)
        videoCapturer!!.initialize(surfaceTextureHelper, context, source.capturerObserver)
        videoCapturer!!.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS)

        videoTrackFromCamera = factory!!.createVideoTrack(VIDEO_TRACK_ID, source).apply {
            setEnabled(true)
            addSink(view)
        }

        audioSource = factory!!.createAudioSource(MediaConstraints())
        localAudioTrack = factory!!.createAudioTrack(AUDIO_TRACK_ID, audioSource)
    }

    private fun initializePeerConnectionFactory() {
        try {
            System.loadLibrary(NATIVE_LIB)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native library failed to load")
        }

        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(rootEglBase!!.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(rootEglBase!!.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun initializeSurfaceViews() {
        val currentView = view ?: return
        try {
            currentView.release()
        } catch (e: Exception) {
            Log.d(TAG, "View already released")
        }
        currentView.init(rootEglBase!!.eglBaseContext, null)
        currentView.setEnableHardwareScaler(true)
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val sharedPref = context?.getSharedPreferences("CameraPrefs", Context.MODE_PRIVATE)
        val useFrontCamera = sharedPref?.getBoolean("UseFrontCamera", false) ?: false

        val enumerator = if (Camera2Enumerator.isSupported(context)) {
            Camera2Enumerator(context)
        } else {
            Camera1Enumerator(true)
        }

        // Try preferred camera
        for (name in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(name) == useFrontCamera) {
                return enumerator.createCapturer(name, null)
            }
        }

        // Fallback to back camera
        for (name in enumerator.deviceNames) {
            if (enumerator.isBackFacing(name)) {
                return enumerator.createCapturer(name, null)
            }
        }
        return null
    }

    private fun sendMessage(message: JSONObject) {
        // Add sender's real WiFi Direct IP address
        val myRealIp = Utils.getMyIP()
        message.put("senderRealIp", myRealIp)

        Timber.d(">>> Sending ${message.optString("type")} with senderRealIp: $myRealIp")

        val json = JSONObject().apply {
            put(TO_DISPLAY, message)
        }
        emitEvent(json)
    }

    private fun extractIpFromCandidate(sdp: String): String {
        val parts = sdp.split(" ")
        return if (parts.size > 4) parts[4] else ""
    }

    private fun isValidLocalCandidate(ip: String): Boolean {
        return when {
            ip.isEmpty() -> false
            ip.startsWith("192.168.49.") -> true  // WiFi Direct
            ip.startsWith("192.168.") && !ip.startsWith("192.0.0.") -> true
            ip.startsWith("10.") -> true
            ip.startsWith("172.") -> {
                val secondOctet = ip.split(".").getOrNull(1)?.toIntOrNull() ?: 0
                secondOctet in 16..31
            }
            ip.startsWith("169.254.") -> true  // Link-local
            else -> false
        }
    }

    private fun replaceCandidateIp(candidateSdp: String, newIp: String): String {
        val parts = candidateSdp.split(" ").toMutableList()
        if (parts.size > 4) {
            parts[4] = newIp
        }
        return parts.joinToString(" ")
    }

    internal inner class SignalingHandler {

        fun handleConnectionEvents() {
            subscribe(
                "CONNECTION_EVENTS",
                Consumer { event ->
                    event?.takeIf { it.has("command") }?.let {
                        when (event.getString("command")) {
                            "CONNECTED" -> {
                                Timber.d("WiFi Direct CONNECTED")
                                ProgressEvents.onNext(ProgressEvents.Events.ConnectionCameraSuccessful)
                                setConnected(true)
                                startClient()
                            }
                            "DISCONNECTED" -> {
                                Timber.d("WiFi Direct DISCONNECTED")
                                ProgressEvents.onNext(ProgressEvents.Events.CameraDisconnected)
                                setConnected(false)
                            }
                        }
                    }
                },
                { error -> Timber.e("Connection event error: $error") },
                { it?.has("command") == true }
            )
        }

        fun handleWebRtcEvents() {
            subscribe(
                "WEBRTC_SIGNALING",
                Consumer { event ->
                    Timber.d("<<< Received event with keys: ${event?.keys()?.asSequence()?.toList()}")

                    val webRtcEvent = event?.getJSONObject(TO_CAMERA) ?: return@Consumer
                    val type = webRtcEvent.getString("type")

                    Timber.d("<<< Processing WebRTC $type")

                    when (type) {
                        TYPE_OFFER -> {
                            Timber.d("Received OFFER")
                            peerConnection?.setRemoteDescription(
                                SimpleSdpObserver(),
                                SessionDescription(SessionDescription.Type.OFFER, webRtcEvent.getString("sdp"))
                            )
                            doAnswer()
                        }

                        TYPE_ANSWER -> {
                            Timber.d("Received ANSWER")
                            peerConnection?.setRemoteDescription(
                                SimpleSdpObserver(),
                                SessionDescription(SessionDescription.Type.ANSWER, webRtcEvent.getString("sdp"))
                            )
                        }

                        TYPE_CANDIDATE -> {
                            val candidateSdp = webRtcEvent.getString("candidate")
                            val senderIp = webRtcEvent.optString("senderRealIp", "")
                            val candidateIp = extractIpFromCandidate(candidateSdp)

                            Timber.d("Received CANDIDATE - candidateIp: $candidateIp, senderRealIp: $senderIp")

                            val finalCandidateSdp = if (!isValidLocalCandidate(candidateIp) &&
                                senderIp.isNotEmpty() &&
                                isValidLocalCandidate(senderIp)) {
                                Timber.d("✓ Fixing candidate: $candidateIp -> $senderIp")
                                replaceCandidateIp(candidateSdp, senderIp)
                            } else if (isValidLocalCandidate(candidateIp)) {
                                Timber.d("✓ Using candidate as-is: $candidateIp")
                                candidateSdp
                            } else {
                                Timber.w("✗ Ignoring invalid candidate: $candidateIp")
                                null
                            }

                            finalCandidateSdp?.let {
                                val candidate = IceCandidate(
                                    webRtcEvent.getString("id"),
                                    webRtcEvent.getInt("label"),
                                    it
                                )
                                peerConnection?.addIceCandidate(candidate)
                            }
                        }
                    }
                },
                { error -> Timber.e("WebRTC signaling error: $error") },
                { it?.has(TO_CAMERA) == true }
            )
        }
    }

    @SuppressLint("LogNotTimber")
    private fun createAppEventsSubscription(): Disposable =
        ProgressEvents.connectionEventFlowable
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { event ->
                    if (event == ProgressEvents.Events.FlipCamera) {
                        (videoCapturer as? CameraVideoCapturer)?.switchCamera(
                            object : CameraVideoCapturer.CameraSwitchHandler {
                                override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                                    context?.getSharedPreferences("CameraPrefs", Context.MODE_PRIVATE)
                                        ?.edit()
                                        ?.putBoolean("UseFrontCamera", isFrontCamera)
                                        ?.apply()
                                }

                                override fun onCameraSwitchError(errorDescription: String?) {
                                    Log.d(TAG, "Camera switch error: $errorDescription")
                                }
                            }
                        )
                    }
                },
                { Log.d(TAG, "Event error: $it") }
            )

    companion object {
        const val VIDEO_TRACK_ID = "ARDAMSv0"
        const val VIDEO_RESOLUTION_WIDTH = 640
        const val VIDEO_RESOLUTION_HEIGHT = 360
        const val FPS = 30
    }

    override fun onDetectionResult(detected: Boolean) {}
}
