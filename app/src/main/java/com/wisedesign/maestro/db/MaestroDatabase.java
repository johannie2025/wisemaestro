package com.wisedesign.maestro.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.wisedesign.maestro.db.dao.EventSetlistDao;
import com.wisedesign.maestro.db.dao.LearningTrackDao;
import com.wisedesign.maestro.db.dao.SongDao;
import com.wisedesign.maestro.db.entity.EventSetlist;
import com.wisedesign.maestro.db.entity.LearningTrack;
import com.wisedesign.maestro.db.entity.Song;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Base de données principale de Wise Maestro.
 *
 * Architecture Singleton avec double-vérification pour être thread-safe.
 * Utilise un pool de 4 threads pour les opérations en arrière-plan.
 *
 * Migration : Utiliser Room Migration au lieu de fallbackToDestructiveMigration
 * en production. En développement, destructive migration simplifie les itérations.
 */
@Database(
    entities = {Song.class, LearningTrack.class, EventSetlist.class},
    version = 1,
    exportSchema = true  // Génère le JSON de schéma pour les migrations
)
public abstract class MaestroDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "maestro_db";

    /** Pool de threads pour les opérations DB hors UI thread. */
    public static final ExecutorService DB_EXECUTOR =
            Executors.newFixedThreadPool(4);

    // ─── Singleton ──────────────────────────────────────────────────────────

    private static volatile MaestroDatabase INSTANCE;

    /**
     * Retourne l'instance unique de la base de données.
     * Crée la base si elle n'existe pas encore.
     *
     * @param context Application context (pas d'Activity pour éviter les fuites)
     */
    public static MaestroDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (MaestroDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            MaestroDatabase.class,
                            DATABASE_NAME
                    )
                    // En développement uniquement — à remplacer par des migrations en prod
                    .fallbackToDestructiveMigration()
                    // Callback pour pré-remplir avec des données de démonstration
                    .addCallback(PRE_POPULATE_CALLBACK)
                    .build();
                }
            }
        }
        return INSTANCE;
    }

    // ─── DAOs ────────────────────────────────────────────────────────────────

    public abstract SongDao songDao();
    public abstract LearningTrackDao learningTrackDao();
    public abstract EventSetlistDao eventSetlistDao();

    // ─── Callback de pré-population ─────────────────────────────────────────

    /**
     * Insère des données de démonstration lors de la première création.
     * Permet de tester l'app sans avoir à saisir de données manuellement.
     */
    private static final RoomDatabase.Callback PRE_POPULATE_CALLBACK = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            DB_EXECUTOR.execute(() -> {
                if (INSTANCE == null) return;
                SongDao songDao = INSTANCE.songDao();

                // Chants de démonstration
                Song song1 = new Song("Gloire à Toi Seigneur", "G", 88);
                song1.artist = "Wise Worship";
                song1.category = "LOUANGE";
                song1.timeSignature = "4/4";
                song1.chordSheet = "[Intro]\nG D Em C\n\n[Verset 1]\n[G]Gloire à [D]Toi Sei[Em]gneur\n[C]Tu règnes sur tout\n";
                songDao.insert(song1);

                Song song2 = new Song("Ô Sainte Présence", "Am", 72);
                song2.artist = "Wise Worship";
                song2.category = "ADORATION";
                song2.timeSignature = "3/4";
                songDao.insert(song2);

                Song song3 = new Song("Ton Amour Est Fort", "D", 96);
                song3.artist = "Collectif";
                song3.category = "LOUANGE";
                song3.timeSignature = "4/4";
                songDao.insert(song3);
            });
        }
    };
}
