package com.flashnote.java.ui.main;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.flashnote.java.DebugLog;
import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.TokenManager;
import com.flashnote.java.data.model.FavoriteItem;
import com.flashnote.java.databinding.FragmentProfileTabBinding;
import com.flashnote.java.data.model.Collection;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.UserProfile;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.data.repository.SyncRepository;
import com.flashnote.java.data.repository.UserRepository;
import com.flashnote.java.ui.auth.AuthViewModel;
import com.flashnote.java.ui.navigation.ShellNavigator;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileTabFragment extends Fragment {
    private FragmentProfileTabBinding binding;
    private SyncRepository syncRepository;
    private FileRepository fileRepository;
    private UserRepository userRepository;
    private TokenManager tokenManager;
    private ActivityResultLauncher<String> filePickerLauncher;
    private UserProfile currentProfile;
    private boolean isEditingBio = false;
    
    private static final String[] AVATAR_EMOJIS = {"💼", "📚", "❤️", "🌟", "🎯", "🚀", "🎨", "🎵", "📷", "🍕", "⚽", "😊"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileTabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FlashNoteApp app = FlashNoteApp.getInstance();
        syncRepository = app.getSyncRepository();
        fileRepository = app.getFileRepository();
        userRepository = app.getUserRepository();
        tokenManager = app.getTokenManager();

        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleFilePicked);

        String username = tokenManager.getUsername();
        if (username != null) {
            binding.usernameText.setText("用户名：" + username);
        } else {
            binding.usernameText.setText("用户名：未知");
        }

        userRepository.getProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile != null) {
                currentProfile = profile;
                updateProfileUI(profile);
            }
        });

        binding.refreshProfileButton.setOnClickListener(v -> fetchProfile());
        binding.editBioButton.setOnClickListener(v -> toggleBioEdit());
        binding.cancelEditButton.setOnClickListener(v -> cancelBioEdit());
        binding.saveBioButton.setOnClickListener(v -> saveBio());

        binding.bootstrapButton.setOnClickListener(v -> syncRepository.bootstrap(syncCallback("bootstrap")));
        binding.pullButton.setOnClickListener(v -> syncRepository.pull(syncCallback("pull")));
        binding.pushButton.setOnClickListener(v -> pushCurrentData());
        binding.uploadButton.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
        binding.avatarContainer.setOnClickListener(v -> showAvatarPicker());
        binding.changePasswordButton.setOnClickListener(v -> openChangePassword());
        binding.settingsButton.setOnClickListener(v -> openSettings());
        binding.logoutButton.setOnClickListener(v -> logout());

        binding.clearLogButton.setOnClickListener(v -> DebugLog.clear());
        DebugLog.getLiveData().observe(getViewLifecycleOwner(), log -> {
            if (binding != null) {
                binding.debugLogText.setText(log == null || log.isEmpty() ? "暂无日志" : log);
            }
        });

        fetchProfile();
    }

    private void fetchProfile() {
        userRepository.fetchProfile(new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
            }

            @Override
            public void onError(String message, int code) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    android.content.Context context = getContext();
                    if (isAdded() && context != null) {
                        Toast.makeText(context, "获取资料失败：" + message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void updateProfileUI(UserProfile profile) {
        if (!isAdded()) {
            return;
        }
        
        String bio = profile.getBio();
        binding.bioText.setText("简介：" + (bio != null && !bio.isEmpty() ? bio : "暂无简介"));
        
        String avatar = profile.getAvatar();
        if (avatar != null && !avatar.isEmpty()) {
            binding.avatarText.setText(avatar);
        } else {
            String username = tokenManager.getUsername();
            if (username != null && !username.isEmpty()) {
                binding.avatarText.setText(String.valueOf(username.charAt(0)));
            }
        }
    }

    private void toggleBioEdit() {
        if (isEditingBio) {
            return;
        }
        isEditingBio = true;

        String currentBio = currentProfile != null ? currentProfile.getBio() : "";
        binding.bioEditText.setText(currentBio != null ? currentBio : "");

        binding.bioText.setVisibility(View.GONE);
        binding.editBioButton.setVisibility(View.GONE);
        binding.bioEditText.setVisibility(View.VISIBLE);
        binding.editButtonsLayout.setVisibility(View.VISIBLE);
    }

    private void cancelBioEdit() {
        isEditingBio = false;
        binding.bioText.setVisibility(View.VISIBLE);
        binding.editBioButton.setVisibility(View.VISIBLE);
        binding.bioEditText.setVisibility(View.GONE);
        binding.editButtonsLayout.setVisibility(View.GONE);
    }

    private void saveBio() {
        if (currentProfile == null) {
            android.content.Context context = getContext();
            if (context != null) {
                Toast.makeText(context, "请先加载资料", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String newBio = binding.bioEditText.getText().toString().trim();
        currentProfile.setBio(newBio);

        userRepository.updateProfile(currentProfile, new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    if (!isAdded() || binding == null) {
                        return;
                    }
                    isEditingBio = false;
                    binding.bioText.setVisibility(View.VISIBLE);
                    binding.editBioButton.setVisibility(View.VISIBLE);
                    binding.bioEditText.setVisibility(View.GONE);
                    binding.editButtonsLayout.setVisibility(View.GONE);
                    android.content.Context context = getContext();
                    if (context != null) {
                        Toast.makeText(context, "资料已更新", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message, int code) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    android.content.Context context = getContext();
                    if (isAdded() && context != null) {
                        Toast.makeText(context, "更新失败：" + message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private SyncRepository.SyncCallback syncCallback(String action) {
        return new SyncRepository.SyncCallback() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                FlashNoteApp app = FlashNoteApp.getInstance();
                app.getFlashNoteRepository().refresh();
                app.getCollectionRepository().refresh();
                app.getFavoriteRepository().refresh();
                getActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        binding.statusText.setText(action + " 成功：" + data);
                    }
                });
            }

            @Override
            public void onError(String message, int code) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        binding.statusText.setText(action + " 失败：" + message);
                    }
                });
            }
        };
    }

    private void pushCurrentData() {
        FlashNoteApp app = FlashNoteApp.getInstance();
        List<FlashNote> notes = app.getFlashNoteRepository().getNotes().getValue();
        List<Collection> collections = app.getCollectionRepository().getCollections().getValue();
        List<FavoriteItem> favorites = app.getFavoriteRepository().getFavorites().getValue();
        List<Message> messages = app.getMessageRepository().getCachedMessages();

        Map<String, Object> payload = new HashMap<>();
        payload.put("notes", notes == null ? List.of() : notes);
        payload.put("collections", collections == null ? List.of() : collections);
        payload.put("messages", messages == null ? List.of() : messages);
        payload.put("favorites", favorites == null ? List.of() : favorites);
        syncRepository.push(payload, syncCallback("push"));
    }

    private void handleFilePicked(@Nullable Uri uri) {
        if (uri == null || !isAdded()) {
            return;
        }

        try {
            File tempFile = new File(requireContext().getCacheDir(), "picked-upload.bin");
            try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                if (inputStream == null) {
                    throw new IllegalStateException("Cannot open selected file");
                }
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            fileRepository.upload(tempFile, new FileRepository.FileCallback() {
                @Override
                public void onSuccess(String objectName) {
                    fileRepository.download(objectName, new FileRepository.FileCallback() {
                        @Override
                        public void onSuccess(String value) {
                            if (!isAdded() || getActivity() == null) {
                                return;
                            }
                            getActivity().runOnUiThread(() -> {
                                if (binding != null) {
                                    binding.statusText.setText("文件上传并回读成功：" + value);
                                }
                            });
                        }

                        @Override
                        public void onError(String message, int code) {
                            if (!isAdded() || getActivity() == null) {
                                return;
                            }
                            getActivity().runOnUiThread(() -> {
                                if (binding != null) {
                                    binding.statusText.setText("文件下载失败：" + message);
                                }
                            });
                        }
                    });
                }

                @Override
                public void onError(String message, int code) {
                    if (!isAdded() || getActivity() == null) {
                        return;
                    }
                    getActivity().runOnUiThread(() -> {
                        if (binding != null) {
                            binding.statusText.setText("文件上传失败：" + message);
                        }
                    });
                }
            });
        } catch (Exception exception) {
            android.content.Context context = getContext();
            if (isAdded() && context != null) {
                Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
            }
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

    private void showAvatarPicker() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("选择头像");
        
        GridLayout gridLayout = new GridLayout(getContext());
        gridLayout.setColumnCount(4);
        gridLayout.setPadding(32, 32, 32, 32);
        
        for (String emoji : AVATAR_EMOJIS) {
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
                if (builder.create() != null) {
                    builder.create().dismiss();
                }
                updateAvatar(emoji);
            });
            
            gridLayout.addView(textView);
        }
        
        builder.setView(gridLayout);
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void updateAvatar(String emoji) {
        if (userRepository == null) {
            return;
        }
        
        userRepository.updateAvatar(emoji, new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    if (binding != null && binding.avatarText != null) {
                        binding.avatarText.setText(emoji);
                    }
                    android.content.Context context = getContext();
                    if (context != null) {
                        Toast.makeText(context, "头像已更新", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message, int code) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    android.content.Context context = getContext();
                    if (isAdded() && context != null) {
                        Toast.makeText(context, "更新头像失败：" + message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
