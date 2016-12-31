package albite.net.rtsp.tests;

import albite.net.rtsp.Message;
import albite.net.rtsp.Request;
import albite.net.rtsp.RtpTransport;
import albite.net.rtsp.RtspClient;
import albite.net.rtsp.RtspServer;
import albite.util.Log;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class RtspTest {

    private static final String TAG = "RtspTest";

    private static final String SDP_DESCRIPTION
            = "v=0\r\n"
            + "c=IN IP4 127.0.0.1\r\n"
            + "m=application 0 RTP/AVP 33\r\n";

    private static final int SERVER_RTP_PORT = 1234;

    private final RtspServer.Listener mListener = new RtspServer.Listener() {
        private static final String TAG = "RtspTest.Listener";

        @Override
        public void onRequestError(Throwable tr) {
            Log.w(TAG, "Request failed", tr);
        }

        @Override
        public byte[] onRequestDescription(String[] accept) throws IOException {
            if (accept != null) {
                List<String> list = Arrays.asList(accept);
                if (!list.contains(Message.MIME_TYPE_SDP)) {
                    throw new IOException("Only SDP is supported");
                }
            }

            // A very simple SDP using RTP format 33 (mp2t (MPEG2 TS) AV)
            // See http://www.iana.org/assignments/rtp-parameters/rtp-parameters.xhtml
            return SDP_DESCRIPTION.getBytes(Message.CHARSET);
        }

        @Override
        public String createSession() {
            // Note: In general, there should be a check for uniqueness,
            // i.e. client session IDs should not clash.
            return UUID.randomUUID().toString();
        }

        @Override
        public int[] onSetupUnicast(String session, URI uri, int rtpPort, int rtcpPort) throws IOException {
            Log.i(TAG, String.format("[%s] Received unicast client setup (%d, %d) for %s",
                    session, rtpPort, rtcpPort, uri));

            // For simplicity, imagine the server has the same ports
            return new int[]{rtpPort, rtcpPort};
        }

        @Override
        public void onPlay(String session, URI uri) throws IOException {
            Log.i(TAG, String.format("[%s] Received PLAY for %s", session, uri));
        }

        @Override
        public void onTeardown(String session) throws IOException {
            Log.i(TAG, String.format("[%s] Teardown", session));
        }
    };

    public RtspTest() {
    }

    private void handleOptions(RtspClient client) throws IOException {
        List<Request.Method> methods = Arrays.asList(client.requestOptions());

        // Print the methods
        for (Request.Method m : methods) {
            Log.i(TAG, m.name());
        }

        // Make sure we have all needed methods
        Request.Method[] requiredMethods = {
            Request.Method.OPTIONS,
            Request.Method.DESCRIBE,
            Request.Method.SETUP,
            Request.Method.PLAY,
            Request.Method.TEARDOWN,};

        for (Request.Method m : requiredMethods) {
            assertTrue(
                    "Required method not supported by server: " + m.name(), methods.contains(m));
        }
    }

    private void handleDescribe(RtspClient client) throws IOException {
        String description = client.requestDescribeSdp();
        assertNotNull("SDP description is null", description);
        Log.i(TAG, "SDP description:\n" + description);
    }

    private void handleSetup(RtspClient client) throws IOException {
        // Get the server transport
        RtpTransport serverTransport
                = client.requestRtpUnicastSetup(SERVER_RTP_PORT);

        // Extract the RTP/RTCP port pair
        int[] serverPortPair = serverTransport.getClientRtpPortPair();

        Log.i(TAG, String.format("Received server setup: (%d, %d)",
                serverPortPair[0], serverPortPair[1]));
    }

    @Test
    public void testFullSession() throws IOException, URISyntaxException {
        // Port 0 would mean automatically selected from free ports
        RtspServer server = new RtspServer(mListener, 0);
        URI uri = new URI("rtsp://localhost:" + server.getLocalPort());
        server.start();

        try {
            Log.i(TAG, "Communicating with " + uri);
            RtspClient client = new RtspClient(uri);
            try {
                handleOptions(client);
                handleDescribe(client);
                handleSetup(client);
                client.requestPlay();

                try {
                    // Wait for a few seconds, as though we are playing
                    Log.i(TAG, "Playing...");
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                }
            } finally {
                client.close();
            }
        } finally {
            server.close();
        }
    }
}
