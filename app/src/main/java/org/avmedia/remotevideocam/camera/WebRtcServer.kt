package org.avmedia.remotevideocam.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.ToneGenerator
import android.util.Log
import android.util.Size
import android.view.SurfaceView
import android.view.TextureView
import androidx.core.content.ContextCompat
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Predicate
import java.util.*
import org.avmedia.remotevideocam.camera.CameraToDisplayEventBus.emitEvent
import org.avmedia.remotevideocam.camera.DisplayToCameraEventBus.subscribe
import org.avmedia.remotevideocam.camera.DisplayToCameraEventBus.unsubscribe
import org.avmedia.remotevideocam.frameanalysis.motion.*
import org.avmedia.remotevideocam.utils.AndGate
import org.avmedia.remotevideocam.utils.ConnectionUtils
import org.avmedia.remotevideocam.utils.ProgressEvents
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import timber.log.Timber

class WebRtcServer : IVideoServer, MotionProcessor.Listener {
    private val TAG = "WebRtcPeer"

    // Preferred Constants
    private val STUN_SERVER_URL = "stun:stun.l.google.com:19302"
    private val STREAM_ID = "ARDAMS"
    private val AUDIO_TRACK_ID = "101"
    private val CAPTURE_THREAD_NAME = "CaptureThread"
    private val NATIVE_LIB_NAME = "jingle_peerconnection_so"

    // Command Constants
    private val CMD_VIDEO_PROTOCOL = "VIDEO_PROTOCOL"
    private val CMD_VIDEO_COMMAND = "VIDEO_COMMAND"
    private val CMD_VIDEO_URL = "VIDEO_SERVER_URL"
    private val VAL_WEBRTC = "WEBRTC"
    private val VAL_START = "START"
    private val VAL_STOP = "STOP"

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
    private var motionProcessor: MotionProcessor? = null
    private var motionNotificationController: MotionNotificationController? = null

    private var isInitialized = false

    override fun init(context: Context?) {
        if (isInitialized) return
        isInitialized = true

        this.context = context
        andGate = AndGate({ startServer() }) { stopServer() }

        andGate!!.addCondition("view set")
        andGate!!.addCondition("camera permission")

        val cameraPerm = ContextCompat.checkSelfPermission(context!!, Manifest.permission.CAMERA)
        andGate!!["camera permission"] = cameraPerm == PackageManager.PERMISSION_GRANTED

        if (rootEglBase == null) {
            rootEglBase = EglBase.create()
        }
        signalingHandler.handleControllerWebRtcEvents()
        motionNotificationController = MotionNotificationController(context)

        createAppEventsSubscription(context)
    }

    override val isRunning: Boolean
        get() = isInitialized

    override fun startClient() {
        emitEvent(ConnectionUtils.createStatus(CMD_VIDEO_PROTOCOL, VAL_WEBRTC))
        sendServerUrl()
        emitEvent(ConnectionUtils.createStatus(CMD_VIDEO_COMMAND, VAL_START))
    }

    override fun sendServerUrl() {
        emitEvent(ConnectionUtils.createStatus(CMD_VIDEO_URL, ""))
    }

    override fun sendVideoStoppedStatus() {
        emitEvent(ConnectionUtils.createStatus(CMD_VIDEO_COMMAND, VAL_STOP))
    }

    override fun setView(view: SurfaceView?) {}
    override fun setView(view: TextureView?) {}
    override fun setView(view: SurfaceViewRenderer?) {
        this.view = view
        if (isInitialized) {
            initializeSurfaceViews()
        }
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
        val pc = peerConnection ?: return
        videoTrackFromCamera?.let { pc.addTrack(it, listOf(STREAM_ID)) }
        localAudioTrack?.let { pc.addTrack(it, listOf(STREAM_ID)) }
    }

    private fun stopStreamingVideo() {
        // In Unified Plan, we typically stop the transceivers or the capturer
        videoCapturer?.stopCapture()
        peerConnection?.transceivers?.forEach { it.stop() }
    }

    private fun doCall() {
        val sdpMediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject().apply {
                    put("type", "offer")
                    put("sdp", sessionDescription.description)
                }
                sendMessage(message)
            }
        }, sdpMediaConstraints)
    }

    private fun doAnswer() {
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject().apply {
                    put("type", "answer")
                    put("sdp", sessionDescription.description)
                }
                sendMessage(message)
            }
        }, MediaConstraints())
    }

    private fun initializePeerConnections() {
        val iceServers = arrayListOf(
            PeerConnection.IceServer.builder(STUN_SERVER_URL).createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(iceCandidate: IceCandidate) {
                val message = JSONObject().apply {
                    put("type", "candidate")
                    put("label", iceCandidate.sdpMLineIndex)
                    put("id", iceCandidate.sdpMid)
                    put("candidate", iceCandidate.sdp)
                }
                sendMessage(message)
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
            override fun onTrack(transceiver: RtpTransceiver) {}
        })
    }

    private fun createVideoTrackFromCameraAndShowIt() {
        videoCapturer = createVideoCapturer()
        val videoSource = factory!!.createVideoSource(videoCapturer!!.isScreencast)

        val newMotionProcessor = MotionProcessor().also {
            this.motionProcessor?.release()
            this.motionProcessor = it
        }
        videoSource.setVideoProcessor(VideoProcessorImpl(newMotionProcessor))

        surfaceTextureHelper = SurfaceTextureHelper.create(CAPTURE_THREAD_NAME, rootEglBase!!.eglBaseContext)
        videoCapturer!!.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
        videoCapturer!!.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS)

        videoTrackFromCamera = factory!!.createVideoTrack(VIDEO_TRACK_ID, videoSource).apply {
            setEnabled(true)
            addSink(view)
        }

        audioSource = factory!!.createAudioSource(MediaConstraints())
        localAudioTrack = factory!!.createAudioTrack(AUDIO_TRACK_ID, audioSource)
    }

    private fun initializePeerConnectionFactory() {
        try {
            System.loadLibrary(NATIVE_LIB_NAME)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native library load failed")
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
            // Attempt to release first to ensure a clean state
            currentView.release()
        } catch (e: Exception) {
            Log.w(TAG, "View release failed or was not initialized: ${e.message}")
        }

        // Initialize only if we have a valid context from the EGL base
        rootEglBase?.eglBaseContext?.let { eglContext ->
            currentView.init(eglContext, null)
            currentView.setEnableHardwareScaler(true)
            currentView.setMirror(false)
        }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val enumerator = if (Camera2Enumerator.isSupported(context)) {
            Camera2Enumerator(context)
        } else {
            Camera1Enumerator(true)
        }

        for (name in enumerator.deviceNames) {
            if (enumerator.isBackFacing(name)) {
                return enumerator.createCapturer(name, null)
            }
        }
        return null
    }

    private fun stopServer() {
        motionProcessor?.release()
        motionProcessor = null
        stopStreamingVideo()
        peerConnection?.dispose()
        peerConnection = null
        view?.release()
        stopClient()
    }

    private fun stopClient() {
        emitEvent(ConnectionUtils.createStatus(CMD_VIDEO_COMMAND, VAL_STOP))
    }

    private fun sendMessage(message: JSONObject) {
        emitEvent(ConnectionUtils.createStatus("WEB_RTC_EVENT", message))
    }

    internal inner class SignalingHandler {
        private val pendingCandidates = mutableListOf<IceCandidate>()

        fun handleControllerWebRtcEvents() {
            subscribe("WEB_RTC_COMMANDS", Consumer { event ->
                val webRtcEvent = event!!.getJSONObject("webrtc_event")
                when (val type = webRtcEvent.getString("type")) {
                    "offer" -> handleOffer(webRtcEvent)
                    "answer" -> handleAnswer(webRtcEvent)
                    "candidate" -> handleCandidate(webRtcEvent)
                }
            }, { Log.e(TAG, "Signaling Error: $it") }, { it!!.has("webrtc_event") })
        }

        private fun handleOffer(event: JSONObject) {
            val sdp = SessionDescription(SessionDescription.Type.OFFER, event.getString("sdp"))
            peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    drainCandidates()
                    doAnswer()
                }
            }, sdp)
        }

        private fun handleAnswer(event: JSONObject) {
            val sdp = SessionDescription(SessionDescription.Type.ANSWER, event.getString("sdp"))
            peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    drainCandidates()
                }
            }, sdp)
        }

        private fun handleCandidate(event: JSONObject) {
            val candidate = IceCandidate(
                event.getString("id"),
                event.getInt("label"),
                event.getString("candidate")
            )
            if (peerConnection?.remoteDescription != null) {
                peerConnection?.addIceCandidate(candidate)
            } else {
                pendingCandidates.add(candidate)
            }
        }

        private fun drainCandidates() {
            pendingCandidates.forEach { peerConnection?.addIceCandidate(it) }
            pendingCandidates.clear()
        }
    }

    private fun flipCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    @SuppressLint("LogNotTimber")
    private fun createAppEventsSubscription(context: Context?): Disposable =
        ProgressEvents.connectionEventFlowable
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ event ->
                when (event) {
                    ProgressEvents.Events.FlipCamera -> flipCamera()
                }
            }, { Log.d(TAG, "Event Error: $it") })

    companion object {
        const val VIDEO_TRACK_ID = "ARDAMSv0"
        const val VIDEO_RESOLUTION_WIDTH = 640
        const val VIDEO_RESOLUTION_HEIGHT = 360
        const val FPS = 30
    }

    override fun onDetectionResult(detected: Boolean) {
        val action = if (detected) MotionDetectionAction.DETECTED else MotionDetectionAction.NOT_DETECTED
        emitEvent(MotionDetectionData(action).toJsonResponse())
    }
}
