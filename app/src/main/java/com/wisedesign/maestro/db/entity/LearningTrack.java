package com.wisedesign.maestro.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entité Room représentant une piste audio d'apprentissage.
 * Liée à un chant (Song), chaque piste est destinée à une voix ou un instrument
 * spécifique pour les répétitions de la semaine.
 *
 * Relations : Song (1) → LearningTrack (N)
 */
@Entity(
    tableName = "learning_tracks",
    foreignKeys = @ForeignKey(
        entity = Song.class,
        parentColumns = "id",
        childColumns = "song_id",
        // Suppression en cascade : si le chant est supprimé, ses pistes aussi
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index(value = {"song_id"})}
)
public class LearningTrack {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    /** Identifiant du chant parent */
    @ColumnInfo(name = "song_id")
    public long songId;

    /**
     * Type de piste — voix ou instrument.
     * Valeurs possibles : SOPRANO, ALTO, TENOR, BASSE_VOCALE,
     *                     GUITARE_RYTHMIQUE, GUITARE_SOLO, BASSE, BATTERIE,
     *                     PIANO, CLAVIER, TOUS (mix général)
     */
    @NonNull
    @ColumnInfo(name = "track_type")
    public String trackType;

    /** Nom affiché pour cette piste (ex: "Soprane - Refrain") */
    @ColumnInfo(name = "label")
    public String label;

    /**
     * Chemin absolu vers le fichier audio sur le stockage interne.
     * Ex: /data/user/0/com.wisedesign.maestro/files/tracks/song_4_soprano.mp3
     */
    @ColumnInfo(name = "file_path")
    public String filePath;

    /** Durée de la piste en millisecondes */
    @ColumnInfo(name = "duration_ms")
    public long durationMs;

    /** Taille du fichier en octets */
    @ColumnInfo(name = "file_size_bytes")
    public long fileSizeBytes;

    /** Format audio : "MP3", "AAC", "OGG", "WAV" */
    @ColumnInfo(name = "format")
    public String format;

    /**
     * Niveau de difficulté de la piste : 1=Facile, 2=Moyen, 3=Difficile.
     * Utilisé pour trier l'ordre d'apprentissage recommandé.
     */
    @ColumnInfo(name = "difficulty")
    public int difficulty;

    /** Notes spécifiques pour cette piste (conseils du Maestro) */
    @ColumnInfo(name = "notes")
    public String notes;

    /** Ordre d'affichage dans la liste des pistes */
    @ColumnInfo(name = "sort_order")
    public int sortOrder;

    /** Indique si la piste est active pour la semaine courante */
    @ColumnInfo(name = "is_active")
    public boolean isActive;

    public LearningTrack() {
        this.isActive = true;
        this.difficulty = 1;
    }

    public LearningTrack(long songId, @NonNull String trackType, String filePath) {
        this();
        this.songId = songId;
        this.trackType = trackType;
        this.filePath = filePath;
    }

    /**
     * Constantes pour les types de pistes — évite les fautes de frappe.
     */
    public static final class TrackType {
        public static final String SOPRANO = "SOPRANO";
        public static final String ALTO = "ALTO";
        public static final String TENOR = "TENOR";
        public static final String BASSE_VOCALE = "BASSE_VOCALE";
        public static final String GUITARE_RYTHMIQUE = "GUITARE_RYTHMIQUE";
        public static final String GUITARE_SOLO = "GUITARE_SOLO";
        public static final String BASSE = "BASSE";
        public static final String BATTERIE = "BATTERIE";
        public static final String PIANO = "PIANO";
        public static final String CLAVIER = "CLAVIER";
        public static final String MIX_GENERAL = "MIX_GENERAL";
    }
}
