package com.wisedesign.maestro.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.wisedesign.maestro.model.LiveCommand;
import com.wisedesign.maestro.network.client.MusicianWebSocketClient;
import com.wisedesign.maestro.network.discovery.NsdHelper;

import java.net.InetAddress;

/**
 * ChantreActivity — Vue du Chantre (chanteur principal / animateur de culte).
 *
 * Affichage prioritaire :
 *  1. Titre du chant (grand, lisible à distance)
 *  2. Gamme courante avec indication de transposition
 *  3. BPM avec indicateur visuel de tempo
 *  4. Paroles + accords complets (chord sheet défilable)
 *  5. Bandeau d'alerte Maestro (rouge/orange selon urgence)
 *  6. Position dans la setlist (ex: "3 / 8")
 *
 * Différences par rapport à MusicianActivity :
 *  - Paroles plus grandes (le chantre doit lire aisément)
 *  - Indicateur de "chant suivant" pour anticiper
 *  - Mode "confiance" : affiche l'intro/structure du chant
 */
public class ChantreActivity extends AppCompatActivity
        implements NsdHelper.NsdEventListener,
                   MusicianWebSocketClient.MusicianClientListener {

    // ─── Vues ────────────────────────────────────────────────────────────────

    private TextView tvConnectionStatus;
    private TextView tvSongTitle;
    private TextView tvSongPosition;      // "3 / 8"
    private TextView tvCurrentKey;
    private TextView tvCurrentBpm;
    private TextView tvChordSheet;        // Paroles + accords
    private TextView tvNextSongHint;      // "Prochain : Ô Sainte Présence"
    private TextView tvAlertBanner;       // Bandeau alerte Maestro
    private View bannerAlert;
    private View btnScrollTop;

    // ─── Réseau ──────────────────────────────────────────────────────────────

    private NsdHelper nsdHelper;
    private MusicianWebSocketClient wsClient;

    // ─── Cycle de vie ────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chantre);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("🎤 Chantre");
        }

        initViews();
        connectToMaestro();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nsdHelper != null) nsdHelper.tearDown();
        if (wsClient != null) wsClient.disconnect();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            RoleSelectionActivity.clearSavedRole(this);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ─── Initialisation ──────────────────────────────────────────────────────

    private void initViews() {
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvSongTitle        = findViewById(R.id.tvSongTitle);
        tvSongPosition     = findViewById(R.id.tvSongPosition);
        tvCurrentKey       = findViewById(R.id.tvCurrentKey);
        tvCurrentBpm       = findViewById(R.id.tvCurrentBpm);
        tvChordSheet       = findViewById(R.id.tvChordSheet);
        tvNextSongHint     = findViewById(R.id.tvNextSongHint);
        tvAlertBanner      = findViewById(R.id.tvAlertBanner);
        bannerAlert        = findViewById(R.id.bannerAlert);
        btnScrollTop       = findViewById(R.id.btnScrollTop);

        if (bannerAlert != null) bannerAlert.setVisibility(View.GONE);

        if (btnScrollTop != null) {
            btnScrollTop.setOnClickListener(v -> {
                // ScrollView.smoothScrollTo(0, 0) — à lier dans le layout
            });
        }
    }

    private void connectToMaestro() {
        updateStatus("🔍 Recherche du Maestro...");
        nsdHelper = new NsdHelper(this, this);
        nsdHelper.startDiscovery();
    }

    // ─── NsdEventListener ────────────────────────────────────────────────────

    @Override
    public void onMaestroFound(InetAddress host, int port, String name) {
        runOnUiThread(() -> updateStatus("Maestro trouvé — Connexion..."));
        nsdHelper.stopDiscovery();
        wsClient = new MusicianWebSocketClient(host, port, this);
        wsClient.connectAsync();
    }

    @Override
    public void onMaestroLost(String serviceName) {
        runOnUiThread(() -> {
            updateStatus("❌ Maestro déconnecté");
            Toast.makeText(this, "Le Maestro s'est déconnecté", Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onServiceRegistered(String registeredName) {}

    @Override
    public void onNsdError(String operation, int errorCode) {
        runOnUiThread(() -> updateStatus("Erreur réseau (" + errorCode + ")"));
    }

    // ─── MusicianClientListener ──────────────────────────────────────────────

    @Override
    public void onConnected() {
        updateStatus("✅ Connecté au Maestro");
    }

    @Override
    public void onDisconnected(String reason) {
        updateStatus("⚠️ Déconnecté — " + reason);
    }

    @Override
    public void onCommandReceived(LiveCommand command) {
        switch (command.action) {

            case LiveCommand.ACTION_CHANGE_SONG:
            case LiveCommand.ACTION_CURRENT_STATE:
                updateSongDisplay(command);
                break;

            case LiveCommand.ACTION_CHANGE_KEY:
                if (tvCurrentKey != null) {
                    tvCurrentKey.setText("🎵 " + command.key);
                    flashView(tvCurrentKey);
                }
                break;

            case LiveCommand.ACTION_CHANGE_BPM:
                if (tvCurrentBpm != null)
                    tvCurrentBpm.setText(command.bpm + " ♩");
                break;

            case LiveCommand.ACTION_ALERT:
                showAlertBanner(command.message, command.level);
                break;

            case LiveCommand.ACTION_SETLIST_LOADED:
                Toast.makeText(this,
                    "📋 Setlist : " + command.eventName, Toast.LENGTH_SHORT).show();
                break;

            case LiveCommand.ACTION_NEXT_SONG:
            case LiveCommand.ACTION_PREV_SONG:
                // L'état mis à jour arrivera dans CHANGE_SONG
                break;
        }
    }

    @Override
    public void onConnectionStateChanged(MusicianWebSocketClient.ConnectionState state) {
        String label;
        switch (state) {
            case CONNECTING:   label = "🔄 Connexion...";   break;
            case CONNECTED:    label = "✅ Connecté";        break;
            case RECONNECTING: label = "🔄 Reconnexion..."; break;
            default:           label = "❌ Déconnecté";
        }
        updateStatus(label);
    }

    @Override
    public void onLatencyMeasured(long latencyMs) {
        // Optionnel pour le chantre
    }

    // ─── Helpers UI ──────────────────────────────────────────────────────────

    private void updateSongDisplay(LiveCommand cmd) {
        if (tvSongTitle != null && cmd.title != null)
            tvSongTitle.setText(cmd.title);

        if (tvCurrentKey != null && cmd.key != null)
            tvCurrentKey.setText("🎵 " + cmd.key);

        if (tvCurrentBpm != null && cmd.bpm > 0)
            tvCurrentBpm.setText(cmd.bpm + " ♩");

        if (tvChordSheet != null && cmd.chordSheet != null)
            tvChordSheet.setText(cmd.chordSheet);

        // Position dans la setlist
        if (tvSongPosition != null && cmd.index >= 0 && cmd.totalSongs > 0)
            tvSongPosition.setText((cmd.index + 1) + " / " + cmd.totalSongs);
    }

    private void showAlertBanner(String message, String level) {
        if (tvAlertBanner == null || bannerAlert == null) return;
        tvAlertBanner.setText(message);

        int bgColor = LiveCommand.LEVEL_URGENT.equals(level)
            ? getColor(R.color.alert_urgent)
            : getColor(R.color.alert_info);
        bannerAlert.setBackgroundColor(bgColor);
        bannerAlert.setVisibility(View.VISIBLE);

        // Disparaît après 6 secondes
        bannerAlert.postDelayed(() -> {
            bannerAlert.animate().alpha(0f).setDuration(300)
                .withEndAction(() -> {
                    bannerAlert.setVisibility(View.GONE);
                    bannerAlert.setAlpha(1f);
                }).start();
        }, 6000);
    }

    private void flashView(View view) {
        view.animate().alpha(0.2f).setDuration(100)
            .withEndAction(() -> view.animate().alpha(1f).setDuration(250).start())
            .start();
    }

    private void updateStatus(String status) {
        if (tvConnectionStatus != null) tvConnectionStatus.setText(status);
    }
}
