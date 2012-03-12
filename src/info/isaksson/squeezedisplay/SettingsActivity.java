package info.isaksson.squeezedisplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

public class SettingsActivity extends PreferenceActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener, ServerDiscoverer.ServerManager, PlayerDiscoverer.PlayerManager {

    Set<PlayerDiscoverer.Player> players = new TreeSet<PlayerDiscoverer.Player>(new Comparator<PlayerDiscoverer.Player>() {
        @Override
        public int compare(PlayerDiscoverer.Player player1, PlayerDiscoverer.Player player2) {
            if (player1.id.equals(player2.id)) {
                return 0;
            } else {
                return player1.name.compareTo(player2.name);
            }
        }
    });
    List<ServerDiscoverer.Server> servers = new ArrayList<ServerDiscoverer.Server>();

    @Override
    protected void onResume() {
        super.onResume();
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
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
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String preference) {
        if (preference.equals("server")) {
            ListPreference playerPreference = (ListPreference) findPreference("player");
            playerPreference.setEntries(new String[0]);
            playerPreference.setEntryValues(new String[0]);
            playerPreference.setEnabled(false);
            EditTextPreference selectedServerPreference = (EditTextPreference) findPreference("server");
            if (selectedServerPreference.getText() != null && selectedServerPreference.getText().length() > 0) {
                fillPlayerList(selectedServerPreference.getText());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        ListPreference playerPreference = (ListPreference) findPreference("player");
        playerPreference.setEntries(new String[0]);
        playerPreference.setEntryValues(new String[0]);
        playerPreference.setEnabled(false);
        players.clear();
        fillServerList();
        EditTextPreference selectedServerPreference = (EditTextPreference) findPreference("server");
        if (selectedServerPreference.getText() != null && selectedServerPreference.getText().length() > 0) {
            fillPlayerList(selectedServerPreference.getText());
        }
    }

    @Override
    public void registerServer(ServerDiscoverer.Server server) {
        servers.add(server);
        String serverPort = server.getIpAddress() + ":" + server.getPort();
        fillPlayerList(serverPort);
    }

    public void fillServerList() {
        try {
            servers.clear();
            ServerDiscoverer.start(getBroadcastAddress(), this);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    private void fillPlayerList(final String serverPort) {
        PlayerDiscoverer.discoverPlayers(serverPort, this);
    }

    @Override
    public void registerPlayer(PlayerDiscoverer.Player player) {
        players.add(player);
        final ListPreference playerPreference = (ListPreference) findPreference("player");
        if (playerPreference != null) {
            final CharSequence entries[] = new String[players.size()];
            final CharSequence entryValues[] = new String[players.size()];
            int i = 0;
            for (PlayerDiscoverer.Player aPlayer : players) {
                entries[i] = aPlayer.name;
                entryValues[i] = aPlayer.id;
                i++;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    playerPreference.setEntries(entries);
                    playerPreference.setEntryValues(entryValues);
                    playerPreference.setEnabled(true);
                }
            });
        }
    }
}