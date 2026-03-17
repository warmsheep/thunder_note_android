package com.flashnote.java.ui.main;

import android.net.Uri;
import android.os.Bundle;
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
import androidx.fragment.app.Fragment;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.TokenManager;
import com.flashnote.java.data.model.FavoriteItem;
import com.flashnote.java.data.model.UserProfile;
import com.flashnote.java.data.repository.UserRepository;
import com.flashnote.java.ui.auth.AuthViewModel;
import com.flashnote.java.ui.navigation.ShellNavigator;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;

public class ProfileTabFragment extends Fragment {
    private com.flashnote.java.databinding.FragmentProfileTabBinding binding;
    private UserRepository userRepository;
    private TokenManager tokenManager;
    private UserProfile currentProfile;
    
    private static final String[] AVATAR_EMOJIS = {"💼", "📚", "❤️", "🌟", "🎯", "🚀", "🎨", "🎵", "📷", "🍕", "⚽", "😊"};

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
        tokenManager = app.getTokenManager();

        String username = tokenManager.getUsername();
        if (username != null) {
            binding.usernameText.setText(username);
        } else {
            binding.usernameText.setText("未知用户");
        }

        userRepository.getProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile != null) {
                currentProfile = profile;
                updateProfileUI(profile);
            }
        });

        loadStats();

        binding.menuChangeAvatar.setOnClickListener(v -> showAvatarPicker());
        binding.menuEditBio.setOnClickListener(v -> showEditBioDialog());
        binding.menuChangePassword.setOnClickListener(v -> openChangePassword());
        binding.menuSettings.setOnClickListener(v -> openSettings());
        binding.menuDebug.setOnClickListener(v -> openDebug());
        binding.menuLogout.setOnClickListener(v -> logout());

        binding.avatarContainer.setOnClickListener(v -> showAvatarPicker());

        fetchProfile();
    }

    private void loadStats() {
        FlashNoteApp app = FlashNoteApp.getInstance();
        
        List<?> notes = app.getFlashNoteRepository().getNotes().getValue();
        binding.flashNoteCount.setText(String.valueOf(notes != null ? notes.size() : 0));
        
        List<?> favorites = app.getFavoriteRepository().getFavorites().getValue();
        binding.favoriteCount.setText(String.valueOf(favorites != null ? favorites.size() : 0));
        
        List<?> collections = app.getCollectionRepository().getCollections().getValue();
        binding.recordCount.setText(String.valueOf(collections != null ? collections.size() : 0));
    }

    private void fetchProfile() {
        userRepository.fetchProfile(new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> loadStats());
                }
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
        binding.bioText.setText(bio != null && !bio.isEmpty() ? bio : "暂无简介");
        
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

    private void showEditBioDialog() {
        if (!isAdded() || getContext() == null) {
            return;
        }

        EditText editText = new EditText(getContext());
        editText.setHint("请输入简介");
        if (currentProfile != null && currentProfile.getBio() != null) {
            editText.setText(currentProfile.getBio());
        }
        editText.setPadding(48, 32, 48, 32);

        new AlertDialog.Builder(getContext())
            .setTitle("编辑简介")
            .setView(editText)
            .setPositiveButton("保存", (dialog, which) -> {
                String newBio = editText.getText().toString().trim();
                updateBio(newBio);
            })
            .setNegativeButton("取消", null)
            .show();
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
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    if (!isAdded() || binding == null) {
                        return;
                    }
                    binding.bioText.setText(bio.isEmpty() ? "暂无简介" : bio);
                    android.content.Context context = getContext();
                    if (context != null) {
                        Toast.makeText(context, "简介已更新", Toast.LENGTH_SHORT).show();
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

    private void openDebug() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        if (getActivity() instanceof ShellNavigator navigator) {
            navigator.openDebug();
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
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
