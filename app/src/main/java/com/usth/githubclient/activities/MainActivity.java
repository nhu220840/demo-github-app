package com.usth.githubclient.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.usth.githubclient.R;
import com.usth.githubclient.databinding.ActivityMainBinding;
import com.usth.githubclient.di.ServiceLocator;
import com.usth.githubclient.domain.model.GitHubUserProfileDataEntry;
import com.usth.githubclient.domain.model.MockDataFactory;
import com.usth.githubclient.domain.model.ReposDataEntry;
import com.usth.githubclient.domain.model.UserSessionData;
import com.usth.githubclient.fragments.FollowersListFragment;
import com.usth.githubclient.fragments.RepositoriesListFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Hosts the main search experience and renders a list of followers that can be filtered.
 */
// Thêm "implements NavigationView.OnNavigationItemSelectedListener"
public class MainActivity extends AppCompatActivity implements
        FollowersListFragment.OnFollowerSelectedListener,
        RepositoriesListFragment.OnRepositorySelectedListener {
    private static final String KEY_CURRENT_QUERY = "key_current_query";
    private static final String KEY_SELECTED_TAB = "key_selected_tab";


    private ActivityMainBinding binding;
    private FollowersListFragment followersFragment;
    private RepositoriesListFragment repositoriesFragment;

    private TextWatcher searchWatcher;
    private final List<GitHubUserProfileDataEntry> allFollowers = new ArrayList<>();
    private final List<ReposDataEntry> mockRepositories = new ArrayList<>();
    private String currentQuery = "";
    private int selectedNavigationItemId = R.id.nav_home;



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initialiseMockFollowers();
        initialiseMockRepositories();

        setupToolbar();

        setupBottomNavigation();

        restoreState(savedInstanceState);
        setupSearchField();

        if (selectedNavigationItemId == R.id.nav_repositories) {
            showRepositoriesScreen();
            binding.bottomNavigation.setSelectedItemId(R.id.nav_repositories);
        } else {
            selectedNavigationItemId = R.id.nav_home;
            showFollowersScreen();
            binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
        }
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                selectedNavigationItemId = R.id.nav_home;
                showFollowersScreen();
                return true;
            } else if (id == R.id.nav_repositories) {
                selectedNavigationItemId = R.id.nav_repositories;
                showRepositoriesScreen();
                return true;
            } else if (id == R.id.nav_profile) {
                Intent intent = UserProfileActivity.createIntent(this, null);
                startActivity(intent);
                return false;
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

    private void showFollowersScreen() {
        binding.toolbar.setTitle(R.string.main_title);
        binding.searchInputLayout.setVisibility(View.VISIBLE);
        binding.resultsSummary.setVisibility(View.VISIBLE);

        repositoriesFragment = null;
        followersFragment = FollowersListFragment.newInstance();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, followersFragment, FollowersListFragment.TAG)
                .commit();
        filterFollowers(currentQuery);
    }

    private void showRepositoriesScreen() {
        binding.toolbar.setTitle(R.string.repositories_title);
        binding.searchInputLayout.setVisibility(View.GONE);
        binding.searchInputEditText.clearFocus();

        followersFragment = null;
        int repositoryCount = mockRepositories.size();
        if (repositoryCount == 0) {
            binding.resultsSummary.setText(R.string.repositories_empty_state);
        } else {
            binding.resultsSummary.setText(getString(R.string.repositories_results_count, repositoryCount));
        }
        binding.resultsSummary.setVisibility(View.VISIBLE);

        repositoriesFragment = RepositoriesListFragment.newInstance();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, repositoriesFragment, RepositoriesListFragment.TAG)
                .commit();
        repositoriesFragment.submitList(mockRepositories);
    }

    private void initialiseMockFollowers() {
        allFollowers.clear();
        allFollowers.addAll(MockDataFactory.mockFollowers());
    }

    private void initialiseMockRepositories() {
        mockRepositories.clear();
        mockRepositories.addAll(MockDataFactory.mockRepositories());
    }


    private void restoreState(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            currentQuery = savedInstanceState.getString(KEY_CURRENT_QUERY, "");
            selectedNavigationItemId = savedInstanceState.getInt(KEY_SELECTED_TAB, R.id.nav_home);
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
        if (selectedNavigationItemId != R.id.nav_home) {
            return;
        }
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
    public void onRepositorySelected(@NonNull ReposDataEntry repository) {
        String url = repository.getHtmlUrl();
        if (!TextUtils.isEmpty(url)) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
                return;
            }
        }
        Toast.makeText(this, R.string.repository_open_browser_error, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_CURRENT_QUERY, currentQuery);
        outState.putInt(KEY_SELECTED_TAB, selectedNavigationItemId);
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
        repositoriesFragment = null;
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