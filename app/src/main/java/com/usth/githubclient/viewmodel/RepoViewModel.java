package com.usth.githubclient.viewmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.usth.githubclient.data.remote.ApiClient;
import com.usth.githubclient.data.remote.GithubApiService;
import com.usth.githubclient.data.repository.AuthRepository;
import com.usth.githubclient.data.repository.RepoRepository;
import com.usth.githubclient.di.ServiceLocator;
import com.usth.githubclient.domain.model.MockDataFactory;
import com.usth.githubclient.domain.model.ReposDataEntry;
import com.usth.githubclient.domain.model.UserSessionData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel responsible for loading repositories and exposing UI-friendly state.
 */
public class RepoViewModel extends ViewModel {

//    private static final boolean FORCE_MOCK_DATA = false;
    private static final String FALLBACK_USERNAME = "octocat";


    private final MutableLiveData<RepositoriesUiState> repositoriesState =
            new MutableLiveData<>(RepositoriesUiState.idle());
    private final MutableLiveData<RepositoryDetailUiState> repositoryDetailState =
            new MutableLiveData<>(RepositoryDetailUiState.empty());

    private final ExecutorService executorService;
    private final RepoRepository repoRepository;
    private final AuthRepository authRepository;

    private String currentUsername;
    private Long selectedRepositoryId;

    public RepoViewModel() {
        this(ServiceLocator.getInstance().authRepository(), buildDefaultRepoRepository());
    }

    public RepoViewModel(@NonNull AuthRepository authRepository,
                         @NonNull RepoRepository repoRepository) {
        this.authRepository = Objects.requireNonNull(authRepository, "authRepository == null");
        this.repoRepository = Objects.requireNonNull(repoRepository, "repoRepository == null");
        this.executorService = Executors.newSingleThreadExecutor();
    }

    private static RepoRepository buildDefaultRepoRepository() {
        ApiClient apiClient = new ApiClient();
        GithubApiService service = apiClient.createService(GithubApiService.class);
        return new RepoRepository(service, ServiceLocator.getInstance().repoMapper());
    }

    public LiveData<RepositoriesUiState> getRepositoriesState() {
        return repositoriesState;
    }

    public LiveData<RepositoryDetailUiState> getRepositoryDetailState() {
        return repositoryDetailState;
    }

    public void loadRepositories(@Nullable String username) {

        String normalized = username == null ? "" : username.trim();
        if (normalized.isEmpty()) {
            UserSessionData session = authRepository.getCachedSession();
            if (session != null) {
                currentUsername = session.getUsername();
                List<ReposDataEntry> cachedRepositories = session.getRepositories();
                if (!cachedRepositories.isEmpty()) {
                    emitRepositoriesSuccess(cachedRepositories, false, false);
                    return;
                }
                normalized = session.getUsername();
            }
            if (normalized == null || normalized.trim().isEmpty()) {
                normalized = FALLBACK_USERNAME;
            }
        }

        if (normalized.isEmpty()) {
            normalized = FALLBACK_USERNAME;
        }

        if (normalized.equalsIgnoreCase(currentUsername)
                && repositoriesState.getValue() != null
                && !repositoriesState.getValue().getRepositories().isEmpty()) {
            return;
        }

        currentUsername = normalized;
        RepositoriesUiState currentState = repositoriesState.getValue();
        List<ReposDataEntry> existing = currentState == null
                ? Collections.emptyList()
                : currentState.getRepositories();
        boolean usingMock = currentState != null && currentState.isUsingMockData();
        repositoriesState.setValue(RepositoriesUiState.loading(existing, usingMock));

        String finalNormalized = normalized;
        executorService.execute(() -> {
            try {
                List<ReposDataEntry> repositories = repoRepository.fetchUserRepositories(finalNormalized);
                emitRepositoriesSuccess(repositories, false, true);
            } catch (IOException exception) {
                String message = exception.getMessage();
                if (message == null || message.trim().isEmpty()) {
                    message = "Unable to load repositories right now.";
                }
                repositoriesState.postValue(
                        RepositoriesUiState.error(message, existing, usingMock));
            }
        });
    }

    public void retry() {
        loadRepositories(currentUsername);
    }

    public void selectRepository(@Nullable ReposDataEntry repository) {
        if (repository == null) {
            selectedRepositoryId = null;
            repositoryDetailState.setValue(RepositoryDetailUiState.empty());
            return;
        }
        selectedRepositoryId = repository.getId();
        boolean usingMock = repositoriesState.getValue() != null
                && repositoriesState.getValue().isUsingMockData();
        repositoryDetailState.setValue(RepositoryDetailUiState.from(repository, usingMock));
    }

    private void emitRepositoriesSuccess(@NonNull List<ReposDataEntry> repositories,
                                         boolean usingMockData,
                                         boolean fromBackgroundThread) {
        List<ReposDataEntry> copy = Collections.unmodifiableList(new ArrayList<>(repositories));
        if (fromBackgroundThread) {
            repositoriesState.postValue(RepositoriesUiState.success(copy, usingMockData));
        } else {
            repositoriesState.setValue(RepositoriesUiState.success(copy, usingMockData));
        }
        emitSelectionFromList(copy, usingMockData, fromBackgroundThread);
    }

    private void emitSelectionFromList(@NonNull List<ReposDataEntry> repositories,
                                       boolean usingMockData,
                                       boolean fromBackgroundThread) {
        if (repositories.isEmpty()) {
            selectedRepositoryId = null;
            if (fromBackgroundThread) {
                repositoryDetailState.postValue(RepositoryDetailUiState.empty());
            } else {
                repositoryDetailState.setValue(RepositoryDetailUiState.empty());
            }
            return;
        }

        ReposDataEntry selected = null;
        if (selectedRepositoryId != null) {
            for (ReposDataEntry entry : repositories) {
                if (entry.getId() == selectedRepositoryId) {
                    selected = entry;
                    break;
                }
            }
        }
        if (selected == null) {
            selected = repositories.get(0);
            selectedRepositoryId = selected.getId();
        }
        RepositoryDetailUiState newState = RepositoryDetailUiState.from(selected, usingMockData);
        if (fromBackgroundThread) {
            repositoryDetailState.postValue(newState);
        } else {
            repositoryDetailState.setValue(newState);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdownNow();
    }

    /**
     * Immutable UI state representing the repository list.
     */
    public static final class RepositoriesUiState {
        private final boolean loading;
        private final List<ReposDataEntry> repositories;
        private final String errorMessage;
        private final boolean usingMockData;

        private RepositoriesUiState(boolean loading,
                                    @NonNull List<ReposDataEntry> repositories,
                                    @Nullable String errorMessage,
                                    boolean usingMockData) {
            this.loading = loading;
            this.repositories = Collections.unmodifiableList(new ArrayList<>(repositories));
            this.errorMessage = errorMessage;
            this.usingMockData = usingMockData;
        }

        public static RepositoriesUiState idle() {
            return new RepositoriesUiState(false, Collections.emptyList(), null, false);
        }

        public static RepositoriesUiState loading(@NonNull List<ReposDataEntry> existing,
                                                  boolean usingMockData) {
            return new RepositoriesUiState(true, existing, null, usingMockData);
        }

        public static RepositoriesUiState success(@NonNull List<ReposDataEntry> repositories,
                                                  boolean usingMockData) {
            return new RepositoriesUiState(false, repositories, null, usingMockData);
        }

        public static RepositoriesUiState error(@NonNull String message,
                                                @NonNull List<ReposDataEntry> existing,
                                                boolean usingMockData) {
            return new RepositoriesUiState(false, existing, message, usingMockData);
        }

        public boolean isLoading() {
            return loading;
        }

        @NonNull
        public List<ReposDataEntry> getRepositories() {
            return repositories;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isUsingMockData() {
            return usingMockData;
        }
    }

    /**
     * Immutable representation of the currently selected repository.
     */
    public static final class RepositoryDetailUiState {
        private final ReposDataEntry repository;
        private final boolean usingMockData;

        private RepositoryDetailUiState(@Nullable ReposDataEntry repository,
                                        boolean usingMockData) {
            this.repository = repository;
            this.usingMockData = usingMockData;
        }

        public static RepositoryDetailUiState empty() {
            return new RepositoryDetailUiState(null, false);
        }

        public static RepositoryDetailUiState from(@NonNull ReposDataEntry repository,
                                                   boolean usingMockData) {
            return new RepositoryDetailUiState(repository, usingMockData);
        }

        @Nullable
        public ReposDataEntry getRepository() {
            return repository;
        }

        public boolean hasRepository() {
            return repository != null;
        }

        public boolean isUsingMockData() {
            return usingMockData;
        }
    }
}