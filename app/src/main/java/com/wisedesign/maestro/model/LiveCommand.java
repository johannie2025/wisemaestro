package com.wisedesign.maestro.model;

/**
 * Modèles de commandes LiveEvent — protocole JSON entre Maestro et Musiciens.
 *
 * Chaque commande est sérialisée en JSON avant d'être broadcastée via WebSocket.
 * Le champ "action" détermine le type de commande. Exemple :
 *
 * {"action":"CHANGE_SONG","songId":4,"title":"Gloire à Toi","key":"G","bpm":88,"index":2}
 * {"action":"CHANGE_KEY","key":"A","semitones":2}
 * {"action":"CHANGE_BPM","bpm":100}
 * {"action":"METRONOME_START","bpm":88,"timeSignature":"4/4"}
 * {"action":"METRONOME_STOP"}
 * {"action":"NEXT_SONG"}
 * {"action":"PREV_SONG"}
 * {"action":"SETLIST_LOADED","setlistId":1,"eventName":"Culte Dimanche","totalSongs":6}
 * {"action":"PERFORMANCE_START"}
 * {"action":"PERFORMANCE_END"}
 * {"action":"ALERT","message":"Répétez le refrain","level":"INFO"}
 * {"action":"PING"}
 * {"action":"PONG","timestamp":1718054400000}
 */
public class LiveCommand {

    // ─── Constantes d'actions ────────────────────────────────────────────────

    /** Changement de chant actif dans la setlist */
    public static final String ACTION_CHANGE_SONG      = "CHANGE_SONG";
    /** Changement de gamme (transposition) */
    public static final String ACTION_CHANGE_KEY       = "CHANGE_KEY";
    /** Changement de tempo */
    public static final String ACTION_CHANGE_BPM       = "CHANGE_BPM";
    /** Démarrage du métronome sur les clients */
    public static final String ACTION_METRONOME_START  = "METRONOME_START";
    /** Arrêt du métronome sur les clients */
    public static final String ACTION_METRONOME_STOP   = "METRONOME_STOP";
    /** Navigation vers le chant suivant */
    public static final String ACTION_NEXT_SONG        = "NEXT_SONG";
    /** Navigation vers le chant précédent */
    public static final String ACTION_PREV_SONG        = "PREV_SONG";
    /** Chargement d'une nouvelle setlist */
    public static final String ACTION_SETLIST_LOADED   = "SETLIST_LOADED";
    /** Début de la performance live */
    public static final String ACTION_PERFORMANCE_START = "PERFORMANCE_START";
    /** Fin de la performance live */
    public static final String ACTION_PERFORMANCE_END  = "PERFORMANCE_END";
    /** Message d'alerte/instruction du Maestro */
    public static final String ACTION_ALERT            = "ALERT";
    /** Ping de vérification de connexion */
    public static final String ACTION_PING             = "PING";
    /** Réponse au ping */
    public static final String ACTION_PONG             = "PONG";
    /** Demande d'état courant (envoyée par un nouveau client qui se connecte) */
    public static final String ACTION_REQUEST_STATE    = "REQUEST_STATE";
    /** Réponse à REQUEST_STATE — état complet de la performance */
    public static final String ACTION_CURRENT_STATE    = "CURRENT_STATE";

    // ─── Niveaux d'alerte ────────────────────────────────────────────────────

    public static final String LEVEL_INFO    = "INFO";
    public static final String LEVEL_WARNING = "WARNING";
    public static final String LEVEL_URGENT  = "URGENT";

    // ─── Champs communs à toutes les commandes ───────────────────────────────

    /** Type d'action (obligatoire dans chaque message) */
    public String action;

    /** Timestamp d'émission (millisecondes) — pour mesurer la latence */
    public long timestamp;

    // ─── Champs spécifiques aux commandes ────────────────────────────────────

    // CHANGE_SONG
    public long songId;
    public String title;
    public String key;
    public int bpm;
    public int index;          // Index dans la setlist (0-based)
    public String chordSheet;  // Accords du chant (transmis pour affichage)

    // CHANGE_KEY
    public int semitones;      // Nombre de demi-tons de transposition (+/-)

    // METRONOME_START
    public String timeSignature;

    // SETLIST_LOADED
    public long setlistId;
    public String eventName;
    public int totalSongs;

    // ALERT
    public String message;
    public String level;

    // CURRENT_STATE — snapshot complet
    public long currentSongId;
    public int currentIndex;
    public String currentKey;
    public int currentBpm;
    public boolean isMetronomeRunning;
    public boolean isPerformanceActive;

    // ─── Constructeurs utilitaires ───────────────────────────────────────────

    public LiveCommand() {
        this.timestamp = System.currentTimeMillis();
    }

    /** Crée une commande CHANGE_SONG. */
    public static LiveCommand changeSong(long songId, String title, String key, int bpm,
                                          int index, String chordSheet) {
        LiveCommand cmd = new LiveCommand();
        cmd.action = ACTION_CHANGE_SONG;
        cmd.songId = songId;
        cmd.title = title;
        cmd.key = key;
        cmd.bpm = bpm;
        cmd.index = index;
        cmd.chordSheet = chordSheet;
        return cmd;
    }

    /** Crée une commande CHANGE_KEY. */
    public static LiveCommand changeKey(String key, int semitones) {
        LiveCommand cmd = new LiveCommand();
        cmd.action = ACTION_CHANGE_KEY;
        cmd.key = key;
        cmd.semitones = semitones;
        return cmd;
    }

    /** Crée une commande CHANGE_BPM. */
    public static LiveCommand changeBpm(int bpm) {
        LiveCommand cmd = new LiveCommand();
        cmd.action = ACTION_CHANGE_BPM;
        cmd.bpm = bpm;
        return cmd;
    }

    /** Crée une commande ALERT. */
    public static LiveCommand alert(String message, String level) {
        LiveCommand cmd = new LiveCommand();
        cmd.action = ACTION_ALERT;
        cmd.message = message;
        cmd.level = level;
        return cmd;
    }

    /** Crée une commande simple (sans payload). */
    public static LiveCommand simple(String action) {
        LiveCommand cmd = new LiveCommand();
        cmd.action = action;
        return cmd;
    }

    @Override
    public String toString() {
        return "LiveCommand{action='" + action + "', timestamp=" + timestamp + "}";
    }
}
