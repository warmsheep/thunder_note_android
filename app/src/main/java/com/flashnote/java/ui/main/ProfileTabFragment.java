package com.flashnote.java.ui.main;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;

import com.bumptech.glide.Glide;
import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.TokenManager;
import com.flashnote.java.data.model.FavoriteItem;
import com.flashnote.java.data.repository.PendingMessageRepository;
import com.flashnote.java.data.model.UserProfile;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.data.repository.MessageRepository;
import com.flashnote.java.data.repository.SyncRepository;
import com.flashnote.java.data.repository.UserRepository;
import com.flashnote.java.R;
import com.flashnote.java.ui.media.MediaUrlResolver;
import com.flashnote.java.ui.auth.AuthViewModel;
import com.flashnote.java.ui.navigation.ShellNavigator;
import androidx.lifecycle.ViewModelProvider;

import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.List;

public class ProfileTabFragment extends Fragment {
    private com.flashnote.java.databinding.FragmentProfileTabBinding binding;
    private UserRepository userRepository;
    private FileRepository fileRepository;
    private TokenManager tokenManager;
    private SyncRepository syncRepository;
    private PendingMessageRepository pendingMessageRepository;
    private final ProfileStatsHelper statsHelper = new ProfileStatsHelper();
    private final ProfileSyncUiHelper syncUiHelper = new ProfileSyncUiHelper();
    private final ProfileEditHelper editHelper = new ProfileEditHelper();
    private final ProfileAvatarHelper avatarHelper = new ProfileAvatarHelper();
    private UserProfile currentProfile;
    private LiveData<Integer> pendingSyncCountLiveData;
    private boolean syncInProgress;
    private int pendingSyncCount;
    
    private static final String[] AVATAR_EMOJIS = {"💼", "📚", "❤️", "🌟", "🎯", "🚀", "🎨", "🎵", "📷", "🍕", "⚽", "😊"};
    private static final int AVATAR_MAX_SIZE = 512;

    // Activity result launchers
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        startCrop(imageUri);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> ucropLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri resultUri = UCrop.getOutput(result.getData());
                    if (resultUri != null) {
                        handleCropResult(resultUri);
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = com.flashnote.java.databinding.FragmentProfileTabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FlashNoteApp app = FlashNoteApp.getInstance();
        userRepository = app.getUserRepository();
        fileRepository = app.getFileRepository();
        tokenManager = app.getTokenManager();
        syncRepository = app.getSyncRepository();
        pendingMessageRepository = app.getPendingMessageRepository();

        String username = tokenManager.getUsername();
        if (username != null) {
            binding.usernameText.setText(username);
        } else {
            binding.usernameText.setText(R.string.status_unknown_user);
        }

        userRepository.getProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile != null) {
                currentProfile = profile;
                updateProfileUI(profile);
            }
        });

        loadCachedRecordCount();
        loadLocalAvatar();
        observePendingSyncCount();

        fetchProfile();
        setupClickActions();
    }

    private void setupClickActions() {
        if (binding == null) {
            return;
        }
        binding.avatarContainer.setOnClickListener(v -> openEditProfile());
        binding.syncButton.setOnClickListener(v -> triggerSync());
        binding.menuChangePassword.setOnClickListener(v -> openChangePassword());
        binding.menuSettings.setOnClickListener(v -> openSettings());
        binding.menuLogout.setOnClickListener(v -> logout());
    }

    private void openEditProfile() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        if (getActivity() instanceof ShellNavigator navigator) {
            navigator.openEditProfile();
        }
    }

    private void loadLocalAvatar() {
        if (!isAdded() || getContext() == null || binding == null) {
            return;
        }
        
        File avatarFile = new File(getContext().getFilesDir(), "avatar.jpg");
        if (avatarFile.exists()) {
            binding.avatarText.setVisibility(View.GONE);
            binding.avatarImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(avatarFile)
                    .circleCrop()
                    .into(binding.avatarImage);
        }
    }

    private void loadStats() {
        FlashNoteApp app = FlashNoteApp.getInstance();

        binding.flashNoteCount.setText(String.valueOf(statsHelper.resolveFlashNoteCount(app)));
        binding.favoriteCount.setText(String.valueOf(statsHelper.resolveFavoriteCount(app)));

        statsHelper.loadRecordCount(app, new ProfileStatsHelper.RecordCountCallback() {
            @Override
            public void onCountReady(long count) {
                runIfUiAlive(() -> {
                    binding.recordCount.setText(String.valueOf(count));
                    statsHelper.persistRecordCount(getContext(), count);
                });
            }

            @Override
            public void onError() {
                runIfUiAlive(() -> binding.recordCount.setText("0"));
            }
        });
    }

    private void observePendingSyncCount() {
        if (pendingMessageRepository == null) {
            return;
        }
        pendingSyncCountLiveData = pendingMessageRepository.observePendingSyncCount();
        pendingSyncCountLiveData.observe(getViewLifecycleOwner(), count -> updateSyncBadge(count == null ? 0 : count));
    }

    private void triggerSync() {
        if (syncRepository == null || syncInProgress) {
            return;
        }
        syncInProgress = true;
        updateSyncProgress(true);
        syncRepository.syncAll(new SyncRepository.SyncCallback() {
            @Override
            public void onSuccess(java.util.Map<String, Object> data) {
                runIfUiAlive(() -> {
                    syncInProgress = false;
                    updateSyncProgress(false);
                    if (syncRepository != null) {
                        syncRepository.getPendingSyncCount(count -> runIfUiAlive(() -> updateSyncBadge(count)));
                    } else {
                        updateSyncBadge(0);
                    }
                    android.content.Context context = getContext();
                    if (context != null) {
                        Toast.makeText(context, R.string.message_sync_completed, Toast.LENGTH_SHORT).show();
                    }
                    loadStats();
                });
            }

            @Override
            public void onError(String message, int code) {
                runIfUiAlive(() -> {
                    syncInProgress = false;
                    updateSyncProgress(false);
                    android.content.Context context = getContext();
                    if (context != null) {
                        Toast.makeText(context, getString(R.string.message_sync_failed, message), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void updateSyncProgress(boolean syncing) {
        if (binding == null) {
            return;
        }
        binding.syncIcon.setVisibility(syncing ? View.INVISIBLE : View.VISIBLE);
        binding.syncProgress.setVisibility(syncing ? View.VISIBLE : View.GONE);
        binding.syncButton.setEnabled(!syncing);
        binding.syncButton.setAlpha(syncing ? 0.75f : 1f);
        refreshSyncHintText();
    }

    private void updateSyncBadge(int count) {
        if (binding == null) {
            return;
        }
        pendingSyncCount = count;
        if (count <= 0) {
            binding.syncPendingBadge.setVisibility(View.GONE);
            refreshSyncHintText();
            return;
        }
        binding.syncPendingBadge.setVisibility(View.VISIBLE);
        binding.syncPendingBadge.setText(syncUiHelper.buildSyncBadgeText(count));
        refreshSyncHintText();
    }

    private void refreshSyncHintText() {
        if (binding == null) {
            return;
        }
        binding.syncHintText.setText(syncUiHelper.buildSyncHintText(syncInProgress, pendingSyncCount));
    }

    private void fetchProfile() {
        userRepository.fetchProfile(new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                runIfUiAlive(ProfileTabFragment.this::loadStats);
            }

            @Override
            public void onError(String message, int code) {
                runIfUiAlive(() -> {
                    android.content.Context context = getContext();
                    if (context != null) {
                        Toast.makeText(context, getString(R.string.message_profile_fetch_failed, message), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void updateProfileUI(UserProfile profile) {
        if (!isAdded()) {
            return;
        }

        String nickname = profile.getNickname();
        if (nickname != null && !nickname.isBlank()) {
            binding.usernameText.setText(nickname.trim());
        } else {
            String username = tokenManager.getUsername();
            binding.usernameText.setText(username == null ? getString(R.string.status_unknown_user) : username);
        }
        
        String bio = profile.getBio();
        binding.bioText.setText(bio != null && !bio.isEmpty() ? bio : getString(R.string.status_empty_bio));
        
        String avatar = profile.getAvatar();
        if (avatar != null && !avatar.isEmpty()) {
            if (avatar.startsWith("http") || avatar.contains("/")) {
                binding.avatarText.setVisibility(View.GONE);
                binding.avatarImage.setVisibility(View.VISIBLE);
                Glide.with(this)
                        .load(buildAvatarGlideSource(avatar))
                        .placeholder(R.drawable.bg_avatar_circle)
                        .error(R.drawable.bg_avatar_circle)
                        .circleCrop()
                        .into(binding.avatarImage);
            } else {
                binding.avatarImage.setVisibility(View.GONE);
                binding.avatarText.setVisibility(View.VISIBLE);
                binding.avatarText.setText(avatar);
            }
        } else {
            String username = tokenManager.getUsername();
            if (username != null && !username.isEmpty()) {
                binding.avatarImage.setVisibility(View.GONE);
                binding.avatarText.setVisibility(View.VISIBLE);
                binding.avatarText.setText(String.valueOf(username.charAt(0)));
            }
        }
    }

    private void showEditBioDialog() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        editHelper.showEditBioDialog(
                requireContext(),
                getResources(),
                currentProfile == null ? null : currentProfile.getBio(),
                this::updateBio
        );
    }

    private void showEditProfileDialog() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        editHelper.showEditProfileDialog(
                requireContext(),
                getResources(),
                currentProfile == null ? null : currentProfile.getNickname(),
                currentProfile == null ? null : currentProfile.getBio(),
                new ProfileEditHelper.ProfileSaveHandler() {
                    @Override
                    public void onSave(@NonNull String nickname, @NonNull String bio) {
                        if (currentProfile == null) {
                            currentProfile = new UserProfile();
                        }
                        currentProfile.setNickname(nickname);
                        currentProfile.setBio(bio);
                        userRepository.updateProfile(currentProfile, new UserRepository.ProfileCallback() {
                            @Override
                            public void onSuccess(UserProfile profile) {
                                runIfUiAlive(() -> {
                                    currentProfile = profile;
                                    updateProfileUI(profile);
                                    android.content.Context ctx = getContext();
                                    if (ctx != null) {
                                        Toast.makeText(ctx, R.string.message_profile_saved, Toast.LENGTH_SHORT).show();
                                        Toast.makeText(ctx, R.string.message_profile_saved, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            @Override
                            public void onError(String message, int code) {
                                runIfUiAlive(() -> {
                                    android.content.Context ctx = getContext();
                                    if (ctx != null) {
                                        Toast.makeText(ctx, getString(R.string.message_save_failed, message), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });
                    }

                    @Override
                    public void onAvatarRequested() {
                        showAvatarPicker();
                    }
                }
        );
    }

    private void updateBio(String bio) {
        if (userRepository == null) {
            return;
        }
        
        if (currentProfile == null) {
            currentProfile = new UserProfile();
        }
        currentProfile.setBio(bio);

        userRepository.updateProfile(currentProfile, new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                runIfUiAlive(() -> {
                    binding.bioText.setText(bio.isEmpty() ? getString(R.string.status_empty_bio) : bio);
                    android.content.Context context = getContext();
                    if (context != null) {
                        Toast.makeText(context, R.string.message_bio_updated, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message, int code) {
                runIfUiAlive(() -> {
                    android.content.Context context = getContext();
                    if (context != null) {
                        Toast.makeText(context, getString(R.string.message_update_failed, message), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showAvatarPicker() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        
        String[] options = {getString(R.string.avatar_picker_gallery), getString(R.string.avatar_picker_emoji)};
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle(R.string.avatar_picker_title);
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                openGalleryPicker();
            } else if (which == 1) {
                showEmojiPicker();
            }
        });
        builder.setNegativeButton(R.string.action_cancel, null);
        builder.show();
    }

    private void openGalleryPicker() {
        if (!isAdded() || getContext() == null) {
            return;
        }

        avatarHelper.openGalleryPicker(galleryLauncher);
    }

    private void startCrop(Uri sourceUri) {
        if (!isAdded() || getContext() == null) {
            return;
        }

        avatarHelper.startCrop(requireContext(), sourceUri, ucropLauncher);
    }

    private void handleCropResult(Uri resultUri) {
        if (!isAdded() || getContext() == null) {
            return;
        }

        avatarHelper.handleCropResult(requireContext(), resultUri, fileRepository, userRepository, new AvatarUiBridgeImpl(), profile -> currentProfile = profile);
    }

    private void loadAvatarImage(String avatarUrl) {
        if (!isAdded() || getContext() == null || binding == null) {
            return;
        }
        
        if (avatarUrl != null && (avatarUrl.startsWith("http") || avatarUrl.contains("/"))) {
            binding.avatarText.setVisibility(View.GONE);
            binding.avatarImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(buildAvatarGlideSource(avatarUrl))
                    .placeholder(R.drawable.bg_avatar_circle)
                    .error(R.drawable.bg_avatar_circle)
                    .circleCrop()
                    .into(binding.avatarImage);
        } else {
            binding.avatarImage.setVisibility(View.GONE);
            binding.avatarText.setVisibility(View.VISIBLE);
        }
    }

    private void showEmojiPicker() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle(R.string.avatar_picker_title);

        GridLayout gridLayout = new GridLayout(getContext());
        gridLayout.setColumnCount(4);
        gridLayout.setPadding(32, 32, 32, 32);
        final String[] selectedEmoji = {null};
        final TextView[] selectedView = {null};
        
        for (String emoji : getResources().getStringArray(R.array.profile_avatar_emojis)) {
            TextView textView = new TextView(getContext());
            textView.setText(emoji);
            textView.setTextSize(28f);
            textView.setGravity(android.view.Gravity.CENTER);
            
            int size = (int) (56 * getResources().getDisplayMetrics().density);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = size;
            params.height = size;
            params.setMargins(8, 8, 8, 8);
            textView.setLayoutParams(params);
            
            textView.setOnClickListener(v -> {
                selectedEmoji[0] = emoji;
                if (selectedView[0] != null) {
                    selectedView[0].setBackground(null);
                }
                selectedView[0] = textView;
                textView.setBackgroundResource(R.drawable.bg_logo_primary_soft);
            });
            
            gridLayout.addView(textView);
        }
        
        builder.setView(gridLayout);
        builder.setPositiveButton(R.string.action_confirm, (dialog, which) -> {
            if (selectedEmoji[0] == null) {
                android.content.Context context = getContext();
                if (context != null) {
                    Toast.makeText(context, R.string.avatar_picker_select_first, Toast.LENGTH_SHORT).show();
                }
                return;
            }
            updateAvatarWithEmoji(selectedEmoji[0]);
        });
        builder.setNegativeButton(R.string.action_cancel, null);
        builder.show();
    }

    private String buildAvatarGlideSource(String avatarValue) {
        return MediaUrlResolver.resolve(avatarValue);
    }

    private void loadCachedRecordCount() {
        if (!isAdded() || binding == null) {
            return;
        }
        Long cached = statsHelper.getCachedRecordCount(getContext());
        if (cached != null) {
            binding.recordCount.setText(String.valueOf(cached));
        }
    }

    private void persistRecordCount(long count) {
        statsHelper.persistRecordCount(getContext(), count);
    }

    private void updateAvatarWithEmoji(String emoji) {
        if (userRepository == null || !isAdded()) {
            return;
        }

        if (currentProfile == null) {
            currentProfile = new UserProfile();
        }
        avatarHelper.updateAvatarWithEmoji(emoji, userRepository, currentProfile, new AvatarUiBridgeImpl(), profile -> {
            currentProfile = profile;
            runIfUiAlive(() -> {
                if (binding != null) {
                    binding.avatarImage.setVisibility(View.GONE);
                    binding.avatarText.setVisibility(View.VISIBLE);
                    binding.avatarText.setText(emoji);
                }
            });
        });
    }

    private void runIfUiAlive(@NonNull Runnable action) {
        androidx.fragment.app.FragmentActivity activity = getActivity();
        if (!isAdded() || activity == null || binding == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            if (!isAdded() || binding == null) {
                return;
            }
            action.run();
        });
    }

    private void clearLocalAvatarCache() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        File avatarFile = new File(getContext().getFilesDir(), "avatar.jpg");
        if (avatarFile.exists()) {
            avatarFile.delete();
        }
    }

    private final class AvatarUiBridgeImpl implements ProfileAvatarHelper.AvatarUiBridge {
        @Override
        public void runIfUiAlive(@NonNull Runnable action) {
            ProfileTabFragment.this.runIfUiAlive(action);
        }

        @Override
        public void loadAvatarImage(@NonNull String avatarUrl) {
            ProfileTabFragment.this.loadAvatarImage(avatarUrl);
        }

        @Override
        public void showToast(@NonNull String message) {
            android.content.Context context = getContext();
            if (context != null) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void clearLocalAvatarCache() {
            ProfileTabFragment.this.clearLocalAvatarCache();
        }

        @NonNull
        @Override
        public android.content.Context requireContext() {
            return ProfileTabFragment.this.requireContext();
        }
    }

    private void openChangePassword() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        if (getActivity() instanceof ShellNavigator navigator) {
            navigator.openChangePassword();
        }
    }

    private void openSettings() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        if (getActivity() instanceof ShellNavigator navigator) {
            navigator.openSettings();
        }
    }

    private void logout() {
        if (!isAdded() || getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        AuthViewModel authViewModel = new ViewModelProvider(getActivity()).get(AuthViewModel.class);
        authViewModel.logout();
        if (getActivity() instanceof ShellNavigator navigator) {
            navigator.logoutToLogin();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            loadStats();
            fetchProfile();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
