package com.wisedesign.maestro.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.wisedesign.maestro.R;
import com.wisedesign.maestro.model.LiveCommand;
import com.wisedesign.maestro.network.client.MusicianWebSocketClient;
import com.wisedesign.maestro.network.discovery.NsdHelper;

import java.net.InetAddress;

/**
 * Activity côté Musicien.
 *
 * Flux complet :
 *   1. startDiscovery() via NsdHelper → trouve automatiquement le Maestro
 *   2. onMaestroFound() → crée MusicianWebSocketClient et connecte
 *   3. onCommandReceived() → met à jour l'UI en temps réel
 *
 * L'UI affiche :
 *   - Chant actuel (titre + gamme + BPM)
 *   - Accords (chord sheet)
 *   - État de connexion + latence
 *   - Indicateur de métronome
 *   - Alertes du Maestro
 *
 * LAYOUT ATTENDU (res/layout/activity_musician.xml) :
 *   tvConnectionStatus, tvSongTitle, tvSongKey, tvBpm,
 *   tvChordSheet, tvAlertMessage, tvLatency, layoutChords
 */
public class MusicianActivity extends AppCompatActivity
        implements NsdHelper.NsdEventListener,
                   MusicianWebSocketClient.MusicianClientListener {

    private static final String TAG = "MusicianActivity";

    // ─── Composants UI ───────────────────────────────────────────────────────

    private TextView tvConnectionStatus;
    private TextView tvSongTitle;
    private TextView tvSongKey;
    private TextView tvBpm;
    private TextView tvChordSheet;
    private TextView tvAlertMessage;
    private TextView tvLatency;
    private View layoutChords;

    // ─── Composants réseau ───────────────────────────────────────────────────

    private NsdHelper nsdHelper;
    private MusicianWebSocketClient wsClient;

    // ─── État courant ────────────────────────────────────────────────────────

    private String currentSongTitle = "";
    private String currentKey = "";
    private int currentBpm = 0;

    // ─── Cycle de vie ────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_musician);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("🎸 Musicien");
        }

        initViews();
        startDiscovery();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Nettoyage propre — essentiel pour économiser batterie/réseau
        if (nsdHelper != null) nsdHelper.tearDown();
        if (wsClient != null) wsClient.disconnect();
    }

    // ─── Initialisation UI ───────────────────────────────────────────────────

    private void initViews() {
        // Dans votre layout XML, liez ces IDs
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvSongTitle        = findViewById(R.id.tvSongTitle);
        tvSongKey          = findViewById(R.id.tvSongKey);
        tvBpm              = findViewById(R.id.tvBpm);
        tvChordSheet       = findViewById(R.id.tvChordSheet);
        tvAlertMessage     = findViewById(R.id.tvAlertMessage);
        tvLatency          = findViewById(R.id.tvLatency);

        updateConnectionStatus("🔍 Recherche du Maestro...");
    }

    // ─── Découverte NSD ──────────────────────────────────────────────────────

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            RoleSelectionActivity.clearSavedRole(this);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startDiscovery() {
        nsdHelper = new NsdHelper(this, this);
        nsdHelper.startDiscovery();
        Log.i(TAG, "Découverte NSD démarrée.");
    }

    // ─── NsdEventListener ────────────────────────────────────────────────────

    @Override
    public void onMaestroFound(InetAddress host, int port, String name) {
        Log.i(TAG, "Maestro trouvé ! IP : " + host.getHostAddress() + " Port : " + port);
        runOnUiThread(() -> updateConnectionStatus("Maestro trouvé — Connexion en cours..."));

        // Arrêter la découverte — on a notre Maestro
        nsdHelper.stopDiscovery();

        // Créer et connecter le client WebSocket
        wsClient = new MusicianWebSocketClient(host, port, this);
        wsClient.connectAsync();
    }

    @Override
    public void onMaestroLost(String serviceName) {
        Log.w(TAG, "Maestro perdu : " + serviceName);
        runOnUiThread(() -> {
            updateConnectionStatus("❌ Maestro déconnecté — Reconnexion...");
            Toast.makeText(this, "Le Maestro s'est déconnecté", Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onServiceRegistered(String registeredName) {
        // Non utilisé côté musicien
    }

    @Override
    public void onNsdError(String operation, int errorCode) {
        Log.e(TAG, "Erreur NSD [" + operation + "] : " + errorCode);
        runOnUiThread(() -> updateConnectionStatus("Erreur réseau — Réessai..."));
    }

    // ─── MusicianClientListener — Gestion des commandes du Maestro ──────────

    @Override
    public void onConnected() {
        // Déjà sur Main Thread (dispatché par MusicianWebSocketClient)
        updateConnectionStatus("✅ Connecté au Maestro");
        Log.i(TAG, "WebSocket connecté au Maestro.");
    }

    @Override
    public void onDisconnected(String reason) {
        updateConnectionStatus("⚠️ Déconnecté — " + reason);
    }

    /**
     * Point central de mise à jour de l'UI.
     * Appelé sur le Main Thread à chaque commande du Maestro.
     */
    @Override
    public void onCommandReceived(LiveCommand command) {
        Log.d(TAG, "Commande reçue : " + command.action);

        switch (command.action) {

            case LiveCommand.ACTION_CHANGE_SONG:
            case LiveCommand.ACTION_CURRENT_STATE:
                handleChangeSong(command);
                break;

            case LiveCommand.ACTION_CHANGE_KEY:
                handleChangeKey(command);
                break;

            case LiveCommand.ACTION_CHANGE_BPM:
                handleChangeBpm(command);
                break;

            case LiveCommand.ACTION_METRONOME_START:
                handleMetronomeStart(command);
                break;

            case LiveCommand.ACTION_METRONOME_STOP:
                handleMetronomeStop();
                break;

            case LiveCommand.ACTION_ALERT:
                handleAlert(command);
                break;

            case LiveCommand.ACTION_PERFORMANCE_START:
                Toast.makeText(this, "🎵 Performance démarrée !", Toast.LENGTH_SHORT).show();
                break;

            case LiveCommand.ACTION_PERFORMANCE_END:
                Toast.makeText(this, "Performance terminée.", Toast.LENGTH_SHORT).show();
                break;

            case LiveCommand.ACTION_SETLIST_LOADED:
                Toast.makeText(this,
                    "Setlist chargée : " + command.eventName +
                    " (" + command.totalSongs + " chants)",
                    Toast.LENGTH_SHORT).show();
                break;
        }
    }

    @Override
    public void onConnectionStateChanged(MusicianWebSocketClient.ConnectionState state) {
        String label;
        switch (state) {
            case CONNECTING:    label = "🔄 Connexion...";     break;
            case CONNECTED:     label = "✅ Connecté";          break;
            case RECONNECTING:  label = "🔄 Reconnexion...";   break;
            case DISCONNECTED:  label = "❌ Déconnecté";       break;
            default:            label = state.name();
        }
        updateConnectionStatus(label);
    }

    @Override
    public void onLatencyMeasured(long latencyMs) {
        if (tvLatency != null) {
            tvLatency.setText("Latence : " + latencyMs + "ms");
        }
    }

    // ─── Handlers de commandes spécifiques ───────────────────────────────────

    private void handleChangeSong(LiveCommand command) {
        currentSongTitle = command.title != null ? command.title : currentSongTitle;
        currentKey       = command.key != null ? command.key : currentKey;
        currentBpm       = command.bpm > 0 ? command.bpm : currentBpm;

        if (tvSongTitle != null) tvSongTitle.setText(currentSongTitle);
        if (tvSongKey   != null) tvSongKey.setText("Ton. : " + currentKey);
        if (tvBpm       != null) tvBpm.setText(currentBpm + " BPM");

        // Afficher les accords
        if (tvChordSheet != null && command.chordSheet != null) {
            tvChordSheet.setText(command.chordSheet);
            if (layoutChords != null) layoutChords.setVisibility(View.VISIBLE);
        }

        // Feedback visuel — vibration brève (optionnel)
        // Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        // if (v != null) v.vibrate(80);
    }

    private void handleChangeKey(LiveCommand command) {
        currentKey = command.key;
        if (tvSongKey != null) {
            tvSongKey.setText("Ton. : " + currentKey);
            // Animation flash pour attirer l'attention du musicien
            tvSongKey.animate().alpha(0.3f).setDuration(100)
                .withEndAction(() -> tvSongKey.animate().alpha(1f).setDuration(200).start())
                .start();
        }
    }

    private void handleChangeBpm(LiveCommand command) {
        currentBpm = command.bpm;
        if (tvBpm != null) tvBpm.setText(currentBpm + " BPM");
    }

    private void handleMetronomeStart(LiveCommand command) {
        Toast.makeText(this,
            "♩ Métronome : " + command.bpm + " BPM (" + command.timeSignature + ")",
            Toast.LENGTH_SHORT).show();
        // Démarrer votre implémentation de métronome ici
        // MetronomeManager.getInstance().start(command.bpm, command.timeSignature);
    }

    private void handleMetronomeStop() {
        // MetronomeManager.getInstance().stop();
    }

    private void handleAlert(LiveCommand command) {
        if (tvAlertMessage != null && command.message != null) {
            tvAlertMessage.setText(command.message);
            tvAlertMessage.setVisibility(View.VISIBLE);

            // Masquer l'alerte après 5 secondes
            tvAlertMessage.postDelayed(() -> {
                if (tvAlertMessage != null) {
                    tvAlertMessage.setVisibility(View.GONE);
                }
            }, 5000);
        }

        // Toast d'alerte selon le niveau d'urgence
        if (LiveCommand.LEVEL_URGENT.equals(command.level)) {
            Toast.makeText(this, "⚠️ " + command.message, Toast.LENGTH_LONG).show();
        }
    }

    // ─── Utilitaires UI ──────────────────────────────────────────────────────

    private void updateConnectionStatus(String status) {
        if (tvConnectionStatus != null) {
            tvConnectionStatus.setText(status);
        }
        Log.d(TAG, "Status : " + status);
    }
}
