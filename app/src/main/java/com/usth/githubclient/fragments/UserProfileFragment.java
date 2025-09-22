package com.usth.githubclient.fragments;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.usth.githubclient.R;
import com.usth.githubclient.databinding.FragmentUserProfileBinding;
import com.usth.githubclient.domain.model.GitHubUserProfileDataEntry;
import com.usth.githubclient.viewmodel.UserViewModel;

import java.text.DateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

/**
 * Renders the detailed information of a GitHub account including optional attributes.
 */
public class UserProfileFragment extends Fragment {

    public static final String TAG = "UserProfileFragment";
    private static final String ARG_USERNAME = "arg_username";

    private FragmentUserProfileBinding binding;
    private UserViewModel viewModel;

    public static UserProfileFragment newInstance(@Nullable String username) {
        UserProfileFragment fragment = new UserProfileFragment();
        Bundle args = new Bundle();
        if (!TextUtils.isEmpty(username)) {
            args.putString(ARG_USERNAME, username);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentUserProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(UserViewModel.class);
        // Lắng nghe trạng thái UI từ ViewModel và gọi hàm renderState để cập nhật giao diện.
        // Đây là cách tiếp cận rất tốt để quản lý trạng thái.
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);

        String username = getArguments() == null ? null : getArguments().getString(ARG_USERNAME);
        viewModel.loadUserProfile(username);
    }

    /**
     * Cập nhật giao diện dựa trên trạng thái (State) từ ViewModel.
     * Hàm này xử lý tất cả các trường hợp: đang tải, lỗi, và thành công.
     */
    private void renderState(@Nullable UserViewModel.UserUiState state) {
        if (binding == null || state == null) {
            return;
        }

        // Hiển thị/ẩn ProgressBar dựa vào trạng thái isLoading.
        binding.progressBar.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);

        // **ĐIỂM SỬA LỖI QUAN TRỌNG**: Xử lý rõ ràng trường hợp có lỗi.
        // Nếu có thông báo lỗi, hiển thị container lỗi và ẩn nội dung chính.
        if (state.getErrorMessage() != null) {
            binding.contentScroll.setVisibility(View.GONE);
            binding.errorContainer.setVisibility(View.VISIBLE);
            binding.errorMessage.setText(state.getErrorMessage());
        } else {
            binding.errorContainer.setVisibility(View.GONE);
        }

        // Chỉ hiển thị dữ liệu profile khi có dữ liệu và không có lỗi.
        GitHubUserProfileDataEntry profile = state.getProfile();
        if (profile != null) {
            binding.contentScroll.setVisibility(View.VISIBLE);
            binding.mockBanner.setVisibility(state.isUsingMockData() ? View.VISIBLE : View.GONE);
            bindProfile(profile);
        } else if (state.getErrorMessage() == null) {
            binding.contentScroll.setVisibility(View.GONE);
        }
    }

    private void bindProfile(@NonNull GitHubUserProfileDataEntry profile) {
        String displayName = profile.getDisplayName().orElse(profile.getUsername());
        binding.displayName.setText(displayName);
        binding.username.setText(getString(R.string.user_profile_username_format, profile.getUsername()));

        updateTextOrHide(binding.bio, profile.getBio().orElse(null));

        Glide.with(binding.avatar)
                .load(profile.getAvatarUrl().orElse(null))
                .placeholder(R.drawable.ic_avatar_placeholder)
                .error(R.drawable.ic_avatar_placeholder)
                .circleCrop()
                .into(binding.avatar);

        binding.repositoriesValue.setText(String.valueOf(profile.getPublicReposCount()));
        binding.followersValue.setText(String.valueOf(profile.getFollowersCount()));
        binding.followingValue.setText(String.valueOf(profile.getFollowingCount()));

        updateRow(binding.locationGroup, binding.locationValue, profile.getLocation().orElse(null));
        updateRow(binding.companyGroup, binding.companyValue, profile.getCompany().orElse(null));
        updateRow(binding.emailGroup, binding.emailValue, profile.getEmail().orElse(null));
        updateLinkRow(binding.blogGroup, binding.blogValue, profile.getBlogUrl().orElse(null));
        updateLinkRow(binding.profileGroup, binding.profileValue, profile.getProfileUrl().orElse(null));

        updateRow(binding.createdAtGroup, binding.createdAtValue, formatDate(profile.getCreatedAt().orElse(null)));
        updateRow(binding.updatedAtGroup, binding.updatedAtValue, formatDate(profile.getUpdatedAt().orElse(null)));

        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(displayName);
            activity.getSupportActionBar().setSubtitle(
                    getString(R.string.user_profile_username_format, profile.getUsername()));
        }
    }

    private void updateTextOrHide(@NonNull TextView view, @Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            view.setVisibility(View.GONE);
            view.setText(null);
        } else {
            view.setVisibility(View.VISIBLE);
            view.setText(value);
        }
    }

    private void updateRow(@NonNull View group, @NonNull TextView valueView, @Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            group.setVisibility(View.GONE);
            valueView.setText(null);
        } else {
            group.setVisibility(View.VISIBLE);
            valueView.setText(value);
        }
    }

    private void updateLinkRow(@NonNull View group, @NonNull TextView valueView, @Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            group.setVisibility(View.GONE);
            valueView.setText(null);
            valueView.setOnClickListener(null);
            return;
        }
        group.setVisibility(View.VISIBLE);
        valueView.setText(value);
        String preparedUrl = prepareUrl(value);
        valueView.setOnClickListener(v -> openLink(preparedUrl));
    }

    @Nullable
    private String formatDate(@Nullable Instant instant) {
        if (instant == null) {
            return null;
        }
        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
        return dateFormat.format(Date.from(instant));
    }

    @NonNull
    private String prepareUrl(@NonNull String raw) {
        String trimmed = raw.trim();
        if (TextUtils.isEmpty(trimmed)) {
            return raw;
        }
        Uri uri = Uri.parse(trimmed);
        if (uri.getScheme() == null) {
            return "https://" + trimmed;
        }
        return trimmed;
    }

    private void openLink(@NonNull String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
            }
        } catch (ActivityNotFoundException ignored) {
            // Silently swallow if no browser is available.
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}