package com.wisedesign.maestro.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.wisedesign.maestro.db.entity.Song;

import java.util.List;

/**
 * DAO (Data Access Object) pour les opérations CRUD sur les chants.
 * Les méthodes retournant LiveData sont observables — l'UI se met à jour
 * automatiquement lors des modifications de la base de données.
 */
@Dao
public interface SongDao {

    // ─── Insertions ──────────────────────────────────────────────────────────

    /** Insère un nouveau chant. Retourne l'ID généré. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Song song);

    /** Insère plusieurs chants en une seule transaction. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<Song> songs);

    // ─── Mises à jour ────────────────────────────────────────────────────────

    /** Met à jour un chant existant. */
    @Update
    void update(Song song);

    // ─── Suppressions ────────────────────────────────────────────────────────

    /** Supprime un chant (et ses pistes en cascade via FK). */
    @Delete
    void delete(Song song);

    /** Supprime un chant par son ID. */
    @Query("DELETE FROM songs WHERE id = :songId")
    void deleteById(long songId);

    // ─── Requêtes de lecture ─────────────────────────────────────────────────

    /**
     * Retourne tous les chants triés alphabétiquement.
     * LiveData permet l'observation depuis le ViewModel.
     */
    @Query("SELECT * FROM songs ORDER BY title ASC")
    LiveData<List<Song>> getAllSongs();

    /** Retourne tous les chants de façon synchrone (pour usage hors UI thread). */
    @Query("SELECT * FROM songs ORDER BY title ASC")
    List<Song> getAllSongsSync();

    /** Récupère un chant par son ID. */
    @Query("SELECT * FROM songs WHERE id = :songId LIMIT 1")
    LiveData<Song> getSongById(long songId);

    /** Récupère un chant par son ID de façon synchrone. */
    @Query("SELECT * FROM songs WHERE id = :songId LIMIT 1")
    Song getSongByIdSync(long songId);

    /**
     * Recherche de chants par titre (insensible à la casse).
     * Utilisé pour la barre de recherche dans l'UI.
     */
    @Query("SELECT * FROM songs WHERE LOWER(title) LIKE LOWER('%' || :query || '%') " +
           "OR LOWER(artist) LIKE LOWER('%' || :query || '%') ORDER BY title ASC")
    LiveData<List<Song>> searchSongs(String query);

    /** Filtre les chants par catégorie. */
    @Query("SELECT * FROM songs WHERE category = :category ORDER BY title ASC")
    LiveData<List<Song>> getSongsByCategory(String category);

    /** Retourne le nombre total de chants. */
    @Query("SELECT COUNT(*) FROM songs")
    int getSongCount();

    /** Récupère les chants récemment modifiés (pour synchronisation). */
    @Query("SELECT * FROM songs WHERE updated_at > :sinceTimestamp ORDER BY updated_at DESC")
    List<Song> getSongsUpdatedSince(long sinceTimestamp);
}
