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

import java.io.IOException;
import java.util.*;

public class TrackMonitor {
    Map<PlayerDiscoverer.Player, BayeuxClient> playerClients = new HashMap<PlayerDiscoverer.Player, BayeuxClient>();

    public void stopMonitoring(final PlayerDiscoverer.Player player) {
        if (player != null) {
            BayeuxClient client = playerClients.get(player);
            if (client != null) {
                client.getChannel("/" + client.getId() + "/slim/playerstatus/" + player.id).unsubscribe();
            }
            playerClients.remove(player);
        }
    }

    public void startMonitoring(final PlayerDiscoverer.Player player, final TrackListener trackListener) {
        stopMonitoring(player);
        HttpClient httpClient = new HttpClient();
        try {
            httpClient.start();

            Map<String, Object> options = new HashMap<String, Object>();
            ClientTransport transport = LongPollingTransport.create(options, httpClient);

            final BayeuxClient client = new BayeuxClient("http://" + player.serverPort + "/cometd", transport);
            playerClients.put(player, client);
            client.getChannel(Channel.META_CONNECT).addListener(new ClientSessionChannel.MessageListener() {
                @Override
                public void onMessage(ClientSessionChannel clientSessionChannel, Message message) {
                    if (!message.isSuccessful()) {
                        trackListener.currentTrackChanged(player, null);
                    }
                }
            });
            client.getChannel(Channel.META_HANDSHAKE).addListener(new ClientSessionChannel.MessageListener() {
                @Override
                public void onMessage(ClientSessionChannel clientSessionChannel, Message message) {
                    if (message.isSuccessful()) {
                        ClientSessionChannel.MessageListener playerListener = new PlayerMessageListener(client, player, trackListener);

                        client.getChannel("/" + client.getId() + "/slim/playerstatus/" + player.id).subscribe(playerListener);
                        List request = new ArrayList();
                        request.add(player.id);
                        request.add(Arrays.asList("status", "-", "10", "tags:GAJSldc", "subscribe:600"));
                        Map<String, Object> requestResponse = new HashMap<String, Object>();
                        requestResponse.put("request", request);
                        requestResponse.put("response", "/" + client.getId() + "/slim/playerstatus/" + player.id);
                        client.getChannel("/slim/subscribe").publish(requestResponse);
                    }
                }
            });
            client.handshake();
        } catch (InterruptedException e) {
            Log.w(PlayerDiscoverer.class.getName(), "Track monitoring interrupted");
        } catch (Exception e) {
            Log.w(PlayerDiscoverer.class.getName(), "Track monitoring failure", e);
        }
    }

    public static class Track {
        private String track;
        private String artist;
        private String album;
        private String cover;

        public Track(String artist, String album, String track, String cover) {
            this.artist = artist;
            this.album = album;
            this.track = track;
            this.cover = cover;
        }

        public String getTrack() {
            return track;
        }

        public String getArtist() {
            return artist;
        }

        public String getAlbum() {
            return album;
        }

        public String getCover() {
            return cover;
        }
    }

    public static interface TrackListener {
        void currentTrackChanged(PlayerDiscoverer.Player player, Track track);

        void playbackStopped(PlayerDiscoverer.Player player);
    }

    private static class PlayerMessageListener implements ClientSessionChannel.MessageListener {
        BayeuxClient client;
        PlayerDiscoverer.Player player;
        private TrackListener trackListener;

        public PlayerMessageListener(BayeuxClient client, PlayerDiscoverer.Player player, TrackListener trackListener) {
            this.client = client;
            this.player = player;
            this.trackListener = trackListener;
        }

        @Override
        public void onMessage(ClientSessionChannel clientSessionChannel, Message message) {
            JsonNode msg = null;
            try {
                msg = new ObjectMapper().readTree(message.getJSON());
                msg = msg.get("data");
            } catch (IOException e) {
                Log.w(TrackMonitor.class.getName(), "Failure when reading playerstatus response", e);
            }
            if (msg != null && msg.get("mode").getTextValue().equals("play") && msg.has("playlist_loop")) {
                JsonNode tracks = msg.get("playlist_loop");
                if (tracks.size() > 0) {
                    JsonNode jsonTrack = tracks.get(0);
                    String track = null;
                    String artist = null;
                    String album = null;
                    String cover = null;
                    if (jsonTrack.has("coverid")) {
                        cover = jsonTrack.get("coverid").getTextValue();
                    } else if (jsonTrack.has("artwork_track_id")) {
                        cover = jsonTrack.get("artwork_track_id").getTextValue();
                    }
                    if (jsonTrack.has("artist")) {
                        artist = jsonTrack.get("artist").getTextValue();
                    } else if (jsonTrack.has("band")) {
                        artist = jsonTrack.get("band").getTextValue();
                    } else if (jsonTrack.has("conductor")) {
                        artist = jsonTrack.get("conductor").getTextValue();
                    } else if (jsonTrack.has("composer")) {
                        artist = jsonTrack.get("composer").getTextValue();
                    } else if (jsonTrack.has("albumartist")) {
                        artist = jsonTrack.get("albumartist").getTextValue();
                    } else if (jsonTrack.has("trackartist")) {
                        artist = jsonTrack.get("trackartist").getTextValue();
                    }
                    if (jsonTrack.has("album")) {
                        album = jsonTrack.get("album").getTextValue();
                    }
                    if (jsonTrack.has("title")) {
                        track = jsonTrack.get("title").getTextValue();
                    }
                    Track trackObject = new Track(artist, album, track, cover);
                    trackListener.currentTrackChanged(player, trackObject);
                } else {
                    trackListener.currentTrackChanged(player, null);
                }
            } else if (msg != null && !msg.get("mode").getTextValue().equals("play")) {
                trackListener.currentTrackChanged(player, null);
            }
        }
    }
}
