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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private var remoteValidIp: String? = null
    private var mirrorState = false
    private val disposables = CompositeDisposable()

    // ---------- Temporary diagnostics (hunting "frames arrive but the screen is black") ----------
    /** Timber has no planted tree, so its logs are silent. Log.d is used directly with tag=RVCDIAG. */
    private val diagTag = "RVCDIAG"
    @Volatile private var diagFrameCount = 0L

    private fun diag(msg: String) {
        Log.d(diagTag, "[view@${System.identityHashCode(this)}] $msg")
    }


    // ---------- Frame heartbeat ----------
    /** Currently attached remote video track. Kept so sinks can be re-attached when the view becomes visible again without waiting for a new offer. */
    private var remoteVideoTrack: VideoTrack? = null
    private var sinksAttached = false

    /** SystemClock.elapsedRealtime() of the last received frame; 0 means no frame received in this session. */
    @Volatile private var lastFrameElapsedRealtimeMs = 0L

    /** Milliseconds since the previous frame; null means no frame has been received in this session yet. */
    private val _msSinceLastFrame = MutableStateFlow<Long?>(null)
    val msSinceLastFrame: StateFlow<Long?> = _msSinceLastFrame.asStateFlow()

    /**
     * Wall-clock time (ms) of the last received frame, used only to display "last update HH:mm:ss".
     * Deduplicated per second so a 30fps stream does not trigger 30 recompositions per second.
     */
    private val _lastFrameTimestampMs = MutableStateFlow(0L)
    val lastFrameTimestampMs: StateFlow<Long> = _lastFrameTimestampMs.asStateFlow()

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    @Volatile private var heartbeatTickerRunning = false
    private val heartbeatTicker =
            object : Runnable {
                override fun run() {
                    if (!heartbeatTickerRunning) return
                    updateMsSinceLastFrame()
                    mainHandler.postDelayed(this, FRAME_HEARTBEAT_TICK_MS)
                }
            }

    /**
     * Frame heartbeat sink: counts only frames that were actually decoded, not network or signaling heartbeats.
     * When the stream stops while the network is still up, this sink stops receiving too, so "stream dead, network alive" is covered.
     */
    private val frameHeartbeatSink = VideoSink { _ ->
        // NOTE: a VideoSink does not own the VideoFrame. Never call frame.release() here.
        // WebRTC dispatches the same underlying buffer to every sink on the track (render sink + heartbeat sink)
        // and the track releases it after dispatch. Releasing it here drops the refcount to 0 too early,
        // which makes the EglRenderer thread crash when it releases the frame again:
        // IllegalStateException: release() called on an object with refcount < 1
        lastFrameElapsedRealtimeMs = SystemClock.elapsedRealtime()
        val nowWallClock = System.currentTimeMillis()
        // While frames arrive the distance is always 0; StateFlow deduplicates it, so no recomposition per frame.
        if (_msSinceLastFrame.value != 0L) _msSinceLastFrame.value = 0L
        if (nowWallClock / 1000L != _lastFrameTimestampMs.value / 1000L) {
            _lastFrameTimestampMs.value = nowWallClock
        }
        val n = ++diagFrameCount
        if (n == 1L || n % 60L == 0L) {
            diag("heartbeat frame #$n visibility=$visibility attached=$isAttachedToWindow")
        }
    }

    // ---------- Limited ICE recovery ----------
    /** Current ICE state, used to decide whether a recovery is still needed. */
    @Volatile private var currentIceState: PeerConnection.IceConnectionState? = null
    @Volatile private var iceRecoveryAttempts = 0
    @Volatile private var iceRecoveryScheduled = false

    companion object {
        private const val TAG = "VideoViewWebRTC"
        private const val RC_CALL = 111
        const val VIDEO_TRACK_ID = "ARDAMSv0"
        const val VIDEO_RESOLUTION_WIDTH = 640
        const val VIDEO_RESOLUTION_HEIGHT = 360
        const val FPS = 30

        /** Freeze threshold: no new frame for more than 2 seconds means the picture is frozen. */
        const val FRAME_FREEZE_TIMEOUT_MS = 2000L

        /** Heartbeat tick: refresh rate of "time since last frame" in the UI; also the resolution of the freeze alert. */
        private const val FRAME_HEARTBEAT_TICK_MS = 250L

        /** Maximum number of automatic recoveries; stop retrying after that to avoid endless reconnects. */
        private const val MAX_ICE_RECOVERY_ATTEMPTS = 3
        private const val ICE_RECOVERY_INITIAL_BACKOFF_MS = 1000L

        /** DISCONNECTED is often a transient blip, so wait this long before recovering. */
        private const val ICE_RECOVERY_STALL_TRIGGER_MS = 5000L
    }

    init {}

    private var isInitialized = false
    @SuppressLint("CheckResult")
    fun init() {
        diag("init() called, isInitialized=$isInitialized")
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        diag("onAttachedToWindow visibility=$visibility")
        // Restore sinks when returning to the viewing screen, otherwise the picture stays black after leaving it.
        addVideoSinks()
    }

    override fun onDetachedFromWindow() {
        diag("onDetachedFromWindow visibility=$visibility")
        super.onDetachedFromWindow()
        disposables.clear()
        isInitialized = false
        // Must detach the frame callbacks when leaving the viewing screen, otherwise they keep firing in the background (battery, heat) and leak.
        removeVideoSinks()
        mainHandler.removeCallbacksAndMessages(null)
        iceRecoveryScheduled = false
    }

    /**
     * Attach sinks only once per track: the render sink (this View) and the heartbeat sink.
     * Re-negotiation may call onAddTrack/onTrack again, so keep this idempotent.
     */
    private fun attachVideoTrack(track: VideoTrack) {
        diag("attachVideoTrack track@${System.identityHashCode(track)} (current@${remoteVideoTrack?.let { System.identityHashCode(it) }})")
        if (remoteVideoTrack !== track) {
            removeVideoSinks()
            remoteVideoTrack = track
        }
        // Make the view visible when a track arrives; otherwise the SurfaceView has no Surface and keeps dropping frames (black screen).
        show()
        addVideoSinks()
        Timber.d("Attached video track with frame heartbeat")
    }

    private fun addVideoSinks() {
        val track = remoteVideoTrack ?: return
        diag("addVideoSinks track@${System.identityHashCode(track)} sinksAttached=$sinksAttached visibility=$visibility attached=$isAttachedToWindow")
        if (sinksAttached) return
        track.setEnabled(true)
        track.addSink(this)
        track.addSink(frameHeartbeatSink)
        sinksAttached = true
        startHeartbeatTicker()
        diag("addVideoSinks done: renderer+heartbeat attached")
    }

    private fun removeVideoSinks() {
        val track = remoteVideoTrack
        diag("removeVideoSinks track@${track?.let { System.identityHashCode(it) }} sinksAttached=$sinksAttached")
        if (sinksAttached && track != null) {
            try {
                track.removeSink(frameHeartbeatSink)
                track.removeSink(this)
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove video sink")
            }
        }
        sinksAttached = false
        stopHeartbeatTicker()
        resetFrameHeartbeat()
    }

    private fun startHeartbeatTicker() {
        if (heartbeatTickerRunning) return
        heartbeatTickerRunning = true
        mainHandler.postDelayed(heartbeatTicker, FRAME_HEARTBEAT_TICK_MS)
    }

    private fun stopHeartbeatTicker() {
        heartbeatTickerRunning = false
        mainHandler.removeCallbacks(heartbeatTicker)
    }

    /** Use elapsedRealtime() so the interval is unaffected by wall-clock changes or NTP adjustments. */
    private fun updateMsSinceLastFrame() {
        val lastFrameAt = lastFrameElapsedRealtimeMs
        if (lastFrameAt <= 0L) {
            if (_msSinceLastFrame.value != null) _msSinceLastFrame.value = null
            return
        }
        val elapsed = (SystemClock.elapsedRealtime() - lastFrameAt).coerceAtLeast(0L)
        // Anything within one tick counts as 0: while playing normally the value stays 0 and StateFlow deduplication avoids recomposition.
        val published = if (elapsed < FRAME_HEARTBEAT_TICK_MS) 0L else elapsed
        if (_msSinceLastFrame.value != published) _msSinceLastFrame.value = published
    }

    private fun resetFrameHeartbeat() {
        lastFrameElapsedRealtimeMs = 0L
        _msSinceLastFrame.value = null
        _lastFrameTimestampMs.value = 0L
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
        diag("start() peerConnection=${peerConnection != null}")
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
        val localIp = (org.avmedia.remotevideocam.camera.ConnectionStrategy.getDisplayConnection(context) as? ILocalConnection)?.getLocalIp()
        if (localIp != null) {
            val message = JSONObject().apply {
                put("type", "DEVICE_INFO")
                put("ip_address", localIp)
            }
            sendMessage(message)
        }
    }

    private fun stop() {
        diag("stop()")
        hide()

        // Session ended: detach frame callbacks and clear heartbeat state so a stale timestamp is never read as "still receiving".
        removeVideoSinks()
        remoteVideoTrack = null
        resetIceRecovery()

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
        diag("show() (was visibility=$visibility)")
        visibility = VISIBLE
    }

    fun hide() {
        diag("hide() (was visibility=$visibility)")
        visibility = GONE
    }

    // ---------- Surface lifecycle diagnostics ----------
    override fun surfaceCreated(holder: SurfaceHolder) {
        super.surfaceCreated(holder)
        diag("surfaceCreated")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        super.surfaceChanged(holder, format, width, height)
        diag("surfaceChanged ${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        super.surfaceDestroyed(holder)
        diag("surfaceDestroyed")
    }


    private fun initializeSurfaceViews() {
        diag("initializeSurfaceViews(): release()+init()")
        release() // just in case

        init(rootEglBase?.eglBaseContext, null)
        setEnableHardwareScaler(true)
        mirrorState = loadMirrorState()
        setMirror(mirrorState)
        recreateSurfaceIfAlreadyVisible()
    }

    /**
     * SurfaceViewRenderer creates its EGL Surface only in the surfaceCreated() callback.
     * If release()/init() happens while the SurfaceView is already visible (surfaceCreated will not fire again),
     * the renderer never gets a Surface: logcat fills with "EglRenderer: Dropping frame - No surface" and the screen stays black.
     * Toggle visibility briefly to force one surfaceDestroyed -> surfaceCreated cycle.
     */
    private fun recreateSurfaceIfAlreadyVisible() {
        if (!isAttachedToWindow || visibility != VISIBLE) return
        diag("force surface recreation (visibility toggle)")
        visibility = INVISIBLE
        postDelayed(
                {
                    if (isAttachedToWindow) {
                        visibility = VISIBLE
                        diag("surface recreation: visible again")
                    }
                },
                80L
        )
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
                    ) {
                        diag("ICE connection state: $iceConnectionState")
                        Timber.i("ICE connection state: $iceConnectionState")
                        currentIceState = iceConnectionState
                        when (iceConnectionState) {
                            PeerConnection.IceConnectionState.CONNECTED,
                            PeerConnection.IceConnectionState.COMPLETED -> resetIceRecovery()
                            PeerConnection.IceConnectionState.FAILED ->
                                    scheduleIceRecovery("ICE FAILED")
                            PeerConnection.IceConnectionState.DISCONNECTED ->
                                    scheduleIceRecoveryIfStillStalled()
                            else -> {}
                        }
                    }
                    override fun onStandardizedIceConnectionChange(
                            newState: PeerConnection.IceConnectionState
                    ) {}
                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {}
                    override fun onIceConnectionReceivingChange(b: Boolean) {}
                    override fun onIceGatheringChange(
                            iceGatheringState: PeerConnection.IceGatheringState
                    ) {}

                    override fun onIceCandidate(iceCandidate: IceCandidate) {
                        Timber.d("Local ICE Candidate: ${iceCandidate.sdpMid}")

                        val localIp = (org.avmedia.remotevideocam.camera.ConnectionStrategy.getDisplayConnection(context) as? ILocalConnection)?.getLocalIp()
                        if (localIp != null && !iceCandidate.sdp.contains(org.avmedia.remotevideocam.utils.Utils.getCommonSubnet(localIp))) {
                            Timber.d("Filtering out local candidate: ${iceCandidate.sdp}")
                            return
                        }

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
                        diag("onAddStream: ${mediaStream.id}")
                        if (mediaStream.videoTracks.isNotEmpty()) {
                            attachVideoTrack(mediaStream.videoTracks[0])
                            diag("Attached video track from onAddStream")
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
                        diag("onAddTrack: ${rtpReceiver.id()}")
                        val track = rtpReceiver.track()
                        if (track is VideoTrack) {
                            attachVideoTrack(track)
                            diag("Attached video track from onAddTrack")
                        }
                        if (track is AudioTrack) {
                            track.setEnabled(isSoundOn())
                            Timber.d("Attached audio track from onAddTrack")
                        }
                    }
                    override fun onTrack(transceiver: RtpTransceiver) {
                        diag("onTrack: ${transceiver.mid}")
                        val track = transceiver.receiver.track()
                        if (track is VideoTrack) {
                            attachVideoTrack(track)
                            diag("Attached video track from onTrack")
                        }
                        if (track is AudioTrack) {
                            track.setEnabled(isSoundOn())
                            Timber.d("Attached audio track from onTrack")
                        }
                    }
                }
        return factory!!.createPeerConnection(rtcConfig, pcConstraints, pcObserver)
    }

    // ---------- Limited ICE recovery: bounded attempts + exponential backoff ----------

    /** On DISCONNECTED, wait a while and recover only if frames are still not arriving. */
    private fun scheduleIceRecoveryIfStillStalled() {
        mainHandler.postDelayed(
                {
                    if (currentIceState != PeerConnection.IceConnectionState.DISCONNECTED) {
                        return@postDelayed
                    }
                    val lastFrameAt = lastFrameElapsedRealtimeMs
                    val noFrameFor =
                            if (lastFrameAt > 0L)
                                    SystemClock.elapsedRealtime() - lastFrameAt
                            else -1L
                    // Do not disturb if frames still arrive; never having received a frame (-1) also counts as abnormal.
                    if (noFrameFor < 0L || noFrameFor >= ICE_RECOVERY_STALL_TRIGGER_MS) {
                        scheduleIceRecovery("ICE DISCONNECTED and no new frame for a long time")
                    }
                },
                ICE_RECOVERY_STALL_TRIGGER_MS
        )
    }

    private fun scheduleIceRecovery(reason: String) {
        if (iceRecoveryScheduled) return
        if (iceRecoveryAttempts >= MAX_ICE_RECOVERY_ATTEMPTS) {
            Timber.w("Automatic recovery limit ($MAX_ICE_RECOVERY_ATTEMPTS attempts) reached, giving up: $reason")
            return
        }
        if (peerConnection == null) return

        val backoffMs = ICE_RECOVERY_INITIAL_BACKOFF_MS shl iceRecoveryAttempts
        iceRecoveryAttempts++
        iceRecoveryScheduled = true
        Timber.i("Automatic recovery attempt $iceRecoveryAttempts will run in ${backoffMs}ms: $reason")

        mainHandler.postDelayed(
                {
                    iceRecoveryScheduled = false
                    if (peerConnection == null) return@postDelayed
                    renegotiateWithIceRestart()
                },
                backoffMs
        )
    }

    /**
     * The viewer actively re-negotiates with an ICE restart.
     * The existing offer/answer/candidate signaling format is reused; the capture side already handles offers, so the protocol is unchanged.
     */
    private fun renegotiateWithIceRestart() {
        val pc = peerConnection ?: return
        val constraints =
                MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
                }
        pc.createOffer(
                object : SimpleSdpObserver() {
                    override fun onCreateSuccess(sessionDescription: SessionDescription) {
                        pc.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                        sendMessage(
                                JSONObject().apply {
                                    put("type", "offer")
                                    put("sdp", sessionDescription.description)
                                }
                        )
                        Timber.i("Sent offer with ICE restart")
                    }

                    override fun onCreateFailure(s: String) {
                        Timber.w("Failed to create ICE restart offer: $s")
                    }
                },
                constraints
        )
    }

    private fun resetIceRecovery() {
        iceRecoveryAttempts = 0
        iceRecoveryScheduled = false
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
            diag("Received WebRTC Event: $type")
            when (type) {
                "offer" -> {
                    if (peerConnection == null) {
                        initializeSurfaceViews()
                        initializePeerConnections()
                    }
                    // The re-negotiation path must also make the view visible, otherwise a SurfaceView without Surface stays black.
                    show()
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
                    val candidateStr = webRtcEvent.getString("candidate")
                    if (remoteValidIp != null && !candidateStr.contains(org.avmedia.remotevideocam.utils.Utils.getCommonSubnet(remoteValidIp!!))) {
                        Timber.d("Filtering out remote candidate: $candidateStr")
                        return
                    }

                    val candidate =
                            IceCandidate(
                                    webRtcEvent.getString("id"),
                                    webRtcEvent.getInt("label"),
                                    candidateStr
                            )
                    peerConnection?.addIceCandidate(candidate)
                }
                // The viewer re-negotiated (ICE restart); receive the answer from the capture side.
                "answer" -> {
                    peerConnection?.setRemoteDescription(
                            SimpleSdpObserver(),
                            SessionDescription(
                                    SessionDescription.Type.ANSWER,
                                    webRtcEvent.getString("sdp")
                            )
                    )
                }
                "DEVICE_INFO" -> {
                    remoteValidIp = webRtcEvent.getString("ip_address")
                    Timber.d("Received DEVICE_INFO. Remote IP: $remoteValidIp")
                }
                "bye" -> {
                    Timber.i("got bye")
                    stop()
                }
            }
        }
    }
}
