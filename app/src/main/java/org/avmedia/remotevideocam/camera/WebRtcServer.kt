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
import org.json.JSONObject
import org.webrtc.*

class WebRtcServer : IVideoServer, VideoProcessor.Listener {
    private val TAG = "WebRtcPeer"

    // Constants
    private val STUN_URL = "stun:stun.l.google.com:19302"
    private val STREAM_ID = "ARDAMS"
    private val AUDIO_TRACK_ID = "101"
    private val CAPTURE_THREAD = "CaptureThread"
    private val NATIVE_LIB = "jingle_peerconnection_so"

    // Event Keys
    private val EVENT_WEB_RTC = "WEB_RTC_EVENT"
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
        println(">>> init called")
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
        signalingHandler.handleControllerWebRtcEvents()

        createAppEventsSubscription()
    }

    override val isRunning: Boolean
        get() = isInitialized

    override fun startClient() {
        println(">>> Start client called")
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
        println(">>> setView called")

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
        // Maintain the "false" constraints from your working version
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
                        val message =
                                JSONObject().apply {
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
                        val message =
                                JSONObject().apply {
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
        val iceServers = ArrayList<PeerConnection.IceServer>()
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)

        peerConnection =
                factory?.createPeerConnection(
                        rtcConfig,
                        object : PeerConnection.Observer {
                            override fun onIceCandidate(candidate: IceCandidate) {
                                Log.d(TAG, "Local Server ICE Candidate: ${candidate.sdpMid}")
                                val message =
                                        JSONObject().apply {
                                            put("type", TYPE_CANDIDATE)
                                            put("label", candidate.sdpMLineIndex)
                                            put("id", candidate.sdpMid)
                                            put("candidate", candidate.sdp)
                                        }
                                sendMessage(message)
                            }
                            // ... (rest of observer overrides kept empty for brevity)
                            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                            override fun onIceConnectionChange(
                                    p0: PeerConnection.IceConnectionState?
                            ) {}
                            override fun onIceConnectionReceivingChange(p0: Boolean) {}
                            override fun onIceGatheringChange(
                                    p0: PeerConnection.IceGatheringState?
                            ) {}
                            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                            override fun onAddStream(p0: MediaStream?) {}
                            override fun onRemoveStream(p0: MediaStream?) {}
                            override fun onDataChannel(p0: DataChannel?) {}
                            override fun onRenegotiationNeeded() {}
                            override fun onAddTrack(
                                    p0: RtpReceiver?,
                                    p1: Array<out MediaStream>?
                            ) {}
                            override fun onTrack(p0: RtpTransceiver?) {}
                        }
                )
    }

    private fun createVideoTrackFromCameraAndShowIt() {
        videoCapturer = createVideoCapturer()
        val source = factory!!.createVideoSource(videoCapturer!!.isScreencast)
        videoSource = source

        val newMotionProcessor =
                VideoProcessor().also {
                    this.motionProcessor?.release()
                    this.motionProcessor = it
                }
        source.setVideoProcessor(VideoProcessorImpl(newMotionProcessor))

        surfaceTextureHelper =
                SurfaceTextureHelper.create(CAPTURE_THREAD, rootEglBase!!.eglBaseContext)
        videoCapturer!!.initialize(surfaceTextureHelper, context, source.capturerObserver)
        videoCapturer!!.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS)

        videoTrackFromCamera =
                factory!!.createVideoTrack(VIDEO_TRACK_ID, source).apply {
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

        val options =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(rootEglBase!!.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(rootEglBase!!.eglBaseContext)

        factory =
                PeerConnectionFactory.builder()
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

        val enumerator =
                if (Camera2Enumerator.isSupported(context)) Camera2Enumerator(context)
                else Camera1Enumerator(true)

        // Try to find the preferred camera
        for (name in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(name) == useFrontCamera) {
                return enumerator.createCapturer(name, null)
            }
        }

        // Fallback
        for (name in enumerator.deviceNames) {
            if (enumerator.isBackFacing(name)) return enumerator.createCapturer(name, null)
        }
        return null
    }

    private fun sendMessage(message: JSONObject) {
        val json = JSONObject()
        json.put(TO_DISPLAY, message)
        emitEvent(json)
    }

    internal inner class SignalingHandler {
        fun handleControllerWebRtcEvents() {
            subscribe(
                    "WEB_RTC_COMMANDS",
                    Consumer { event ->
                        val webRtcEvent = event!!.getJSONObject(TO_CAMERA)
                        val type = webRtcEvent.getString("type")
                        Log.d(TAG, "Server received WebRTC Event: $type")
                        when (type) {
                            TYPE_OFFER -> {
                                Log.d(TAG, "Server received OFFER")
                                peerConnection?.setRemoteDescription(
                                        SimpleSdpObserver(),
                                        SessionDescription(
                                                SessionDescription.Type.OFFER,
                                                webRtcEvent.getString("sdp")
                                        )
                                )
                                doAnswer()
                            }
                            TYPE_ANSWER -> {
                                Log.d(TAG, "Server received ANSWER")
                                peerConnection?.setRemoteDescription(
                                        SimpleSdpObserver(),
                                        SessionDescription(
                                                SessionDescription.Type.ANSWER,
                                                webRtcEvent.getString("sdp")
                                        )
                                )
                            }
                            TYPE_CANDIDATE -> {
                                Log.d(TAG, "Server received CANDIDATE")
                                val candidate =
                                        IceCandidate(
                                                webRtcEvent.getString("id"),
                                                webRtcEvent.getInt("label"),
                                                webRtcEvent.getString("candidate")
                                        )
                                peerConnection?.addIceCandidate(candidate)
                            }
                        }
                    },
                    { Log.d(TAG, "Signaling Error: $it") },
                    { it!!.has(TO_CAMERA) }
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
                                                override fun onCameraSwitchDone(
                                                        isFrontCamera: Boolean
                                                ) {
                                                    val sharedPref =
                                                            context?.getSharedPreferences(
                                                                    "CameraPrefs",
                                                                    Context.MODE_PRIVATE
                                                            )
                                                    with(sharedPref?.edit()) {
                                                        this?.putBoolean(
                                                                "UseFrontCamera",
                                                                isFrontCamera
                                                        )
                                                        this?.apply()
                                                    }
                                                }

                                                override fun onCameraSwitchError(
                                                        errorDescription: String?
                                                ) {
                                                    Log.d(
                                                            TAG,
                                                            "Camera Switch Error: $errorDescription"
                                                    )
                                                }
                                            }
                                    )
                                }
                            },
                            { Log.d(TAG, "Event Error: $it") }
                    )

    companion object {
        const val VIDEO_TRACK_ID = "ARDAMSv0"
        const val VIDEO_RESOLUTION_WIDTH = 640
        const val VIDEO_RESOLUTION_HEIGHT = 360
        const val FPS = 30
    }

    override fun onDetectionResult(detected: Boolean) {}
}
