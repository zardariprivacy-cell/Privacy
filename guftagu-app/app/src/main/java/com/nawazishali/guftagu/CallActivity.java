package com.nawazishali.guftagu;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Voice/video call over WebRTC. Signaling (offer/answer/ICE/state) travels as
 * small JSON nodes under /calls/{uidPair} in the same RTDB the chat uses,
 * polled like the rest of the app. Media itself is DTLS-SRTP encrypted by
 * WebRTC. Incoming calls only ring while the app is open (no FCM, by design).
 */
public class CallActivity extends BaseActivity implements PeerConnection.Observer {

    public static volatile boolean active = false;
    private static boolean factoryInited = false;

    private String myUid, peerUid, peerName, kind, callId;
    private boolean isVideo, incoming, answered, ended, remoteSet, connected;
    private long startedAt, connectedAt;

    private EglBase eglBase;
    private PeerConnectionFactory factory;
    private PeerConnection pc;
    private VideoCapturer capturer;
    private SurfaceTextureHelper stHelper;
    private AudioTrack localAudio;
    private VideoTrack localVideo;
    private SurfaceViewRenderer localView, remoteView;
    private final List<IceCandidate> pendingIce = new ArrayList<>();
    private final Set<String> seenIce = new HashSet<>();

    private TextView tvStatus;
    private Button btnMute, btnSpeaker;
    private LinearLayout incomingBar, callBar;
    private boolean muted, speaker, ringTick;
    private AudioManager audioManager;
    private int prevAudioMode;
    private boolean prevSpeaker;
    private Ringtone ringtone;
    private Vibrator vibrator;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean polling;
    private Runnable pendingAction;

    private String node() {
        return "/calls/" + callId;
    }

    private static class SdpObs implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sdp) { }
        @Override public void onSetSuccess() { }
        @Override public void onCreateFailure(String s) { }
        @Override public void onSetFailure(String s) { }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        myUid = Session.uid(this);
        peerUid = getIntent().getStringExtra("peerUid");
        peerName = getIntent().getStringExtra("peerName");
        kind = getIntent().getStringExtra("kind");
        incoming = getIntent().getBooleanExtra("incoming", false);
        if (myUid == null || peerUid == null) {
            finish();
            return;
        }
        isVideo = "video".equals(kind);
        callId = myUid.compareTo(peerUid) < 0 ? myUid + "_" + peerUid : peerUid + "_" + myUid;
        active = true;
        answered = !incoming;
        startedAt = System.currentTimeMillis();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            prevAudioMode = audioManager.getMode();
            prevSpeaker = audioManager.isSpeakerphoneOn();
        }
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        buildUi();
        if (incoming) {
            tvStatus.setText(isVideo ? "📹 Video call aa rahi hai..." : "📞 Call aa rahi hai...");
            startRinging();
            startPolling();
        } else {
            ensurePermissionsThen(this::startOutgoing);
        }
    }

    // ---------------- UI ----------------

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B141A);
        setContentView(root);

        remoteView = new SurfaceViewRenderer(this);
        root.addView(remoteView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (!isVideo) remoteView.setVisibility(View.GONE);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        top.setPadding(dp(16), dp(48), dp(16), 0);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        root.addView(top, tlp);

        TextView avatar = new TextView(this);
        avatar.setText(peerName != null && !peerName.isEmpty() ? peerName.substring(0, 1).toUpperCase() : "?");
        avatar.setTextColor(0xFFFFFFFF);
        avatar.setTextSize(30);
        avatar.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable av = new android.graphics.drawable.GradientDrawable();
        av.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        av.setColor(0xFF00A884);
        avatar.setBackground(av);
        top.addView(avatar, new LinearLayout.LayoutParams(dp(76), dp(76)));

        TextView name = new TextView(this);
        name.setText(peerName == null ? "" : peerName);
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(22);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(12);
        name.setLayoutParams(nlp);
        top.addView(name);

        tvStatus = new TextView(this);
        tvStatus.setText("...");
        tvStatus.setTextColor(0xFFB9C6CC);
        tvStatus.setTextSize(15);
        tvStatus.setGravity(Gravity.CENTER);
        top.addView(tvStatus);

        localView = new SurfaceViewRenderer(this);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(dp(104), dp(140), Gravity.BOTTOM | Gravity.END);
        llp.setMargins(0, 0, dp(14), dp(120));
        root.addView(localView, llp);
        if (!isVideo) localView.setVisibility(View.GONE);

        incomingBar = new LinearLayout(this);
        incomingBar.setOrientation(LinearLayout.HORIZONTAL);
        incomingBar.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        ilp.bottomMargin = dp(46);
        root.addView(incomingBar, ilp);
        Button btnAccept = roundBtn("📞", 0xFF25D366);
        Button btnReject = roundBtn("✖", 0xFFE53935);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(66), dp(66));
        blp.setMargins(dp(28), 0, dp(28), 0);
        btnAccept.setLayoutParams(blp);
        btnReject.setLayoutParams(new LinearLayout.LayoutParams(dp(66), dp(66)));
        incomingBar.addView(btnReject);
        incomingBar.addView(btnAccept);
        btnAccept.setOnClickListener(v -> {
            stopRinging();
            ensurePermissionsThen(this::answerCall);
        });
        btnReject.setOnClickListener(v -> rejectCall());

        callBar = new LinearLayout(this);
        callBar.setOrientation(LinearLayout.HORIZONTAL);
        callBar.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        clp.bottomMargin = dp(40);
        root.addView(callBar, clp);
        btnMute = roundBtn("🎤", 0xFF2A3942);
        btnSpeaker = roundBtn("🔈", 0xFF2A3942);
        Button btnSwitch = roundBtn("🔄", 0xFF2A3942);
        Button btnEnd = roundBtn("✖", 0xFFE53935);
        for (Button b : new Button[]{btnMute, btnSpeaker, btnSwitch, btnEnd}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(58), dp(58));
            p.setMargins(dp(9), 0, dp(9), 0);
            b.setLayoutParams(p);
            callBar.addView(b);
        }
        if (!isVideo) btnSwitch.setVisibility(View.GONE);
        btnMute.setOnClickListener(v -> {
            muted = !muted;
            if (localAudio != null) localAudio.setEnabled(!muted);
            btnMute.setText(muted ? "🔇" : "🎤");
        });
        btnSpeaker.setOnClickListener(v -> {
            speaker = !speaker;
            if (audioManager != null) audioManager.setSpeakerphoneOn(speaker);
            btnSpeaker.setText(speaker ? "🔊" : "🔈");
        });
        btnSwitch.setOnClickListener(v -> {
            if (capturer instanceof CameraVideoCapturer) {
                ((CameraVideoCapturer) capturer).switchCamera(null);
            }
        });
        btnEnd.setOnClickListener(v -> endCall(true));

        incomingBar.setVisibility(incoming ? View.VISIBLE : View.GONE);
        callBar.setVisibility(incoming ? View.GONE : View.VISIBLE);
    }

    private Button roundBtn(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(20);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(color);
        b.setBackground(g);
        return b;
    }

    // ---------------- permissions ----------------

    private void ensurePermissionsThen(Runnable action) {
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO);
        }
        if (isVideo && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.CAMERA);
        }
        if (need.isEmpty()) {
            action.run();
        } else {
            pendingAction = action;
            requestPermissions(need.toArray(new String[0]), 77);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 77) return;
        boolean ok = grantResults.length > 0;
        for (int g : grantResults) if (g != PackageManager.PERMISSION_GRANTED) ok = false;
        if (ok && pendingAction != null) {
            Runnable a = pendingAction;
            pendingAction = null;
            a.run();
        } else {
            Toast.makeText(this, "Call ke liye mic/camera ki permission chahiye", Toast.LENGTH_LONG).show();
            endCall(true);
        }
    }

    // ---------------- ringing ----------------

    private void startRinging() {
        try {
            ringtone = RingtoneManager.getRingtone(this,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
            if (ringtone != null) ringtone.play();
        } catch (Exception ignored) {
        }
    }

    private void stopRinging() {
        try {
            if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
        } catch (Exception ignored) {
        }
        ringtone = null;
    }

    // ---------------- call flow ----------------

    private void startOutgoing() {
        tvStatus.setText("📞 Calling...");
        try {
            JSONObject n = new JSONObject();
            n.put("from", myUid);
            n.put("to", peerUid);
            n.put("kind", kind);
            n.put("state", "ringing");
            n.put("ts", System.currentTimeMillis());
            Api.dbPut(this, node(), n, (ok, e) -> { });
        } catch (Exception ignored) {
        }
        initRtc();
        if (pc == null) return;
        pc.createOffer(new SdpObs() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                pc.setLocalDescription(new SdpObs(), sdp);
                try {
                    JSONObject o = new JSONObject();
                    o.put("type", "offer");
                    o.put("sdp", sdp.description);
                    Api.dbPut(CallActivity.this, node() + "/offer", o, (ok, e) -> { });
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onCreateFailure(String err) {
                fail("Call nahi bani");
            }
        }, new MediaConstraints());
        startPolling();
    }

    private void answerCall() {
        answered = true;
        incomingBar.setVisibility(View.GONE);
        callBar.setVisibility(View.VISIBLE);
        tvStatus.setText("Connecting...");
        initRtc();
        if (pc == null) return;
        Api.dbGet(this, node(), (n, e) -> {
            if (ended) return;
            if (n == null || n.optJSONObject("offer") == null) {
                fail("Call khatam ho gayi");
                return;
            }
            SessionDescription remote = new SessionDescription(
                    SessionDescription.Type.OFFER, n.optJSONObject("offer").optString("sdp"));
            pc.setRemoteDescription(new SdpObs() {
                @Override
                public void onSetSuccess() {
                    remoteSet = true;
                    flushIce();
                    pc.createAnswer(new SdpObs() {
                        @Override
                        public void onCreateSuccess(SessionDescription sdp) {
                            pc.setLocalDescription(new SdpObs(), sdp);
                            try {
                                JSONObject a = new JSONObject();
                                a.put("type", "answer");
                                a.put("sdp", sdp.description);
                                Api.dbPut(CallActivity.this, node() + "/answer", a, (ok, e2) -> { });
                                Api.dbPutStr(CallActivity.this, node() + "/state", "active", (ok, e2) -> { });
                            } catch (Exception ignored) {
                            }
                        }

                        @Override
                        public void onCreateFailure(String err) {
                            fail("Answer nahi bana");
                        }
                    }, new MediaConstraints());
                }

                @Override
                public void onSetFailure(String err) {
                    fail("Offer set nahi hua");
                }
            }, remote);
            startPolling();
        });
    }

    private void rejectCall() {
        stopRinging();
        ended = true;
        polling = false;
        Api.dbDelete(this, node(), (ok, e) -> { });
        cleanup();
        finish();
    }

    private void endCall(boolean byMe) {
        if (ended) return;
        ended = true;
        polling = false;
        stopRinging();
        if (byMe) {
            Api.dbPutStr(this, node() + "/state", "ended", (ok, e) -> { });
            handler.postDelayed(() -> Api.dbDelete(this, node(), (ok, e) -> { }), 1500);
        }
        cleanup();
        finish();
    }

    private void fail(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
        endCall(true);
    }

    // ---------------- signaling poll ----------------

    private void startPolling() {
        if (polling) return;
        polling = true;
        handler.post(pollTask);
    }

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (ended || !polling) return;
            pollOnce();
            handler.postDelayed(this, 1800);
        }
    };

    private void pollOnce() {
        if (incoming && !answered) {
            ringTick = !ringTick;
            if (ringTick && vibrator != null) {
                try {
                    vibrator.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE));
                } catch (Exception ignored) {
                }
            }
            Api.dbGet(this, node(), (n, e) -> {
                if (ended || answered) return;
                if (n == null || !"ringing".equals(n.optString("state"))) {
                    tvStatus.setText("Call khatam");
                    endCall(false);
                }
            });
            return;
        }
        if (!connected && System.currentTimeMillis() - startedAt > 60000) {
            tvStatus.setText("Koi jawab nahi mila");
            endCall(true);
            return;
        }
        if (!incoming && !remoteSet && pc != null) {
            Api.dbGet(this, node() + "/answer", (a, e) -> {
                if (ended || a == null || remoteSet || pc == null) return;
                SessionDescription remote = new SessionDescription(
                        SessionDescription.Type.ANSWER, a.optString("sdp"));
                pc.setRemoteDescription(new SdpObs() {
                    @Override
                    public void onSetSuccess() {
                        remoteSet = true;
                        flushIce();
                    }
                }, remote);
            });
        }
        if (pc != null) {
            Api.dbGet(this, node() + "/ice/" + peerUid, (ice, e) -> {
                if (ended || ice == null || pc == null) return;
                Iterator<String> it = ice.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    if (!seenIce.add(k)) continue;
                    JSONObject c = ice.optJSONObject(k);
                    if (c == null) continue;
                    IceCandidate cand = new IceCandidate(
                            c.optString("mid"), c.optInt("idx"), c.optString("sdp"));
                    if (remoteSet) {
                        try {
                            pc.addIceCandidate(cand);
                        } catch (Exception ignored) {
                        }
                    } else {
                        pendingIce.add(cand);
                    }
                }
            });
            Api.dbGet(this, node(), (n, e) -> {
                if (ended) return;
                if (n == null || "ended".equals(n.optString("state"))) endCall(false);
            });
        }
        if (connected) {
            long sec = (System.currentTimeMillis() - connectedAt) / 1000;
            tvStatus.setText(String.format(Locale.getDefault(), "🔒 Encrypted • %02d:%02d", sec / 60, sec % 60));
        }
    }

    private void flushIce() {
        if (pc == null) return;
        for (IceCandidate c : pendingIce) {
            try {
                pc.addIceCandidate(c);
            } catch (Exception ignored) {
            }
        }
        pendingIce.clear();
    }

    // ---------------- WebRTC setup ----------------

    private void initRtc() {
        try {
            if (!factoryInited) {
                PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                        .builder(this).setEnableInternalTracer(false).createInitializationOptions());
                factoryInited = true;
            }
            eglBase = EglBase.create();
            JavaAudioDeviceModule adm = JavaAudioDeviceModule.builder(this)
                    .setUseHardwareNoiseSuppressor(true)
                    .setUseHardwareAcousticEchoCanceler(true)
                    .createAudioDeviceModule();
            factory = PeerConnectionFactory.builder()
                    .setAudioDeviceModule(adm)
                    .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
                    .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                    .createPeerConnectionFactory();
            List<PeerConnection.IceServer> servers = new ArrayList<>();
            servers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
            servers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
            PeerConnection.RTCConfiguration cfg = new PeerConnection.RTCConfiguration(servers);
            pc = factory.createPeerConnection(cfg, this);
            if (pc == null) {
                fail("Call engine nahi bana");
                return;
            }
            AudioSource aSrc = factory.createAudioSource(new MediaConstraints());
            localAudio = factory.createAudioTrack("audio", aSrc);
            pc.addTrack(localAudio, Collections.singletonList("stream"));
            if (isVideo) {
                remoteView.init(eglBase.getEglBaseContext(), null);
                localView.init(eglBase.getEglBaseContext(), null);
                remoteView.setEnableHardwareScaler(true);
                localView.setEnableHardwareScaler(true);
                localView.setMirror(true);
                capturer = createCapturer();
                if (capturer != null) {
                    VideoSource vSrc = factory.createVideoSource(false);
                    stHelper = SurfaceTextureHelper.create("GuftaguCapture", eglBase.getEglBaseContext());
                    capturer.initialize(stHelper, this, vSrc.getCapturerObserver());
                    capturer.startCapture(480, 640, 24);
                    localVideo = factory.createVideoTrack("video", vSrc);
                    localVideo.addSink(localView);
                    pc.addTrack(localVideo, Collections.singletonList("stream"));
                }
            }
            if (audioManager != null) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                speaker = isVideo;
                audioManager.setSpeakerphoneOn(speaker);
                if (btnSpeaker != null) btnSpeaker.setText(speaker ? "🔊" : "🔈");
            }
        } catch (Exception e) {
            fail("Call engine error");
        }
    }

    private VideoCapturer createCapturer() {
        Camera2Enumerator en = new Camera2Enumerator(this);
        String[] names = en.getDeviceNames();
        for (String n : names) if (en.isFrontFacing(n)) return en.createCapturer(n, null);
        for (String n : names) if (en.isBackFacing(n)) return en.createCapturer(n, null);
        return null;
    }

    private void cleanup() {
        active = false;
        try {
            if (capturer != null) {
                try {
                    capturer.stopCapture();
                } catch (Exception ignored) {
                }
                capturer.dispose();
            }
        } catch (Exception ignored) {
        }
        try {
            if (pc != null) {
                pc.close();
                pc.dispose();
            }
        } catch (Exception ignored) {
        }
        try {
            if (localView != null) localView.release();
        } catch (Exception ignored) {
        }
        try {
            if (remoteView != null) remoteView.release();
        } catch (Exception ignored) {
        }
        try {
            if (factory != null) factory.dispose();
        } catch (Exception ignored) {
        }
        try {
            if (eglBase != null) eglBase.release();
        } catch (Exception ignored) {
        }
        try {
            if (audioManager != null) {
                audioManager.setMode(prevAudioMode);
                audioManager.setSpeakerphoneOn(prevSpeaker);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        polling = false;
        if (!ended) {
            ended = true;
            cleanup();
        }
        active = false;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        endCall(true);
    }

    // ---------------- PeerConnection.Observer ----------------

    @Override
    public void onIceCandidate(IceCandidate candidate) {
        try {
            JSONObject c = new JSONObject();
            c.put("mid", candidate.sdpMid);
            c.put("idx", candidate.sdpMLineIndex);
            c.put("sdp", candidate.sdp);
            Api.dbPost(this, node() + "/ice/" + myUid, c, (id, e) -> { });
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
        runOnUiThread(() -> {
            if (ended) return;
            switch (state) {
                case CONNECTED:
                case COMPLETED:
                    if (!connected) {
                        connected = true;
                        connectedAt = System.currentTimeMillis();
                    }
                    break;
                case DISCONNECTED:
                    tvStatus.setText("Connection kamzor hai...");
                    break;
                case FAILED:
                case CLOSED:
                    if (answered) endCall(false);
                    break;
                default:
                    break;
            }
        });
    }

    @Override
    public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
        if (receiver.track() instanceof VideoTrack && remoteView != null) {
            VideoTrack vt = (VideoTrack) receiver.track();
            runOnUiThread(() -> {
                try {
                    vt.addSink(remoteView);
                } catch (Exception ignored) {
                }
            });
        }
    }

    @Override
    public void onAddStream(MediaStream stream) {
        if (!stream.videoTracks.isEmpty() && remoteView != null) {
            VideoTrack vt = stream.videoTracks.get(0);
            runOnUiThread(() -> {
                try {
                    vt.addSink(remoteView);
                } catch (Exception ignored) {
                }
            });
        }
    }

    @Override public void onSignalingChange(PeerConnection.SignalingState state) { }
    @Override public void onIceConnectionReceivingChange(boolean receiving) { }
    @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) { }
    @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) { }
    @Override public void onRemoveStream(MediaStream stream) { }
    @Override public void onDataChannel(DataChannel channel) { }
    @Override public void onRenegotiationNeeded() { }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
