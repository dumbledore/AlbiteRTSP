package albite.net.rtsp.sample;

import albite.net.rtsp.Message;
import albite.net.rtsp.RtspServer;
import albite.util.Log;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class RtspWithFFMpeg {

    private static final String FFMPEG_PREPARE
            = "ffmpeg -i video.mp4 -strict -2 -c:v mpeg4 -c:a aac -ac 2 -b:v 4000k -b:a 128k -f mpegts video.mpegts";

    private static final String FFMPEG_STREAM
            = "ffmpeg -re -i video.mpegts -c:v copy -c:a copy -f rtp_mpegts rtp://127.0.0.1:";

    private static final String FFMPEG_PLAY
            = "ffplay rtsp://127.0.0.1:";

    public static final String TAG = "RtspFFMpeg";

    // Hard-coded to 0, to ease the client in port selection
    private static final int RTP_PORT = 1234;

    private static final String SDP_DESCRIPTION
            = "v=0\r\n"
            + "c=IN IP4 127.0.0.1\r\n"
            + "m=application " + RTP_PORT + " RTP/AVP 33\r\n";

    private static class Listener implements RtspServer.Listener {

        private static final String TAG = "RtspFFMpeg.Listener";

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

            Log.i(TAG, "Start ffmpeg streaming");
            Log.i(TAG, FFMPEG_STREAM + rtpPort);

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

    public static void main(String[] args) {
        Log.i(TAG, "Prepare the video for streaming like so:");
        Log.i(TAG, FFMPEG_PREPARE);

        try {
            stream(30);
        } catch (Throwable tr) {
            Log.e(TAG, "Streaming failed", tr);
        }
    }

    private static void stream(int duration) throws IOException, URISyntaxException, InterruptedException {
        // Port 0 would mean automatically selected from free ports
        RtspServer server = new RtspServer(new Listener(), 0);
        server.start();

        try {
            Log.i(TAG, "Start RTSP playback:");
            Log.i(TAG, FFMPEG_PLAY + server.getLocalPort());
            Log.i(TAG, "Waiting " + duration + "s...");
            Thread.sleep(duration * 1000);
        } finally {
            server.close();
        }
    }
}
