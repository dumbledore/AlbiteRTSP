package albite.net.rtsp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class RtpTransport {

    public static final String RTP_DEFAULT = "RTP/AVP";
    public static final String RTP_UDP = "RTP/AVP/UDP";
    public static final String RTP_TCP = "RTP/AVP/TCP";

    public static final String PARAMETER_CLIENT_PORT = "client_port";
    public static final String PARAMETER_SERVER_PORT = "server_port";
    public static final String PARAMETER_SOURCE = "source";
    public static final String PARAMETER_UNICAST = "unicast";

    private final Map<String, String> mParameters = new HashMap<>();

    public RtpTransport() {
    }

    public static RtpTransport fromString(String s) throws IOException {
        String[] params = s.split("\\s*;\\s*");

        if (params.length == 0) {
            throw new IOException("No parameters found in transport: " + s);
        }

        // Lower transport
        String transport = params[0];

        if (transport.equals(RTP_TCP)) {
            throw new IOException("RTP over TCP is not supported");
        }

        if (!transport.equals(RTP_DEFAULT) && !transport.equals(RTP_UDP)) {
            throw new IOException("Transport type is not valid: " + s);
        }

        RtpTransport rtp = new RtpTransport();

        // We have at least one param
        for (int i = 1; i < params.length; i++) {
            String p = params[i];
            int pos = p.indexOf('=');
            if (pos < 0) {
                // Not a pair parameter
                rtp.mParameters.put(p, null);
            } else {
                // Parse the pair
                String key = p.substring(0, pos);
                String value = p.substring(pos + 1);
                rtp.mParameters.put(key, value);
            }
        }

        return rtp;
    }

    @Override
    public String toString() {
        String[] params = new String[mParameters.size() + 1];
        params[0] = RTP_UDP;

        int i = 1;
        for (Map.Entry<String, String> entry : mParameters.entrySet()) {
            if (entry.getValue() == null) {
                params[i++] = entry.getKey();
            } else {
                params[i++] = entry.getKey() + "=" + entry.getValue();
            }
        }

        return String.join(";", params);
    }

    public boolean isUnicast() {
        return mParameters.containsKey(PARAMETER_UNICAST);
    }

    public void setUnicast() {
        mParameters.put(PARAMETER_UNICAST, null);
    }

    private int[] getRtpPortPair(String key) {
        if (!mParameters.containsKey(key)) {
            return null;
        }

        String p = mParameters.get(key);

        int pos = p.indexOf('-');

        if (pos < 0) {
            throw new IllegalArgumentException("Parameter is not a port pair: " + p);
        }

        // The pair is RTP-RTCP, so we need the first part
        int rtp = Integer.parseInt(p.substring(0, pos));
        int rtcp = Integer.parseInt(p.substring(pos + 1));

        return new int[]{rtp, rtcp};
    }

    public int[] getClientRtpPortPair() {
        return getRtpPortPair(PARAMETER_CLIENT_PORT);
    }

    public int[] getServerRtpPortPair() {
        return getRtpPortPair(PARAMETER_SERVER_PORT);
    }

    private void setRtpPort(String key, int port) {
        setRtpPortPair(key, port, port + 1);
    }

    private void setRtpPortPair(String key, int rtp, int rtcp) {
        mParameters.put(key, String.format("%d-%d", rtp, rtcp));
    }

    public void setClientRtpPort(int port) {
        setRtpPort(PARAMETER_CLIENT_PORT, port);
    }

    public void setClientRtpPortPair(int rtp, int rtcp) {
        setRtpPortPair(PARAMETER_CLIENT_PORT, rtp, rtcp);
    }

    public void setServerRtpPort(int port) {
        setRtpPort(PARAMETER_SERVER_PORT, port);
    }

    public void setServerRtpPortPair(int rtp, int rtcp) {
        setRtpPortPair(PARAMETER_SERVER_PORT, rtp, rtcp);
    }

    public InetAddress getSource() throws UnknownHostException {
        if (mParameters.containsKey(PARAMETER_SOURCE)) {
            return InetAddress.getByName(mParameters.get(PARAMETER_SOURCE));
        }

        return null;
    }

    public void setSource(InetAddress source) {
        mParameters.put(PARAMETER_SOURCE, source.toString());
    }
}
