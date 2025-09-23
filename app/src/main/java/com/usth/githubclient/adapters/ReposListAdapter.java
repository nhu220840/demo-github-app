package com.usth.githubclient.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.usth.githubclient.R;
import com.usth.githubclient.databinding.RepositoriesListItemBinding;
import com.usth.githubclient.domain.model.ReposDataEntry;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter responsible for rendering repository entries inside a RecyclerView.
 */
public class ReposListAdapter extends ListAdapter<ReposDataEntry, ReposListAdapter.RepositoryViewHolder> {

    private static final DiffUtil.ItemCallback<ReposDataEntry> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ReposDataEntry>() {
                @Override
                public boolean areItemsTheSame(@NonNull ReposDataEntry oldItem,
                                               @NonNull ReposDataEntry newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull ReposDataEntry oldItem,
                                                  @NonNull ReposDataEntry newItem) {
                    return oldItem.equals(newItem);
                }
            };

    private final OnRepositoryClickListener listener;

    public ReposListAdapter(@NonNull OnRepositoryClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public RepositoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        RepositoriesListItemBinding binding =
                RepositoriesListItemBinding.inflate(inflater, parent, false);
        return new RepositoryViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RepositoryViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class RepositoryViewHolder extends RecyclerView.ViewHolder {

        private final RepositoriesListItemBinding binding;
        private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.getDefault());

        RepositoryViewHolder(@NonNull RepositoriesListItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ReposDataEntry repository) {
            binding.repositoryName.setText(repository.getName());

            String fullName = repository.getFullName().orElse(null);
            if (TextUtils.isEmpty(fullName) || fullName.equals(repository.getName())) {
                binding.repositoryFullName.setVisibility(View.GONE);
                binding.repositoryFullName.setText(null);
            } else {
                binding.repositoryFullName.setVisibility(View.VISIBLE);
                binding.repositoryFullName.setText(fullName);
            }

            String description = repository.getDescription().orElse(null);
            if (TextUtils.isEmpty(description)) {
                binding.repositoryDescription.setVisibility(View.GONE);
                binding.repositoryDescription.setText(null);
            } else {
                binding.repositoryDescription.setVisibility(View.VISIBLE);
                binding.repositoryDescription.setText(description);
            }

            bindMeta(repository);
            bindStats(repository);

            itemView.setOnClickListener(v -> listener.onRepositoryClicked(repository));
        }

        private void bindMeta(@NonNull ReposDataEntry repository) {
            List<String> metaParts = new ArrayList<>();
            repository.getLanguage().ifPresent(language -> {
                if (!TextUtils.isEmpty(language)) {
                    metaParts.add(language);
                }
            });
            repository.getDefaultBranch().ifPresent(branch -> {
                if (!TextUtils.isEmpty(branch)) {
                    metaParts.add(branch);
                }
            });

            if (metaParts.isEmpty()) {
                binding.repositoryMeta.setVisibility(View.GONE);
                binding.repositoryMeta.setText(null);
            } else {
                binding.repositoryMeta.setVisibility(View.VISIBLE);
                binding.repositoryMeta.setText(TextUtils.join(" • ", metaParts));
            }
        }

        private void bindStats(@NonNull ReposDataEntry repository) {
            List<String> stats = new ArrayList<>();
            stats.add(formatStat(R.string.repository_stats_stars, repository.getStargazersCount()));
            stats.add(formatStat(R.string.repository_stats_forks, repository.getForksCount()));
            stats.add(formatStat(R.string.repository_stats_watchers, repository.getWatchersCount()));
            stats.add(formatStat(R.string.repository_stats_open_issues, repository.getOpenIssuesCount()));
            binding.repositoryStats.setText(TextUtils.join(" • ", stats));
        }

        private String formatStat(int labelResId, int count) {
            String formattedCount = numberFormat.format(Math.max(count, 0));
            return itemView.getContext().getString(labelResId, formattedCount);
        }
    }

    /**
     * Callback invoked when a repository item is tapped.
     */
    public interface OnRepositoryClickListener {
        void onRepositoryClicked(@NonNull ReposDataEntry repository);
    }
}