package com.wisedesign.maestro.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.wisedesign.maestro.db.MaestroDatabase;
import com.wisedesign.maestro.db.entity.EventSetlist;
import com.wisedesign.maestro.model.LiveCommand;
import com.wisedesign.maestro.network.discovery.NsdHelper;
import com.wisedesign.maestro.network.server.MaestroWebSocketServer;

/**
 * Service Android de premier plan (Foreground Service) qui orchestre :
 * - Le serveur WebSocket Maestro (MaestroWebSocketServer)
 * - L'enregistrement NSD pour la découverte automatique (NsdHelper)
 * - La gestion du hotspot Wi-Fi (WifiApManager)
 * - La diffusion des actions LiveEvent
 *
 * Fonctionne comme Foreground Service pour rester actif en arrière-plan
 * pendant toute la performance live.
 *
 * Accès depuis l'Activity via le Binder (LocalBinder pattern).
 *
 * Intents de broadcast locaux émis :
 *   - ACTION_CLIENT_COUNT_CHANGED : {"count": N}
 *   - ACTION_SERVER_STATE_CHANGED  : {"running": true/false}
 */
public class MaestroLiveService extends Service {

    private static final String TAG = "MaestroLiveService";

    // ─── Constantes de notification ──────────────────────────────────────────

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID   = "wise_maestro_live_channel";
    private static final String CHANNEL_NAME = "Wise Maestro Live";

    // ─── Actions de broadcast local ──────────────────────────────────────────

    public static final String ACTION_CLIENT_COUNT_CHANGED = "com.wisedesign.maestro.CLIENT_COUNT";
    public static final String ACTION_SERVER_STATE_CHANGED = "com.wisedesign.maestro.SERVER_STATE";
    public static final String EXTRA_CLIENT_COUNT          = "client_count";
    public static final String EXTRA_SERVER_RUNNING        = "server_running";

    // ─── Composants internes ─────────────────────────────────────────────────

    private MaestroWebSocketServer webSocketServer;
    private NsdHelper nsdHelper;
    private WifiHotspotManager hotspotManager;
    private boolean isServerRunning = false;

    /** Setlist active en cours de performance */
    private long activeSetlistId = -1;

    // ─── Binder (pour communication Activity ↔ Service) ─────────────────────

    public class LocalBinder extends Binder {
        public MaestroLiveService getService() {
            return MaestroLiveService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // ─── Cycle de vie du Service ─────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "MaestroLiveService créé.");
        createNotificationChannel();
        hotspotManager = new WifiHotspotManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "MaestroLiveService démarré.");

        // Démarrer en Foreground immédiatement pour éviter le kill Android
        startForeground(NOTIFICATION_ID, buildNotification(0, false));

        // Démarrer le hotspot Wi-Fi puis le serveur
        startHotspotThenServer();

        // Si le service est tué, Android le redémarre automatiquement
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLiveSession();
        Log.i(TAG, "MaestroLiveService détruit.");
    }

    // ─── Démarrage du hotspot puis du serveur ────────────────────────────────

    /**
     * Démarre d'abord le hotspot Wi-Fi "WiseMaestro", puis le serveur WebSocket.
     */
    private void startHotspotThenServer() {
        hotspotManager.startHotspot(new WifiHotspotManager.HotspotCallback() {
            @Override
            public void onHotspotStarted(String ssid) {
                Log.i(TAG, "Hotspot démarré : " + ssid);
                startWebSocketServer();
            }

            @Override
            public void onHotspotFailed(String reason) {
                Log.w(TAG, "Hotspot échoué (" + reason + ") — démarrage serveur quand même.");
                // Le serveur peut fonctionner sur le réseau Wi-Fi existant
                startWebSocketServer();
            }
        });
    }

    // ─── Démarrage du serveur WebSocket ─────────────────────────────────────

    private void startWebSocketServer() {
        if (isServerRunning) {
            Log.w(TAG, "Serveur déjà en cours d'exécution.");
            return;
        }

        webSocketServer = new MaestroWebSocketServer();
        webSocketServer.setEventListener(new MaestroWebSocketServer.ServerEventListener() {

            @Override
            public void onMusicianConnected(String clientAddress, int totalClients) {
                Log.i(TAG, "Musicien connecté depuis " + clientAddress +
                      " | Total : " + totalClients);
                updateNotification(totalClients, true);
                broadcastClientCount(totalClients);
            }

            @Override
            public void onMusicianDisconnected(String clientAddress, int totalClients) {
                Log.i(TAG, "Musicien déconnecté : " + clientAddress);
                updateNotification(totalClients, true);
                broadcastClientCount(totalClients);
            }

            @Override
            public void onMessageReceived(String message, String fromAddress) {
                Log.d(TAG, "Message de " + fromAddress + " : " + message);
                // Traitement des messages musicien si nécessaire
            }

            @Override
            public void onServerError(Exception ex) {
                Log.e(TAG, "Erreur serveur WebSocket", ex);
            }
        });

        try {
            webSocketServer.start();
            isServerRunning = true;
            Log.i(TAG, "✅ Serveur WebSocket démarré sur le port " +
                  MaestroWebSocketServer.SERVER_PORT);

            // Enregistrer le service NSD pour la découverte automatique
            startNsdRegistration();
            broadcastServerState(true);

        } catch (Exception e) {
            Log.e(TAG, "Impossible de démarrer le serveur WebSocket", e);
            isServerRunning = false;
        }
    }

    // ─── Enregistrement NSD ──────────────────────────────────────────────────

    private void startNsdRegistration() {
        nsdHelper = new NsdHelper(this, new NsdHelper.NsdEventListener() {
            @Override
            public void onServiceRegistered(String registeredName) {
                Log.i(TAG, "Service NSD enregistré : " + registeredName);
            }

            @Override
            public void onMaestroFound(java.net.InetAddress host, int port, String name) {
                // Ne devrait pas être appelé côté Maestro
            }

            @Override
            public void onMaestroLost(String serviceName) {
                // Ne devrait pas être appelé côté Maestro
            }

            @Override
            public void onNsdError(String operation, int errorCode) {
                Log.e(TAG, "Erreur NSD [" + operation + "] Code : " + errorCode);
            }
        });
        nsdHelper.startRegistration();
    }

    // ─── API publique — Commandes Live (appelées depuis MaestroActivity) ────

    /**
     * Charge une setlist et notifie tous les musiciens.
     *
     * @param setlistId ID de la setlist Room à activer
     */
    public void loadSetlist(long setlistId) {
        this.activeSetlistId = setlistId;

        MaestroDatabase.DB_EXECUTOR.execute(() -> {
            EventSetlist setlist = MaestroDatabase.getInstance(this)
                    .eventSetlistDao().getSetlistByIdSync(setlistId);
            if (setlist == null) return;

            LiveCommand cmd = LiveCommand.simple(LiveCommand.ACTION_SETLIST_LOADED);
            cmd.setlistId = setlistId;
            cmd.eventName = setlist.eventName;
            // Compter les chants depuis le JSON (simplifié)
            cmd.totalSongs = setlist.songsJson.split("\\{").length - 1;

            broadcastCommand(cmd);
        });
    }

    /**
     * Change le chant actif — commande principale du Maestro.
     *
     * @param songId    ID Room du chant
     * @param title     Titre du chant
     * @param key       Gamme/tonalité (ex: "G", "Am")
     * @param bpm       Tempo en BPM
     * @param index     Position dans la setlist (0-based)
     * @param chordSheet Accords à afficher sur les clients
     */
    public void changeSong(long songId, String title, String key, int bpm,
                            int index, String chordSheet) {
        LiveCommand cmd = LiveCommand.changeSong(songId, title, key, bpm, index, chordSheet);
        broadcastCommand(cmd);

        // Persister l'index courant en base
        if (activeSetlistId != -1) {
            MaestroDatabase.DB_EXECUTOR.execute(() -> {
                MaestroDatabase.getInstance(this).eventSetlistDao()
                    .updateCurrentSongIndex(activeSetlistId, index, System.currentTimeMillis());
            });
        }
    }

    /** Change la gamme/tonalité (transposition). */
    public void changeKey(String newKey, int semitones) {
        broadcastCommand(LiveCommand.changeKey(newKey, semitones));
    }

    /** Change le tempo. */
    public void changeBpm(int bpm) {
        broadcastCommand(LiveCommand.changeBpm(bpm));
    }

    /** Démarre le métronome sur tous les clients. */
    public void startMetronome(int bpm, String timeSignature) {
        LiveCommand cmd = LiveCommand.simple(LiveCommand.ACTION_METRONOME_START);
        cmd.bpm = bpm;
        cmd.timeSignature = timeSignature;
        broadcastCommand(cmd);
    }

    /** Arrête le métronome sur tous les clients. */
    public void stopMetronome() {
        broadcastCommand(LiveCommand.simple(LiveCommand.ACTION_METRONOME_STOP));
    }

    /** Envoie un message d'alerte/instruction aux musiciens. */
    public void sendAlert(String message, String level) {
        broadcastCommand(LiveCommand.alert(message, level));
    }

    /** Navigue au chant suivant dans la setlist. */
    public void nextSong() {
        broadcastCommand(LiveCommand.simple(LiveCommand.ACTION_NEXT_SONG));
    }

    /** Navigue au chant précédent. */
    public void prevSong() {
        broadcastCommand(LiveCommand.simple(LiveCommand.ACTION_PREV_SONG));
    }

    /** Démarre officiellement la performance (notifie tous les clients). */
    public void startPerformance() {
        broadcastCommand(LiveCommand.simple(LiveCommand.ACTION_PERFORMANCE_START));
    }

    /** Termine la performance. */
    public void endPerformance() {
        broadcastCommand(LiveCommand.simple(LiveCommand.ACTION_PERFORMANCE_END));
        if (activeSetlistId != -1) {
            MaestroDatabase.DB_EXECUTOR.execute(() -> {
                MaestroDatabase.getInstance(this).eventSetlistDao()
                    .updateStatus(activeSetlistId, EventSetlist.Status.COMPLETED,
                                  System.currentTimeMillis());
            });
        }
    }

    // ─── Diffusion interne ───────────────────────────────────────────────────

    /** Envoie une commande via le serveur WebSocket. */
    private void broadcastCommand(LiveCommand command) {
        if (webSocketServer != null && isServerRunning) {
            webSocketServer.broadcastCommand(command);
        } else {
            Log.w(TAG, "Serveur non démarré — commande ignorée : " + command.action);
        }
    }

    // ─── Arrêt de session ────────────────────────────────────────────────────

    public void stopLiveSession() {
        if (nsdHelper != null) {
            nsdHelper.tearDown();
            nsdHelper = null;
        }
        if (webSocketServer != null) {
            webSocketServer.stopServer();
            webSocketServer = null;
        }
        if (hotspotManager != null) {
            hotspotManager.stopHotspot();
        }
        isServerRunning = false;
        broadcastServerState(false);
        stopForeground(true);
        stopSelf();
    }

    // ─── Notification Foreground ─────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Session live Wise Maestro en cours");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(int clientCount, boolean serverRunning) {
        String status = serverRunning
            ? clientCount + " musicien(s) connecté(s)"
            : "Démarrage...";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("🎵 Wise Maestro — Live")
            .setContentText(status)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build();
    }

    private void updateNotification(int clientCount, boolean serverRunning) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(clientCount, serverRunning));
        }
    }

    // ─── Broadcasts locaux (Activity → observe les changements d'état) ───────

    private void broadcastClientCount(int count) {
        Intent intent = new Intent(ACTION_CLIENT_COUNT_CHANGED);
        intent.putExtra(EXTRA_CLIENT_COUNT, count);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastServerState(boolean running) {
        Intent intent = new Intent(ACTION_SERVER_STATE_CHANGED);
        intent.putExtra(EXTRA_SERVER_RUNNING, running);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    // ─── Accesseurs ─────────────────────────────────────────────────────────

    public boolean isServerRunning() { return isServerRunning; }
    public int getConnectedClientCount() {
        return webSocketServer != null ? webSocketServer.getConnectedClientCount() : 0;
    }
}
