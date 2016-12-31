package albite.net.rtsp;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

public class RtspClient implements Closeable {

    private static final int CONNECTION_TIMEOUT_MS = 5000;

    private final InetSocketAddress mAddress;
    private final URI mUri;
    private String mSession;
    private int mSequenceNumber = 1;

    // PLAY
    // TEARDOWN
    public RtspClient(URI uri) {
        mUri = uri;
        mAddress = new InetSocketAddress(uri.getHost(), uri.getPort());
    }

    public Response transfer(Request request) throws IOException {
        return transfer(request, CONNECTION_TIMEOUT_MS);
    }

    public Response transfer(Request request, int connectTimeout) throws IOException {
        Socket socket = new Socket();

        try {
            socket.bind(null);
            socket.connect(mAddress, connectTimeout);

            // Write the request
            request.send(socket.getOutputStream());

            // Read the response
            Response response = Response.readFromStream(socket.getInputStream());

            // Validate the response
            if (response.getStatus().getKind() != Response.Status.Kind.SUCCESS) {
                throw new RtspException(response.getStatus(),
                        "Tranfser failed: " + response.getStatus().toString());
            }

            if (response.getSequenceNumber() != request.getSequenceNumber()) {
                throw new IOException("Response CSeq does not match");
            }

            // Generally, the session MUST be set only in SETUP,
            // however not all servers comply, and Wowza sets it early in
            // DESCRIBE. So we should allow for that.
            if (mSession != null && !mSession.equals(response.getSession())) {
                throw new IOException("The session was changed");
            } else if (mSession == null) {
                mSession = response.getSession();
            }

            return response;
        } finally {
            socket.close();
        }
    }

    private Request createRequest(Request.Method method, URI uri) {
        Request.Description desc = new Request.Description(method, uri);
        Request request = new Request(desc, mSequenceNumber++, mSession);
        // We do not support persistant connections
        request.setNonPersistent();
        return request;
    }

    public Request.Method[] requestOptions() throws IOException {
        Request request = createRequest(Request.Method.OPTIONS, mUri);
        Response response = transfer(request);

        // e.g.: Public: OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN
        String[] methodNames = response.getOptionalHeader(
                Message.HEADER_PUBLIC).split("\\s*,\\s*");

        if (methodNames != null) {
            Request.Method[] methods = new Request.Method[methodNames.length];
            for (int i = 0; i < methods.length; i++) {
                methods[i] = Request.Method.valueOf(methodNames[i]);
            }

            return methods;
        }

        throw new IOException("No public header found");
    }

    public Message requestDescribe(String[] accept) throws IOException {
        Request request = createRequest(Request.Method.DESCRIBE, mUri);
        request.setHeader(Message.HEADER_ACCEPT, String.join(", ", accept));
        Response response = transfer(request);

        // Validate the response
        if (response.getBody() == null) {
            throw new IOException("DESCRIBE reponse has no body");
        }

        return response;
    }

    public String requestDescribeSdp() throws IOException {
        Message message = requestDescribe(new String[]{Message.MIME_TYPE_SDP});
        String contentType = message.getOptionalHeader(Message.HEADER_CONTENT_TYPE);
        if (contentType != null && !contentType.equals(Message.MIME_TYPE_SDP)) {
            throw new IOException("Returned type for DESCRIBE is not SDP: " + contentType);
        }

        return new String(message.getBody());
    }

    public RtpTransport requestRtpUnicastSetup(int clientRtpPort) throws IOException {
        return requestRtpUnicastSetup(mUri, clientRtpPort);
    }

    public RtpTransport requestRtpUnicastSetup(URI uri, int clientRtpPort) throws IOException {
        RtpTransport clientTransport = new RtpTransport();
        clientTransport.setUnicast();
        clientTransport.setClientRtpPort(clientRtpPort);
        return requestSetup(uri, clientTransport);
    }

    public RtpTransport requestSetup(RtpTransport clientTransport) throws IOException {
        return requestSetup(mUri, clientTransport);
    }

    public RtpTransport requestSetup(URI uri, RtpTransport clientTransport) throws IOException {
        Request request = createRequest(Request.Method.SETUP, uri);
        request.setHeader(Message.HEADER_TRANSPORT, clientTransport.toString());
        Response response = transfer(request);
        String t = response.getRequiredHeader(Message.HEADER_TRANSPORT);
        return RtpTransport.fromString(t);
    }

    public void requestPlay() throws IOException {
        requestPlay(mUri);
    }

    public void requestPlay(URI uri) throws IOException {
        Request request = createRequest(Request.Method.PLAY, uri);
        // Note: Range not supported for now
        transfer(request);
    }

    private void requestTearDown() throws IOException {
        Request request = createRequest(Request.Method.TEARDOWN, mUri);
        transfer(request);
    }

    @Override
    public void close() throws IOException {
        requestTearDown();
    }
}
