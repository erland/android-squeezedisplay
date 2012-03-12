package info.isaksson.squeezedisplay;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.client.BayeuxClient;
import org.cometd.client.transport.ClientTransport;
import org.cometd.client.transport.LongPollingTransport;
import org.eclipse.jetty.client.HttpClient;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class PlayerDiscoverer {
    public static interface PlayerManager {
        void registerPlayer(Player player);
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
        HttpClient httpClient = new HttpClient();
        try {
            httpClient.start();

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
                    }
                }
            });
            client.handshake();
        } catch (InterruptedException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
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
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
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
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                    }
                }
                if (msg.has("sn_players_loop")) {
                    JsonNode otherPlayerNodes = msg.get("sn_players_loop");
                    for (JsonNode player : otherPlayerNodes) {
                        String id = player.get("playerid").getTextValue();
                        String name = player.get("name").getTextValue();
                        playerManager.registerPlayer(new Player(id, name));
                    }
                }
            }
            client.getChannel("/" + client.getId() + "/slim/serverstatus").unsubscribe();
        }
    }
}
