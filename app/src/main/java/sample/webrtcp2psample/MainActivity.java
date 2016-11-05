package sample.webrtcp2psample;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;

import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "MainActivity";

    private final static List<PeerConnection.IceServer> iceServers = Arrays.asList(
            new PeerConnection.IceServer("stun:stun.l.google.com:19302"),
            new PeerConnection.IceServer("stun:stun1.l.google.com:19302"),
            new PeerConnection.IceServer("stun:stun2.l.google.com:19302"),
            new PeerConnection.IceServer("stun:stun3.l.google.com:19302")
    );

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();


    private PeerConnection peerConnection;

    private Future<WebSocket> wsf;

    private DataChannel dataChannel;

    private MySdpObserver sdpObserver = new MySdpObserver();

    private MediaConstraints mediaConstraints = new MediaConstraints();

    private DataChannel.Observer dataChannelObserver = new DataChannelObserver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wsf = AsyncHttpClient.getDefaultInstance().websocket("ws://mizumanju.denshinohazama.com:3001/", "json", (ex, ws) -> {
            if (ex != null) {
                Log.e(TAG, "WebSocket での通信に失敗しました", ex);
                return;
            }
            Log.d(TAG, "WebSocket が繋がりました");
            ws.setStringCallback(s -> {
                JSONObject message;
                String type;
                String sdp;
                JSONObject ice;
                String sdpMid;
                String candidate;
                int sdpMLineIndex;
                try {
                    message = new JSONObject(s);
                    Log.d(TAG, message.toString(4));
                    type = message.getString("type").toLowerCase();
                    if (!type.equals("candidate")) {
                        sdp = message.getString("sdp");
                        ice = null;
                        sdpMid = null;
                        sdpMLineIndex = 0;
                        candidate = null;
                    } else {
                        sdp = null;
                        ice = message.getJSONObject("ice");
                        sdpMid = ice.getString("sdpMid");
                        sdpMLineIndex = ice.getInt("sdpMLineIndex");
                        candidate = ice.getString("candidate");
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "JSON の形式が不正です。", e);
                    return;
                }
                executor.execute(() -> {
                    SessionDescription sd = null;
                    switch (type) {
                        case "offer":
                            Log.d(TAG, "Received offer ...");
                            prepareNewConnection();
                            sd = new SessionDescription(
                                    SessionDescription.Type.OFFER,
                                    sdp);
                            peerConnection.setRemoteDescription(sdpObserver, sd);
                            break;
                        case "answer":
                            Log.d(TAG, "Received answer ...");
                            sd = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
                            peerConnection.setRemoteDescription(sdpObserver, sd);
                            break;
                        case "candidate":
                            Log.d(TAG, String.format("Received ICE candidate ... sdpMid: %s, sdpMLineIndex: %d, candidate: %s", sdpMid, sdpMLineIndex, candidate));
                            if (peerConnection != null) {
                                peerConnection.addIceCandidate(new IceCandidate(sdpMid, sdpMLineIndex, candidate));
                            }
                            break;
                    }

                });
                executor.execute(() -> {
                    if (type.equals("offer")) {
                        peerConnection.createAnswer(sdpObserver, mediaConstraints);
                    }
                });

            });
        });


    }

    private void prepareNewConnection() {
        mediaConstraints.optional.add(
                new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));

        // Initialize field trials.
        PeerConnectionFactory.initializeFieldTrials("");


        // video  使わないけど true にしないと new PeerConnectionFactory(options) で例外発生
        // http://stackoverflow.com/questions/29499725/new-peerconnectionfactory-gives-error-on-android
        // https://bugs.chromium.org/p/webrtc/issues/detail?id=3416
        if (!PeerConnectionFactory.initializeAndroidGlobals(this.getApplicationContext(), true, false, false)) {
            Log.d(TAG, "Failed to initializeAndroidGlobals");
        }
        // Options 指定しない方が ICE Candedate が正しそう。
        //PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        //options.networkIgnoreMask = 0;
        PeerConnectionFactory peerConnectionFactory = new PeerConnectionFactory(null);
        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, mediaConstraints, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "onSignalingChange: " + signalingState);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: " + iceConnectionState);
            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {
                Log.d(TAG, "onIceConnectionReceivingChange: " + b);
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                Log.d(TAG, "onIceGatheringChange: " + iceGatheringState);
            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                Log.d(TAG, "onIceCandidate");
                if (iceCandidate == null) {
                    return;
                }
                String message = String.format(Locale.ENGLISH, "{\"type\":\"candidate\",\"ice\":{\"candidate\":\"%s\",\"sdpMid\":\"%s\",\"sdpMLineIndex\":\"%d\"}}",
                        iceCandidate.sdp,
                        iceCandidate.sdpMid,
                        iceCandidate.sdpMLineIndex
                );
                Log.d(TAG, "onIceCandidate: " + message);
                wsf.setCallback((e, ws) -> {
                    if (e != null) {
                        Log.e(TAG, "onIceCandedate で例外が発生しました。", e);
                        return;
                    }
                    ws.send(message);
                });
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
                Log.d(TAG, "onIceCandidatesRemoved: " + iceCandidates.length);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {

            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {

            }

            @Override
            public void onDataChannel(DataChannel dc) {
                executor.execute(() -> {
                    dataChannel = dc;
                    Log.d(TAG, "ondatachannel: " + dc.label());
                    dc.registerObserver(dataChannelObserver);
                });
            }

            @Override
            public void onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded");
            }
        });
        if (sdpObserver.initiator) {
            DataChannel.Init init = new DataChannel.Init();
            init.negotiated = false;
            init.ordered = true;
            init.id = 1;
            dataChannel = peerConnection.createDataChannel("gamedata", init);
            dataChannel.registerObserver(dataChannelObserver);
        }
    }

    public void makeOffer(View view) {
        if (peerConnection != null) {
            return;
        }
        sdpObserver.initiator = true;
        executor.execute(() -> {
            prepareNewConnection();
            peerConnection.createOffer(sdpObserver, mediaConstraints);
        });

    }

    public void sendMessage(View view) {
        ByteBuffer color = ByteBuffer.allocate(3);
        View v = view.getRootView();
        color.put((byte) ((SeekBar) v.findViewById(R.id.red)).getProgress());
        color.put((byte) ((SeekBar) v.findViewById(R.id.green)).getProgress());
        color.put((byte) ((SeekBar) v.findViewById(R.id.blue)).getProgress());
        color.flip();
        executor.execute(() -> {
            Log.d(TAG, "send start.");
            dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(String.valueOf(color.limit()).getBytes(Charset.forName("UTF-8"))), false));
            dataChannel.send(new DataChannel.Buffer(color, true));
            Log.d(TAG, "send: " + Arrays.toString(color.array()));
        });
    }

    private void sendSdp(SessionDescription.Type type, String sdp) {
        wsf.setCallback((e, ws) -> {
            if (e != null) {
                Log.e(TAG, "WebSocket の通信で例外", e);
                return;
            }
            String msg = String.format("{\"type\":\"%s\",\"sdp\":\"%s\"}",
                    type.toString().toLowerCase(),
                    sdp.replaceAll("\r\n", "\\\\r\\\\n"));
            ws.send(msg);
            Log.d(TAG, "SDP=" + sdp);
        });
    }

    private class MySdpObserver implements SdpObserver {
        private boolean initiator = false;

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Log.d(TAG, "onCreateSuccess setLocalDescription");
            executor.execute(() -> {
                peerConnection.setLocalDescription(sdpObserver, sessionDescription);
                wsf.setCallback((e, ws) -> {
                    if (e != null) {
                        Log.e(TAG, "WebSocket の通信で例外", e);
                        return;
                    }
                    sendSdp(initiator ? SessionDescription.Type.OFFER : SessionDescription.Type.ANSWER, peerConnection.getLocalDescription().description);
                });
            });
        }

        @Override
        public void onSetSuccess() {
            Log.d(TAG, "onSetSuccess");
        }

        @Override
        public void onCreateFailure(String s) {
            Log.e(TAG, "onCreateFailure: " + s);
        }

        @Override
        public void onSetFailure(String s) {
            Log.e(TAG, "onSetFailure: " + s);
        }
    }

    private class DataChannelObserver implements DataChannel.Observer {
        @Override
        public void onBufferedAmountChange(long l) {
            Log.d(TAG, "DataChannel onBufferedAmountChange: " + l);
        }

        @Override
        public void onStateChange() {
            executor.execute(() -> Log.d(TAG, "DataChannel onStateChange. "));
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {
            if (!buffer.binary) {
                Log.d(TAG, buffer.data.toString());
                return;
            }
            int color = Color.rgb(buffer.data.get(), buffer.data.get(), buffer.data.get());
            runOnUiThread(() -> findViewById(R.id.textView).getRootView().setBackgroundColor(color));
        }
    }
}
