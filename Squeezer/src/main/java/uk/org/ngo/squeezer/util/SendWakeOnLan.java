package uk.org.ngo.squeezer.util;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Sends a Wake-on-LAN (WOL) message.
 */
public class SendWakeOnLan {
    private static final String TAG = SendWakeOnLan.class.getSimpleName();

    /**
     * UDP port to broadcast WOL message to.
     */
    private static final int WOL_PORT = 9;
    public static final String BROADCAST_ADDRESS = "255.255.255.255";

    public static void sendWakeOnLan(byte[] mac) {

        // The WOL message payload is 6 bytes of all 255 followed by sixteen repetitions of the
        // target computer's 48-bit MAC address, for a total of 102 bytes.
        byte[] request = new byte[6 + 16 * mac.length];

        try(DatagramSocket socket = new DatagramSocket()) {
            InetAddress broadcastAddress = InetAddress.getByName(BROADCAST_ADDRESS);
            DatagramPacket packet = new DatagramPacket(request, request.length, broadcastAddress, WOL_PORT);

            Arrays.fill(request, 0, 6, (byte) 255);
            for (int p = 6; p < request.length; p += mac.length) {
                System.arraycopy(mac, 0, request, p, mac.length);
            }

            socket.setBroadcast(true);
            socket.send(packet);
        } catch (SocketException e) {
            Log.e(TAG, "SocketException", e);
            // TODO remote logging
        } catch (UnknownHostException e) {
            Log.e(TAG, "UnknownHostException", e);
            // TODO remote logging
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
            // TODO remote logging
        }
    }

}
