package com.wisedesign.maestro.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.wisedesign.maestro.db.entity.EventSetlist;

import java.util.List;

/**
 * DAO pour les setlists d'événements.
 * Gère la persistance des programmes de culte/concert.
 */
@Dao
public interface EventSetlistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(EventSetlist setlist);

    @Update
    void update(EventSetlist setlist);

    @Delete
    void delete(EventSetlist setlist);

    /** Récupère toutes les setlists triées par date décroissante. */
    @Query("SELECT * FROM event_setlists ORDER BY event_date DESC")
    LiveData<List<EventSetlist>> getAllSetlists();

    /** Récupère une setlist par son ID. */
    @Query("SELECT * FROM event_setlists WHERE id = :setlistId LIMIT 1")
    LiveData<EventSetlist> getSetlistById(long setlistId);

    /** Version synchrone pour le service live. */
    @Query("SELECT * FROM event_setlists WHERE id = :setlistId LIMIT 1")
    EventSetlist getSetlistByIdSync(long setlistId);

    /**
     * Récupère la setlist active en cours de performance.
     * Il ne doit y avoir qu'une seule setlist ACTIVE à la fois.
     */
    @Query("SELECT * FROM event_setlists WHERE status = 'ACTIVE' LIMIT 1")
    LiveData<EventSetlist> getActiveSetlist();

    /** Version synchrone de la setlist active. */
    @Query("SELECT * FROM event_setlists WHERE status = 'ACTIVE' LIMIT 1")
    EventSetlist getActiveSetlistSync();

    /**
     * Met à jour l'index du chant courant pendant la performance.
     * Appelé à chaque changement de chant par le Maestro.
     */
    @Query("UPDATE event_setlists SET current_song_index = :index, updated_at = :timestamp " +
           "WHERE id = :setlistId")
    void updateCurrentSongIndex(long setlistId, int index, long timestamp);

    /**
     * Change le statut d'une setlist.
     * Utilisé pour démarrer (ACTIVE) ou terminer (COMPLETED) une performance.
     */
    @Query("UPDATE event_setlists SET status = :status, updated_at = :timestamp WHERE id = :setlistId")
    void updateStatus(long setlistId, String status, long timestamp);

    /**
     * Met à jour le JSON de la setlist (quand on ajoute/supprime/réordonne des chants).
     */
    @Query("UPDATE event_setlists SET songs_json = :songsJson, updated_at = :timestamp WHERE id = :setlistId")
    void updateSongsJson(long setlistId, String songsJson, long timestamp);

    /** Récupère les setlists des 30 derniers jours pour l'historique. */
    @Query("SELECT * FROM event_setlists WHERE event_date > :sinceTimestamp ORDER BY event_date DESC")
    List<EventSetlist> getRecentSetlists(long sinceTimestamp);
}
