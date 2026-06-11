package com.wisedesign.maestro.ui;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.wisedesign.maestro.model.LiveCommand;
import com.wisedesign.maestro.service.MaestroLiveService;

/**
 * MaestroActivity — Interface de contrôle principal du Maestro.
 *
 * Fonctionnalités :
 * - Démarrage/arrêt du serveur live (MaestroLiveService)
 * - Affichage du nombre de musiciens connectés en temps réel
 * - Sélection et réordonnancement de la setlist (drag & drop)
 * - Changement de chant (suivant / précédent / tap direct)
 * - Transposition de gamme (+/- demi-tons)
 * - Ajustement BPM avec métronome intégré
 * - Envoi d'alertes aux musiciens
 * - Bouton "Changer de rôle" pour revenir à l'écran d'accueil
 */
public class MaestroActivity extends AppCompatActivity {

    private static final String TAG = "MaestroActivity";

    // ─── Vues ────────────────────────────────────────────────────────────────

    private TextView tvServerStatus;
    private TextView tvClientCount;
    private TextView tvCurrentSong;
    private TextView tvCurrentKey;
    private TextView tvCurrentBpm;
    private TextView tvHotspotInfo;
    private RecyclerView rvSetlist;
    private FloatingActionButton fabNext;
    private FloatingActionButton fabPrev;
    private View btnStartServer;
    private View btnStopServer;
    private View btnKeyDown, btnKeyUp;
    private View btnBpmDown, btnBpmUp;
    private View btnMetronome;
    private View btnSendAlert;
    private Chip chipMetronomeStatus;

    // ─── Service ─────────────────────────────────────────────────────────────

    private MaestroLiveService liveService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            MaestroLiveService.LocalBinder lb = (MaestroLiveService.LocalBinder) binder;
            liveService = lb.getService();
            serviceBound = true;
            updateServerStatusUI(liveService.isServerRunning());
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            liveService = null;
        }
    };

    // ─── État courant de performance ─────────────────────────────────────────

    private long activeSetlistId = -1;
    private String currentKey = "C";
    private int currentBpm    = 80;
    private int currentIndex  = -1;
    private boolean isMetronomeOn = false;

    // ─── BroadcastReceiver pour les events du Service ─────────────────────────

    private final BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case MaestroLiveService.ACTION_CLIENT_COUNT_CHANGED:
                    int count = intent.getIntExtra(MaestroLiveService.EXTRA_CLIENT_COUNT, 0);
                    updateClientCount(count);
                    break;
                case MaestroLiveService.ACTION_SERVER_STATE_CHANGED:
                    boolean running = intent.getBooleanExtra(MaestroLiveService.EXTRA_SERVER_RUNNING, false);
                    updateServerStatusUI(running);
                    break;
            }
        }
    };

    // ─── Cycle de vie ────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maestro);

        initViews();
        setupClickListeners();
        registerServiceReceiver();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind au service s'il tourne déjà
        Intent serviceIntent = new Intent(this, MaestroLiveService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceReceiver);
    }

    // ─── Initialisation UI ───────────────────────────────────────────────────

    private void initViews() {
        tvServerStatus   = findViewById(R.id.tvServerStatus);
        tvClientCount    = findViewById(R.id.tvClientCount);
        tvCurrentSong    = findViewById(R.id.tvCurrentSong);
        tvCurrentKey     = findViewById(R.id.tvCurrentKey);
        tvCurrentBpm     = findViewById(R.id.tvCurrentBpm);
        tvHotspotInfo    = findViewById(R.id.tvHotspotInfo);
        rvSetlist        = findViewById(R.id.rvSetlist);
        fabNext          = findViewById(R.id.fabNext);
        fabPrev          = findViewById(R.id.fabPrev);
        btnStartServer   = findViewById(R.id.btnStartServer);
        btnStopServer    = findViewById(R.id.btnStopServer);
        btnKeyDown       = findViewById(R.id.btnKeyDown);
        btnKeyUp         = findViewById(R.id.btnKeyUp);
        btnBpmDown       = findViewById(R.id.btnBpmDown);
        btnBpmUp         = findViewById(R.id.btnBpmUp);
        btnMetronome     = findViewById(R.id.btnMetronome);
        btnSendAlert     = findViewById(R.id.btnSendAlert);
        chipMetronomeStatus = findViewById(R.id.chipMetronomeStatus);

        rvSetlist.setLayoutManager(new LinearLayoutManager(this));
        tvHotspotInfo.setText("Réseau : WiseMaestro | MDP : wisemaestro2025");
    }

    private void setupClickListeners() {
        // Démarrer le serveur live
        btnStartServer.setOnClickListener(v -> startLiveServer());

        // Arrêter le serveur
        btnStopServer.setOnClickListener(v -> confirmStopServer());

        // Navigation setlist
        fabNext.setOnClickListener(v -> navigateNext());
        fabPrev.setOnClickListener(v -> navigatePrev());

        // Transposition gamme
        btnKeyDown.setOnClickListener(v -> transposeKey(-1));
        btnKeyUp.setOnClickListener(v -> transposeKey(+1));

        // BPM
        btnBpmDown.setOnClickListener(v -> adjustBpm(-5));
        btnBpmUp.setOnClickListener(v -> adjustBpm(+5));

        // Long press BPM ±1
        btnBpmDown.setOnLongClickListener(v -> { adjustBpm(-1); return true; });
        btnBpmUp.setOnLongClickListener(v -> { adjustBpm(+1); return true; });

        // Métronome
        btnMetronome.setOnClickListener(v -> toggleMetronome());

        // Alerte rapide
        btnSendAlert.setOnClickListener(v -> showAlertDialog());
    }

    // ─── Contrôles Live ──────────────────────────────────────────────────────

    /** Démarre MaestroLiveService (hotspot + serveur WS + NSD). */
    private void startLiveServer() {
        Intent serviceIntent = new Intent(this, MaestroLiveService.class);
        startForegroundService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        Snackbar.make(btnStartServer, "Démarrage du serveur live...", Snackbar.LENGTH_SHORT).show();
    }

    private void confirmStopServer() {
        new AlertDialog.Builder(this)
            .setTitle("Arrêter la session live ?")
            .setMessage("Tous les musiciens seront déconnectés.")
            .setPositiveButton("Arrêter", (d, w) -> stopLiveServer())
            .setNegativeButton("Annuler", null)
            .show();
    }

    private void stopLiveServer() {
        if (serviceBound && liveService != null) {
            liveService.stopLiveSession();
        }
    }

    /** Passe au chant suivant dans la setlist. */
    private void navigateNext() {
        if (!checkServiceBound()) return;
        liveService.nextSong();
        currentIndex++;
        // Ici, charger le chant depuis la setlist Room et appeler liveService.changeSong(...)
    }

    private void navigatePrev() {
        if (!checkServiceBound()) return;
        liveService.prevSong();
        currentIndex = Math.max(0, currentIndex - 1);
    }

    /**
     * Transpose la gamme de N demi-tons et broadcast aux musiciens.
     * Table de transposition circulaire : C → C# → D → ... → B → C
     */
    private void transposeKey(int semitones) {
        if (!checkServiceBound()) return;
        currentKey = transposeNote(currentKey, semitones);
        tvCurrentKey.setText(currentKey);
        liveService.changeKey(currentKey, semitones);

        // Animation flash sur l'affichage gamme
        tvCurrentKey.animate().alpha(0.3f).setDuration(80)
            .withEndAction(() -> tvCurrentKey.animate().alpha(1f).setDuration(200).start())
            .start();
    }

    private void adjustBpm(int delta) {
        if (!checkServiceBound()) return;
        currentBpm = Math.max(40, Math.min(240, currentBpm + delta));
        tvCurrentBpm.setText(currentBpm + " BPM");
        liveService.changeBpm(currentBpm);

        // Si métronome actif, le redémarrer avec le nouveau BPM
        if (isMetronomeOn) {
            liveService.startMetronome(currentBpm, "4/4");
        }
    }

    private void toggleMetronome() {
        if (!checkServiceBound()) return;
        isMetronomeOn = !isMetronomeOn;
        if (isMetronomeOn) {
            liveService.startMetronome(currentBpm, "4/4");
            chipMetronomeStatus.setText("♩ Métronome ON");
            chipMetronomeStatus.setChipBackgroundColorResource(R.color.green_live);
        } else {
            liveService.stopMetronome();
            chipMetronomeStatus.setText("Métronome OFF");
            chipMetronomeStatus.setChipBackgroundColorResource(R.color.grey_inactive);
        }
    }

    /** Dialog d'envoi d'alerte rapide aux musiciens. */
    private void showAlertDialog() {
        if (!checkServiceBound()) return;

        String[] alertMessages = {
            "⏸ Pause — On reprend dans 2 min",
            "🔁 Répétez le refrain",
            "🎵 On change de ton — attendez",
            "⬇️ Plus doucement SVP",
            "⬆️ Plus fort SVP",
            "🎸 Guitare — écoute le piano",
            "🥁 Batterie — suivre le Maestro",
            "🎤 Micros — vérifiez le son",
            "✅ Super ! Continuez",
            "⛔ Stop — problème technique"
        };

        new AlertDialog.Builder(this)
            .setTitle("Envoyer une alerte aux musiciens")
            .setItems(alertMessages, (dialog, which) -> {
                String msg = alertMessages[which];
                String level = which >= 8 ? LiveCommand.LEVEL_URGENT : LiveCommand.LEVEL_INFO;
                liveService.sendAlert(msg, level);
                Toast.makeText(this, "Alerte envoyée : " + msg, Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("Annuler", null)
            .show();
    }

    // ─── Mises à jour UI ─────────────────────────────────────────────────────

    private void updateServerStatusUI(boolean running) {
        if (tvServerStatus == null) return;
        if (running) {
            tvServerStatus.setText("🟢 Serveur LIVE actif");
            tvServerStatus.setTextColor(getColor(R.color.green_live));
            btnStartServer.setVisibility(View.GONE);
            btnStopServer.setVisibility(View.VISIBLE);
        } else {
            tvServerStatus.setText("⚫ Serveur arrêté");
            tvServerStatus.setTextColor(getColor(R.color.grey_inactive));
            btnStartServer.setVisibility(View.VISIBLE);
            btnStopServer.setVisibility(View.GONE);
        }
    }

    private void updateClientCount(int count) {
        if (tvClientCount != null) {
            tvClientCount.setText(count + " musicien" + (count > 1 ? "s" : "") + " connecté" + (count > 1 ? "s" : ""));
        }
    }

    // ─── Menu ────────────────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_maestro, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_change_role) {
            // Effacer le rôle mémorisé et revenir à l'écran de sélection
            RoleSelectionActivity.clearSavedRole(this);
            startActivity(new Intent(this, RoleSelectionActivity.class));
            finish();
            return true;
        } else if (id == R.id.action_library) {
            startActivity(new Intent(this, LibraryActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ─── Enregistrement BroadcastReceiver ────────────────────────────────────

    private void registerServiceReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(MaestroLiveService.ACTION_CLIENT_COUNT_CHANGED);
        filter.addAction(MaestroLiveService.ACTION_SERVER_STATE_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceReceiver, filter);
    }

    // ─── Utilitaires ─────────────────────────────────────────────────────────

    private boolean checkServiceBound() {
        if (!serviceBound || liveService == null) {
            Snackbar.make(fabNext, "Démarrez d'abord le serveur live.", Snackbar.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * Transpose une note musicale de N demi-tons.
     * Table chromatique : C, C#, D, D#, E, F, F#, G, G#, A, A#, B
     */
    private static final String[] CHROMATIC_SCALE = {
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };

    private String transposeNote(String note, int semitones) {
        for (int i = 0; i < CHROMATIC_SCALE.length; i++) {
            if (CHROMATIC_SCALE[i].equalsIgnoreCase(note)) {
                int newIndex = ((i + semitones) % 12 + 12) % 12;
                return CHROMATIC_SCALE[newIndex];
            }
        }
        return note; // Retourne la note inchangée si non trouvée (ex: "Am")
    }
}
