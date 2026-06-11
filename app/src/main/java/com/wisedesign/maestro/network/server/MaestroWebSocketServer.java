package com.wisedesign.maestro.network.server;

import android.util.Log;

import com.google.gson.Gson;
import com.wisedesign.maestro.model.LiveCommand;

import org.java_websocket.WebSocket;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Serveur WebSocket embarqué côté Maestro.
 *
 * Ce serveur tourne en arrière-plan sur l'appareil du Maestro et gère :
 * - L'acceptation des connexions des musiciens
 * - Le broadcast des commandes LiveEvent en JSON
 * - Le ping/pong pour détecter les déconnexions silencieuses
 * - L'envoi de l'état courant aux nouveaux clients qui se connectent
 *
 * Port par défaut : 8765 (évite les conflits avec les ports système courants)
 *
 * Dépendance Gradle : implementation 'org.java-websocket:Java-WebSocket:1.5.4'
 */
public class MaestroWebSocketServer extends WebSocketServer {

    private static final String TAG = "MaestroWSServer";

    /** Port d'écoute du serveur WebSocket */
    public static final int SERVER_PORT = 8765;

    /** Intervalle de ping pour détecter les clients déconnectés (secondes) */
    private static final int PING_INTERVAL_SEC = 10;

    /** Délai maximum sans réponse avant de déconnecter un client (secondes) */
    private static final int PING_TIMEOUT_SEC = 30;

    // ─── État du serveur ─────────────────────────────────────────────────────

    /** Sérialisation/désérialisation JSON */
    private final Gson gson;

    /**
     * Ensemble thread-safe des clients connectés.
     * ConcurrentHashMap utilisé comme Set pour la concurrence.
     */
    private final Set<WebSocket> connectedClients;

    /** Snapshot du dernier état broadcasté — envoyé aux nouveaux clients */
    private LiveCommand lastKnownState;

    /** Scheduler pour le ping périodique */
    private final ScheduledExecutorService pingScheduler;
    private ScheduledFuture<?> pingTask;

    /** Callback vers le Service Android pour les notifications UI */
    private ServerEventListener eventListener;

    // ─── Interface Callback ──────────────────────────────────────────────────

    /**
     * Interface implémentée par MaestroLiveService pour réagir aux événements réseau.
     */
    public interface ServerEventListener {
        /** Appelé quand un musicien se connecte */
        void onMusicianConnected(String clientAddress, int totalClients);
        /** Appelé quand un musicien se déconnecte */
        void onMusicianDisconnected(String clientAddress, int totalClients);
        /** Appelé quand un client envoie un message (ex: REQUEST_STATE) */
        void onMessageReceived(String message, String fromAddress);
        /** Appelé sur erreur serveur */
        void onServerError(Exception ex);
    }

    // ─── Constructeur ────────────────────────────────────────────────────────

    public MaestroWebSocketServer() {
        super(new InetSocketAddress(SERVER_PORT));
        this.gson = new Gson();
        this.connectedClients = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.pingScheduler = Executors.newSingleThreadScheduledExecutor();

        // Configuration du serveur pour la performance
        setReuseAddr(true);          // Réutilise l'adresse immédiatement après arrêt
        setTcpNoDelay(true);         // Désactive l'algorithme de Nagle — réduit la latence
        setConnectionLostTimeout(PING_TIMEOUT_SEC);
    }

    // ─── Cycle de vie ────────────────────────────────────────────────────────

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connectedClients.add(conn);
        String clientAddr = getClientAddress(conn);
        Log.i(TAG, "Musicien connecté : " + clientAddr +
              " | Total clients : " + connectedClients.size());

        // Envoie immédiatement l'état courant au nouveau client
        // pour synchroniser son interface sans attendre la prochaine action
        if (lastKnownState != null) {
            String stateJson = gson.toJson(lastKnownState);
            conn.send(stateJson);
            Log.d(TAG, "État courant envoyé au nouveau client : " + stateJson);
        }

        // Notifie le service Android
        if (eventListener != null) {
            eventListener.onMusicianConnected(clientAddr, connectedClients.size());
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connectedClients.remove(conn);
        String clientAddr = getClientAddress(conn);
        Log.i(TAG, "Musicien déconnecté : " + clientAddr +
              " | Raison : " + reason +
              " | Remote : " + remote +
              " | Clients restants : " + connectedClients.size());

        if (eventListener != null) {
            eventListener.onMusicianDisconnected(clientAddr, connectedClients.size());
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.d(TAG, "Message reçu de " + getClientAddress(conn) + " : " + message);

        try {
            LiveCommand cmd = gson.fromJson(message, LiveCommand.class);
            if (cmd == null || cmd.action == null) return;

            switch (cmd.action) {
                case LiveCommand.ACTION_REQUEST_STATE:
                    // Le client demande l'état courant (reconnexion, nouveau client)
                    handleRequestState(conn);
                    break;

                case LiveCommand.ACTION_PING:
                    // Répondre au ping du client
                    LiveCommand pong = LiveCommand.simple(LiveCommand.ACTION_PONG);
                    conn.send(gson.toJson(pong));
                    break;

                default:
                    // Autres messages — transmis au service pour traitement
                    if (eventListener != null) {
                        eventListener.onMessageReceived(message, getClientAddress(conn));
                    }
            }
        } catch (Exception e) {
            Log.w(TAG, "Message WebSocket invalide : " + message, e);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        String clientAddr = (conn != null) ? getClientAddress(conn) : "inconnu";
        Log.e(TAG, "Erreur WebSocket pour client " + clientAddr, ex);

        if (conn != null) {
            connectedClients.remove(conn);
        }
        if (eventListener != null) {
            eventListener.onServerError(ex);
        }
    }

    @Override
    public void onStart() {
        Log.i(TAG, "✅ Serveur Maestro démarré sur le port " + SERVER_PORT);
        startPingTask();
    }

    // ─── Diffusion de commandes (API publique) ───────────────────────────────

    /**
     * Diffuse une commande LiveEvent à TOUS les musiciens connectés.
     * Met également à jour le snapshot d'état pour les nouveaux arrivants.
     *
     * @param command La commande à broadcaster
     */
    public void broadcastCommand(LiveCommand command) {
        if (command == null) return;
        command.timestamp = System.currentTimeMillis();

        // Mettre à jour le snapshot d'état si c'est une commande d'état
        if (isStatefulCommand(command.action)) {
            updateLastKnownState(command);
        }

        String json = gson.toJson(command);
        broadcastRaw(json);
        Log.d(TAG, "📡 Broadcast → " + connectedClients.size() +
              " clients | Action: " + command.action + " | " + json);
    }

    /**
     * Envoie un message JSON brut à tous les clients connectés.
     * Utilise la méthode broadcast() native de WebSocketServer.
     */
    public void broadcastRaw(String json) {
        if (connectedClients.isEmpty()) {
            Log.d(TAG, "Aucun client connecté — broadcast ignoré");
            return;
        }
        // broadcast() est thread-safe dans Java-WebSocket
        broadcast(json);
    }

    /**
     * Envoie un message à un client spécifique.
     *
     * @param conn    La connexion cible
     * @param command La commande à envoyer
     */
    public void sendToClient(WebSocket conn, LiveCommand command) {
        if (conn != null && conn.isOpen()) {
            conn.send(gson.toJson(command));
        }
    }

    // ─── Gestion de l'état ───────────────────────────────────────────────────

    /**
     * Répond à la demande d'état d'un client.
     * Envoie le dernier état connu si disponible.
     */
    private void handleRequestState(WebSocket conn) {
        if (lastKnownState != null) {
            lastKnownState.action = LiveCommand.ACTION_CURRENT_STATE;
            conn.send(gson.toJson(lastKnownState));
        }
    }

    /**
     * Met à jour le snapshot d'état interne selon le type de commande.
     * Ce snapshot est envoyé aux nouveaux clients à leur connexion.
     */
    private void updateLastKnownState(LiveCommand command) {
        if (lastKnownState == null) {
            lastKnownState = new LiveCommand();
            lastKnownState.action = LiveCommand.ACTION_CURRENT_STATE;
        }

        switch (command.action) {
            case LiveCommand.ACTION_CHANGE_SONG:
                lastKnownState.currentSongId = command.songId;
                lastKnownState.currentIndex = command.index;
                lastKnownState.currentKey = command.key;
                lastKnownState.currentBpm = command.bpm;
                lastKnownState.title = command.title;
                lastKnownState.chordSheet = command.chordSheet;
                lastKnownState.isPerformanceActive = true;
                break;

            case LiveCommand.ACTION_CHANGE_KEY:
                lastKnownState.currentKey = command.key;
                break;

            case LiveCommand.ACTION_CHANGE_BPM:
                lastKnownState.currentBpm = command.bpm;
                break;

            case LiveCommand.ACTION_METRONOME_START:
                lastKnownState.isMetronomeRunning = true;
                break;

            case LiveCommand.ACTION_METRONOME_STOP:
                lastKnownState.isMetronomeRunning = false;
                break;

            case LiveCommand.ACTION_PERFORMANCE_START:
                lastKnownState.isPerformanceActive = true;
                break;

            case LiveCommand.ACTION_PERFORMANCE_END:
                lastKnownState.isPerformanceActive = false;
                break;

            case LiveCommand.ACTION_SETLIST_LOADED:
                lastKnownState.setlistId = command.setlistId;
                lastKnownState.eventName = command.eventName;
                lastKnownState.totalSongs = command.totalSongs;
                break;
        }
        lastKnownState.timestamp = System.currentTimeMillis();
    }

    /**
     * Détermine si une commande doit mettre à jour le snapshot d'état.
     */
    private boolean isStatefulCommand(String action) {
        switch (action) {
            case LiveCommand.ACTION_CHANGE_SONG:
            case LiveCommand.ACTION_CHANGE_KEY:
            case LiveCommand.ACTION_CHANGE_BPM:
            case LiveCommand.ACTION_METRONOME_START:
            case LiveCommand.ACTION_METRONOME_STOP:
            case LiveCommand.ACTION_PERFORMANCE_START:
            case LiveCommand.ACTION_PERFORMANCE_END:
            case LiveCommand.ACTION_SETLIST_LOADED:
                return true;
            default:
                return false;
        }
    }

    // ─── Ping/Pong pour détection de déconnexion ─────────────────────────────

    /**
     * Démarre la tâche de ping périodique.
     * Java-WebSocket gère nativement le ping/pong via setConnectionLostTimeout(),
     * mais ce ping applicatif permet aussi de mesurer la latence.
     */
    private void startPingTask() {
        pingTask = pingScheduler.scheduleAtFixedRate(() -> {
            if (connectedClients.isEmpty()) return;
            LiveCommand ping = LiveCommand.simple(LiveCommand.ACTION_PING);
            broadcastCommand(ping);
        }, PING_INTERVAL_SEC, PING_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    // ─── Arrêt propre ────────────────────────────────────────────────────────

    /**
     * Arrête le serveur proprement.
     * Ferme toutes les connexions et libère les ressources.
     */
    public void stopServer() {
        Log.i(TAG, "Arrêt du serveur Maestro...");

        // Annuler le ping
        if (pingTask != null && !pingTask.isCancelled()) {
            pingTask.cancel(false);
        }
        pingScheduler.shutdown();

        // Fermer toutes les connexions proprement
        for (WebSocket conn : connectedClients) {
            if (conn.isOpen()) {
                conn.close(CloseFrame.GOING_AWAY, "Serveur Maestro arrêté");
            }
        }
        connectedClients.clear();

        try {
            stop(1000); // Timeout 1 seconde pour l'arrêt
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Interruption lors de l'arrêt du serveur", e);
        }
        Log.i(TAG, "Serveur Maestro arrêté.");
    }

    // ─── Utilitaires ────────────────────────────────────────────────────────

    /** Retourne l'adresse IP du client sous forme lisible. */
    private String getClientAddress(WebSocket conn) {
        if (conn == null || conn.getRemoteSocketAddress() == null) return "inconnu";
        return conn.getRemoteSocketAddress().getAddress().getHostAddress();
    }

    /** Retourne le nombre de clients actuellement connectés. */
    public int getConnectedClientCount() {
        return connectedClients.size();
    }

    /** Vérifie si le serveur a des clients connectés. */
    public boolean hasClients() {
        return !connectedClients.isEmpty();
    }

    // ─── Setters ────────────────────────────────────────────────────────────

    public void setEventListener(ServerEventListener listener) {
        this.eventListener = listener;
    }
}
