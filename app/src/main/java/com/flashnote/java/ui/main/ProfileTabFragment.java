package com.flashnote.java.ui.main;

import android.app.Activity;
import android.content.Intent;
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
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.TokenManager;
import com.flashnote.java.data.model.FavoriteItem;
import com.flashnote.java.data.model.UserProfile;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.data.repository.MessageRepository;
import com.flashnote.java.data.repository.UserRepository;
import com.flashnote.java.R;
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
    private UserProfile currentProfile;
    
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

        loadLocalAvatar();

        fetchProfile();
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
        
        List<?> notes = app.getFlashNoteRepository().getNotes().getValue();
        binding.flashNoteCount.setText(String.valueOf(notes != null ? notes.size() : 0));
        
        List<?> favorites = app.getFavoriteRepository().getFavorites().getValue();
        binding.favoriteCount.setText(String.valueOf(favorites != null ? favorites.size() : 0));
        
        app.getMessageRepository().countMessages(new MessageRepository.CountCallback() {
            @Override
            public void onSuccess(long count) {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> binding.recordCount.setText(String.valueOf(count)));
                }
            }

            @Override
            public void onError(String message, int code) {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> binding.recordCount.setText("0"));
                }
            }
        });
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
            if (avatar.startsWith("http") || avatar.contains("/")) {
                binding.avatarText.setVisibility(View.GONE);
                binding.avatarImage.setVisibility(View.VISIBLE);
                Glide.with(this)
                        .load(avatar)
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
        
        String[] options = {"从相册选择图片", "选择emoji头像", "取消"};
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("选择头像");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                openGalleryPicker();
            } else if (which == 1) {
                showEmojiPicker();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void openGalleryPicker() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private void startCrop(Uri sourceUri) {
        if (!isAdded() || getContext() == null) {
            return;
        }
        
        try {
            File outputDir = getContext().getCacheDir();
            File outputFile = new File(outputDir, "avatar_crop.jpg");
            
            UCrop.Options options = new UCrop.Options();
            options.setCircleDimmedLayer(true);
            options.setShowCropFrame(false);
            options.setShowCropGrid(false);
            options.setCompressionQuality(90);
            options.setToolbarTitle("裁剪头像");
            
            Intent ucropIntent = UCrop.of(sourceUri, Uri.fromFile(outputFile))
                    .withAspectRatio(1, 1)
                    .withMaxResultSize(AVATAR_MAX_SIZE, AVATAR_MAX_SIZE)
                    .withOptions(options)
                    .getIntent(getContext());
            
            ucropLauncher.launch(ucropIntent);
        } catch (Exception e) {
            android.content.Context context = getContext();
            if (context != null) {
                Toast.makeText(context, "无法打开图片裁剪", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleCropResult(Uri resultUri) {
        if (!isAdded() || getContext() == null) {
            return;
        }
        
        android.content.Context context = getContext();
        
        try {
            File inputFile = new File(resultUri.getPath());
            if (!inputFile.exists()) {
                Toast.makeText(context, "裁剪后的图片不存在", Toast.LENGTH_SHORT).show();
                return;
            }
            
            File avatarFile = new File(context.getFilesDir(), "avatar.jpg");
            
            java.io.FileInputStream fis = new java.io.FileInputStream(inputFile);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(avatarFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fis.close();
            fos.close();
            
            uploadAvatar(avatarFile);
            
        } catch (Exception e) {
            Toast.makeText(context, "保存头像失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadAvatar(File avatarFile) {
        if (!isAdded() || getContext() == null || fileRepository == null) {
            return;
        }
        
        android.content.Context context = getContext();
        
        fileRepository.upload(avatarFile, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(String avatarUrl) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    updateAvatarToServer(avatarUrl);
                });
            }

            @Override
            public void onError(String message, int code) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    android.content.Context ctx = getContext();
                    if (ctx != null) {
                        Toast.makeText(ctx, "上传头像失败: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void updateAvatarToServer(String avatarUrl) {
        if (userRepository == null || !isAdded()) {
            return;
        }
        
        userRepository.updateAvatar(avatarUrl, new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        loadAvatarImage(avatarUrl);
                        Toast.makeText(getContext(), "头像已更新", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message, int code) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    android.content.Context ctx = getContext();
                    if (ctx != null) {
                        Toast.makeText(ctx, "更新头像失败: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void loadAvatarImage(String avatarUrl) {
        if (!isAdded() || getContext() == null || binding == null) {
            return;
        }
        
        if (avatarUrl != null && (avatarUrl.startsWith("http") || avatarUrl.contains("/"))) {
            binding.avatarText.setVisibility(View.GONE);
            binding.avatarImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(avatarUrl)
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
                updateAvatarWithEmoji(emoji);
            });
            
            gridLayout.addView(textView);
        }
        
        builder.setView(gridLayout);
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void updateAvatarWithEmoji(String emoji) {
        if (userRepository == null || !isAdded()) {
            return;
        }
        
        userRepository.updateAvatar(emoji, new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        binding.avatarImage.setVisibility(View.GONE);
                        binding.avatarText.setVisibility(View.VISIBLE);
                        binding.avatarText.setText(emoji);
                        Toast.makeText(getContext(), "头像已更新", Toast.LENGTH_SHORT).show();
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
            fetchProfile();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
