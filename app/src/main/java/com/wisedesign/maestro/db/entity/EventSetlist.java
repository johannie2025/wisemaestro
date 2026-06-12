package com.wisedesign.maestro.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Entité Room représentant une setlist d'événement (culte, concert).
 * Une setlist contient une liste ordonnée de chants avec leurs paramètres
 * de performance (gamme transposée, BPM ajusté, notes live).
 *
 * La liste des chants est stockée comme JSON sérialisé dans le champ
 * `songs_json` pour simplifier le schéma et éviter une table de jointure N:N.
 *
 * Format JSON : [{"songId":1,"key":"G","bpm":92,"notes":"Intro 4 mesures"},...]
 */
@Entity(tableName = "event_setlists")
public class EventSetlist {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    /** Nom de l'événement (ex: "Culte du Dimanche 15 Juin") */
    @NonNull
    @ColumnInfo(name = "event_name")
    public String eventName;

    /** Date de l'événement (timestamp en millisecondes) */
    @ColumnInfo(name = "event_date")
    public long eventDate;

    /** Lieu ou nom de l'église */
    @ColumnInfo(name = "venue")
    public String venue;

    /**
     * Liste des chants sérialisée en JSON.
     * Chaque entrée contient : songId, key, bpm, notes, order
     */
    @ColumnInfo(name = "songs_json")
    public String songsJson;

    /**
     * Index du chant actuellement actif pendant la performance live.
     * -1 = aucun chant actif (avant le début ou pause)
     */
    @ColumnInfo(name = "current_song_index")
    public int currentSongIndex;

    /**
     * Statut de la setlist.
     * DRAFT : en préparation | ACTIVE : en cours de performance | COMPLETED : terminé
     */
    @ColumnInfo(name = "status")
    public String status;

    /** Maestro responsable (nom affiché sur les appareils clients) */
    @ColumnInfo(name = "maestro_name")
    public String maestroName;

    /** Notes générales pour tout l'équipe */
    @ColumnInfo(name = "general_notes")
    public String generalNotes;

    /** Timestamp de création */
    @ColumnInfo(name = "created_at")
    public long createdAt;

    /** Timestamp de la dernière modification */
    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    public EventSetlist() {
        this.currentSongIndex = -1;
        this.status = Status.DRAFT;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.songsJson = "[]";
    }

    @Ignore
    public EventSetlist(@NonNull String eventName, String maestroName) {
        this();
        this.eventName = eventName;
        this.maestroName = maestroName;
    }

    /**
     * Constantes de statut pour éviter les fautes de frappe.
     */
    public static final class Status {
        public static final String DRAFT = "DRAFT";
        public static final String ACTIVE = "ACTIVE";
        public static final String COMPLETED = "COMPLETED";
    }
}
