package com.flashnote.java.ui.main;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
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

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.TokenManager;
import com.flashnote.java.data.model.UserProfile;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.data.repository.UserRepository;
import com.flashnote.java.databinding.FragmentEditProfileBinding;
import com.flashnote.java.ui.media.MediaUrlResolver;
import com.yalantis.ucrop.UCrop;

import java.io.File;

public class EditProfileFragment extends Fragment {
    private static final int AVATAR_MAX_SIZE = 512;
    private static final String[] AVATAR_EMOJIS = {"💼", "📚", "❤️", "🌟", "🎯", "🚀", "🎨", "🎵", "📷", "🍕", "⚽", "😊"};

    private FragmentEditProfileBinding binding;
    private UserRepository userRepository;
    private FileRepository fileRepository;
    private TokenManager tokenManager;

    private UserProfile currentProfile;
    private String pendingAvatar;
    private boolean isUploadingAvatar;

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
        binding = FragmentEditProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FlashNoteApp app = FlashNoteApp.getInstance();
        userRepository = app.getUserRepository();
        fileRepository = app.getFileRepository();
        tokenManager = app.getTokenManager();

        binding.backButton.setOnClickListener(v -> navigateBack());
        binding.avatarContainer.setOnClickListener(v -> showAvatarPicker());
        binding.saveButton.setOnClickListener(v -> saveProfile());

        userRepository.getProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile == null || !isAdded() || binding == null) {
                return;
            }
            currentProfile = profile;
            fillForm(profile);
        });

        userRepository.fetchProfile(new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
            }

            @Override
            public void onError(String message, int code) {
                showToast("获取资料失败：" + message);
            }
        });
    }

    private void fillForm(@NonNull UserProfile profile) {
        if (binding.nicknameInput.getText() == null || binding.nicknameInput.getText().toString().isEmpty()) {
            binding.nicknameInput.setText(profile.getNickname() == null ? "" : profile.getNickname());
        }
        if (binding.bioInput.getText() == null || binding.bioInput.getText().toString().isEmpty()) {
            binding.bioInput.setText(profile.getBio() == null ? "" : profile.getBio());
        }
        String avatarValue = pendingAvatar == null ? profile.getAvatar() : pendingAvatar;
        renderAvatar(avatarValue);
    }

    private void showAvatarPicker() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        String[] options = {"从相册选择图片", "选择emoji头像"};
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("选择头像")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openGalleryPicker();
                    } else {
                        showEmojiPicker();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openGalleryPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private void startCrop(@NonNull Uri sourceUri) {
        if (!isAdded() || getContext() == null) {
            return;
        }
        try {
            File outputFile = new File(requireContext().getCacheDir(), "avatar_crop_edit.jpg");
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
                    .getIntent(requireContext());
            ucropLauncher.launch(ucropIntent);
        } catch (Exception e) {
            showToast("无法打开图片裁剪");
        }
    }

    private void handleCropResult(@NonNull Uri resultUri) {
        if (!isAdded() || fileRepository == null) {
            return;
        }
        File avatarFile = new File(requireContext().getCacheDir(), "avatar_pending_upload.jpg");
        try (java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(resultUri);
             java.io.FileOutputStream fos = new java.io.FileOutputStream(avatarFile)) {
            if (inputStream == null) {
                showToast("读取头像失败");
                return;
            }
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        } catch (Exception e) {
            showToast("保存头像失败");
            return;
        }

        showProgress(true);
        isUploadingAvatar = true;
        fileRepository.upload(avatarFile, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(String value) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    isUploadingAvatar = false;
                    showProgress(false);
                    pendingAvatar = value;
                    renderAvatar(value);
                    showToast("头像已预览，可点击保存提交");
                });
            }

            @Override
            public void onError(String message, int code) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    isUploadingAvatar = false;
                    showProgress(false);
                    showToast("上传头像失败：" + message);
                });
            }
        });
    }

    private void showEmojiPicker() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        GridLayout gridLayout = new GridLayout(getContext());
        gridLayout.setColumnCount(4);
        gridLayout.setPadding(32, 32, 32, 32);
        final String[] selected = {null};
        final TextView[] selectedView = {null};

        for (String emoji : AVATAR_EMOJIS) {
            TextView textView = new TextView(getContext());
            textView.setText(emoji);
            textView.setTextSize(28f);
            textView.setGravity(Gravity.CENTER);

            int size = (int) (56 * getResources().getDisplayMetrics().density);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = size;
            params.height = size;
            params.setMargins(8, 8, 8, 8);
            textView.setLayoutParams(params);
            textView.setOnClickListener(v -> {
                selected[0] = emoji;
                if (selectedView[0] != null) {
                    selectedView[0].setBackground(null);
                }
                selectedView[0] = textView;
                textView.setBackgroundResource(R.drawable.bg_logo_primary_soft);
            });
            gridLayout.addView(textView);
        }

        new android.app.AlertDialog.Builder(getContext())
                .setTitle("选择头像")
                .setView(gridLayout)
                .setPositiveButton("确定", (dialog, which) -> {
                    if (selected[0] == null) {
                        showToast("请先选择一个头像");
                        return;
                    }
                    pendingAvatar = selected[0];
                    renderAvatar(selected[0]);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void renderAvatar(@Nullable String avatar) {
        if (binding == null) {
            return;
        }
        String value = avatar;
        if (value == null || value.isBlank()) {
            value = currentProfile == null ? null : currentProfile.getAvatar();
        }
        if (value == null || value.isBlank()) {
            binding.avatarImage.setVisibility(View.GONE);
            binding.avatarText.setVisibility(View.VISIBLE);
            String username = tokenManager.getUsername();
            binding.avatarText.setText((username == null || username.isEmpty()) ? "😊" : String.valueOf(username.charAt(0)));
            return;
        }
        if (value.startsWith("http") || value.contains("/")) {
            binding.avatarText.setVisibility(View.GONE);
            binding.avatarImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(buildAvatarGlideSource(value))
                    .placeholder(R.drawable.bg_avatar_circle)
                    .error(R.drawable.bg_avatar_circle)
                    .circleCrop()
                    .into(binding.avatarImage);
            return;
        }
        binding.avatarImage.setVisibility(View.GONE);
        binding.avatarText.setVisibility(View.VISIBLE);
        binding.avatarText.setText(value);
    }

    private Object buildAvatarGlideSource(@NonNull String avatarValue) {
        String resolved = MediaUrlResolver.resolve(avatarValue);
        String token = tokenManager.getAccessToken();
        if (token == null || token.isEmpty()) {
            return resolved;
        }
        return new GlideUrl(resolved, new LazyHeaders.Builder()
                .addHeader("Authorization", "Bearer " + token)
                .build());
    }

    private void saveProfile() {
        if (binding == null || userRepository == null || isUploadingAvatar) {
            if (isUploadingAvatar) {
                showToast("头像上传中，请稍候");
            }
            return;
        }
        String nickname = binding.nicknameInput.getText() == null ? "" : binding.nicknameInput.getText().toString().trim();
        String bio = binding.bioInput.getText() == null ? "" : binding.bioInput.getText().toString().trim();

        UserProfile profile = currentProfile == null ? new UserProfile() : currentProfile;
        profile.setNickname(nickname);
        profile.setBio(bio);
        if (pendingAvatar != null && !pendingAvatar.isBlank()) {
            profile.setAvatar(pendingAvatar);
        }

        showProgress(true);
        userRepository.updateProfile(profile, new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    showProgress(false);
                    showToast("资料已保存");
                    navigateBack();
                });
            }

            @Override
            public void onError(String message, int code) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    showProgress(false);
                    showToast("保存失败：" + message);
                });
            }
        });
    }

    private void showProgress(boolean loading) {
        if (binding == null) {
            return;
        }
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.saveButton.setEnabled(!loading);
    }

    private void navigateBack() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        getActivity().getSupportFragmentManager().popBackStack();
    }

    private void showToast(@NonNull String message) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
