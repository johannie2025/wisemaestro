package com.wisedesign.maestro.network.discovery;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * NsdHelper — Gestion de la découverte automatique du Maestro sur le réseau local.
 *
 * Utilise l'API NsdManager d'Android (Network Service Discovery / mDNS / Bonjour).
 *
 * CÔTÉ MAESTRO : appeler startRegistration() après démarrage du serveur WebSocket.
 *   → Le device s'annonce sur le réseau avec le nom "WiseMaestro" et le port 8765.
 *
 * CÔTÉ MUSICIEN : appeler startDiscovery() pour trouver automatiquement le Maestro.
 *   → Dès que le service est trouvé, onMaestroFound() est appelé avec l'IP et le port.
 *
 * Aucune adresse IP ne doit être saisie manuellement.
 *
 * Permissions requises dans AndroidManifest.xml :
 *   <uses-permission android:name="android.permission.INTERNET" />
 *   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
 *   <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
 *   <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
 */
public class NsdHelper {

    private static final String TAG = "NsdHelper";

    /** Type de service mDNS — identifie l'application sur le réseau */
    public static final String SERVICE_TYPE = "_wisemaestro._tcp.";

    /** Nom du service annoncé sur le réseau */
    public static final String SERVICE_NAME = "WiseMaestro";

    /** Port du serveur WebSocket */
    public static final int SERVICE_PORT = 8765;

    /** Délai max pour la résolution d'un service (millisecondes) */
    private static final long RESOLVE_TIMEOUT_MS = 10_000;

    // ─── État interne ────────────────────────────────────────────────────────

    private final NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;
    private NsdManager.DiscoveryListener discoveryListener;
    private NsdManager.ResolveListener resolveListener;

    /** Nom réel enregistré (NSD peut le modifier en cas de conflit, ex: WiseMaestro (2)) */
    private String registeredServiceName;

    /** Indique si la découverte est en cours */
    private boolean isDiscovering = false;

    /** Indique si l'enregistrement est en cours */
    private boolean isRegistered = false;

    /** Callback vers le code appelant */
    private final NsdEventListener eventListener;

    // ─── Interface Callback ──────────────────────────────────────────────────

    /**
     * Interface de callback pour notifier le code appelant des événements NSD.
     */
    public interface NsdEventListener {
        /**
         * Appelé quand le Maestro est trouvé et résolu sur le réseau.
         * @param host    Adresse IP du Maestro
         * @param port    Port WebSocket du Maestro
         * @param name    Nom du service (ex: "WiseMaestro")
         */
        void onMaestroFound(InetAddress host, int port, String name);

        /**
         * Appelé quand le service Maestro disparaît du réseau.
         * (Maestro s'est déconnecté ou a éteint son hotspot)
         */
        void onMaestroLost(String serviceName);

        /**
         * Appelé quand l'enregistrement du service réussit (côté Maestro).
         * @param registeredName Nom enregistré (peut différer du nom demandé)
         */
        void onServiceRegistered(String registeredName);

        /**
         * Appelé sur toute erreur NSD.
         */
        void onNsdError(String operation, int errorCode);
    }

    // ─── Constructeur ────────────────────────────────────────────────────────

    public NsdHelper(Context context, NsdEventListener listener) {
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.eventListener = listener;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CÔTÉ MAESTRO — Enregistrement du service
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Enregistre le service Maestro sur le réseau local.
     * À appeler APRÈS le démarrage du serveur WebSocket.
     *
     * Les autres appareils sur le même réseau Wi-Fi pourront
     * détecter ce service via startDiscovery().
     */
    public void startRegistration() {
        if (isRegistered) {
            Log.w(TAG, "Service déjà enregistré. stopRegistration() d'abord.");
            return;
        }

        // Créer le NsdServiceInfo avec les métadonnées du service
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(SERVICE_NAME);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(SERVICE_PORT);

        // Ajouter des attributs TXT pour les métadonnées (API 21+)
        // Ces attributs sont optionnels mais utiles pour identifier l'app
        serviceInfo.setAttribute("app", "WiseMaestro");
        serviceInfo.setAttribute("version", "1.0");

        registrationListener = buildRegistrationListener();

        nsdManager.registerService(
            serviceInfo,
            NsdManager.PROTOCOL_DNS_SD,  // DNS-SD = mDNS (compatible iOS/macOS/Android)
            registrationListener
        );

        Log.i(TAG, "Enregistrement NSD démarré pour : " + SERVICE_NAME + " sur port " + SERVICE_PORT);
    }

    /**
     * Arrête l'enregistrement du service (à appeler quand le serveur s'arrête).
     */
    public void stopRegistration() {
        if (!isRegistered || registrationListener == null) return;
        try {
            nsdManager.unregisterService(registrationListener);
            isRegistered = false;
            Log.i(TAG, "Service NSD désenregistré.");
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Listener NSD déjà désenregistré.", e);
        }
    }

    /**
     * Construit le listener d'enregistrement NSD.
     */
    private NsdManager.RegistrationListener buildRegistrationListener() {
        return new NsdManager.RegistrationListener() {

            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                // NSD peut modifier le nom pour éviter les conflits
                registeredServiceName = serviceInfo.getServiceName();
                isRegistered = true;
                Log.i(TAG, "✅ Service NSD enregistré sous le nom : " + registeredServiceName);

                if (eventListener != null) {
                    eventListener.onServiceRegistered(registeredServiceName);
                }
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                isRegistered = false;
                Log.e(TAG, "❌ Échec enregistrement NSD. Code : " + errorCode);
                if (eventListener != null) {
                    eventListener.onNsdError("REGISTRATION", errorCode);
                }
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                isRegistered = false;
                Log.i(TAG, "Service NSD désenregistré : " + serviceInfo.getServiceName());
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Échec désenregistrement NSD. Code : " + errorCode);
            }
        };
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CÔTÉ MUSICIEN — Découverte du service
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Lance la découverte du service Maestro sur le réseau local.
     * À appeler depuis MusicianActivity ou un Service Android.
     *
     * Dès que le Maestro est trouvé, onMaestroFound() est déclenché
     * avec l'adresse IP et le port pour la connexion WebSocket.
     */
    public void startDiscovery() {
        if (isDiscovering) {
            Log.w(TAG, "Découverte NSD déjà en cours.");
            return;
        }

        discoveryListener = buildDiscoveryListener();

        nsdManager.discoverServices(
            SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener
        );

        isDiscovering = true;
        Log.i(TAG, "🔍 Découverte NSD démarrée pour le type : " + SERVICE_TYPE);
    }

    /**
     * Arrête la découverte (à appeler quand le musicien est connecté ou quitte).
     * IMPORTANT : Toujours arrêter la découverte pour économiser la batterie.
     */
    public void stopDiscovery() {
        if (!isDiscovering || discoveryListener == null) return;
        try {
            nsdManager.stopServiceDiscovery(discoveryListener);
            isDiscovering = false;
            Log.i(TAG, "Découverte NSD arrêtée.");
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Listener de découverte NSD déjà arrêté.", e);
        }
    }

    /**
     * Construit le listener de découverte.
     * Quand un service WiseMaestro est trouvé, lance la résolution
     * pour obtenir l'adresse IP réelle.
     */
    private NsdManager.DiscoveryListener buildDiscoveryListener() {
        return new NsdManager.DiscoveryListener() {

            @Override
            public void onDiscoveryStarted(String regType) {
                Log.i(TAG, "Découverte démarrée pour : " + regType);
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                Log.i(TAG, "Service trouvé : " + service.getServiceName() +
                      " | Type : " + service.getServiceType());

                // Vérifier que c'est bien notre service Maestro
                if (!service.getServiceType().equals(SERVICE_TYPE)) {
                    Log.d(TAG, "Service ignoré (type différent) : " + service.getServiceType());
                    return;
                }

                if (service.getServiceName().startsWith(SERVICE_NAME)) {
                    Log.i(TAG, "✅ Maestro trouvé ! Résolution en cours...");
                    // Résoudre le service pour obtenir l'IP et le port réels
                    resolveService(service);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.w(TAG, "Service Maestro perdu : " + service.getServiceName());
                if (eventListener != null) {
                    eventListener.onMaestroLost(service.getServiceName());
                }
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                isDiscovering = false;
                Log.i(TAG, "Découverte NSD arrêtée pour : " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                isDiscovering = false;
                Log.e(TAG, "Échec démarrage découverte. Code : " + errorCode);
                if (eventListener != null) {
                    eventListener.onNsdError("DISCOVERY_START", errorCode);
                }
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Échec arrêt découverte. Code : " + errorCode);
            }
        };
    }

    // ─── Résolution du service ───────────────────────────────────────────────

    /**
     * Résout un service NSD pour obtenir l'adresse IP et le port.
     *
     * IMPORTANT : NsdManager.resolveService() ne peut traiter qu'une résolution
     * à la fois. Une file d'attente est nécessaire pour éviter les erreurs
     * FAILURE_ALREADY_ACTIVE si plusieurs services sont trouvés simultanément.
     *
     * Dans cette implémentation, on résout immédiatement le premier Maestro trouvé
     * (cas d'usage normal : un seul Maestro par session).
     */
    private void resolveService(NsdServiceInfo serviceInfo) {
        resolveListener = buildResolveListener();

        // Arrêter la découverte avant de résoudre pour éviter les conflits
        // (optimisation — la découverte peut reprendre si la résolution échoue)
        stopDiscovery();

        nsdManager.resolveService(serviceInfo, resolveListener);
        Log.d(TAG, "Résolution NSD en cours pour : " + serviceInfo.getServiceName());
    }

    /**
     * Construit le listener de résolution.
     * Appelé une fois que l'IP/port sont déterminés.
     */
    private NsdManager.ResolveListener buildResolveListener() {
        return new NsdManager.ResolveListener() {

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "❌ Résolution NSD échouée pour : " + serviceInfo.getServiceName() +
                      " | Code : " + errorCode);

                // En cas d'échec, relancer la découverte pour réessayer
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) {
                    Log.w(TAG, "Résolution déjà active — attente et nouvelle tentative...");
                    // Réessai différé de 2 secondes
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        resolveService(serviceInfo);
                    }, 2000);
                } else {
                    if (eventListener != null) {
                        eventListener.onNsdError("RESOLVE", errorCode);
                    }
                    // Relancer la découverte après un échec
                    startDiscovery();
                }
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                InetAddress host = serviceInfo.getHost();
                int port = serviceInfo.getPort();
                String name = serviceInfo.getServiceName();

                Log.i(TAG, "🎯 Maestro résolu ! IP : " + host.getHostAddress() +
                      " | Port : " + port + " | Nom : " + name);

                // Notifier le client — il peut maintenant se connecter en WebSocket
                if (eventListener != null) {
                    eventListener.onMaestroFound(host, port, name);
                }
            }
        };
    }

    // ─── Nettoyage complet ───────────────────────────────────────────────────

    /**
     * Libère toutes les ressources NSD.
     * À appeler dans onDestroy() de l'Activity ou du Service.
     */
    public void tearDown() {
        stopDiscovery();
        stopRegistration();
        Log.i(TAG, "NsdHelper nettoyé.");
    }

    // ─── Accesseurs ─────────────────────────────────────────────────────────

    public boolean isRegistered() { return isRegistered; }
    public boolean isDiscovering() { return isDiscovering; }
    public String getRegisteredServiceName() { return registeredServiceName; }
}
