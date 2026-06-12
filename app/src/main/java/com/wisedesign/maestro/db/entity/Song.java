package com.wisedesign.maestro.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Entité Room représentant un chant dans la base de données locale.
 * Stocke toutes les métadonnées musicales nécessaires pour la performance live.
 */
@Entity(tableName = "songs")
public class Song {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    /** Titre du chant (obligatoire) */
    @NonNull
    @ColumnInfo(name = "title")
    public String title;

    /** Artiste ou auteur original */
    @ColumnInfo(name = "artist")
    public String artist;

    /** Gamme/Tonalité par défaut (ex: "G", "Am", "Bb") */
    @ColumnInfo(name = "default_key")
    public String defaultKey;

    /** Tempo par défaut en BPM */
    @ColumnInfo(name = "default_bpm")
    public int defaultBpm;

    /**
     * Chiffrage rythmique (ex: "4/4", "3/4", "6/8").
     * Utilisé par le métronome intégré.
     */
    @ColumnInfo(name = "time_signature")
    public String timeSignature;

    /**
     * Paroles et accords en format texte structuré.
     * Format : [Verset 1]\n[C]Accord [G]Paroles...\n
     */
    @ColumnInfo(name = "chord_sheet")
    public String chordSheet;

    /** Notes supplémentaires pour le Maestro */
    @ColumnInfo(name = "notes")
    public String notes;

    /** Catégorie : "LOUANGE", "ADORATION", "OFFRANDE", etc. */
    @ColumnInfo(name = "category")
    public String category;

    /** Durée estimée en secondes */
    @ColumnInfo(name = "duration_seconds")
    public int durationSeconds;

    /** Timestamp de création (millisecondes) */
    @ColumnInfo(name = "created_at")
    public long createdAt;

    /** Timestamp de dernière modification */
    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    public Song() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.defaultBpm = 80;
        this.timeSignature = "4/4";
        this.defaultKey = "C";
    }

    /**
     * Constructeur rapide pour création en live.
     * @Ignore indique à Room d'utiliser uniquement le constructeur sans args.
     */
    @Ignore
    public Song(@NonNull String title, String defaultKey, int defaultBpm) {
        this();
        this.title = title;
        this.defaultKey = defaultKey;
        this.defaultBpm = defaultBpm;
    }

    @Override
    public String toString() {
        return "Song{id=" + id + ", title='" + title + "', key='" + defaultKey
                + "', bpm=" + defaultBpm + "}";
    }
}
