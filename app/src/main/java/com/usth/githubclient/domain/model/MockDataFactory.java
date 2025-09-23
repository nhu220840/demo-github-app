package com.usth.githubclient.domain.model;

import android.annotation.SuppressLint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper factory that produces deterministic mock domain models so that the UI layer can be built
 * and validated without talking to the real GitHub API.
 */
@SuppressLint("NewApi")
public final class MockDataFactory {

    private static final Instant USER_CREATED_AT = Instant.parse("2011-01-25T18:44:36Z");
    private static final Instant USER_UPDATED_AT = Instant.parse("2024-02-20T07:20:45Z");

    private static final Instant REPO_ONE_CREATED_AT = Instant.parse("2023-05-10T08:00:00Z");
    private static final Instant REPO_ONE_UPDATED_AT = Instant.parse("2024-02-18T16:30:00Z");
    private static final Instant REPO_ONE_PUSHED_AT = Instant.parse("2024-02-21T09:15:00Z");

    private static final Instant REPO_TWO_CREATED_AT = Instant.parse("2022-07-05T07:45:00Z");
    private static final Instant REPO_TWO_UPDATED_AT = Instant.parse("2024-01-05T11:20:00Z");
    private static final Instant REPO_TWO_PUSHED_AT = Instant.parse("2024-02-28T17:10:00Z");

    private static final Instant REPO_THREE_CREATED_AT = Instant.parse("2021-11-12T05:30:00Z");
    private static final Instant REPO_THREE_UPDATED_AT = Instant.parse("2024-02-12T19:40:00Z");
    private static final Instant REPO_THREE_PUSHED_AT = Instant.parse("2024-03-02T13:05:00Z");

    private static final Instant SESSION_EXPIRES_AT = Instant.parse("2024-12-31T23:59:59Z");
    private static final Instant SESSION_LAST_SYNC_AT = Instant.parse("2024-03-15T14:45:00Z");

    private MockDataFactory() {
        // Utility class.
    }

    /**
     * Creates a mock GitHub user profile matching the format expected by the domain layer.
     */
    public static GitHubUserProfileDataEntry mockUserProfile() {
        return GitHubUserProfileDataEntry.builder(583231L, "octocat")
                .displayName("The Octocat")
                .avatarUrl("https://avatars.githubusercontent.com/u/583231?v=4")
                .bio("Friendly mock account for UI previews. Loves Kotlin and espresso tests.")
                .company("GitHub")
                .blogUrl("https://github.blog")
                .email("octocat@github.com")
                .location("San Francisco, CA")
                .publicReposCount(42)
                .followersCount(3580)
                .followingCount(9)
                .profileUrl("https://github.com/octocat")
                .createdAt(USER_CREATED_AT)
                .updatedAt(USER_UPDATED_AT)
                .build();
    }

    /**
     * Creates a deterministic list of repositories owned by the mock profile.
     */
    public static List<ReposDataEntry> mockRepositories() {
        return mockRepositories(mockUserProfile());
    }

    /**
     * Creates repositories for the provided owner so downstream code can reuse the same profile
     * instance when composing mock sessions.
     */
    public static List<ReposDataEntry> mockRepositories(GitHubUserProfileDataEntry owner) {
        GitHubUserProfileDataEntry repoOwner = owner == null ? mockUserProfile() : owner;

        List<ReposDataEntry> repositories = new ArrayList<>();

        repositories.add(ReposDataEntry.builder(1296269L, "hello-android",
                        "https://github.com/" + repoOwner.getUsername() + "/hello-android")
                .fullName(repoOwner.getUsername() + "/hello-android")
                .description("Sample Android project demonstrating UI states and repository usage.")
                .language("Kotlin")
                .stargazersCount(1280)
                .forksCount(320)
                .watchersCount(980)
                .openIssuesCount(12)
                .defaultBranch("main")
                .isPrivate(false)
                .isFork(false)
                .createdAt(REPO_ONE_CREATED_AT)
                .updatedAt(REPO_ONE_UPDATED_AT)
                .pushedAt(REPO_ONE_PUSHED_AT)
                .owner(repoOwner)
                .build());

        repositories.add(ReposDataEntry.builder(9876543L, "compose-showcase",
                        "https://github.com/" + repoOwner.getUsername() + "/compose-showcase")
                .fullName(repoOwner.getUsername() + "/compose-showcase")
                .description("Jetpack Compose components extracted from the GitHub Client UI.")
                .language("Kotlin")
                .stargazersCount(856)
                .forksCount(210)
                .watchersCount(640)
                .openIssuesCount(4)
                .defaultBranch("main")
                .isPrivate(false)
                .isFork(false)
                .createdAt(REPO_TWO_CREATED_AT)
                .updatedAt(REPO_TWO_UPDATED_AT)
                .pushedAt(REPO_TWO_PUSHED_AT)
                .owner(repoOwner)
                .build());

        repositories.add(ReposDataEntry.builder(5550001L, "github-analytics",
                        "https://github.com/" + repoOwner.getUsername() + "/github-analytics")
                .fullName(repoOwner.getUsername() + "/github-analytics")
                .description("Dashboard showcasing charts built entirely from mock GitHub metrics.")
                .language("TypeScript")
                .stargazersCount(642)
                .forksCount(98)
                .watchersCount(502)
                .openIssuesCount(7)
                .defaultBranch("develop")
                .isPrivate(false)
                .isFork(true)
                .createdAt(REPO_THREE_CREATED_AT)
                .updatedAt(REPO_THREE_UPDATED_AT)
                .pushedAt(REPO_THREE_PUSHED_AT)
                .owner(repoOwner)
                .build());

        // --- Dữ liệu mới thêm vào ---

        // 4. Repo với tên và mô tả rất dài
        repositories.add(ReposDataEntry.builder(5550002L, "this-is-a-repository-with-a-very-long-name-to-test-ui-wrapping",
                        "https://github.com/" + repoOwner.getUsername() + "/long-name-repo")
                .fullName(repoOwner.getUsername() + "/this-is-a-repository-with-a-very-long-name-to-test-ui-wrapping")
                .description("This is a mock description that is intentionally made very long to see how the user interface handles text overflow, wrapping, and truncation within the repository list item and detail views. It should ideally be handled gracefully without breaking the layout.")
                .language("Java")
                .stargazersCount(101)
                .forksCount(22)
                .watchersCount(50)
                .openIssuesCount(5)
                .defaultBranch("main")
                .isPrivate(false)
                .isFork(false)
                .owner(repoOwner)
                .build());

        // 5. Repo không có mô tả và ngôn ngữ
        repositories.add(ReposDataEntry.builder(5550003L, "minimal-repo",
                        "https://github.com/" + repoOwner.getUsername() + "/minimal-repo")
                .fullName(repoOwner.getUsername() + "/minimal-repo")
                .description(null) // No description
                .language(null)   // No language
                .stargazersCount(15)
                .forksCount(3)
                .watchersCount(10)
                .openIssuesCount(1)
                .defaultBranch("main")
                .isPrivate(false)
                .isFork(false)
                .owner(repoOwner)
                .build());

        // 6. Repo "mới tinh" với các chỉ số bằng 0
        repositories.add(ReposDataEntry.builder(5550004L, "brand-new-project",
                        "https://github.com/" + repoOwner.getUsername() + "/brand-new-project")
                .fullName(repoOwner.getUsername() + "/brand-new-project")
                .description("A freshly created repository for a new project idea.")
                .language("Python")
                .stargazersCount(0)
                .forksCount(0)
                .watchersCount(0)
                .openIssuesCount(0)
                .defaultBranch("main")
                .isPrivate(false)
                .isFork(false)
                .owner(repoOwner)
                .build());

        // 7. Repo private
        repositories.add(ReposDataEntry.builder(5550005L, "secret-project",
                        "https://github.com/" + repoOwner.getUsername() + "/secret-project")
                .fullName(repoOwner.getUsername() + "/secret-project")
                .description("A private repository for top-secret development work.")
                .language("Go")
                .stargazersCount(2)
                .forksCount(1)
                .watchersCount(1)
                .openIssuesCount(0)
                .defaultBranch("master")
                .isPrivate(true) // Mark as private
                .isFork(false)
                .owner(repoOwner)
                .build());


        return Collections.unmodifiableList(repositories);
    }

    /**
     * Provides a curated list of followers so that follower and following screens have data to
     * render while the networking layer is still under construction.
     */
    public static List<GitHubUserProfileDataEntry> mockFollowers() {
        List<GitHubUserProfileDataEntry> followers = new ArrayList<>();

        followers.add(GitHubUserProfileDataEntry.builder(1024L, "android-dev")
                .displayName("Android Dev")
                .avatarUrl("https://avatars.githubusercontent.com/u/1024?v=4")
                .bio("Builds delightful Android experiences and shares UI prototyping tips.")
                .company("JetBrains")
                .blogUrl("https://blog.jetbrains.com")
                .location("Amsterdam, NL")
                .publicReposCount(75)
                .followersCount(2500)
                .followingCount(120)
                .profileUrl("https://github.com/android-dev")
                .build());

        followers.add(GitHubUserProfileDataEntry.builder(2048L, "compose-wizard")
                .displayName("Compose Wizard")
                .avatarUrl("https://avatars.githubusercontent.com/u/2048?v=4")
                .bio("Turns product mocks into silky smooth Compose layouts in minutes.")
                .company("Compose Studio")
                .blogUrl("https://compose.studio/blog")
                .location("Berlin, Germany")
                .publicReposCount(34)
                .followersCount(980)
                .followingCount(75)
                .profileUrl("https://github.com/compose-wizard")
                .build());

        followers.add(GitHubUserProfileDataEntry.builder(4096L, "design-systems")
                .displayName("Design Systems Guild")
                .avatarUrl("https://avatars.githubusercontent.com/u/4096?v=4")
                .bio("Collective of designers and engineers working on reusable UI kits.")
                .company("Systems Co.")
                .blogUrl("https://design.systems")
                .location("New York, USA")
                .publicReposCount(120)
                .followersCount(5400)
                .followingCount(260)
                .profileUrl("https://github.com/design-systems")
                .build());

        return Collections.unmodifiableList(followers);
    }

    /**
     * Provides a ready-to-use user session that stitches together the mock profile and repositories
     * so authentication flows can be implemented without waiting for a backend.
     */
    public static UserSessionData mockUserSession() {
        GitHubUserProfileDataEntry profile = mockUserProfile();
        List<ReposDataEntry> repositories = mockRepositories(profile);

        return UserSessionData.builder(profile.getUsername(), "gho_mocktoken1234567890")
                .tokenType("Bearer")
                .accessTokenExpiration(SESSION_EXPIRES_AT)
                .userProfile(profile)
                .repositories(repositories)
                .lastSyncedAt(SESSION_LAST_SYNC_AT)
                .build();
    }
}