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
import com.usth.githubclient.data.remote.ApiClient;
import com.usth.githubclient.data.remote.GithubApiService;
import com.usth.githubclient.data.repository.RepoRepository;
import com.usth.githubclient.data.repository.UserRepository;
import com.usth.githubclient.databinding.ActivityMainBinding;
import com.usth.githubclient.di.ServiceLocator;
import com.usth.githubclient.domain.model.GitHubUserProfileDataEntry;
import com.usth.githubclient.domain.model.ReposDataEntry;
import com.usth.githubclient.domain.model.UserSessionData;
import com.usth.githubclient.fragments.FollowersListFragment;
import com.usth.githubclient.fragments.RepositoriesListFragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hosts the main search experience and renders a list of followers that can be filtered.
 */
// Thêm "implements NavigationView.OnNavigationItemSelectedListener"
public class MainActivity extends AppCompatActivity implements
        FollowersListFragment.OnFollowerSelectedListener,
        RepositoriesListFragment.OnRepositorySelectedListener {
    private static final String KEY_CURRENT_QUERY = "key_current_query";
    private static final String KEY_SELECTED_TAB = "key_selected_tab";
    private static final String DEFAULT_USERNAME = "octocat";



    private ActivityMainBinding binding;
    private FollowersListFragment followersFragment;
    private RepositoriesListFragment repositoriesFragment;

    private TextWatcher searchWatcher;
    private final List<GitHubUserProfileDataEntry> allFollowers = new ArrayList<>();
    private final List<ReposDataEntry> allRepositories = new ArrayList<>();
    private final ExecutorService networkExecutor = Executors.newFixedThreadPool(2);
    private UserRepository userRepository;
    private RepoRepository repoRepository;
    private String activeUsername = DEFAULT_USERNAME;
    private boolean followersLoading;
    private boolean repositoriesLoading;
    private CharSequence followersSummaryText;
    private CharSequence repositoriesSummaryText;
    private String currentQuery = "";
    private int selectedNavigationItemId = R.id.nav_home;



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initialiseDataSources();
        activeUsername = resolveInitialUsername();
        followersSummaryText = getString(R.string.followers_loading_state);
        repositoriesSummaryText = getString(R.string.repositories_loading_state);

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

        loadFollowersFromApi();
        loadRepositoriesFromApi();
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
            binding.toolbar.setSubtitle(getString(R.string.main_subtitle_username, activeUsername));
        }
    }

    private void showFollowersScreen() {
        binding.toolbar.setTitle(R.string.main_title);
        binding.searchInputLayout.setVisibility(View.VISIBLE);
        binding.resultsSummary.setVisibility(View.VISIBLE);
        CharSequence summary = followersSummaryText;
        if (summary == null) {
            summary = followersLoading
                    ? getString(R.string.followers_loading_state)
                    : getString(R.string.followers_results_empty);
            followersSummaryText = summary;
        }
        binding.resultsSummary.setText(summary);

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
        CharSequence summary = repositoriesSummaryText;
        if (summary == null) {
            summary = repositoriesLoading
                    ? getString(R.string.repositories_loading_state)
                    : getString(R.string.repositories_empty_state);
            repositoriesSummaryText = summary;
        }
        binding.resultsSummary.setText(summary);

        repositoriesFragment = RepositoriesListFragment.newInstance();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, repositoriesFragment, RepositoriesListFragment.TAG)
                .commit();
        repositoriesFragment.submitList(allRepositories);
    }

    private void initialiseDataSources() {
        ApiClient apiClient = new ApiClient();
        GithubApiService apiService = apiClient.createService(GithubApiService.class);
        userRepository = new UserRepository(apiService, ServiceLocator.getInstance().userMapper());
        repoRepository = new RepoRepository(apiService, ServiceLocator.getInstance().repoMapper());
    }

    @NonNull
    private String resolveInitialUsername() {
        UserSessionData session = ServiceLocator.getInstance().authRepository().getCachedSession();
        if (session != null && !TextUtils.isEmpty(session.getUsername())) {
            return session.getUsername();
        }
        return DEFAULT_USERNAME;
    }

    private void loadFollowersFromApi() {
        if (userRepository == null) {
            return;
        }
        followersLoading = true;
        setFollowersSummary(getString(R.string.followers_loading_state));
        networkExecutor.execute(() -> {
            try {
                List<GitHubUserProfileDataEntry> followers = userRepository.fetchFollowers(activeUsername);
                runOnUiThread(() -> {
                    if (binding == null) {
                        return;
                    }
                    followersLoading = false;
                    allFollowers.clear();
                    allFollowers.addAll(followers);
                    filterFollowers(currentQuery);
                });
            } catch (IOException exception) {
                runOnUiThread(() -> {
                    if (binding == null) {
                        return;
                    }
                    followersLoading = false;
                    allFollowers.clear();
                    if (followersFragment != null) {
                        followersFragment.submitList(Collections.emptyList());
                    }
                    setFollowersSummary(getString(R.string.followers_error_state));
                    Toast.makeText(MainActivity.this, getString(R.string.followers_error_state), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadRepositoriesFromApi() {
        if (repoRepository == null) {
            return;
        }
        repositoriesLoading = true;
        setRepositoriesSummary(getString(R.string.repositories_loading_state));
        networkExecutor.execute(() -> {
            try {
                List<ReposDataEntry> repositories = repoRepository.fetchUserRepositories(activeUsername);
                runOnUiThread(() -> {
                    if (binding == null) {
                        return;
                    }
                    repositoriesLoading = false;
                    allRepositories.clear();
                    allRepositories.addAll(repositories);
                    updateRepositoriesSummary();
                    if (repositoriesFragment != null) {
                        repositoriesFragment.submitList(allRepositories);
                    }
                });
            } catch (IOException exception) {
                runOnUiThread(() -> {
                    if (binding == null) {
                        return;
                    }
                    repositoriesLoading = false;
                    allRepositories.clear();
                    if (repositoriesFragment != null) {
                        repositoriesFragment.submitList(Collections.emptyList());
                    }
                    setRepositoriesSummary(getString(R.string.repositories_error_state));
                    Toast.makeText(MainActivity.this, getString(R.string.repositories_error_state), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void updateRepositoriesSummary() {
        if (repositoriesLoading) {
            setRepositoriesSummary(getString(R.string.repositories_loading_state));
            return;
        }
        if (allRepositories.isEmpty()) {
            setRepositoriesSummary(getString(R.string.repositories_empty_state));
        } else {
            setRepositoriesSummary(getString(R.string.repositories_results_count, allRepositories.size()));
        }
    }

    private void setFollowersSummary(@NonNull CharSequence summary) {
        followersSummaryText = summary;
        if (binding != null && selectedNavigationItemId == R.id.nav_home) {
            binding.resultsSummary.setText(summary);
        }
    }

    private void setRepositoriesSummary(@NonNull CharSequence summary) {
        repositoriesSummaryText = summary;
        if (binding != null && selectedNavigationItemId == R.id.nav_repositories) {
            binding.resultsSummary.setText(summary);
        }
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
        if (followersLoading) {
            setFollowersSummary(getString(R.string.followers_loading_state));
            if (followersFragment != null) {
                followersFragment.submitList(Collections.emptyList());
            }
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
        CharSequence summary;
        if (visibleCount == 0) {
            summary = getString(R.string.followers_results_empty);
        } else if (trimmedQuery.isEmpty()) {
            summary = getString(R.string.followers_results_all, visibleCount);
        } else {
            summary = getString(R.string.followers_results_filtered, visibleCount, allFollowers.size());
        }
        setFollowersSummary(summary);
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
        networkExecutor.shutdownNow();
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