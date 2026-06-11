package com.wisedesign.maestro.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

/**
 * RoleSelectionActivity — Écran d'accueil de Wise Maestro.
 *
 * L'utilisateur choisit son rôle au lancement :
 *
 *  🎹 MAESTRO      → Chef d'orchestre/culte. Héberge le serveur,
 *                     contrôle la setlist, transpositions, BPM.
 *
 *  🎤 CHANTRE       → Chanteur principal / animateur. Voit les paroles
 *                     et accords en temps réel, reçoit les alertes Maestro.
 *
 *  🎸 MUSICIEN      → Instrumentiste (guitare, basse, batterie, clavier...).
 *                     Voit la gamme courante, accords, BPM.
 *
 *  🎙️ CHORISTE      → Membre du chœur (SATB). Voit sa partition vocale
 *                     et les paroles synchronisées.
 *
 *  📺 PROJECTEUR    → Mode affichage uniquement (TV/vidéoprojecteur).
 *                     Affiche les paroles en grand format pour l'assemblée.
 *
 * Le rôle choisi est transmis en Intent Extra à l'Activity suivante
 * et persiste dans SharedPreferences pour éviter de re-sélectionner
 * à chaque ouverture (option "se souvenir de mon rôle").
 */
public class RoleSelectionActivity extends AppCompatActivity {

    // ─── Constantes de rôles ─────────────────────────────────────────────────

    public static final String EXTRA_ROLE = "user_role";

    public static final String ROLE_MAESTRO    = "MAESTRO";
    public static final String ROLE_CHANTRE    = "CHANTRE";
    public static final String ROLE_MUSICIEN   = "MUSICIEN";
    public static final String ROLE_CHORISTE   = "CHORISTE";
    public static final String ROLE_PROJECTEUR = "PROJECTEUR";

    private static final String PREFS_NAME      = "wise_maestro_prefs";
    private static final String PREF_SAVED_ROLE = "saved_role";
    private static final String PREF_REMEMBER   = "remember_role";

    // ─── Vues ────────────────────────────────────────────────────────────────

    private CardView cardMaestro, cardChantre, cardMusicien, cardChoriste, cardProjecteur;
    private ImageView ivLogo;
    private TextView tvAppTitle, tvSubtitle;
    private View layoutRoles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        // Vérifier si un rôle est mémorisé
        if (hasSavedRole()) {
            String savedRole = getSavedRole();
            navigateToRole(savedRole);
            return;
        }

        initViews();
        playEntryAnimation();
    }

    // ─── Initialisation ──────────────────────────────────────────────────────

    private void initViews() {
        ivLogo       = findViewById(R.id.ivLogo);
        tvAppTitle   = findViewById(R.id.tvAppTitle);
        tvSubtitle   = findViewById(R.id.tvSubtitle);
        layoutRoles  = findViewById(R.id.layoutRoles);

        cardMaestro    = findViewById(R.id.cardMaestro);
        cardChantre    = findViewById(R.id.cardChantre);
        cardMusicien   = findViewById(R.id.cardMusicien);
        cardChoriste   = findViewById(R.id.cardChoriste);
        cardProjecteur = findViewById(R.id.cardProjecteur);

        // Clic sur chaque carte de rôle
        cardMaestro.setOnClickListener(v    -> onRoleSelected(ROLE_MAESTRO));
        cardChantre.setOnClickListener(v    -> onRoleSelected(ROLE_CHANTRE));
        cardMusicien.setOnClickListener(v   -> onRoleSelected(ROLE_MUSICIEN));
        cardChoriste.setOnClickListener(v   -> onRoleSelected(ROLE_CHORISTE));
        cardProjecteur.setOnClickListener(v -> onRoleSelected(ROLE_PROJECTEUR));
    }

    // ─── Animation d'entrée ──────────────────────────────────────────────────

    /**
     * Animation en cascade : logo → titre → cards de rôle.
     * Donne une impression de "révélation progressive" à l'ouverture.
     */
    private void playEntryAnimation() {
        // Logo : fondu + légère montée
        ivLogo.setAlpha(0f);
        ivLogo.setTranslationY(40f);
        ivLogo.animate()
            .alpha(1f).translationY(0f)
            .setDuration(600).setStartDelay(100).start();

        // Titre : fondu décalé
        tvAppTitle.setAlpha(0f);
        tvAppTitle.animate()
            .alpha(1f).setDuration(500).setStartDelay(400).start();

        tvSubtitle.setAlpha(0f);
        tvSubtitle.animate()
            .alpha(1f).setDuration(500).setStartDelay(600).start();

        // Cards : apparition en cascade avec léger rebond
        View[] cards = {cardMaestro, cardChantre, cardMusicien, cardChoriste, cardProjecteur};
        for (int i = 0; i < cards.length; i++) {
            final View card = cards[i];
            card.setAlpha(0f);
            card.setTranslationX(80f);
            card.animate()
                .alpha(1f).translationX(0f)
                .setDuration(400)
                .setStartDelay(800 + (i * 100L))
                .start();
        }
    }

    // ─── Sélection de rôle ───────────────────────────────────────────────────

    /**
     * Appelé quand l'utilisateur tape sur une carte de rôle.
     * Animation de confirmation puis navigation.
     */
    private void onRoleSelected(String role) {
        // Animation de "pression" sur la carte
        CardView selectedCard = getCardForRole(role);
        if (selectedCard != null) {
            selectedCard.animate()
                .scaleX(0.95f).scaleY(0.95f).setDuration(80)
                .withEndAction(() ->
                    selectedCard.animate()
                        .scaleX(1f).scaleY(1f).setDuration(120)
                        .withEndAction(() -> navigateToRole(role))
                        .start()
                ).start();
        } else {
            navigateToRole(role);
        }
    }

    /**
     * Navigue vers l'Activity appropriée selon le rôle.
     *
     * MAESTRO    → MaestroActivity   (contrôle serveur + setlist)
     * CHANTRE    → ChantreActivity   (paroles + alertes + navigation)
     * MUSICIEN   → MusicianActivity  (gamme + accords + BPM)
     * CHORISTE   → ChoristActivity   (partition voix + paroles)
     * PROJECTEUR → ProjectorActivity (paroles grand format, TV)
     */
    private void navigateToRole(String role) {
        Intent intent;

        switch (role) {
            case ROLE_MAESTRO:
                intent = new Intent(this, MaestroActivity.class);
                break;
            case ROLE_CHANTRE:
                intent = new Intent(this, ChantreActivity.class);
                break;
            case ROLE_MUSICIEN:
                intent = new Intent(this, MusicianActivity.class);
                break;
            case ROLE_CHORISTE:
                intent = new Intent(this, ChoristActivity.class);
                break;
            case ROLE_PROJECTEUR:
                intent = new Intent(this, ProjectorActivity.class);
                break;
            default:
                intent = new Intent(this, MusicianActivity.class);
        }

        intent.putExtra(EXTRA_ROLE, role);
        startActivity(intent);
        // Transition glissante vers la droite
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    // ─── SharedPreferences — Mémorisation du rôle ────────────────────────────

    /** Mémorise le rôle choisi pour éviter de re-sélectionner au prochain lancement. */
    public void saveRole(String role) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(PREF_SAVED_ROLE, role)
            .putBoolean(PREF_REMEMBER, true)
            .apply();
    }

    /** Efface le rôle mémorisé (bouton "Changer de rôle" dans les Activities). */
    public static void clearSavedRole(android.content.Context context) {
        context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .remove(PREF_SAVED_ROLE)
            .putBoolean(PREF_REMEMBER, false)
            .apply();
    }

    private boolean hasSavedRole() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(PREF_REMEMBER, false);
    }

    private String getSavedRole() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(PREF_SAVED_ROLE, ROLE_MUSICIEN);
    }

    // ─── Utilitaire ──────────────────────────────────────────────────────────

    private CardView getCardForRole(String role) {
        switch (role) {
            case ROLE_MAESTRO:    return cardMaestro;
            case ROLE_CHANTRE:    return cardChantre;
            case ROLE_MUSICIEN:   return cardMusicien;
            case ROLE_CHORISTE:   return cardChoriste;
            case ROLE_PROJECTEUR: return cardProjecteur;
            default: return null;
        }
    }
}
