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
import io.reactivex.functions.Consumer
import io.reactivex.functions.Predicate
import org.avmedia.remotevideocam.camera.CameraToDisplayEventBus.emitEvent
import org.avmedia.remotevideocam.camera.DisplayToCameraEventBus.subscribe
import org.avmedia.remotevideocam.camera.DisplayToCameraEventBus.unsubscribe
import org.avmedia.remotevideocam.utils.AndGate
import org.avmedia.remotevideocam.utils.ConnectionUtils
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection.*
import org.webrtc.PeerConnectionFactory.InitializationOptions
import timber.log.Timber
import java.util.*

/*
This class initiates a WebRTC call to the controller, by sending an WebRTC "offer"
to the controller, providing its A/V capabilities. It then waits for an "answer" with
controller's capabilities. The two sides then exchange ICE candidates until a suitable
common capabilities are found, and then media is streamed from this class to the controller.

Note that the media is streamed only one way from this class to the controller.

WebRTC does not specify signaling protocol. Usually, a separate signaling server is used
witch mediates between the two WebRTC peers, and communication from and to this server is
carried over WebSocket. However, we already have a communication channel between the peers
(NetworkServiceConnection) so we are using it instead. No separate signaling server is required.

It is possible in the future to factor out signaling into a separate class and provide
various signalling types, such as to separate signalling server.
 */
class WebRtcServer : IVideoServer {
    private val TAG = "WebRtcPeer"
    private var view: SurfaceViewRenderer? = null
    private var resolution = Size(640, 360)

    // WebRTC-specific
    private var rootEglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var videoTrackFromCamera: VideoTrack? = null
    var audioConstraints: MediaConstraints? = null
    var audioSource: AudioSource? = null
    var localAudioTrack: AudioTrack? = null
    var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var peerConnection: PeerConnection? = null
    var mediaStream: MediaStream? = null
    private var andGate: AndGate? = null
    private var context: Context? = null
    private val cameraControlHandler = CameraControlHandler()
    private val signalingHandler =
        SignalingHandler()

    // IVideoServer Interface
    override fun init(context: Context?) {
        this.context = context
        andGate = AndGate({ startServer() }) { stopServer() }
        andGate!!.addCondition("connected")
        andGate!!.addCondition("view set")
        // andGate!!.addCondition("camera permission")
        // andGate!!.addCondition("resolution set")
        val camera =
            ContextCompat.checkSelfPermission(context!!, Manifest.permission.CAMERA)
        andGate!!.set("camera permission", camera == PackageManager.PERMISSION_GRANTED)

        rootEglBase = EglBase.create()
        cameraControlHandler.handleCameraControlEvents()
        signalingHandler.handleControllerWebRtcEvents()
    }

    override val isRunning: Boolean
        get() = false

    override fun startClient() {
        emitEvent(ConnectionUtils.createStatus("VIDEO_PROTOCOL", "WEBRTC"))
        sendServerUrl()
        emitEvent(ConnectionUtils.createStatus("VIDEO_COMMAND", "START"))
    }

    override fun sendServerUrl() {
        emitEvent(ConnectionUtils.createStatus("VIDEO_SERVER_URL", ""))
    }

    override fun sendVideoStoppedStatus() {
        emitEvent(ConnectionUtils.createStatus("VIDEO_COMMAND", "STOP"))
    }

    override fun setView(view: SurfaceView?) {}
    override fun setView(view: TextureView?) {}
    override fun setView(view: SurfaceViewRenderer?) {
        this.view = view
        this.view!!.isEnabled = false
        andGate?.set("view set", true)
    }

    override fun setConnected(connected: Boolean, context: Context?) {
        andGate?.set("connected", connected)
        val camera =
            ContextCompat.checkSelfPermission(context!!, Manifest.permission.CAMERA)
        andGate?.set("camera permission", camera == PackageManager.PERMISSION_GRANTED)
    }

    override fun setResolution(w: Int, h: Int) {
        resolution = Size(w, h)
        andGate?.set("resolution set", true)
    }

    // end Interface
    // local methods
    private fun startServer() {
        initializeSurfaceViews()
        initializePeerConnectionFactory()
        createVideoTrackFromCameraAndShowIt()
        initializePeerConnections()
        startStreamingVideo()
        doCall()
        startClient()
    }

    private fun doAnswer() {
        peerConnection!!.createAnswer(
            object : SimpleSdpObserver() {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    peerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
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

    private fun startStreamingVideo() {
        mediaStream = factory!!.createLocalMediaStream("ARDAMS")
        mediaStream?.addTrack(videoTrackFromCamera)
        mediaStream?.addTrack(localAudioTrack)
        peerConnection!!.addStream(mediaStream)
        // cameraControlHandler.disableAudio()
    }

    private fun stopStreamingVideo() {
        peerConnection!!.removeStream(mediaStream)
    }

    private fun stopServer() {
        mediaStream!!.removeTrack(videoTrackFromCamera)
        mediaStream!!.removeTrack(localAudioTrack)
        view!!.release()
        stopClient()
    }

    private fun stopClient() {
        emitEvent(ConnectionUtils.createStatus("VIDEO_COMMAND", "STOP"))
    }

    private fun doCall() {
        val sdpMediaConstraints = MediaConstraints()
        sdpMediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false")
        )
        sdpMediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false")
        )
        peerConnection!!.createOffer(
            object : SimpleSdpObserver() {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    peerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                    val message = JSONObject()
                    try {
                        message.put("type", "offer")
                        message.put("sdp", sessionDescription.description)
                        sendMessage(message)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            },
            sdpMediaConstraints
        )
    }

    private fun initializePeerConnections() {
        peerConnection = createPeerConnection(factory)
    }

    private fun createPeerConnection(factory: PeerConnectionFactory?): PeerConnection? {
        val iceServers = ArrayList<IceServer>()
        val stunServer =
            IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        iceServers.add(stunServer)
        val rtcConfig = RTCConfiguration(iceServers)
        val pcConstraints = MediaConstraints()
        val pcObserver: PeerConnection.Observer = object : PeerConnection.Observer {
            override fun onSignalingChange(signalingState: SignalingState) {
                Log.d(TAG, "onSignalingChange: ")
            }

            override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: ")
            }

            override fun onStandardizedIceConnectionChange(
                newState: IceConnectionState
            ) {
            }

            override fun onConnectionChange(newState: PeerConnectionState) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {
                Log.d(TAG, "onIceConnectionReceivingChange: ")
            }

            override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
                Log.d(TAG, "onIceGatheringChange: ")
            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                Log.d(TAG, "onIceCandidate: ")
                val message = JSONObject()
                try {
                    message.put("type", "candidate")
                    message.put("label", iceCandidate.sdpMLineIndex)
                    message.put("id", iceCandidate.sdpMid)
                    message.put("candidate", iceCandidate.sdp)
                    Log.d(TAG, "onIceCandidate: sending candidate $message")
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }

            override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                Log.d(TAG, "onIceCandidatesRemoved: ")
            }

            override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent) {}
            override fun onAddStream(mediaStream: MediaStream) {
                Log.d(TAG, "onAddStream: " + mediaStream.videoTracks.size)
                val remoteVideoTrack = mediaStream.videoTracks[0]
                val remoteAudioTrack = mediaStream.audioTracks[0]

                remoteAudioTrack.setEnabled(true)
                remoteVideoTrack.setEnabled(true)

                remoteVideoTrack.addSink(view)
            }

            override fun onRemoveStream(mediaStream: MediaStream) {
                Log.d(TAG, "onRemoveStream: ")
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                Log.d(TAG, "onDataChannel: ")
            }

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded: ")
            }

            override fun onAddTrack(
                rtpReceiver: RtpReceiver,
                mediaStreams: Array<MediaStream>
            ) {
            }

            override fun onTrack(transceiver: RtpTransceiver) {}
        }
        return factory!!.createPeerConnection(rtcConfig, pcConstraints, pcObserver)
    }

    private fun sendMessage(message: JSONObject) {
        emitEvent(ConnectionUtils.createStatus("WEB_RTC_EVENT", message))
    }

    private fun createVideoTrackFromCameraAndShowIt() {
        audioConstraints = MediaConstraints()
        val videoCapturer = createVideoCapturer()
        val videoSource =
            factory!!.createVideoSource(videoCapturer!!.isScreencast)
        surfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", rootEglBase!!.eglBaseContext)
        videoCapturer.initialize(
            surfaceTextureHelper,
            context,
            videoSource.capturerObserver
        )
        videoCapturer.startCapture(
            VIDEO_RESOLUTION_WIDTH,
            VIDEO_RESOLUTION_HEIGHT,
            FPS
        )
        videoTrackFromCamera =
            factory!!.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        videoTrackFromCamera?.setEnabled(true)
        videoTrackFromCamera?.addSink(view)

        // create an AudioSource instance
        audioSource = factory!!.createAudioSource(audioConstraints)
        localAudioTrack = factory!!.createAudioTrack("101", audioSource)
    }

    private fun initializePeerConnectionFactory() {
        val encoderFactory: VideoEncoderFactory =
            DefaultVideoEncoderFactory(rootEglBase!!.eglBaseContext, true, true)
        val decoderFactory: VideoDecoderFactory =
            DefaultVideoDecoderFactory(rootEglBase!!.eglBaseContext)
        val initializationOptions =
            InitializationOptions.builder(context)
                .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun initializeSurfaceViews() {
        view!!.init(rootEglBase!!.eglBaseContext, null)
        view!!.setEnableHardwareScaler(true)
        view!!.setMirror(true)
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val videoCapturer: VideoCapturer?
        videoCapturer = if (useCamera2()) {
            createCameraCapturer(Camera2Enumerator(context))
        } else {
            createCameraCapturer(Camera1Enumerator(true))
        }
        return videoCapturer
    }

    private fun useCamera2(): Boolean {
        return Camera2Enumerator.isSupported(context)
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }

    // Other commands
    internal inner class CameraControlHandler {
        fun disableAudio() {
            if (mediaStream!!.audioTracks.size > 0) {
                val audio = mediaStream!!.audioTracks[0]
                audio.setEnabled(false)

                // inform the controller of current state
                emitEvent(
                    ConnectionUtils.createStatus(
                        "TOGGLE_SOUND",
                        false
                    )
                )
            }
        }

        @SuppressLint("LogNotTimber")
        fun handleCameraControlEvents() {
            subscribe(
                this.javaClass.simpleName,
                Consumer { event: JSONObject? ->
                    val commandType = event!!.getString("command")
                    when (commandType) {
                        "TOGGLE_SOUND" -> if (mediaStream!!.audioTracks.size > 0) {
                            val audio = mediaStream!!.audioTracks[0]
                            audio.setEnabled(!audio.enabled())

                            // inform the controller of current state
                            emitEvent(
                                ConnectionUtils.createStatus("TOGGLE_SOUND", audio.enabled())
                            )
                        }
                    }
                },
                Consumer { error: Throwable? ->
                    Log.d(
                        null,
                        "Error occurred in ControllerToBotEventBus: $error"
                    )
                },
                Predicate { event: JSONObject? ->
                    (event!!.has("command")
                            && ("TOGGLE_SOUND"
                            ==
                            event.getString(
                                "command"
                            )))
                } // filter out all but the "TOGGLE_SOUND" commands..
            )
        }
    }

    // Utils
    private fun beep() {
        val tg = ToneGenerator(6, 100)
        tg.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE)
    }

    internal inner class SignalingHandler {
        fun handleControllerWebRtcEvents() {
            subscribe(
                "WEB_RTC_COMMANDS",
                Consumer { event: JSONObject? ->
                    val commandType = ""
                    val webRtcEvent = event!!.getJSONObject("webrtc_event")
                    val type = webRtcEvent.getString("type")
                    when (type) {
                        "offer" -> {
                            Timber.d("connectToSignallingServer: received an offer \$isInitiator \$isStarted")
                            peerConnection!!.setRemoteDescription(
                                SimpleSdpObserver(),
                                SessionDescription(
                                    SessionDescription.Type.OFFER, webRtcEvent.getString("sdp")
                                )
                            )
                            doAnswer()
                        }
                        "answer" -> {
                            val remoteDescr = webRtcEvent.getString("sdp")
                            Timber.i("Got remote description %s", remoteDescr)
                            peerConnection!!.setRemoteDescription(
                                SimpleSdpObserver(),
                                SessionDescription(SessionDescription.Type.ANSWER, remoteDescr)
                            )
                        }
                        "candidate" -> {
                            val candidate = IceCandidate(
                                webRtcEvent.getString("id"),
                                webRtcEvent.getInt("label"),
                                webRtcEvent.getString("candidate")
                            )
                            peerConnection!!.addIceCandidate(candidate)
                        }
                    }
                },
                Consumer { error: Throwable? ->
                    Timber.d(
                        "Error occurred in handleControllerWebRtcEvents: %s", error
                    )
                },
                Predicate { commandJsn: JSONObject? ->
                    commandJsn!!.has(
                        "webrtc_event"
                    )
                } // filter out all non "webrtc_event" messages.
            )
        }

        fun shutDown() {
            // Not used
            unsubscribe("WEB_RTC_COMMANDS")
        }
    }

    companion object {
        const val VIDEO_TRACK_ID = "ARDAMSv0"
        const val VIDEO_RESOLUTION_WIDTH = 640
        const val VIDEO_RESOLUTION_HEIGHT = 360
        const val FPS = 30
    }
}