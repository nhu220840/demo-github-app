package com.usth.githubclient.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;

import com.google.android.material.navigation.NavigationView;
import com.usth.githubclient.R;
import com.usth.githubclient.databinding.ActivityMainBinding;
import com.usth.githubclient.di.ServiceLocator;
import com.usth.githubclient.domain.model.GitHubUserProfileDataEntry;
import com.usth.githubclient.domain.model.MockDataFactory;
import com.usth.githubclient.domain.model.UserSessionData;
import com.usth.githubclient.fragments.FollowersListFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Hosts the main search experience and renders a list of followers that can be filtered.
 */
// Thêm "implements NavigationView.OnNavigationItemSelectedListener"
public class MainActivity extends AppCompatActivity implements FollowersListFragment.OnFollowerSelectedListener {

    private static final String KEY_CURRENT_QUERY = "key_current_query";

    private ActivityMainBinding binding;
    private FollowersListFragment followersFragment;
    private TextWatcher searchWatcher;
    private List<GitHubUserProfileDataEntry> allFollowers = new ArrayList<>();
    private String currentQuery = "";


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();

        // ---- XÓA ĐOẠN CODE CŨ DƯỚI ĐÂY ----
    /*
    ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
    binding.drawerLayout.addDrawerListener(toggle);
    toggle.syncState();

    binding.navView.setNavigationItemSelectedListener(this);
    */
        // ---- KẾT THÚC PHẦN XÓA ----

        // ---- THÊM PHƯƠNG THỨC MỚI VÀO ĐÂY ----
        setupBottomNavigation();

        attachFollowersFragment();
        initialiseMockFollowers();
        restoreState(savedInstanceState);
        setupSearchField();

        filterFollowers(currentQuery);
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                Toast.makeText(this, "Home selected", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.nav_profile) {
                Toast.makeText(this, "Profile selected", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.nav_repositories) {
                Toast.makeText(this, "Repositories selected", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setTitle(R.string.main_title);

        UserSessionData session = ServiceLocator.getInstance().authRepository().getCachedSession();
        if (session != null) {
            Optional<GitHubUserProfileDataEntry> optionalProfile = session.getUserProfile();
            if (optionalProfile.isPresent()) {
                GitHubUserProfileDataEntry profile = optionalProfile.get();
                Optional<String> optionalDisplayName = profile.getDisplayName();
                if (optionalDisplayName.isPresent() && !TextUtils.isEmpty(optionalDisplayName.get())) {
                    binding.toolbar.setSubtitle(optionalDisplayName.get());
                } else {
                    binding.toolbar.setSubtitle(getString(R.string.main_subtitle_username, session.getUsername()));
                }
            } else {
                binding.toolbar.setSubtitle(getString(R.string.main_subtitle_username, session.getUsername()));
            }
        } else {
            binding.toolbar.setSubtitle(R.string.main_subtitle_guest);
        }
    }

    private void attachFollowersFragment() {
        followersFragment = (FollowersListFragment) getSupportFragmentManager()
                .findFragmentByTag(FollowersListFragment.TAG);
        if (followersFragment == null) {
            followersFragment = FollowersListFragment.newInstance();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, followersFragment, FollowersListFragment.TAG)
                    .commitNow();
        }
    }

    private void initialiseMockFollowers() {
        allFollowers.clear();
        allFollowers.addAll(MockDataFactory.mockFollowers());
    }

    private void restoreState(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            currentQuery = savedInstanceState.getString(KEY_CURRENT_QUERY, "");
            if (!TextUtils.isEmpty(currentQuery)) {
                binding.searchInputEditText.setText(currentQuery);
                binding.searchInputEditText.setSelection(currentQuery.length());
            }
        }
    }

    private void setupSearchField() {
        searchWatcher = new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s == null ? "" : s.toString();
                filterFollowers(currentQuery);
            }
        };
        binding.searchInputEditText.addTextChangedListener(searchWatcher);
    }

    private void filterFollowers(@NonNull String query) {
        List<GitHubUserProfileDataEntry> filteredFollowers;
        String trimmedQuery = query.trim();
        if (trimmedQuery.isEmpty()) {
            filteredFollowers = new ArrayList<>(allFollowers);
        } else {
            String lowerQuery = trimmedQuery.toLowerCase(Locale.getDefault());
            filteredFollowers = new ArrayList<>();
            for (GitHubUserProfileDataEntry follower : allFollowers) {
                if (matchesQuery(follower, lowerQuery)) {
                    filteredFollowers.add(follower);
                }
            }
        }

        if (followersFragment != null) {
            followersFragment.submitList(filteredFollowers);
        }
        updateResultsSummary(trimmedQuery, filteredFollowers.size());
    }

    private boolean matchesQuery(GitHubUserProfileDataEntry follower, String lowerQuery) {
        if (follower == null) {
            return false;
        }
        if (containsIgnoreCase(follower.getUsername(), lowerQuery)) {
            return true;
        }
        if (containsIgnoreCase(follower.getDisplayName().orElse(""), lowerQuery)) {
            return true;
        }
        return containsIgnoreCase(follower.getBio().orElse(""), lowerQuery);
    }

    private boolean containsIgnoreCase(@Nullable String source, @NonNull String query) {
        if (TextUtils.isEmpty(source)) {
            return false;
        }
        return source.toLowerCase(Locale.getDefault()).contains(query);
    }

    private void updateResultsSummary(@NonNull String trimmedQuery, int visibleCount) {
        if (visibleCount == 0) {
            binding.resultsSummary.setText(R.string.followers_results_empty);
            return;
        }
        if (trimmedQuery.isEmpty()) {
            binding.resultsSummary.setText(getString(R.string.followers_results_all, visibleCount));
        } else {
            binding.resultsSummary.setText(getString(R.string.followers_results_filtered, visibleCount, allFollowers.size()));
        }
    }

    @Override
    public void onFollowerSelected(@NonNull GitHubUserProfileDataEntry follower) {
        Optional<String> optionalProfileUrl = follower.getProfileUrl();
        if (optionalProfileUrl.isPresent() && !TextUtils.isEmpty(optionalProfileUrl.get())) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(optionalProfileUrl.get()));
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
                return;
            }
        }
        Toast.makeText(this, getString(R.string.followers_profile_unavailable, follower.getUsername()), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_CURRENT_QUERY, currentQuery);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (binding != null && searchWatcher != null) {
            binding.searchInputEditText.removeTextChangedListener(searchWatcher);
        }
        binding = null;
        searchWatcher = null;
        followersFragment = null;
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }
}