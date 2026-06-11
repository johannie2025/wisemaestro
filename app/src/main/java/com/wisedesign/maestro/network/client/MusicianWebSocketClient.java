package com.wisedesign.maestro.network.client;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.wisedesign.maestro.model.LiveCommand;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.InetAddress;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Client WebSocket côté Musicien.
 *
 * Se connecte au serveur Maestro (MaestroWebSocketServer) via l'URI
 * fournie par NsdHelper après la résolution mDNS.
 *
 * Fonctionnalités :
 * - Connexion automatique avec retry exponentiel en cas d'échec
 * - Désérialisation JSON des commandes LiveEvent
 * - Dispatch sur le Main Thread pour mise à jour directe de l'UI
 * - Ping/pong applicatif pour mesurer la latence
 * - Reconnexion automatique après perte de connexion
 *
 * Usage typique :
 *   MusicianWebSocketClient client = new MusicianWebSocketClient(host, port, listener);
 *   client.connectAsync();
 *   // ... utilisation ...
 *   client.disconnect();
 */
public class MusicianWebSocketClient {

    private static final String TAG = "MusicianWSClient";

    /** Délai initial avant le premier retry (millisecondes) */
    private static final long RETRY_INITIAL_DELAY_MS = 2_000;
    /** Délai maximum entre les retries */
    private static final long RETRY_MAX_DELAY_MS     = 30_000;
    /** Multiplicateur de backoff exponentiel */
    private static final double RETRY_BACKOFF_FACTOR  = 1.5;
    /** Nombre maximum de tentatives (-1 = illimité) */
    private static final int MAX_RETRY_ATTEMPTS       = 10;

    // ─── État de connexion ───────────────────────────────────────────────────

    public enum ConnectionState {
        DISCONNECTED,   // Pas de connexion active
        CONNECTING,     // Tentative de connexion en cours
        CONNECTED,      // Connecté et opérationnel
        RECONNECTING    // Reconnexion après perte de connexion
    }

    // ─── Interface Callback ──────────────────────────────────────────────────

    /**
     * Interface implémentée par l'Activity ou le ViewModel du musicien.
     * Tous les callbacks sont exécutés sur le Main Thread Android.
     */
    public interface MusicianClientListener {

        /** Connexion établie avec le Maestro */
        void onConnected();

        /** Déconnexion du Maestro (intentionnelle ou perte réseau) */
        void onDisconnected(String reason);

        /** Commande reçue du Maestro — mise à jour de l'UI ici */
        void onCommandReceived(LiveCommand command);

        /** Changement d'état de connexion */
        void onConnectionStateChanged(ConnectionState state);

        /** Réponse à un ping — latence en millisecondes */
        void onLatencyMeasured(long latencyMs);
    }

    // ─── Membres internes ────────────────────────────────────────────────────

    private final InetAddress maestroHost;
    private final int maestroPort;
    private final Gson gson;
    private final Handler mainHandler;
    private final MusicianClientListener listener;

    /** Instance du WebSocketClient Java-WebSocket */
    private WebSocketClient webSocketClient;

    /** État courant de la connexion */
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;

    /** Nombre de tentatives de reconnexion effectuées */
    private int retryCount = 0;

    /** Délai courant entre les retries (ms) — augmente exponentiellement */
    private long currentRetryDelay = RETRY_INITIAL_DELAY_MS;

    /** Scheduler pour les reconnexions et pings */
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> reconnectFuture;
    private ScheduledFuture<?> pingFuture;

    /** Timestamp du dernier ping envoyé (pour calcul de latence) */
    private long lastPingTimestamp;

    /** Indique si la déconnexion est intentionnelle (ne pas reconnecter) */
    private volatile boolean intentionalDisconnect = false;

    // ─── Constructeur ────────────────────────────────────────────────────────

    public MusicianWebSocketClient(InetAddress maestroHost, int maestroPort,
                                    MusicianClientListener listener) {
        this.maestroHost = maestroHost;
        this.maestroPort = maestroPort;
        this.listener = listener;
        this.gson = new Gson();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    // ─── Connexion ───────────────────────────────────────────────────────────

    /**
     * Lance la connexion au Maestro de façon asynchrone.
     * L'URI est construite à partir de l'IP/port fournis par NsdHelper.
     */
    public void connectAsync() {
        intentionalDisconnect = false;
        retryCount = 0;
        currentRetryDelay = RETRY_INITIAL_DELAY_MS;
        attemptConnection();
    }

    /**
     * Tente une connexion WebSocket au Maestro.
     * Appelée initialement et à chaque retry.
     */
    private void attemptConnection() {
        setConnectionState(ConnectionState.CONNECTING);

        URI serverUri;
        try {
            String uriString = "ws://" + maestroHost.getHostAddress() + ":" + maestroPort;
            serverUri = URI.create(uriString);
            Log.i(TAG, "Connexion WebSocket vers : " + uriString +
                  " (tentative " + (retryCount + 1) + ")");
        } catch (Exception e) {
            Log.e(TAG, "URI Maestro invalide", e);
            setConnectionState(ConnectionState.DISCONNECTED);
            return;
        }

        // Fermer le client précédent proprement si nécessaire
        if (webSocketClient != null && !webSocketClient.isClosed()) {
            webSocketClient.close();
        }

        webSocketClient = new WebSocketClient(serverUri) {

            @Override
            public void onOpen(ServerHandshake handshakeData) {
                Log.i(TAG, "✅ Connecté au Maestro : " + serverUri);
                retryCount = 0;
                currentRetryDelay = RETRY_INITIAL_DELAY_MS;
                setConnectionState(ConnectionState.CONNECTED);

                // Demander l'état courant immédiatement après connexion
                // pour synchroniser l'affichage sans attendre la prochaine action
                sendCommand(LiveCommand.simple(LiveCommand.ACTION_REQUEST_STATE));

                // Démarrer les pings pour surveiller la latence
                startPingTask();

                mainHandler.post(() -> {
                    if (listener != null) listener.onConnected();
                });
            }

            @Override
            public void onMessage(String message) {
                handleMessage(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                Log.w(TAG, "Connexion fermée. Code : " + code +
                      " | Raison : " + reason + " | Remote : " + remote);

                stopPingTask();
                setConnectionState(ConnectionState.DISCONNECTED);

                mainHandler.post(() -> {
                    if (listener != null) listener.onDisconnected(reason);
                });

                // Reconnexion automatique sauf si déconnexion intentionnelle
                if (!intentionalDisconnect && retryCount < MAX_RETRY_ATTEMPTS) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onError(Exception ex) {
                Log.e(TAG, "Erreur WebSocket", ex);
                // onClose sera appelé après onError — la reconnexion y est gérée
            }
        };

        // Configuration pour la performance réseau locale
        webSocketClient.setTcpNoDelay(true);         // Réduction latence
        webSocketClient.setConnectionLostTimeout(15); // 15s timeout

        // Connexion sur thread réseau (non-bloquant pour l'UI)
        webSocketClient.connect();
    }

    // ─── Traitement des messages ─────────────────────────────────────────────

    /**
     * Désérialise et dispatche les messages reçus du Maestro.
     * Les listeners sont appelés sur le Main Thread.
     */
    private void handleMessage(String rawMessage) {
        Log.d(TAG, "📨 Message reçu : " + rawMessage);

        try {
            LiveCommand command = gson.fromJson(rawMessage, LiveCommand.class);
            if (command == null || command.action == null) {
                Log.w(TAG, "Commande JSON invalide ou incomplète : " + rawMessage);
                return;
            }

            // Traitement spécial pour le PONG (calcul latence)
            if (LiveCommand.ACTION_PONG.equals(command.action)) {
                long latency = System.currentTimeMillis() - lastPingTimestamp;
                Log.d(TAG, "📶 Latence Maestro : " + latency + "ms");
                mainHandler.post(() -> {
                    if (listener != null) listener.onLatencyMeasured(latency);
                });
                return;
            }

            // Répondre au ping du serveur
            if (LiveCommand.ACTION_PING.equals(command.action)) {
                sendCommand(LiveCommand.simple(LiveCommand.ACTION_PONG));
                return;
            }

            // Dispatcher la commande sur le Main Thread pour l'UI
            final LiveCommand finalCommand = command;
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onCommandReceived(finalCommand);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Erreur de désérialisation JSON : " + rawMessage, e);
        }
    }

    // ─── Envoi de commandes ──────────────────────────────────────────────────

    /**
     * Envoie une commande au Maestro (usage rare depuis le client).
     * Ex: REQUEST_STATE, PONG
     */
    public void sendCommand(LiveCommand command) {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            Log.w(TAG, "Envoi impossible — non connecté.");
            return;
        }
        try {
            String json = gson.toJson(command);
            webSocketClient.send(json);
        } catch (Exception e) {
            Log.e(TAG, "Erreur envoi commande", e);
        }
    }

    // ─── Reconnexion automatique ─────────────────────────────────────────────

    /**
     * Planifie une tentative de reconnexion avec backoff exponentiel.
     * Exemple : 2s → 3s → 4.5s → 6.75s → ... → max 30s
     */
    private void scheduleReconnect() {
        retryCount++;
        setConnectionState(ConnectionState.RECONNECTING);

        Log.i(TAG, "Reconnexion dans " + currentRetryDelay + "ms (tentative " +
              retryCount + "/" + MAX_RETRY_ATTEMPTS + ")");

        reconnectFuture = scheduler.schedule(() -> {
            if (!intentionalDisconnect) {
                attemptConnection();
            }
        }, currentRetryDelay, TimeUnit.MILLISECONDS);

        // Augmentation exponentielle du délai (plafonné à RETRY_MAX_DELAY_MS)
        currentRetryDelay = Math.min(
            (long) (currentRetryDelay * RETRY_BACKOFF_FACTOR),
            RETRY_MAX_DELAY_MS
        );
    }

    // ─── Ping/Pong (mesure de latence) ───────────────────────────────────────

    /** Démarre l'envoi périodique de pings (toutes les 10 secondes). */
    private void startPingTask() {
        stopPingTask();
        pingFuture = scheduler.scheduleAtFixedRate(() -> {
            if (isConnected()) {
                lastPingTimestamp = System.currentTimeMillis();
                sendCommand(LiveCommand.simple(LiveCommand.ACTION_PING));
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private void stopPingTask() {
        if (pingFuture != null && !pingFuture.isCancelled()) {
            pingFuture.cancel(false);
        }
    }

    // ─── Déconnexion propre ──────────────────────────────────────────────────

    /**
     * Déconnecte proprement le client et libère les ressources.
     * Empêche toute tentative de reconnexion automatique.
     */
    public void disconnect() {
        intentionalDisconnect = true;

        // Annuler les reconnexions planifiées
        if (reconnectFuture != null && !reconnectFuture.isCancelled()) {
            reconnectFuture.cancel(false);
        }
        stopPingTask();
        scheduler.shutdown();

        if (webSocketClient != null && !webSocketClient.isClosed()) {
            webSocketClient.close();
        }

        setConnectionState(ConnectionState.DISCONNECTED);
        Log.i(TAG, "Client déconnecté intentionnellement.");
    }

    // ─── Utilitaires ─────────────────────────────────────────────────────────

    /** Retourne true si le client est connecté et la connexion ouverte. */
    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED
                && webSocketClient != null
                && webSocketClient.isOpen();
    }

    /** Met à jour l'état et notifie le listener sur le Main Thread. */
    private void setConnectionState(ConnectionState newState) {
        if (connectionState != newState) {
            connectionState = newState;
            Log.d(TAG, "État connexion → " + newState);
            mainHandler.post(() -> {
                if (listener != null) listener.onConnectionStateChanged(newState);
            });
        }
    }

    public ConnectionState getConnectionState() { return connectionState; }
    public InetAddress getMaestroHost() { return maestroHost; }
    public int getMaestroPort() { return maestroPort; }
    public int getRetryCount() { return retryCount; }
}
