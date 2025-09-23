package com.usth.githubclient.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.usth.githubclient.R;
import com.usth.githubclient.adapters.ReposListAdapter;
import com.usth.githubclient.databinding.FragmentGeneralListBinding;
import com.usth.githubclient.domain.model.ReposDataEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Displays a scrollable list of repositories and notifies the host when one is selected.
 */
public class RepositoriesListFragment extends Fragment {

    public static final String TAG = "RepositoriesListFragment";

    private FragmentGeneralListBinding binding;
    private ReposListAdapter adapter;
    private OnRepositorySelectedListener listener;
    private List<ReposDataEntry> pendingRepositories = Collections.emptyList();

    public static RepositoriesListFragment newInstance() {
        return new RepositoriesListFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnRepositorySelectedListener) {
            listener = (OnRepositorySelectedListener) context;
        } else {
            throw new IllegalStateException("Host activity must implement OnRepositorySelectedListener");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGeneralListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupRecyclerView();
        if (binding != null) {
            binding.emptyView.setText(R.string.repositories_empty_state);
        }
        applyPendingRepositories();
        updateEmptyState();
    }

    private void setupRecyclerView() {
        adapter = new ReposListAdapter(repository -> {
            if (listener != null) {
                listener.onRepositorySelected(repository);
            }
        });
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setHasFixedSize(true);
        binding.recyclerView.setAdapter(adapter);
    }

    /**
     * Submit a new list of repositories to render. Safe to call before the view exists.
     */
    public void submitList(@Nullable List<ReposDataEntry> repositories) {
        if (repositories == null) {
            pendingRepositories = Collections.emptyList();
        } else {
            pendingRepositories = new ArrayList<>(repositories);
        }
        applyPendingRepositories();
    }

    private void applyPendingRepositories() {
        if (adapter == null) {
            return;
        }
        adapter.submitList(new ArrayList<>(pendingRepositories), this::updateEmptyState);
    }

    private void updateEmptyState() {
        if (binding == null) {
            return;
        }
        boolean isEmpty = adapter == null || adapter.getItemCount() == 0;
        binding.recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        binding.emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.recyclerView.setAdapter(null);
            binding = null;
        }
        adapter = null;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    /**
     * Notifies when a repository is tapped.
     */
    public interface OnRepositorySelectedListener {
        void onRepositorySelected(@NonNull ReposDataEntry repository);
    }
}