package com.usth.githubclient.viewmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.usth.githubclient.data.remote.ApiClient;
import com.usth.githubclient.data.remote.GithubApiService;
import com.usth.githubclient.data.repository.AuthRepository;
import com.usth.githubclient.data.repository.UserRepository;
import com.usth.githubclient.di.ServiceLocator;
import com.usth.githubclient.domain.model.GitHubUserProfileDataEntry;
import com.usth.githubclient.domain.model.MockDataFactory;
import com.usth.githubclient.domain.model.UserSessionData;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel responsible for loading a GitHub profile and exposing UI-friendly state.
 */
public class UserViewModel extends ViewModel {

    private final MutableLiveData<UserUiState> uiState = new MutableLiveData<>(UserUiState.idle());
    private final ExecutorService executorService;
    private final AuthRepository authRepository;
    private final UserRepository userRepository;

    private String currentUsername;

    // **BIẾN ĐIỀU KHIỂN**: Đặt là 'true' để BẬT chế độ mock data
    private static final boolean FORCE_MOCK_DATA = true;

    public UserViewModel() {
        this(ServiceLocator.getInstance().authRepository(), buildDefaultUserRepository());
    }

    public UserViewModel(@NonNull AuthRepository authRepository,
                         @NonNull UserRepository userRepository) {
        this.authRepository = Objects.requireNonNull(authRepository, "authRepository == null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository == null");
        this.executorService = Executors.newSingleThreadExecutor();
    }

    private static UserRepository buildDefaultUserRepository() {
        ApiClient apiClient = new ApiClient();
        GithubApiService service = apiClient.createService(GithubApiService.class);
        return new UserRepository(service, ServiceLocator.getInstance().userMapper());
    }

    public LiveData<UserUiState> getUiState() {
        return uiState;
    }

    public void loadUserProfile(@Nullable String username) {
        // **THAY ĐỔI QUAN TRỌNG**: Kiểm tra biến FORCE_MOCK_DATA.
        // Nếu là 'true', gọi useMockProfile() và dừng lại ngay lập tức.
        if (FORCE_MOCK_DATA) {
            useMockProfile();
            return;
        }

        // --- Logic cũ để tải dữ liệu thật ---
        String normalized = username == null ? "" : username.trim();

        if (normalized.isEmpty()) {
            UserSessionData session = authRepository.getCachedSession();
            if (session != null) {
                Optional<GitHubUserProfileDataEntry> profile = session.getUserProfile();
                if (profile.isPresent()) {
                    currentUsername = profile.get().getUsername();
                    uiState.setValue(UserUiState.success(profile.orElse(null), false));
                    return;
                }
                normalized = session.getUsername();
            } else {
                useMockProfile();
                return;
            }
        }

        if (normalized.equalsIgnoreCase(currentUsername)
                && uiState.getValue() != null
                && uiState.getValue().getProfile() != null) {
            return;
        }

        currentUsername = normalized;
        uiState.setValue(UserUiState.loading());

        final String requestedUsername = normalized;
        executorService.execute(() -> {
            try {
                GitHubUserProfileDataEntry profile = userRepository.fetchUserProfile(requestedUsername);
                uiState.postValue(UserUiState.success(profile, false));
            } catch (IOException exception) {
                String message = exception.getMessage();
                if (message == null || message.trim().isEmpty()) {
                    message = "Unable to load this profile right now.";
                }
                uiState.postValue(UserUiState.error(message));
            }
        });
    }

    public void retry() {
        if (currentUsername == null && uiState.getValue() != null
                && uiState.getValue().getProfile() != null) {
            return;
        }
        loadUserProfile(currentUsername);
    }

    public void useMockProfile() {
        GitHubUserProfileDataEntry mockProfile = MockDataFactory.mockUserProfile();
        currentUsername = mockProfile.getUsername();
        uiState.setValue(UserUiState.success(mockProfile, true));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdownNow();
    }

    /**
     * Immutable representation of the UI state exposed to the fragment.
     */
    public static final class UserUiState {
        // ... (phần còn lại của lớp UserUiState không thay đổi)
        private final boolean loading;
        private final GitHubUserProfileDataEntry profile;
        private final String errorMessage;
        private final boolean usingMockData;

        private UserUiState(boolean loading,
                            GitHubUserProfileDataEntry profile,
                            String errorMessage,
                            boolean usingMockData) {
            this.loading = loading;
            this.profile = profile;
            this.errorMessage = errorMessage;
            this.usingMockData = usingMockData;
        }

        public static UserUiState idle() {
            return new UserUiState(false, null, null, false);
        }

        public static UserUiState loading() {
            return new UserUiState(true, null, null, false);
        }

        public static UserUiState success(@NonNull GitHubUserProfileDataEntry profile, boolean usingMockData) {
            return new UserUiState(false, Objects.requireNonNull(profile, "profile == null"), null, usingMockData);
        }

        public static UserUiState error(@NonNull String message) {
            return new UserUiState(false, null, Objects.requireNonNull(message, "message == null"), false);
        }

        public boolean isLoading() {
            return loading;
        }

        @Nullable
        public GitHubUserProfileDataEntry getProfile() {
            return profile;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isUsingMockData() {
            return usingMockData;
        }
    }
}