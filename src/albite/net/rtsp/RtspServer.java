package albite.net.rtsp;

import albite.util.Log;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;

public class RtspServer implements Closeable {

    private static final String TAG = "RtspServer";
    private static final int SERVER_ACCEPT_TIMEOUT_MS = 5000;
    private static final int CLIENT_READ_TIMEOUT_MS = 15000;

    private static final boolean DEBUG = false;

    public interface Listener {

        void onRequestError(Throwable tr);

        byte[] onRequestDescription(String[] accept) throws IOException;

        String createSession();

        int[] onSetupUnicast(String session, URI uri, int rtpPort, int rtcpPort) throws IOException;

        void onPlay(String session, URI uri) throws IOException;

        void onTeardown(String session) throws IOException;
    }

    private static final Request.Method[] METHODS = {
        Request.Method.OPTIONS,
        Request.Method.DESCRIBE,
        Request.Method.SETUP,
        Request.Method.PLAY,
        Request.Method.TEARDOWN,};

    private static final String METHOD_NAMES;

    static {
        String[] names = new String[METHODS.length];

        for (int i = 0; i < METHODS.length; i++) {
            names[i] = METHODS[i].name();
        }

        METHOD_NAMES = String.join(", ", names);
    }

    private final Listener mListener;
    private final ServerSocket mServer;
    private final ServerThread mThread;

    public RtspServer(Listener listener, int port) throws IOException {
        mListener = listener;
        mServer = new ServerSocket(port, 1); // Handle one client per request
        mServer.setSoTimeout(SERVER_ACCEPT_TIMEOUT_MS);
        mThread = new ServerThread();
    }

    public void start() {
        mThread.start();
    }

    @Override
    public void close() throws IOException {
        try {
            mThread.requestExitAndWait();
        } catch (InterruptedException e) {
        }
    }

    public InetAddress getLocalAddress() {
        return mServer.getInetAddress();
    }

    public int getLocalPort() {
        return mServer.getLocalPort();
    }

    private Response handle(Request request) throws IOException {
        try {
            switch (request.getDescription().getMethod()) {
                case OPTIONS:
                    return handleOptions(request);

                case DESCRIBE:
                    return handleDescribe(request);

                case SETUP:
                    return handleSetup(request);

                case PLAY:
                    return handlePlay(request);

                case TEARDOWN:
                    return handleTeardown(request);
            }
        } catch (RtspException e) {
            // The listener wants to return a custom error
            Log.e(TAG, "Returning custom RTSP response: " + e.getStatus());
            return new Response(e.getStatus(), request.getSequenceNumber(), request.getSession());
        } catch (Throwable tr) {
            // Server error
            Log.e(TAG, "Failed handling request", tr);
            return Response.respondWithError(request);
        }

        // Unsupported request
        Response.Status status = Response.Status.NOT_IMPLEMENTED;
        return new Response(status, request.getSequenceNumber());
    }

    private Response handleOptions(Request request) throws IOException {
        Response response = Response.respondWithSuccess(request);

        // Fill in the headers for the options response
        response.setHeader(Message.HEADER_PUBLIC, METHOD_NAMES);

        return response;
    }

    private Response handleDescribe(Request request) throws IOException {
        // Parse the accepted types
        String[] accept = null;

        String s = request.getOptionalHeader(Message.HEADER_CONTENT_TYPE);
        if (s != null) {
            accept = s.split("\\s*,\\s*");
        }

        byte[] body = mListener.onRequestDescription(accept);
        return Response.respondWithSuccess(request, body);
    }

    private Response handleSetup(Request request) throws IOException {
        String t = request.getRequiredHeader(Message.HEADER_TRANSPORT);
        RtpTransport clientTransport = RtpTransport.fromString(t);

        if (!clientTransport.isUnicast()) {
            throw new IOException("Only unicast is supported");
        }

        if (!request.containsHeader(Message.HEADER_SESSION)) {
            // Create the session
            request.setHeader(Message.HEADER_SESSION, mListener.createSession());
        }

        int[] clientRtpPorts = clientTransport.getClientRtpPortPair();
        int[] serverRtpPorts = mListener.onSetupUnicast(
                request.getSession(),
                request.getDescription().getUri(),
                clientRtpPorts[0], clientRtpPorts[1]);

        // Prepare the server transport response
        RtpTransport serverTransport = new RtpTransport();
        serverTransport.setClientRtpPortPair(
                clientRtpPorts[0], clientRtpPorts[1]);
        serverTransport.setServerRtpPortPair(
                serverRtpPorts[0], serverRtpPorts[1]);
        serverTransport.setSource(mServer.getInetAddress());

        Response response = Response.respondWithSuccess(request);
        response.setHeader(Message.HEADER_TRANSPORT, serverTransport.toString());
        response.setHeader(TAG, TAG);
        return response;
    }

    private Response handlePlay(Request request) throws IOException {
        // TODO: Add support for ranges
        String session = request.getSession();
        mListener.onPlay(session, request.getDescription().getUri());
        return Response.respondWithSuccess(request);
    }

    private Response handleTeardown(Request request) throws IOException {
        String session = request.getSession();
        mListener.onTeardown(session);
        return Response.respondWithSuccess(request);
    }

    private class ServerThread extends Thread {

        private boolean mExitRequested = false;

        public void requestExitAndWait() throws IOException, InterruptedException {
            mExitRequested = true;
            join();
        }

        @Override
        public void run() {
            while (true) {
                Socket client;

                if (mExitRequested) {
                    try {
                        Log.i(TAG, "Exiting");
                        mServer.close();
                    } catch (IOException e) {
                        Log.w(TAG, "Failed closing server socket", e);
                    }

                    return;
                }

                // Using an accept without a timeout would be preferrable,
                // however, it is *OBVIOUS* that a ServerSocket would be
                // used from two threads, one that would block on accept()
                // the other that would call close() when the server socket
                // has to be closed.
                // However, the socket methods are NOT synchronized,
                // therefore explicit synchronization is needed.
                // Unfortunately, accept() does not release the lock,
                // meaning close() would not be called, which makes this
                // unusable.
                try {
                    client = mServer.accept();
                } catch (IOException e) {
                    continue;
                }

                try {
                    client.setSoTimeout(CLIENT_READ_TIMEOUT_MS);

                    // Read the request
                    Request request = Request.readFromStream(client.getInputStream());

                    if (DEBUG) {
                        Log.d(TAG, "Recieved request: " + request);
                    }

                    // Handle it to get a response
                    Response response = handle(request);

                    if (DEBUG) {
                        Log.d(TAG, "Sending response: " + response);
                    }

                    // Do not support persistent connections
                    response.setNonPersistent();

                    // Send the response
                    response.send(client.getOutputStream());

                    // FIXME: Wait for the client to read the response, before
                    // closing the socket, or it would get and EOF before it
                    // had read the data. Sounds fishy given this is TCP.
                    Thread.sleep(500);
                } catch (Throwable tr) {
                    Log.w(TAG, "Failed responding to request", tr);
                    mListener.onRequestError(tr);
                } finally {
                    try {
                        client.close();
                    } catch (IOException e) {
                        Log.w(TAG, "Failed closing client socket", e);
                    }
                }
            }
        }
    }
}
