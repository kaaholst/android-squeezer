package uk.org.ngo.squeezer.util;

import android.content.Context;

import androidx.annotation.NonNull;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import uk.org.ngo.squeezer.R;

/**
 * Scans the local network for servers.
 */
public class ScanNetworkTask implements Runnable {
    private static final String TAG = ScanNetworkTask.class.getSimpleName();

    private final ScanNetworkCallback callback;
    private final int defaultHttpPort;
    private final Handler uiThreadHandler = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;

    /**
     * Map server names to IP addresses.
     */
    private final TreeMap<String, String> mServerMap = new TreeMap<>();

    /**
     * UDP port to broadcast discovery requests to.
     */
    private static final int DISCOVERY_PORT = 3483;

    /**
     * Maximum time to wait between discovery attempts (ms).
     */
    public static final int DISCOVERY_ATTEMPT_TIMEOUT = 1400;

    public ScanNetworkTask(Context context, ScanNetworkCallback callback) {
        this.callback = callback;
        defaultHttpPort = context.getResources().getInteger(R.integer.DefaultHttpPort);
    }

    /**
     * Discover Squeeze servers on the local network.
     * <p>
     * Do this by sending an UDP broadcasts to port 3483 and wait approximately
     * DISCOVERY_ATTEMPT_TIMEOUT for responses. Squeeze servers are supposed to listen for this,
     * and respond with a packet that starts 'E' and some information about the server in type,
     * value pairs
     * <p>
     * The server name is the section with the type "NAME".
     * The http port is the section with the type "JSON".
     * <p>
     * Map the name to an IP address:port and store in mServerMap for later use.
     * <p>
     * See the Slim::Networking::Discovery module in Squeeze server for more details.
     */
    @Override
    public void run() {
        DatagramSocket socket = null;

        // UDP broadcast data that causes Squeeze servers to reply. The
        // format is 'e', followed by null-terminated tags that indicate the
        // data to return.
        //
        // The Squeeze server uses the size of the request packet to
        // determine the size of the response packet.

        byte[] request = {
                'e', // 'existence' ?
                'I', 'P', 'A', 'D', 0, // Include IP address
                'N', 'A', 'M', 'E', 0, // Include server name
                'J', 'S', 'O', 'N', 0, // Include server port
        };
        byte[] data = new byte[512];
        System.arraycopy(request, 0, data, 0, request.length);

        try {
            InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
            boolean timedOut;
            socket = new DatagramSocket();
            DatagramPacket discoveryPacket = new DatagramPacket(data, data.length,
                    broadcastAddress, DISCOVERY_PORT);

            byte[] buf = new byte[512];
            DatagramPacket responsePacket = new DatagramPacket(buf, buf.length);

            socket.setSoTimeout(DISCOVERY_ATTEMPT_TIMEOUT);
            socket.send(discoveryPacket);
            timedOut = false;
            while (!timedOut) {
                if (cancelled) {
                    break;
                }
                try {
                    socket.receive(responsePacket);
                    if (buf[0] == (byte) 'E') {
                        Map<String, String> discover = parseDiscover(responsePacket.getLength(), responsePacket.getData());

                        String name = discover.get("NAME");
                        if (name != null) {
                            String host = responsePacket.getAddress().getHostAddress();
                            String port = discover.containsKey("JSON") ? discover.get("JSON") : String.valueOf(defaultHttpPort);
                            mServerMap.put(name, host + ':' + port);
                        }
                    }
                } catch (IOException e) {
                    timedOut = true;
                }
            }

        } catch (SocketException e) {
            // new DatagramSocket(3483)
        } catch (UnknownHostException e) {
            // InetAddress.getByName()
            Log.e(TAG, "UnknownHostException", e);
            // TODO remote logging Util.crashlyticsLogException(e);
        } catch (IOException e) {
            // socket.send()
            Log.e(TAG, "IOException", e);
            // TODO remote logging Util.crashlyticsLogException(e);
        } finally {
            if (socket != null) {
                socket.close();
            }
        }

        // For testing that multiple servers are handled correctly.
        // mServerMap.put("Dummy", "127.0.0.1");
        if (!cancelled) {
            uiThreadHandler.post(() -> callback.onScanFinished(mServerMap));
        }
    }

    /**
     * Parse a Squeezeserver broadcast response.
     * <p>
     * The response buffer consists of a literal 'E' followed by 1 or more packed tuples that
     * follow the format {4-byte-type}{1-byte-length}{[length]-bytes-values}.
     * <p>
     * See the code in the server's Slim/Networking/Discovery::gotTLVRequest() method for more
     * details on how the response is constructed.
     *
     * @return A map with type, value from the response packet. May be empty if the response is
     *      truncated
     */
    @NonNull
    static Map<String, String> parseDiscover(int packetLength, byte[] buffer) {
        Map<String, String> result = new HashMap<>();

        int i = 1;
        while (i < packetLength) {
            // Check if the buffer is truncated by the server, and bail out if it is.
            if (i + 5 > packetLength) {
                break;
            }

            // Extract type and skip over it
            String type = new String(buffer, i, 4);
            i += 4;

            // Read the length, and skip over it.& 0xff to it is an unsigned byte
            int length = buffer[i++] & 0xFF;

            // Check if the buffer is truncated by the server, and bail out if it is.
            if (i + length > packetLength) {
                break;
            }

            // Extract the value and skip over it.
            String value = new String(buffer, i, length);
            i += length;

            result.put(type, value);
        }

        return result;
    }

    public void cancel() {
        cancelled = true;
    }

    public interface ScanNetworkCallback {
        void onScanFinished(Map<String, String> servers);
    }
}
