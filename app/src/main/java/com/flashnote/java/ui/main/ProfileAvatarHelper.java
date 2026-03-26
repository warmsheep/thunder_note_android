package com.flashnote.java.ui.main;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.flashnote.java.data.model.UserProfile;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.data.repository.UserRepository;
import com.yalantis.ucrop.UCrop;

import java.io.File;

final class ProfileAvatarHelper {
    private static final int AVATAR_MAX_SIZE = 1024;

    interface AvatarUiBridge {
        void runIfUiAlive(@NonNull Runnable action);

        void loadAvatarImage(@NonNull String avatarUrl);

        void showToast(@NonNull String message);

        void clearLocalAvatarCache();

        @NonNull Context requireContext();
    }

    void openGalleryPicker(@NonNull androidx.activity.result.ActivityResultLauncher<Intent> galleryLauncher) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    void startCrop(@NonNull Context context,
                   @NonNull Uri sourceUri,
                   @NonNull androidx.activity.result.ActivityResultLauncher<Intent> ucropLauncher) {
        try {
            File outputDir = context.getCacheDir();
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
                    .getIntent(context);

            ucropLauncher.launch(ucropIntent);
        } catch (Exception e) {
            Toast.makeText(context, "无法打开图片裁剪", Toast.LENGTH_SHORT).show();
        }
    }

    void handleCropResult(@NonNull Context context,
                          @NonNull Uri resultUri,
                          @NonNull FileRepository fileRepository,
                          @NonNull UserRepository userRepository,
                          @NonNull AvatarUiBridge bridge,
                          @NonNull java.util.function.Consumer<UserProfile> profileConsumer) {
        try {
            File avatarFile = new File(context.getFilesDir(), "avatar.jpg");
            try (java.io.InputStream inputStream = context.getContentResolver().openInputStream(resultUri);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(avatarFile)) {
                if (inputStream == null) {
                    Toast.makeText(context, "裁剪后的图片读取失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            uploadAvatar(avatarFile, fileRepository, userRepository, bridge, profileConsumer);
        } catch (Exception e) {
            Toast.makeText(context, "保存头像失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    void updateAvatarWithEmoji(@NonNull String emoji,
                               @NonNull UserRepository userRepository,
                               @NonNull UserProfile profile,
                               @NonNull AvatarUiBridge bridge,
                               @NonNull java.util.function.Consumer<UserProfile> profileConsumer) {
        userRepository.updateAvatar(emoji, new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile updatedProfile) {
                profileConsumer.accept(updatedProfile);
                bridge.runIfUiAlive(() -> {
                    bridge.clearLocalAvatarCache();
                    updatedProfile.setAvatar(emoji);
                    bridge.showToast("头像已更新");
                });
            }

            @Override
            public void onError(String message, int code) {
                bridge.runIfUiAlive(() -> bridge.showToast("更新头像失败：" + message));
            }
        });
    }

    private void uploadAvatar(@NonNull File avatarFile,
                              @NonNull FileRepository fileRepository,
                              @NonNull UserRepository userRepository,
                              @NonNull AvatarUiBridge bridge,
                              @NonNull java.util.function.Consumer<UserProfile> profileConsumer) {
        fileRepository.upload(avatarFile, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(String avatarUrl) {
                updateAvatarToServer(avatarUrl, userRepository, bridge, profileConsumer);
            }

            @Override
            public void onError(String message, int code) {
                bridge.runIfUiAlive(() -> bridge.showToast("上传头像失败: " + message));
            }
        });
    }

    private void updateAvatarToServer(@NonNull String avatarUrl,
                                      @NonNull UserRepository userRepository,
                                      @NonNull AvatarUiBridge bridge,
                                      @NonNull java.util.function.Consumer<UserProfile> profileConsumer) {
        userRepository.updateAvatar(avatarUrl, new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                profileConsumer.accept(profile);
                bridge.runIfUiAlive(() -> {
                    bridge.loadAvatarImage(avatarUrl);
                    bridge.showToast("头像已更新");
                });
            }

            @Override
            public void onError(String message, int code) {
                bridge.runIfUiAlive(() -> bridge.showToast("更新头像失败: " + message));
            }
        });
    }
}
