package com.wisedesign.maestro.ui;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.wisedesign.maestro.model.LiveCommand;
import com.wisedesign.maestro.network.client.MusicianWebSocketClient;
import com.wisedesign.maestro.network.discovery.NsdHelper;

import java.net.InetAddress;

/**
 * ProjectorActivity — Mode affichage TV / Vidéoprojecteur.
 *
 * Conçu pour être lancé sur une tablette ou Android TV Box
 * branchée sur le projecteur de l'église.
 *
 * Caractéristiques :
 *  - Plein écran absolu (pas de barre de statut, pas de navigation)
 *  - Fond noir, texte blanc très grand (lisible à 20m)
 *  - Paroles uniquement (sans accords — c'est pour l'assemblée)
 *  - Transition fondu entre les chants
 *  - Indicateur de connexion discret en coin
 *  - Reconnexion automatique sans intervention
 *  - Écran toujours allumé (FLAG_KEEP_SCREEN_ON)
 */
public class ProjectorActivity extends AppCompatActivity
        implements NsdHelper.NsdEventListener,
                   MusicianWebSocketClient.MusicianClientListener {

    // ─── Vues ────────────────────────────────────────────────────────────────

    private TextView tvSongTitle;
    private TextView tvLyrics;
    private TextView tvConnectionDot;   // Petit indicateur de connexion discret
    private View     layoutWaiting;     // Affiché avant connexion

    // ─── Réseau ──────────────────────────────────────────────────────────────

    private NsdHelper nsdHelper;
    private MusicianWebSocketClient wsClient;

    // ─── Cycle de vie ────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Plein écran absolu — aucune distraction pour l'assemblée
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        // Masquer la barre de navigation système
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );

        // Masquer la ActionBar
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        setContentView(R.layout.activity_projector);

        initViews();
        connectToMaestro();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nsdHelper != null) nsdHelper.tearDown();
        if (wsClient != null) wsClient.disconnect();
    }

    // ─── Initialisation ──────────────────────────────────────────────────────

    private void initViews() {
        tvSongTitle      = findViewById(R.id.tvSongTitle);
        tvLyrics         = findViewById(R.id.tvLyrics);
        tvConnectionDot  = findViewById(R.id.tvConnectionDot);
        layoutWaiting    = findViewById(R.id.layoutWaiting);

        showWaitingScreen();
    }

    private void connectToMaestro() {
        nsdHelper = new NsdHelper(this, this);
        nsdHelper.startDiscovery();
    }

    // ─── Écran d'attente ─────────────────────────────────────────────────────

    private void showWaitingScreen() {
        if (layoutWaiting != null) layoutWaiting.setVisibility(View.VISIBLE);
        if (tvSongTitle != null)   tvSongTitle.setVisibility(View.GONE);
        if (tvLyrics != null)      tvLyrics.setVisibility(View.GONE);
    }

    private void showContentScreen() {
        if (layoutWaiting != null) layoutWaiting.setVisibility(View.GONE);
        if (tvSongTitle != null)   tvSongTitle.setVisibility(View.VISIBLE);
        if (tvLyrics != null)      tvLyrics.setVisibility(View.VISIBLE);
    }

    // ─── NsdEventListener ────────────────────────────────────────────────────

    @Override
    public void onMaestroFound(InetAddress host, int port, String name) {
        runOnUiThread(() -> setConnectionDot("🟡"));
        nsdHelper.stopDiscovery();
        wsClient = new MusicianWebSocketClient(host, port, this);
        wsClient.connectAsync();
    }

    @Override
    public void onMaestroLost(String serviceName) {
        runOnUiThread(() -> setConnectionDot("🔴"));
    }

    @Override public void onServiceRegistered(String name) {}
    @Override public void onNsdError(String op, int code) {
        runOnUiThread(() -> setConnectionDot("🔴"));
    }

    // ─── MusicianClientListener ──────────────────────────────────────────────

    @Override
    public void onConnected() {
        setConnectionDot("🟢");
    }

    @Override
    public void onDisconnected(String reason) {
        setConnectionDot("🔴");
    }

    @Override
    public void onCommandReceived(LiveCommand command) {
        switch (command.action) {

            case LiveCommand.ACTION_CHANGE_SONG:
            case LiveCommand.ACTION_CURRENT_STATE:
                showContentScreen();
                displaySong(command.title, command.chordSheet);
                break;

            case LiveCommand.ACTION_PERFORMANCE_START:
                showContentScreen();
                break;

            case LiveCommand.ACTION_PERFORMANCE_END:
                // Fin du culte — afficher un message de clôture
                crossFadeTo(tvLyrics, "🙏 Que Dieu vous bénisse !");
                break;
        }
    }

    @Override
    public void onConnectionStateChanged(MusicianWebSocketClient.ConnectionState state) {
        switch (state) {
            case CONNECTED:    setConnectionDot("🟢"); break;
            case RECONNECTING: setConnectionDot("🟡"); break;
            case DISCONNECTED: setConnectionDot("🔴"); break;
            default: break;
        }
    }

    @Override
    public void onLatencyMeasured(long latencyMs) {}

    // ─── Affichage du contenu ────────────────────────────────────────────────

    /**
     * Affiche le chant avec une transition fondu pour ne pas perturber l'assemblée.
     */
    private void displaySong(String title, String chordSheet) {
        // Fondu sortant
        if (tvSongTitle != null) {
            tvSongTitle.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                tvSongTitle.setText(title != null ? title : "");
                tvSongTitle.animate().alpha(1f).setDuration(400).start();
            }).start();
        }

        if (tvLyrics != null) {
            tvLyrics.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                // Pour le projecteur : paroles uniquement, sans les crochets d'accords
                String lyrics = extractLyricsOnly(chordSheet);
                tvLyrics.setText(lyrics);
                tvLyrics.animate().alpha(1f).setDuration(400).start();
            }).start();
        }
    }

    /**
     * Fondu enchaîné vers un nouveau texte dans une TextView.
     */
    private void crossFadeTo(TextView view, String newText) {
        if (view == null) return;
        view.animate().alpha(0f).setDuration(500).withEndAction(() -> {
            view.setText(newText);
            view.animate().alpha(1f).setDuration(600).start();
        }).start();
    }

    /**
     * Extrait les paroles pures en supprimant les marqueurs d'accords.
     * "[G]Gloire [D]à Toi [Em]Seigneur" → "Gloire à Toi Seigneur"
     */
    private String extractLyricsOnly(String chordSheet) {
        if (chordSheet == null) return "";
        // Supprimer [Accord] et les en-têtes de section [Verset 1], [Refrain]...
        return chordSheet
            .replaceAll("\\[(?:[A-G][^\\]]*|Verset[^\\]]*|Refrain[^\\]]*|Pont[^\\]]*|Intro[^\\]]*)\\]", "")
            .replaceAll("(?m)^\\s*$\\n", "")   // Supprimer les lignes vides consécutives
            .trim();
    }

    // ─── Indicateur de connexion ─────────────────────────────────────────────

    /** Petit dot de connexion en coin — à peine visible pour l'assemblée. */
    private void setConnectionDot(String dot) {
        if (tvConnectionDot != null) tvConnectionDot.setText(dot);
    }
}
