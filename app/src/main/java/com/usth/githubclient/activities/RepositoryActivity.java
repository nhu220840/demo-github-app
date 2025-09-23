package com.usth.githubclient.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

// 1. Import class Binding
import com.usth.githubclient.databinding.ActivityRepositoryBinding;
import com.usth.githubclient.domain.model.ReposDataEntry;
import com.usth.githubclient.fragments.RepositoriesListFragment;
import com.usth.githubclient.viewmodel.RepoViewModel;

import java.util.Locale;

public class RepositoryActivity extends AppCompatActivity implements
        RepositoriesListFragment.OnRepositorySelectedListener {

    private RepoViewModel viewModel;

    // 2. Khai báo biến binding
    private ActivityRepositoryBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 3. Khởi tạo binding và set content view
        binding = ActivityRepositoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(RepoViewModel.class);

        // 4. Lắng nghe sự thay đổi và cập nhật UI thông qua binding
        viewModel.getRepositoryDetailState().observe(this, detailState -> {
            if (detailState.hasRepository()) {
                ReposDataEntry repo = detailState.getRepository();

                // Sử dụng binding để truy cập các View
                binding.repoName.setText(repo.getName());
                binding.repoDescription.setText(repo.getDescription().orElse("No description available."));
                binding.repoLanguage.setText(String.format("Language: %s", repo.getLanguage().orElse("N/A")));
                binding.repoStars.setText(String.format(Locale.US, "Stars: %d", repo.getStargazersCount()));
                binding.repoForks.setText(String.format(Locale.US, "Forks: %d", repo.getForksCount()));

                // Hiển thị badge nếu là mock data
                binding.mockDataBadge.setVisibility(detailState.isUsingMockData() ? View.VISIBLE : View.GONE);
            }
        });

        // Load repositories lần đầu
        if (savedInstanceState == null) {
            viewModel.loadRepositories(null);
        }
    }

    @Override
    public void onRepositorySelected(ReposDataEntry repository) {
        viewModel.selectRepository(repository);
        // Toast này không còn cần thiết nữa, có thể xóa đi
        // Toast.makeText(this, "Repo selected: " + repository.getName(), Toast.LENGTH_SHORT).show();
    }
}