package com.wisedesign.maestro.service;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * WifiHotspotManager — Création et gestion du hotspot Wi-Fi "WiseMaestro".
 *
 * L'appareil du Maestro crée un réseau Wi-Fi privé auquel les musiciens
 * se connectent. Aucun accès Internet n'est requis.
 *
 * ⚠️ IMPORTANT — Permissions requises dans AndroidManifest.xml :
 *   <uses-permission android:name="android.permission.CHANGE_WIFI_STATE"/>
 *   <uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
 *   <uses-permission android:name="android.permission.WRITE_SETTINGS"
 *       tools:ignore="ProtectedPermissions"/>
 *
 * Sur Android 8+ (API 26+), la méthode setWifiApEnabled() est dépréciée.
 * Sur Android 10+ (API 29+), WifiManager.LocalOnlyHotspotCallback est recommandé
 * mais ne permet pas de définir le SSID personnalisé.
 *
 * STRATÉGIE ADOPTÉE :
 * - API < 26 : Réflexion sur setWifiApEnabled() (fonctionne sur la plupart des devices)
 * - API >= 26 : startLocalOnlyHotspot() (SSID aléatoire — on informe l'utilisateur)
 *
 * Pour un contrôle total du SSID sur API 26+, une configuration Android Enterprise
 * ou le Wi-Fi Direct (WifiP2pManager) peut être utilisé en alternative.
 */
public class WifiHotspotManager {

    private static final String TAG = "WifiHotspotManager";

    /** SSID du hotspot Wise Maestro */
    public static final String HOTSPOT_SSID = "WiseMaestro";

    /** Mot de passe du hotspot (minimum 8 caractères pour WPA2) */
    public static final String HOTSPOT_PASSWORD = "wisemaestro2025";

    // ─── Interface Callback ──────────────────────────────────────────────────

    public interface HotspotCallback {
        /** Appelé quand le hotspot est actif */
        void onHotspotStarted(String ssid);
        /** Appelé en cas d'échec */
        void onHotspotFailed(String reason);
    }

    // ─── Membres ─────────────────────────────────────────────────────────────

    private final Context context;
    private final WifiManager wifiManager;
    private WifiManager.LocalOnlyHotspotReservation hotspotReservation; // API 26+
    private final Handler mainHandler;

    public WifiHotspotManager(Context context) {
        this.context = context.getApplicationContext();
        this.wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    // ─── Démarrage du hotspot ────────────────────────────────────────────────

    /**
     * Démarre le hotspot Wi-Fi "WiseMaestro".
     * Choisit la méthode adaptée à la version Android.
     */
    public void startHotspot(HotspotCallback callback) {
        if (wifiManager == null) {
            callback.onHotspotFailed("WifiManager non disponible");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8+ : LocalOnlyHotspot (SSID généré par Android, affiché à l'utilisateur)
            startLocalOnlyHotspot(callback);
        } else {
            // Android < 8 : Réflexion sur l'API privée setWifiApEnabled
            startHotspotLegacy(callback);
        }
    }

    /**
     * API 26+ : Démarre un hotspot local uniquement.
     * Le SSID est généré par Android (ex: "DIRECT-xy-Android_AAAA").
     * Le callback retourne ce SSID pour que le Maestro puisse l'afficher.
     */
    private void startLocalOnlyHotspot(HotspotCallback callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        Log.i(TAG, "Démarrage LocalOnlyHotspot (API 26+)...");

        wifiManager.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() {

            @Override
            public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                hotspotReservation = reservation;
                String ssid = HOTSPOT_SSID; // On affiche notre SSID cible
                if (reservation.getWifiConfiguration() != null) {
                    String realSsid = reservation.getWifiConfiguration().SSID;
                    if (realSsid != null && !realSsid.isEmpty()) {
                        ssid = realSsid;
                    }
                }
                Log.i(TAG, "✅ LocalOnlyHotspot démarré. SSID : " + ssid);
                final String finalSsid = ssid;
                mainHandler.post(() -> callback.onHotspotStarted(finalSsid));
            }

            @Override
            public void onStopped() {
                Log.w(TAG, "LocalOnlyHotspot arrêté.");
                hotspotReservation = null;
            }

            @Override
            public void onFailed(int reason) {
                Log.e(TAG, "Échec LocalOnlyHotspot. Raison : " + reason);
                mainHandler.post(() -> callback.onHotspotFailed("Code erreur : " + reason));
            }

        }, mainHandler);
    }

    /**
     * Android < 8 : Utilise la réflexion pour accéder à setWifiApEnabled().
     * Configure le SSID "WiseMaestro" avec WPA2.
     *
     * @deprecated Utilise des APIs privées — peut ne pas fonctionner sur tous les devices.
     */
    @SuppressWarnings({"deprecation", "JavaReflectionMemberAccess"})
    private void startHotspotLegacy(HotspotCallback callback) {
        Log.i(TAG, "Démarrage hotspot legacy (API < 26)...");

        try {
            // Désactiver le Wi-Fi client avant d'activer le hotspot
            wifiManager.setWifiEnabled(false);

            // Créer la configuration du hotspot
            WifiConfiguration apConfig = new WifiConfiguration();
            apConfig.SSID = HOTSPOT_SSID;
            apConfig.preSharedKey = HOTSPOT_PASSWORD;
            apConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            apConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);

            // Accès par réflexion à la méthode privée setWifiApEnabled
            Method setWifiApMethod = wifiManager.getClass()
                .getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            boolean result = (Boolean) setWifiApMethod.invoke(wifiManager, apConfig, true);

            if (result) {
                Log.i(TAG, "✅ Hotspot legacy démarré : " + HOTSPOT_SSID);
                // Petit délai pour que le hotspot soit pleinement actif
                mainHandler.postDelayed(() -> callback.onHotspotStarted(HOTSPOT_SSID), 1500);
            } else {
                Log.e(TAG, "setWifiApEnabled a retourné false.");
                callback.onHotspotFailed("setWifiApEnabled a retourné false");
            }

        } catch (Exception e) {
            Log.e(TAG, "Réflexion setWifiApEnabled échouée", e);
            callback.onHotspotFailed("Réflexion échouée : " + e.getMessage());
        }
    }

    // ─── Arrêt du hotspot ────────────────────────────────────────────────────

    /**
     * Arrête le hotspot et libère les ressources.
     */
    @SuppressWarnings({"deprecation", "JavaReflectionMemberAccess"})
    public void stopHotspot() {
        // API 26+ : fermer la réservation LocalOnlyHotspot
        if (hotspotReservation != null) {
            hotspotReservation.close();
            hotspotReservation = null;
            Log.i(TAG, "LocalOnlyHotspot fermé.");
            return;
        }

        // API < 26 : réflexion
        if (wifiManager != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            try {
                Method setWifiApMethod = wifiManager.getClass()
                    .getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
                setWifiApMethod.invoke(wifiManager, null, false);
                Log.i(TAG, "Hotspot legacy arrêté.");
            } catch (Exception e) {
                Log.w(TAG, "Arrêt hotspot legacy échoué", e);
            }
        }
    }

    // ─── Vérification d'état ─────────────────────────────────────────────────

    /**
     * Vérifie si le hotspot est actif (API < 26 uniquement via réflexion).
     * Sur API 26+, utiliser la présence de hotspotReservation.
     */
    @SuppressWarnings("JavaReflectionMemberAccess")
    public boolean isHotspotActive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return hotspotReservation != null;
        }
        try {
            Method isApEnabledMethod = wifiManager.getClass().getMethod("isWifiApEnabled");
            return (Boolean) isApEnabledMethod.invoke(wifiManager);
        } catch (Exception e) {
            return false;
        }
    }
}
