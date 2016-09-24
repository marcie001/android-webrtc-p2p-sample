package sample.webrtcp2psample;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.NumberPicker;
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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "MainActivity";

    private final static List<PeerConnection.IceServer> iceServers = Arrays.asList(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));

    private PeerConnection peerConnection;

    private Future<WebSocket> wsf;

    private DataChannel dataChannel;

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
                String textToReceiveSdp = "";
                try {
                    JSONObject message = new JSONObject(s);
                    String type = message.getString("type");
                    switch (type) {
                        case "offer":
                            Log.d(TAG, "Received offer ...");
                            peerConnection = prepareNewConnection();
                            peerConnection.setRemoteDescription(new SdpObserver() {
                                @Override
                                public void onCreateSuccess(SessionDescription sessionDescription) {

                                }

                                @Override
                                public void onSetSuccess() {

                                }

                                @Override
                                public void onCreateFailure(String s) {

                                }

                                @Override
                                public void onSetFailure(String s) {

                                }
                            }, new SessionDescription(SessionDescription.Type.OFFER, message.getString("sdp")));
                            break;
                        case "answer":
                            Log.d(TAG, "Received answer ...");
                            peerConnection.setRemoteDescription(new SdpObserver() {
                                @Override
                                public void onCreateSuccess(SessionDescription sessionDescription) {

                                }

                                @Override
                                public void onSetSuccess() {

                                }

                                @Override
                                public void onCreateFailure(String s) {

                                }

                                @Override
                                public void onSetFailure(String s) {

                                }
                            }, new SessionDescription(SessionDescription.Type.ANSWER, message.getString("sdp")));
                            break;
                        case "candidate":
                            Log.d(TAG, "Received ICE candidate ...");

                            peerConnection.addIceCandidate(new IceCandidate("gamedata", 0, message.getString("candidate")));
                            break;
                    }

                } catch (JSONException e) {
                    Log.e(TAG, "JSON の形式が不正です。", e);
                }


            });
        });


    }

    private PeerConnection prepareNewConnection() {
        if (!PeerConnectionFactory.initializeAndroidGlobals(this.getApplicationContext(), false, false, false, null)) {
            Log.e(TAG, "PeerConnectionFactory の初期化に失敗しました");
        }
        PeerConnectionFactory peerConnectionFactory = new PeerConnectionFactory();
        dataChannel = peerConnection.createDataChannel("gamedata", new DataChannel.Init());
        return peerConnectionFactory.createPeerConnection(iceServers, new MediaConstraints(), new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "onSignalingChange");
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "onIceConnectionChange");
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                Log.d(TAG, "onIceGatheringChange");
            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                String message = String.format("{ \"type\": \"candidate\", ice: \"%s\" }", iceCandidate.sdp);
                wsf.setCallback((e, ws) -> {
                    Log.d(TAG, "onIceCandedate");
                    if (e != null) {
                        Log.e(TAG, "onIceCandedate で例外が発生しました。", e);
                        return;
                    }
                    ws.send(message);
                });
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {

            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {

            }

            @Override
            public void onDataChannel(DataChannel dc) {
                dataChannel = dc;
                Log.d(TAG, "ondatachannel");
                dataChannel.registerObserver(new DataChannel.Observer() {
                    @Override
                    public void onStateChange() {
                        Log.d(TAG, "DataChannel onStateChange: " + dataChannel.state().name());
                    }

                    @Override
                    public void onMessage(DataChannel.Buffer buffer) {
                        String color = String.format("#%X%X%X", buffer.data.get(), buffer.data.get(), buffer.data.get());
                        Log.d(TAG, "color: " + color);
                    }
                });
            }

            @Override
            public void onRenegotiationNeeded() {

            }
        });
    }

    public void makeOffer(View view) {
        if (peerConnection != null) {
            return;
        }
        peerConnection = prepareNewConnection();
        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "createOffer onCreateSuccess");
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        Log.d(TAG, "setLocalDescription onCreateSuccess");
                        wsf.setCallback((e, ws) -> {
                            if (e != null) {
                                Log.e(TAG, "WebSocket の通信で例外", e);
                            }
                            ws.send(sessionDescription.description);
                            Log.d(TAG, "SDP=" + sessionDescription.description);
                        });
                    }

                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "setLocalDescription onSetSuccess");
                    }

                    @Override
                    public void onCreateFailure(String s) {
                        Log.d(TAG, "setLocalDescription onCreateFailure");
                    }

                    @Override
                    public void onSetFailure(String s) {
                        Log.d(TAG, "setLocalDescription onSetFailure");
                    }
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {
                Log.d(TAG, "createOffer onSetSuccess");
            }

            @Override
            public void onCreateFailure(String s) {
                Log.d(TAG, "createOffer onCreateFailure");
            }

            @Override
            public void onSetFailure(String s) {
                Log.d(TAG, "createOffer onSetFilure");
            }
        }, new MediaConstraints());
    }

    public void sendMessage(View view) {
        ByteBuffer color = ByteBuffer.allocate(3);
        color.put((byte) ((SeekBar) view.findViewById(R.id.red)).getProgress());
        color.put((byte) ((SeekBar) view.findViewById(R.id.green)).getProgress());
        color.put((byte) ((SeekBar) view.findViewById(R.id.blue)).getProgress());
        wsf.setCallback((e, ws) -> {
            ws.send(color.array());
        });
    }
}
