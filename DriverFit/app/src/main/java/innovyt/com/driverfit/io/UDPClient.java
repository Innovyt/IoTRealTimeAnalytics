package innovyt.com.driverfit.io;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import innovyt.com.driverfit.utils.Constants;

public class UDPClient {
    private String serverAddress;
    private String serverPort;

    public UDPClient(String ipAddress, String port) {
        serverAddress = ipAddress;
        serverPort = port;
    }

    public void send(final byte[] buf, int length) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DatagramSocket socket = new DatagramSocket();
                    InetAddress local = InetAddress.getByName(serverAddress);
                    int bufLength = buf.length;
                    DatagramPacket packet = new DatagramPacket(buf, bufLength, local, Integer.parseInt(serverPort));
                    socket.send(packet);
                } catch (SocketException e) {
                    Log.e(Constants.TAG, e.getMessage());
                } catch (UnknownHostException e) {
                    Log.e(Constants.TAG, e.getMessage());
                } catch (IOException e) {
                    Log.e(Constants.TAG, e.getMessage());
                } catch (Exception e) {
                    Log.e(Constants.TAG, e.getMessage());
                }

            }
        });
        thread.start();

    }
}
