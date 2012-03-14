package info.isaksson.squeezedisplay;

import android.util.Log;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.client.BayeuxClient;
import org.cometd.client.transport.ClientTransport;
import org.cometd.client.transport.LongPollingTransport;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpDestination;
import org.eclipse.jetty.client.security.Realm;
import org.eclipse.jetty.client.security.RealmResolver;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class PlayerDiscoverer {
    public static interface PlayerManager {
        void registerPlayer(Player player);

        void discoveryFinished(String serverPort);

        void discoveryFailed(String serverPort);
    }

    static class Player {
        public String id;
        public String name;
        public String serverPort;

        public Player(String id, String name, String serverPort) {
            this.id = id;
            this.name = name;
            this.serverPort = serverPort;
        }

        public Player(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static void discoverPlayers(final String serverPort, final PlayerManager playerManager) {
        final HttpClient httpClient = new HttpClient();
        try {
            httpClient.start();
            httpClient.setRealmResolver(new RealmResolver() {
                @Override
                public Realm getRealm(String realm, HttpDestination httpDestination, String contextPath) throws IOException {
                    // Just return null for now as we don't support password protected servers yet
                    httpClient.setRealmResolver(null);
                    throw new IOException("Server authentication not supported");
                }
            });
            Map<String, Object> options = new HashMap<String, Object>();
            ClientTransport transport = LongPollingTransport.create(options, httpClient);

            final BayeuxClient client = new BayeuxClient("http://" + serverPort + "/cometd", transport);
            client.getChannel(Channel.META_HANDSHAKE).addListener(new ClientSessionChannel.MessageListener() {
                @Override
                public void onMessage(ClientSessionChannel clientSessionChannel, Message message) {
                    if (message.isSuccessful()) {
                        client.getChannel(Channel.META_HANDSHAKE).removeListener(this);
                        ClientSessionChannel.MessageListener serverListener = new ServerMessageListener(client, serverPort, playerManager);

                        client.getChannel("/" + client.getId() + "/slim/serverstatus").subscribe(serverListener);
                        Map<String, Object> requestResponse = new HashMap<String, Object>();

                        List request = new ArrayList();
                        request.add("");
                        request.add(Arrays.asList("serverstatus", "0", "50", "subscribe:60"));
                        requestResponse.put("request", request);
                        requestResponse.put("response", "/" + client.getId() + "/slim/serverstatus");
                        client.getChannel("/slim/subscribe").publish(requestResponse);
                    } else {
                        client.getChannel(Channel.META_HANDSHAKE).removeListener(this);
                        playerManager.discoveryFailed(serverPort);
                    }
                }
            });
            client.handshake();
        } catch (InterruptedException e) {
            Log.e(PlayerDiscoverer.class.getName(), "Player discovery interrupted");
            playerManager.discoveryFailed(serverPort);
        } catch (Exception e) {
            Log.e(PlayerDiscoverer.class.getName(), "Player discovery failure", e);
            playerManager.discoveryFailed(serverPort);
        }
    }

    private static class ServerMessageListener implements ClientSessionChannel.MessageListener {

        BayeuxClient client;
        String serverPort;
        private PlayerManager playerManager;

        public ServerMessageListener(BayeuxClient client, String serverPort, PlayerManager playerManager) {
            this.client = client;
            this.serverPort = serverPort;
            this.playerManager = playerManager;
        }

        @Override
        public void onMessage(ClientSessionChannel clientSessionChannel, Message message) {
            JsonNode msg = null;
            try {
                msg = new ObjectMapper().readTree(message.getJSON());
                msg = msg.get("data");
            } catch (IOException e) {
                Log.e(PlayerDiscoverer.class.getName(), "Failure when reading serverstatus response", e);
            }
            if (msg != null) {
                JsonNode playerNodes = msg.get("players_loop");
                for (JsonNode player : playerNodes) {
                    String id = player.get("playerid").getTextValue();
                    String name = player.get("name").getTextValue();
                    playerManager.registerPlayer(new Player(id, name, serverPort));
                }
                if (msg.has("other_players_loop")) {
                    JsonNode otherPlayerNodes = msg.get("other_players_loop");
                    for (JsonNode player : otherPlayerNodes) {
                        String id = player.get("playerid").getTextValue();
                        String name = player.get("name").getTextValue();
                        String serverUrl = player.get("serverurl").getTextValue();
                        try {
                            URL url = new URL(serverUrl);
                            playerManager.registerPlayer(new Player(id, name, url.getHost() + ":" + url.getPort()));
                        } catch (MalformedURLException e) {
                            // This should never happen
                            Log.w(PlayerDiscoverer.class.getName(), "Incorrect server url received, skipping: " + serverUrl);
                        }
                    }
                }
                /*
                if (msg.has("sn_players_loop")) {
                    JsonNode otherPlayerNodes = msg.get("sn_players_loop");
                    for (JsonNode player : otherPlayerNodes) {
                        String id = player.get("playerid").getTextValue();
                        String name = player.get("name").getTextValue();
                        playerManager.registerPlayer(new Player(id, name));
                    }
                }
                */
            }
            client.getChannel("/" + client.getId() + "/slim/serverstatus").unsubscribe();
            playerManager.discoveryFinished(serverPort);
        }
    }
}
