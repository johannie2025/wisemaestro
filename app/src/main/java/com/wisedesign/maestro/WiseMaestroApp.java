package com.wisedesign.maestro;

import android.app.Application;
import android.util.Log;

import com.wisedesign.maestro.db.MaestroDatabase;

/**
 * Classe Application principale de Wise Maestro.
 * Initialise les composants globaux au démarrage du processus.
 */
public class WiseMaestroApp extends Application {

    private static final String TAG = "WiseMaestroApp";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Wise Maestro démarré.");

        // Pré-initialiser la base de données Room en arrière-plan
        // pour que la première ouverture ne souffre pas de latence
        MaestroDatabase.DB_EXECUTOR.execute(() -> {
            MaestroDatabase.getInstance(this);
            Log.i(TAG, "Base de données Room initialisée.");
        });
    }
}
