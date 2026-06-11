package com.wisedesign.maestro.ui;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * LibraryActivity — Bibliothèque musicale et gestion des setlists.
 *
 * Trois onglets :
 *  📚 Chants      → Liste tous les chants (Room), recherche, ajout/édition
 *  📋 Setlists    → Gestion des programmes de culte
 *  🎓 Semaine     → Module apprentissage — pistes audio par voix/instrument
 *
 * Accessible depuis MaestroActivity (menu) et depuis l'écran de rôle
 * via un bouton "Bibliothèque" (hors session live).
 */
public class LibraryActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("📚 Bibliothèque");
        }

        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);

        viewPager.setAdapter(new LibraryPagerAdapter(this));

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("Chants");   tab.setIcon(android.R.drawable.ic_media_play); break;
                case 1: tab.setText("Setlists"); tab.setIcon(android.R.drawable.ic_menu_agenda); break;
                case 2: tab.setText("Semaine");  tab.setIcon(android.R.drawable.ic_menu_today); break;
            }
        }).attach();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    // ─── Adaptateur ViewPager2 ───────────────────────────────────────────────

    private static class LibraryPagerAdapter extends FragmentStateAdapter {

        public LibraryPagerAdapter(FragmentActivity fa) { super(fa); }

        @Override
        public int getItemCount() { return 3; }

        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:  return new SongsFragment();
                case 1:  return new SetlistsFragment();
                case 2:  return new WeeklyLearningFragment();
                default: return new SongsFragment();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fragment Chants
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fragment listant tous les chants de la base Room.
     * Fonctionnalités : recherche, filtre par catégorie, ajout, édition, suppression.
     */
    public static class SongsFragment extends Fragment {
        public SongsFragment() { super(R.layout.fragment_songs); }

        @Override
        public void onViewCreated(android.view.View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            // RecyclerView + SongAdapter + SearchView
            // ViewModel observe songDao.getAllSongs() via LiveData
            // FAB → SongEditActivity pour créer/modifier un chant
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fragment Setlists
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fragment listant les setlists (programmes de culte).
     * Fonctionnalités : création, duplication, activation pour session live.
     */
    public static class SetlistsFragment extends Fragment {
        public SetlistsFragment() { super(R.layout.fragment_setlists); }

        @Override
        public void onViewCreated(android.view.View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            // RecyclerView + SetlistAdapter
            // Swipe-to-delete, tap → SetlistDetailActivity (drag-to-reorder les chants)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fragment Apprentissage Semaine
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fragment du module apprentissage hebdomadaire.
     *
     * Liste les chants actifs de la semaine avec leurs pistes audio.
     * Chaque chant s'expand pour révéler :
     *   🎤 Voix : Soprano / Alto / Ténor / Basse
     *   🎸 Instruments : Guitare Rythmique / Solo / Basse / Batterie / Piano
     *
     * Chaque piste a un bouton Play/Pause ExoPlayer.
     * Mode "boucle" pour répéter une section difficile.
     */
    public static class WeeklyLearningFragment extends Fragment {
        public WeeklyLearningFragment() { super(R.layout.fragment_weekly_learning); }

        @Override
        public void onViewCreated(android.view.View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            // ExpandableRecyclerView — GroupItem = Song, ChildItem = LearningTrack
            // AudioPlayerManager gère la lecture exclusive (un seul player actif à la fois)
            // Bouton "Importer piste" → Intent.ACTION_OPEN_DOCUMENT pour MP3/AAC
        }
    }
}
