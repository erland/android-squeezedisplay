package info.isaksson.squeezedisplay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.*;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;
import com.bugsense.trace.BugSenseHandler;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;

public class SqueezeDisplayActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener, ServerDiscoverer.ServerManager, PlayerDiscoverer.PlayerManager, TrackMonitor.TrackListener {
    private final static String API_KEY = "5f6fa3e464dd2797f5e780b19367aedb";
    PlayerDiscoverer.Player player = null;
    String wantedPlayer = null;
    List<ServerDiscoverer.Server> servers = new ArrayList<ServerDiscoverer.Server>();
    TrackMonitor trackMonitor = new TrackMonitor();
    Integer width;
    Integer height;
    Boolean debug = false;
    private static final Object syncObject = new Object();
    private List<String> serverDiscoveryInProgress = Collections.synchronizedList(new ArrayList<String>());
    private List<String> serverDiscoveryFailed = Collections.synchronizedList(new ArrayList<String>());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        debug = sharedPrefs.getBoolean("debug", false);
        setContentView(R.layout.main);
        //new SimpleEula(this).show();

        BugSenseHandler.setup(this, "e85d57cc");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        TextView statusView = (TextView) findViewById(R.id.status);
        if (debug) {
            statusView.setVisibility(View.VISIBLE);
        } else {
            statusView.setVisibility(View.INVISIBLE);
        }
        final TextView trackView = (TextView) findViewById(R.id.track);
        final TextView artistView = (TextView) findViewById(R.id.artist);
        final TextView albumView = (TextView) findViewById(R.id.album);
        trackView.setSelected(true);
        artistView.setSelected(true);
        albumView.setSelected(true);

        final ImageView imageView = (ImageView) findViewById(R.id.image);
        if (imageView.getViewTreeObserver().isAlive()) {
            imageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    width = imageView.getWidth();
                    height = imageView.getHeight();
                    imageView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }
            });
        }
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (debug) {
                    TextView statusView = (TextView) findViewById(R.id.status);
                    statusView.setText("Changing image because of user request...");
                }
                setNextImage();
            }
        });

        String player = sharedPrefs.getString("player", null);
        setupViewForPlayer(player);
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String preference) {
        if (preference.equals("player")) {
            String player = sharedPreferences.getString("player", null);
            setupViewForPlayer(player);
        } else if (preference.equals("debug")) {
            debug = sharedPreferences.getBoolean("debug", false);
            TextView statusView = (TextView) findViewById(R.id.status);
            statusView.setText("");
            if (debug) {
                statusView.setVisibility(View.VISIBLE);
            } else {
                statusView.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void setupViewForPlayer(String player) {
        if (this.player == null || !this.player.id.equals(player)) {
            if (this.player != null) {
                trackMonitor.stopMonitoring(this.player);
            }
            if (nextImageTimer != null) {
                nextImageTimer.cancel();
            }
            ImageView imageView = (ImageView) findViewById(R.id.image);
            if (imageView.getDrawable() != null) {
                imageView.setAnimation(null);
                imageView.setImageDrawable(null);
            }
            TextView artistView = (TextView) findViewById(R.id.artist);
            artistView.setText(" ");
            TextView albumView = (TextView) findViewById(R.id.album);
            albumView.setText(" ");
            TextView trackView = (TextView) findViewById(R.id.track);
            trackView.setText("Finding player...");
            this.player = null;
            this.wantedPlayer = player;
            detectServers();
        }
    }

    @Override
    public void discoveryFinished() {
        handleFinishedDiscovery();
    }

    @Override
    public void registerServer(ServerDiscoverer.Server server) {
        servers.add(server);
        if (player == null) {
            detectPlayer(server.getIpAddress() + ":" + server.getPort());
        }
    }

    @Override
    public void discoveryFailed(String serverPort) {
        serverDiscoveryInProgress.remove(serverPort);
        serverDiscoveryFailed.add(serverPort);
        handleFinishedDiscovery();
    }

    @Override
    public void discoveryFinished(String serverPort) {
        serverDiscoveryInProgress.remove(serverPort);
        handleFinishedDiscovery();
    }

    private void handleFinishedDiscovery() {
        if (player == null && serverDiscoveryInProgress.size() == 0) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ImageView imageView = (ImageView) findViewById(R.id.image);
                    if (imageView.getDrawable() != null) {
                        imageView.setAnimation(null);
                        imageView.setImageDrawable(null);
                    }
                    TextView artistView = (TextView) findViewById(R.id.artist);
                    artistView.setText(" ");
                    TextView albumView = (TextView) findViewById(R.id.album);
                    albumView.setText(" ");
                    TextView trackView = (TextView) findViewById(R.id.track);
                    trackView.setText("No players found");
                }
            });
        }
    }

    @Override
    public void registerPlayer(final PlayerDiscoverer.Player player) {
        if ((wantedPlayer == null && this.player == null) || player.id.equals(wantedPlayer)) {
            this.player = player;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ImageView imageView = (ImageView) findViewById(R.id.image);
                    if (imageView.getDrawable() != null) {
                        imageView.setImageDrawable(null);
                    }
                    TextView artistView = (TextView) findViewById(R.id.artist);
                    artistView.setText(" ");
                    TextView albumView = (TextView) findViewById(R.id.album);
                    albumView.setText("Connecting to");
                    TextView trackView = (TextView) findViewById(R.id.track);
                    trackView.setText(player.name);
                }
            });
            trackMonitor.startMonitoring(player, this);
        }
    }

    private List<String> images = Collections.synchronizedList(new ArrayList<String>());
    private TrackMonitor.Track currentTrack = null;
    private int currentArtistImage = 0;
    private Timer nextImageTimer = null;

    @Override
    public void playbackStopped(final PlayerDiscoverer.Player player) {
        if (this.player != null && this.player.id.equals(player.id)) {
            if (nextImageTimer != null) {
                nextImageTimer.cancel();
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView artistView = (TextView) findViewById(R.id.artist);
                    artistView.setText(" ");
                    TextView albumView = (TextView) findViewById(R.id.album);
                    albumView.setText(player.name);
                    TextView trackView = (TextView) findViewById(R.id.track);
                    trackView.setText("Stopped/Paused");
                    ImageView imageView = (ImageView) findViewById(R.id.image);
                    imageView.setImageDrawable(null);
                    View creditView = findViewById(R.id.credit);
                    creditView.setVisibility(View.INVISIBLE);
                }
            });
        }
    }

    @Override
    public void currentTrackChanged(final PlayerDiscoverer.Player player, final TrackMonitor.Track track) {
        if (this.player != null && this.player.id.equals(player.id)) {
            if (debug) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView statusView = (TextView) findViewById(R.id.status);
                        statusView.setText("Changing image because of track changed...");
                    }
                });
            }
            if (currentTrack == null || track == null || currentTrack.getArtist() == null || track.getArtist() == null || !currentTrack.getArtist().equals(track.getArtist()) || images.size() == 0) {
                this.currentTrack = track;
                synchronized (syncObject) {
                    this.currentArtistImage = -1;
                    this.images.clear();
                }
                if (track != null && track.getArtist() != null) {
                    try {
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                        String order = sharedPreferences.getString("ordering", "dateadded");
                        URL imageUrl = new URL("http://ws.audioscrobbler.com/2.0/?method=artist.getimages&artist=" + URLEncoder.encode(track.getArtist(), "utf-8") + "&order=" + order + "&limit=100&format=json&api_key=" + API_KEY);
                        URLConnection connection = imageUrl.openConnection();
                        connection.setRequestProperty("User-Agent", "SqueezeDisplay/1.0");
                        JsonNode json = new ObjectMapper().readTree(connection.getInputStream());
                        if (json.has("images")) {
                            JsonNode images = json.get("images").get("image");
                            if (images != null && images.isArray()) {
                                for (JsonNode image : images) {
                                    String imageURL = image.get("sizes").get("size").get(0).get("#text").getTextValue();
                                    Integer width = Integer.valueOf(image.get("sizes").get("size").get(0).get("width").getTextValue());
                                    Integer height = Integer.valueOf(image.get("sizes").get("size").get(0).get("height").getTextValue());
                                    if (this.width / 4 < width || this.height / 4 < height) {
                                        this.images.add(imageURL);
                                    }
                                }
                            } else if (images != null) {
                                String imageURL = images.get("sizes").get("size").get(0).get("#text").getTextValue();
                                Integer width = Integer.valueOf(images.get("sizes").get("size").get(0).get("width").getTextValue());
                                Integer height = Integer.valueOf(images.get("sizes").get("size").get(0).get("height").getTextValue());
                                if (this.width / 4 < width || this.height / 4 < height) {
                                    this.images.add(imageURL);
                                }
                            }
                        }
                        Collections.shuffle(this.images);
                    } catch (MalformedURLException e) {
                        Log.w(SqueezeDisplayActivity.class.getName(), "Invalid url when connecting to LastFM", e);
                    } catch (JsonProcessingException e) {
                        Log.w(SqueezeDisplayActivity.class.getName(), "Error when processing answer from LastFM", e);
                    } catch (IOException e) {
                        Log.w(SqueezeDisplayActivity.class.getName(), "Error when retrieving data from LastFM", e);
                    }
                }
                if (track != null && this.images.size() == 0 && track.getCover() != null) {
                    this.images.add("http://" + player.serverPort + "/music/" + track.getCover() + "/cover");
                }
            } else {
                this.currentTrack = track;
            }
            displayTrackInfo(false);
        }
    }

    private boolean updateTextItems() {
        boolean updated = false;
        TextView trackView = (TextView) findViewById(R.id.track);
        TextView artistView = (TextView) findViewById(R.id.artist);
        TextView albumView = (TextView) findViewById(R.id.album);
        if (currentTrack != null) {
            if (!trackView.getText().equals(currentTrack.getTrack())) {
                trackView.setText(currentTrack.getTrack());
            }
            if (!artistView.getText().equals(currentTrack.getArtist())) {
                artistView.setText(currentTrack.getArtist());
                updated = true;
            }
            if (!albumView.getText().equals(currentTrack.getAlbum())) {
                albumView.setText(currentTrack.getAlbum());
                currentArtistImage = -1;
                updated = true;
            }
        } else {
            trackView.setText(" ");
            artistView.setText(" ");
            albumView.setText(" ");
        }
        return updated;
    }

    private void setNextImage() {
        String image = null;
        synchronized (syncObject) {
            boolean useAlbumCover = false;
            if (currentArtistImage < 0) {
                currentArtistImage = (int) (images.size() * Math.random());
                if (currentTrack != null && currentTrack.getCover() != null) {
                    useAlbumCover = true;
                }
            }
            currentArtistImage++;
            if (currentArtistImage >= images.size()) {
                currentArtistImage = 0;
            }
            if (images.size() > 0 && !useAlbumCover) {
                image = images.get(currentArtistImage);
            } else if (useAlbumCover) {
                image = "http://" + player.serverPort + "/music/" + currentTrack.getCover() + "/cover";
            }
        }
        if (image != null) {
            new ImageRetrieveTask(width, height, image, player).execute();
        }
    }

    private class ImageRetrieveTask extends AsyncTask<Void, Void, Drawable> {
        Integer width;
        Integer height;
        String image;
        PlayerDiscoverer.Player player;

        public ImageRetrieveTask(Integer width, Integer height, String image, PlayerDiscoverer.Player player) {
            this.width = width;
            this.height = height;
            this.image = image;
            this.player = player;
            if (nextImageTimer != null) {
                nextImageTimer.cancel();
            }
        }

        @Override
        protected Drawable doInBackground(Void... voids) {
            try {
                String url;
                if (image.contains(player.serverPort)) {
                    url = image + "_" + width + "x" + height + "_p.png";
                } else {
                    url = "http://mysqueezebox.com/public/imageproxy?u=" + URLEncoder.encode(image, "utf-8") + "&w=" + width + "&h=" + height + "&m=p";
                }
                InputStream is = (InputStream) new URL(url).getContent();
                System.gc();
                try {
                    return Drawable.createFromStream(is, "src");
                } catch (OutOfMemoryError e) {
                    System.gc();
                    return null;
                }
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(final Drawable drawable) {
            View creditView = findViewById(R.id.credit);
            if (image.contains(player.serverPort) && !debug) {
                creditView.setVisibility(View.INVISIBLE);
            } else {
                creditView.setVisibility(View.VISIBLE);
            }
            final ImageView imageView = (ImageView) findViewById(R.id.image);
            if (drawable != null) {
                if (imageView.getDrawable() != null && (imageView.getAnimation() == null || !imageView.getAnimation().hasStarted())) {
                    final AlphaAnimation fadeOut = new AlphaAnimation(1.00f, 0.00f);
                    fadeOut.setDuration(1000);
                    fadeOut.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            AlphaAnimation fadeIn = new AlphaAnimation(0.00f, 1.00f);
                            fadeIn.setDuration(1000);
                            if (debug) {
                                fadeIn.setAnimationListener(new Animation.AnimationListener() {
                                    @Override
                                    public void onAnimationStart(Animation animation) {
                                        TextView statusView = (TextView) findViewById(R.id.status);
                                        statusView.setText("Fading in image");
                                    }

                                    @Override
                                    public void onAnimationEnd(Animation animation) {
                                        TextView statusView = (TextView) findViewById(R.id.status);
                                        statusView.setText(image);
                                    }

                                    @Override
                                    public void onAnimationRepeat(Animation animation) {
                                    }
                                });
                            }
                            imageView.setAnimation(fadeIn);
                            imageView.setImageDrawable(drawable);
                            System.gc();
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {
                        }
                    });
                    imageView.startAnimation(fadeOut);
                } else {
                    AlphaAnimation fadeIn = new AlphaAnimation(0.00f, 1.00f);
                    fadeIn.setDuration(1000);
                    if (debug) {
                        fadeIn.setAnimationListener(new Animation.AnimationListener() {
                            @Override
                            public void onAnimationStart(Animation animation) {
                                TextView statusView = (TextView) findViewById(R.id.status);
                                statusView.setText("Fading in image");
                            }

                            @Override
                            public void onAnimationEnd(Animation animation) {
                                TextView statusView = (TextView) findViewById(R.id.status);
                                statusView.setText(image);
                            }

                            @Override
                            public void onAnimationRepeat(Animation animation) {
                            }
                        });
                    }
                    imageView.setAnimation(fadeIn);
                    imageView.setImageDrawable(drawable);
                    System.gc();
                }
            } else {
                imageView.setImageDrawable(null);
            }
            if (images.size() > 1) {
                nextImageTimer = new Timer();
                if (imageView.getDrawable() != null) {
                    nextImageTimer.schedule(new TimerTask() {
                        public void run() {
                            if (debug) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        TextView statusView = (TextView) findViewById(R.id.status);
                                        statusView.setText("Changing image because of normal timer...");
                                    }
                                });
                            }
                            displayTrackInfo(true);
                        }
                    }, 15000);
                } else {
                    nextImageTimer.schedule(new TimerTask() {
                        public void run() {
                            if (debug) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        TextView statusView = (TextView) findViewById(R.id.status);
                                        statusView.setText("Changing image because of image loading problem...");
                                    }
                                });
                            }
                            displayTrackInfo(true);
                        }
                    }, 500);
                }
            }
        }
    }

    private void displayTrackInfo(final Boolean forcedImageChange) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (player != null) {
                    if (images.size() == 0) {
                        updateTextItems();
                        ImageView imageView = (ImageView) findViewById(R.id.image);
                        imageView.setImageDrawable(null);
                    } else {
                        if (updateTextItems()) {
                            final ImageView imageView = (ImageView) findViewById(R.id.image);
                            if (imageView.getDrawable() != null) {
                                final AlphaAnimation fadeOut = new AlphaAnimation(1.00f, 0.00f);
                                fadeOut.setDuration(1000);
                                fadeOut.setAnimationListener(new Animation.AnimationListener() {
                                    @Override
                                    public void onAnimationStart(Animation animation) {
                                    }

                                    @Override
                                    public void onAnimationEnd(Animation animation) {
                                        if (imageView.getAnimation() == fadeOut) {
                                            imageView.setImageDrawable(null);
                                            System.gc();
                                        }
                                    }

                                    @Override
                                    public void onAnimationRepeat(Animation animation) {
                                    }
                                });
                                imageView.startAnimation(fadeOut);
                            }
                            setNextImage();
                        } else if (forcedImageChange) {
                            setNextImage();
                        }
                    }
                }
            }
        });
    }

    private void detectPlayer(String serverPort) {
        if (player == null) {
            serverDiscoveryInProgress.add(serverPort);
            PlayerDiscoverer.discoverPlayers(serverPort, this);
        }
    }

    public void detectServers() {
        servers.clear();
        serverDiscoveryInProgress.clear();
        serverDiscoveryFailed.clear();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String serverPort = sharedPreferences.getString("server", null);
        if (serverPort != null && serverPort.trim().length() > 0) {
            if (!serverPort.contains(":")) {
                serverPort += ":9000";
            }
            detectPlayer(serverPort);
        }
        try {
            ServerDiscoverer.start(getBroadcastAddress(), this);
        } catch (IOException e) {
            Log.e(SqueezeDisplayActivity.class.getName(), "Failed to initiate server discovery", e);
        }
    }

    private InetAddress getBroadcastAddress() throws IOException {
        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp = wifi.getDhcpInfo();

        int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
        byte[] quads = new byte[4];
        for (int k = 0; k < 4; k++)
            quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
        return InetAddress.getByAddress(quads);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings_title:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            trackMonitor.stopMonitoring(player);
        }
        super.onDestroy();
    }
}
