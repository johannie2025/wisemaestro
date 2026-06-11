package com.wisedesign.maestro.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.wisedesign.maestro.db.entity.LearningTrack;

import java.util.List;

/**
 * DAO pour les pistes d'apprentissage audio.
 * Gère les opérations sur les fichiers associés aux chants pour les répétitions.
 */
@Dao
public interface LearningTrackDao {

    // ─── Insertions ──────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(LearningTrack track);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<LearningTrack> tracks);

    // ─── Mises à jour ────────────────────────────────────────────────────────

    @Update
    void update(LearningTrack track);

    /** Active ou désactive une piste pour la semaine courante. */
    @Query("UPDATE learning_tracks SET is_active = :active WHERE id = :trackId")
    void setActive(long trackId, boolean active);

    // ─── Suppressions ────────────────────────────────────────────────────────

    @Delete
    void delete(LearningTrack track);

    /** Supprime toutes les pistes d'un chant (utile pour ré-import). */
    @Query("DELETE FROM learning_tracks WHERE song_id = :songId")
    void deleteAllForSong(long songId);

    // ─── Requêtes de lecture ─────────────────────────────────────────────────

    /**
     * Retourne toutes les pistes d'un chant, triées par ordre d'affichage.
     * Les voix apparaissent avant les instruments (sort_order défini à l'insertion).
     */
    @Query("SELECT * FROM learning_tracks WHERE song_id = :songId " +
           "ORDER BY sort_order ASC, track_type ASC")
    LiveData<List<LearningTrack>> getTracksForSong(long songId);

    /** Version synchrone — utilisée par le lecteur audio en background. */
    @Query("SELECT * FROM learning_tracks WHERE song_id = :songId AND is_active = 1 " +
           "ORDER BY sort_order ASC")
    List<LearningTrack> getActiveTracksForSongSync(long songId);

    /**
     * Récupère une piste spécifique d'un chant par type.
     * Ex: getTrackByType(4, "SOPRANO") → piste soprano du chant 4
     */
    @Query("SELECT * FROM learning_tracks WHERE song_id = :songId AND track_type = :trackType LIMIT 1")
    LearningTrack getTrackByType(long songId, String trackType);

    /** Retourne toutes les pistes actives de la semaine (pour la vue "Programme"). */
    @Query("SELECT * FROM learning_tracks WHERE is_active = 1 ORDER BY song_id ASC, sort_order ASC")
    LiveData<List<LearningTrack>> getAllActiveTracks();

    /** Calcule l'espace disque total utilisé par les pistes. */
    @Query("SELECT SUM(file_size_bytes) FROM learning_tracks")
    long getTotalStorageUsed();
}
