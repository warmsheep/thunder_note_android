package com.flashnote.java.ui.chat;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.flashnote.java.databinding.FragmentChatBinding;

import java.io.File;

public class ChatAttachmentFlowHelper {

    public interface AttachmentUiBridge {
        @NonNull Context requireContext();
        void runOnUiThread(@NonNull Runnable action);
        void showToast(@NonNull String message);
        void scrollToBottomAfterLayout();
        void suppressGestureUnlockForExternalFlow();
    }

    public interface LauncherBridge {
        void openMediaPicker(@NonNull Intent intent);
        void openFilePicker(@NonNull Intent intent);
        void openCamera(@NonNull Intent intent);
        void openCardEditor();
    }

    private final FragmentChatBinding binding;
    private final ChatViewModel chatViewModel;
    private final ChatMediaHelper mediaHelper;
    private final AttachmentUiBridge uiBridge;
    private final LauncherBridge launcherBridge;

    private boolean isToolsPanelVisible = false;
    @Nullable
    private Uri cameraPhotoUri;

    public ChatAttachmentFlowHelper(@NonNull FragmentChatBinding binding,
                                    @NonNull ChatViewModel chatViewModel,
                                    @NonNull ChatMediaHelper mediaHelper,
                                    @NonNull AttachmentUiBridge uiBridge,
                                    @NonNull LauncherBridge launcherBridge) {
        this.binding = binding;
        this.chatViewModel = chatViewModel;
        this.mediaHelper = mediaHelper;
        this.uiBridge = uiBridge;
        this.launcherBridge = launcherBridge;
    }

    public void setupToolsPanel() {
        binding.addButton.setOnClickListener(v -> toggleToolsPanel());

        binding.toolImageVideo.setOnClickListener(v -> {
            hideToolsPanel();
            openMediaPicker();
        });

        binding.toolFile.setOnClickListener(v -> {
            hideToolsPanel();
            openFilePicker();
        });

        binding.toolCamera.setOnClickListener(v -> {
            hideToolsPanel();
            openCamera();
        });

        binding.toolCard.setOnClickListener(v -> {
            hideToolsPanel();
            launcherBridge.openCardEditor();
        });
    }

    public void toggleToolsPanel() {
        isToolsPanelVisible = !isToolsPanelVisible;
        binding.toolsPanel.setVisibility(isToolsPanelVisible ? View.VISIBLE : View.GONE);
    }

    public void hideToolsPanel() {
        isToolsPanelVisible = false;
        binding.toolsPanel.setVisibility(View.GONE);
    }

    public void openMediaPicker() {
        uiBridge.suppressGestureUnlockForExternalFlow();
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        launcherBridge.openMediaPicker(intent);
    }

    public void openFilePicker() {
        uiBridge.suppressGestureUnlockForExternalFlow();
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        launcherBridge.openFilePicker(intent);
    }

    public void openCamera() {
        Context context = uiBridge.requireContext();
        if (!mediaHelper.hasCamera(context)) {
            uiBridge.showToast("设备没有相机");
            return;
        }

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = mediaHelper.prepareCameraPhotoFile(context);

        if (photoFile != null) {
            uiBridge.suppressGestureUnlockForExternalFlow();
            cameraPhotoUri = mediaHelper.buildCameraUri(context, photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                launcherBridge.openCamera(takePictureIntent);
            } catch (ActivityNotFoundException e) {
                uiBridge.showToast("无法打开相机");
            }
        }
    }

    public void onMediaPicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        String mimeType = mediaHelper.resolveMimeType(uiBridge.requireContext(), uri);
        if (mimeType == null) {
            uiBridge.showToast("无法识别文件类型");
            return;
        }

        if (mimeType.startsWith("image/")) {
            onImagePicked(uri);
        } else if (mimeType.startsWith("video/")) {
            onVideoPicked(uri);
        } else {
            uiBridge.showToast("请选择图片或视频");
        }
    }

    public void onImagePicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        String originalFileName = mediaHelper.getOriginalFileName(uiBridge.requireContext(), uri);
        mediaHelper.copyUriToTempFile(uiBridge.requireContext().getApplicationContext(), uiBridge::runOnUiThread, uri, "image", file -> {
            if (file == null) {
                uiBridge.showToast("文件处理失败");
                return;
            }

            chatViewModel.enqueueMedia(
                    "IMAGE",
                    file,
                    originalFileName != null ? originalFileName : file.getName(),
                    file.length(),
                    null,
                    () -> uiBridge.runOnUiThread(uiBridge::scrollToBottomAfterLayout)
            );
        });
    }

    public void onVideoPicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        String originalFileName = mediaHelper.getOriginalFileName(uiBridge.requireContext(), uri);

        mediaHelper.copyUriToTempFile(uiBridge.requireContext().getApplicationContext(), uiBridge::runOnUiThread, uri, "video", file -> {
            if (file == null) {
                uiBridge.showToast("文件处理失败");
                return;
            }

            chatViewModel.enqueueMedia(
                    "VIDEO",
                    file,
                    originalFileName != null ? originalFileName : file.getName(),
                    file.length(),
                    null,
                    () -> uiBridge.runOnUiThread(uiBridge::scrollToBottomAfterLayout)
            );
        });
    }

    public void onFilePicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        String originalFileName = mediaHelper.getOriginalFileName(uiBridge.requireContext(), uri);
        mediaHelper.copyUriToTempFile(uiBridge.requireContext().getApplicationContext(), uiBridge::runOnUiThread, uri, "file", file -> {
            if (file == null) {
                uiBridge.showToast("文件处理失败");
                return;
            }

            chatViewModel.enqueueMedia(
                    "FILE",
                    file,
                    originalFileName != null ? originalFileName : file.getName(),
                    file.length(),
                    null,
                    () -> uiBridge.runOnUiThread(uiBridge::scrollToBottomAfterLayout)
            );
        });
    }

    public void onCameraPhoto(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        String originalFileName = mediaHelper.getOriginalFileName(uiBridge.requireContext(), uri);
        mediaHelper.copyUriToTempFile(uiBridge.requireContext().getApplicationContext(), uiBridge::runOnUiThread, uri, "image", file -> {
            if (file == null) {
                uiBridge.showToast("文件处理失败");
                return;
            }

            chatViewModel.enqueueMedia(
                    "IMAGE",
                    file,
                    originalFileName != null ? originalFileName : file.getName(),
                    file.length(),
                    null,
                    () -> uiBridge.runOnUiThread(uiBridge::scrollToBottomAfterLayout)
            );
        });
    }

    @Nullable
    public Uri getCameraPhotoUri() {
        return cameraPhotoUri;
    }
}
