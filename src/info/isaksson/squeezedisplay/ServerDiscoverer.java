package info.isaksson.squeezedisplay;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class ServerDiscoverer {

    public static interface ServerManager {
        void registerServer(Server server);

        void discoveryFinished();
    }

    public static class Server {
        private String ipAddress;
        private int port;
        private String name;
        private String version;

        public Server(String ipAddress, int port, String name, String version) {
            this.ipAddress = ipAddress;
            this.port = port;
            this.name = name;
            this.version = version;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public int getPort() {
            return port;
        }
    }

    private static final int DISCOVERY_PORT = 3483;
    private static final int TIMEOUT_MS = 5000;
    private static final String DISCOVERY_PACKET = "eIPAD\0NAME\0JSON\0VERS\0";
    private static Thread discoveryThread;

    public static void start(final InetAddress broadcastAddress, final ServerManager serverManager) throws IOException {
        if (discoveryThread != null) {
            try {
                discoveryThread.join();
            } catch (InterruptedException e) {
                // If it's interrupted we handle it the same way as if it's finished
                Log.d(ServerDiscoverer.class.getName(), "Interrupted discovery");
            }
            discoveryThread = null;
        }
        discoveryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                DatagramSocket socket = null;
                try {
                    socket = sendDiscoveryRequest(broadcastAddress);
                    listenForResponses(socket, serverManager);
                } catch (IOException e) {
                    Log.e(ServerDiscoverer.class.getName(), "Failure when discover servers", e);
                } finally {
                    if (socket != null) {
                        socket.close();
                    }
                    discoveryThread = null;
                }
            }
        });
        discoveryThread.start();
    }

    private static DatagramSocket sendDiscoveryRequest(InetAddress broadcastAddress) throws IOException {
        DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT);
        socket.setBroadcast(true);
        socket.setSoTimeout(TIMEOUT_MS);
        DatagramPacket packet = new DatagramPacket(DISCOVERY_PACKET.getBytes(),
                DISCOVERY_PACKET.length(), broadcastAddress, DISCOVERY_PORT);
        socket.send(packet);
        return socket;
    }

    private static void listenForResponses(DatagramSocket socket, ServerManager serverManager) throws IOException {
        byte[] buf = new byte[256];
        try {
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                buf = packet.getData();
                int i = 1;
                int l;
                String t;
                String v;

                if ((char) buf[0] == 'E') {
                    String ipAddress = packet.getAddress().getHostAddress();
                    String version = null;
                    String name = null;
                    int port = -1;

                    while (i < packet.getLength()) {
                        t = new String(buf, i, 4);
                        l = (int) buf[i + 4];
                        v = new String(buf, i + 5, l);
                        i = i + 5 + l;
                        if (t.equals("JSON")) {
                            try {
                                port = Integer.parseInt(v);
                            } catch (NumberFormatException e) {
                                e.printStackTrace();
                            }
                        } else if (t.equals("NAME")) {
                            name = v;
                        } else if (t.equals("VERS")) {
                            version = v;
                        }
                    }

                    if (port > 0) {
                        serverManager.registerServer(new Server(ipAddress, port, name, version));
                    }
                }
            }
        } catch (SocketTimeoutException e) {
            // No more servers, let's return to caller
            serverManager.discoveryFinished();
        }
    }
}