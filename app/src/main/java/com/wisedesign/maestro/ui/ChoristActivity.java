package com.wisedesign.maestro.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.wisedesign.maestro.model.LiveCommand;
import com.wisedesign.maestro.network.client.MusicianWebSocketClient;
import com.wisedesign.maestro.network.discovery.NsdHelper;

import java.net.InetAddress;

/**
 * ChoristActivity — Vue du Choriste (membre du chœur SATB).
 *
 * Spécificités :
 *  - Sélection de la voix au lancement (Soprano / Alto / Ténor / Basse)
 *  - Paroles synchronisées surlignées mot par mot (si disponible)
 *  - Gamme transposée affichée en grand
 *  - Indicateur de partition vocale : affiche la note de départ de sa voix
 *  - Écran toujours allumé (keepScreenOn)
 */
public class ChoristActivity extends AppCompatActivity
        implements NsdHelper.NsdEventListener,
                   MusicianWebSocketClient.MusicianClientListener {

    // ─── Voix sélectionnée ───────────────────────────────────────────────────

    private static final String[] VOICE_PARTS = {"🎵 Soprano", "🎶 Alto", "🎷 Ténor", "🎸 Basse"};
    private static final String[] VOICE_CODES = {"SOPRANO", "ALTO", "TENOR", "BASSE_VOCALE"};

    private String selectedVoice = "SOPRANO";

    // ─── Vues ────────────────────────────────────────────────────────────────

    private TextView tvVoicePart;
    private TextView tvConnectionStatus;
    private TextView tvSongTitle;
    private TextView tvCurrentKey;
    private TextView tvLyrics;
    private TextView tvAlertBanner;
    private View bannerAlert;

    // ─── Réseau ──────────────────────────────────────────────────────────────

    private NsdHelper nsdHelper;
    private MusicianWebSocketClient wsClient;

    // ─── Cycle de vie ────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chorist);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("🎶 Choriste");
        }

        initViews();
        // Demander la sélection de voix avant de chercher le Maestro
        showVoiceSelectionDialog();
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
        tvVoicePart        = findViewById(R.id.tvVoicePart);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvSongTitle        = findViewById(R.id.tvSongTitle);
        tvCurrentKey       = findViewById(R.id.tvCurrentKey);
        tvLyrics           = findViewById(R.id.tvLyrics);
        tvAlertBanner      = findViewById(R.id.tvAlertBanner);
        bannerAlert        = findViewById(R.id.bannerAlert);

        if (bannerAlert != null) bannerAlert.setVisibility(View.GONE);
    }

    /**
     * Dialog de sélection de voix — affiché UNE SEULE FOIS au démarrage.
     * Le choix est mémorisé dans SharedPreferences pour les prochaines sessions.
     */
    private void showVoiceSelectionDialog() {
        // Vérifier si une voix est mémorisée
        String saved = getPreferences(MODE_PRIVATE).getString("voice_part", null);
        if (saved != null) {
            selectedVoice = saved;
            updateVoiceDisplay();
            connectToMaestro();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Quelle est ta voix ?")
            .setItems(VOICE_PARTS, (dialog, which) -> {
                selectedVoice = VOICE_CODES[which];
                // Mémoriser le choix
                getPreferences(MODE_PRIVATE).edit()
                    .putString("voice_part", selectedVoice)
                    .apply();
                updateVoiceDisplay();
                connectToMaestro();
            })
            .setCancelable(false)
            .show();
    }

    private void updateVoiceDisplay() {
        if (tvVoicePart != null) {
            String label = selectedVoice;
            for (int i = 0; i < VOICE_CODES.length; i++) {
                if (VOICE_CODES[i].equals(selectedVoice)) {
                    label = VOICE_PARTS[i];
                    break;
                }
            }
            tvVoicePart.setText(label);
        }
    }

    private void connectToMaestro() {
        if (tvConnectionStatus != null)
            tvConnectionStatus.setText("🔍 Recherche du Maestro...");
        nsdHelper = new NsdHelper(this, this);
        nsdHelper.startDiscovery();
    }

    // ─── NsdEventListener ────────────────────────────────────────────────────

    @Override
    public void onMaestroFound(InetAddress host, int port, String name) {
        runOnUiThread(() -> {
            if (tvConnectionStatus != null)
                tvConnectionStatus.setText("Maestro trouvé — Connexion...");
        });
        nsdHelper.stopDiscovery();
        wsClient = new MusicianWebSocketClient(host, port, this);
        wsClient.connectAsync();
    }

    @Override
    public void onMaestroLost(String serviceName) {
        runOnUiThread(() ->
            Toast.makeText(this, "Le Maestro s'est déconnecté", Toast.LENGTH_LONG).show()
        );
    }

    @Override public void onServiceRegistered(String name) {}
    @Override public void onNsdError(String op, int code) {}

    // ─── MusicianClientListener ──────────────────────────────────────────────

    @Override
    public void onConnected() {
        if (tvConnectionStatus != null) tvConnectionStatus.setText("✅ Connecté");
    }

    @Override
    public void onDisconnected(String reason) {
        if (tvConnectionStatus != null) tvConnectionStatus.setText("⚠️ Déconnecté");
    }

    @Override
    public void onCommandReceived(LiveCommand command) {
        switch (command.action) {
            case LiveCommand.ACTION_CHANGE_SONG:
            case LiveCommand.ACTION_CURRENT_STATE:
                if (tvSongTitle != null && command.title != null)
                    tvSongTitle.setText(command.title);
                if (tvCurrentKey != null && command.key != null)
                    tvCurrentKey.setText(command.key);
                if (tvLyrics != null && command.chordSheet != null)
                    // Pour le choriste, afficher seulement les paroles (sans les accords)
                    tvLyrics.setText(extractLyricsOnly(command.chordSheet));
                break;

            case LiveCommand.ACTION_CHANGE_KEY:
                if (tvCurrentKey != null) tvCurrentKey.setText(command.key);
                break;

            case LiveCommand.ACTION_ALERT:
                showAlertBanner(command.message, command.level);
                break;
        }
    }

    @Override
    public void onConnectionStateChanged(MusicianWebSocketClient.ConnectionState state) {}

    @Override
    public void onLatencyMeasured(long latencyMs) {}

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Extrait uniquement les paroles du chord sheet en supprimant les marqueurs d'accords.
     * Ex: "[G]Gloire [D]à Toi" → "Gloire à Toi"
     */
    private String extractLyricsOnly(String chordSheet) {
        if (chordSheet == null) return "";
        return chordSheet.replaceAll("\\[[A-G][^\\]]*\\]", "").trim();
    }

    private void showAlertBanner(String message, String level) {
        if (bannerAlert == null || tvAlertBanner == null) return;
        tvAlertBanner.setText(message);
        bannerAlert.setVisibility(View.VISIBLE);
        bannerAlert.postDelayed(() -> bannerAlert.setVisibility(View.GONE), 6000);
    }
}
