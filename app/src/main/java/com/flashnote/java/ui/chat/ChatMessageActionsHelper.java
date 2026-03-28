package com.flashnote.java.ui.chat;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.repository.FavoriteRepository;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.databinding.FragmentChatBinding;
import com.flashnote.java.databinding.PopupMessageActionsBinding;
import com.flashnote.java.ui.main.FlashNoteViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChatMessageActionsHelper {

    public interface ActionsUiBridge {
        boolean isAdded();

        @Nullable
        Context getContext();

        @NonNull
        Context requireContext();

        @Nullable
        Activity getActivity();

        @NonNull
        LayoutInflater getLayoutInflater();

        void showToast(@NonNull String message);

        void runIfUiAlive(@NonNull Runnable action);

        void enterMultiSelectMode(@Nullable Message firstSelected);

        void markSkipNextScroll();
    }

    private final FragmentChatBinding binding;
    private final FileRepository fileRepository;
    private final ChatShareHelper shareHelper;
    private final ChatViewModel chatViewModel;
    private final FlashNoteViewModel flashNoteViewModel;
    private final FavoriteRepository favoriteRepository;
    private final ActionsUiBridge uiBridge;

    public ChatMessageActionsHelper(@NonNull FragmentChatBinding binding,
                                    @NonNull FileRepository fileRepository,
                                    @NonNull ChatShareHelper shareHelper,
                                    @NonNull ChatViewModel chatViewModel,
                                    @NonNull FlashNoteViewModel flashNoteViewModel,
                                    @NonNull FavoriteRepository favoriteRepository,
                                    @NonNull ActionsUiBridge uiBridge) {
        this.binding = binding;
        this.fileRepository = fileRepository;
        this.shareHelper = shareHelper;
        this.chatViewModel = chatViewModel;
        this.flashNoteViewModel = flashNoteViewModel;
        this.favoriteRepository = favoriteRepository;
        this.uiBridge = uiBridge;
    }

    public void showMessageActions(@NonNull Message message,
                                   long currentFlashNoteId,
                                   @NonNull View clickedView) {
        if (!uiBridge.isAdded()) {
            return;
        }

        PopupMessageActionsBinding popupBinding = PopupMessageActionsBinding.inflate(uiBridge.getLayoutInflater());

        PopupWindow popupWindow = new PopupWindow(
                popupBinding.getRoot(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setBackgroundDrawable(null);
        popupWindow.setOutsideTouchable(true);

        popupBinding.actionForward.setOnClickListener(v -> {
            popupWindow.dismiss();
            showForwardDialog(message, currentFlashNoteId);
        });

        popupBinding.actionCopy.setOnClickListener(v -> {
            popupWindow.dismiss();
            String textToCopy = message.getContent();
            if (textToCopy == null || textToCopy.isEmpty()) {
                if (message.getFileName() != null && !message.getFileName().isEmpty()) {
                    textToCopy = message.getFileName();
                } else {
                    textToCopy = "";
                }
            }
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    uiBridge.requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("message", textToCopy);
            clipboard.setPrimaryClip(clip);
            if (uiBridge.isAdded() && uiBridge.getContext() != null) {
                android.widget.Toast.makeText(uiBridge.getContext(), "已复制", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        popupBinding.actionShare.setOnClickListener(v -> {
            popupWindow.dismiss();
            shareMessageToExternalApp(message);
        });

        popupBinding.actionFavorite.setOnClickListener(v -> {
            popupWindow.dismiss();
            handleFavorite(message);
        });

        popupBinding.actionDelete.setOnClickListener(v -> {
            popupWindow.dismiss();
            handleDelete(message);
        });

        popupBinding.actionSelect.setOnClickListener(v -> {
            popupWindow.dismiss();
            uiBridge.enterMultiSelectMode(message);
        });

        showPopupNearAnchor(clickedView, popupWindow, popupBinding.getRoot());
    }

    private void handleDelete(@NonNull Message message) {
        if (message.getId() == null) {
            android.content.Context context = uiBridge.getContext();
            if (context != null) {
                android.widget.Toast.makeText(context, "消息尚未保存，无法删除", android.widget.Toast.LENGTH_SHORT).show();
            }
            return;
        }

        new android.app.AlertDialog.Builder(uiBridge.requireContext())
                .setTitle("删除消息")
                .setMessage("确定要删除这条消息吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    uiBridge.markSkipNextScroll();
                    chatViewModel.deleteMessage(message.getId(), () -> {
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void shareMessageToExternalApp(@NonNull Message message) {
        if (!uiBridge.isAdded() || uiBridge.getContext() == null) {
            return;
        }
        Context context = uiBridge.requireContext();
        String mediaType = message.getMediaType();
        if (TextUtils.isEmpty(mediaType)
                || "TEXT".equalsIgnoreCase(mediaType)
                || TextUtils.isEmpty(message.getMediaUrl())) {
            uiBridge.showToast("仅支持分享图片/视频/语音/文件");
            return;
        }

        File localFile = shareHelper.tryResolveLocalFile(context, message.getMediaUrl());
        if (localFile != null && localFile.exists()) {
            try {
                shareHelper.shareFileByIntent(context, localFile, message);
            } catch (ActivityNotFoundException e) {
                uiBridge.showToast("未找到可分享的应用");
            }
            return;
        }

        String objectName = shareHelper.extractObjectNameForDownload(message.getMediaUrl());
        if (TextUtils.isEmpty(objectName)) {
            uiBridge.showToast("文件地址无效，无法分享");
            return;
        }
        fileRepository.download(objectName, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(String path) {
                uiBridge.runIfUiAlive(() -> {
                    File file = new File(path);
                    if (!file.exists()) {
                        uiBridge.showToast("分享文件不存在");
                        return;
                    }
                    try {
                        shareHelper.shareFileByIntent(context, file, message);
                    } catch (ActivityNotFoundException e) {
                        uiBridge.showToast("未找到可分享的应用");
                    }
                });
            }

            @Override
            public void onError(String errorMessage, int code) {
                uiBridge.runIfUiAlive(() -> uiBridge.showToast("准备分享失败: " + errorMessage));
            }
        });
    }

    private void handleFavorite(@NonNull Message message) {
        if (message.getId() == null) {
            android.content.Context context = uiBridge.getContext();
            if (context != null) {
                android.widget.Toast.makeText(context, "消息尚未保存，无法收藏", android.widget.Toast.LENGTH_SHORT).show();
            }
            return;
        }
        favoriteRepository.addFavorite(message.getId(), new FavoriteRepository.ActionCallback() {
            @Override
            public void onSuccess(String messageText) {
                uiBridge.runIfUiAlive(() -> {
                    android.content.Context context = uiBridge.getContext();
                    if (context != null) {
                        android.widget.Toast.makeText(context, messageText, android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String messageText, int code) {
                uiBridge.runIfUiAlive(() -> {
                    android.content.Context context = uiBridge.getContext();
                    if (context != null) {
                        android.widget.Toast.makeText(context, messageText, android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showForwardDialog(@NonNull Message message,
                                   long currentFlashNoteId) {
        List<FlashNote> notes = flashNoteViewModel.getNotes().getValue();
        if (notes == null || notes.isEmpty()) {
            android.content.Context context = uiBridge.getContext();
            if (context != null) {
                android.widget.Toast.makeText(context, "暂无可转发的闪记会话", android.widget.Toast.LENGTH_SHORT).show();
            }
            return;
        }

        List<FlashNote> targets = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        for (FlashNote note : notes) {
            if (note.getId() == null || note.getId() == currentFlashNoteId) {
                continue;
            }
            targets.add(note);
            titles.add(note.getTitle());
        }

        if (targets.isEmpty()) {
            android.content.Context context = uiBridge.getContext();
            if (context != null) {
                android.widget.Toast.makeText(context, "暂无其他闪记会话可转发", android.widget.Toast.LENGTH_SHORT).show();
            }
            return;
        }

        new android.app.AlertDialog.Builder(uiBridge.requireContext())
                .setTitle("转发到闪记")
                .setItems(titles.toArray(new String[0]), (dialog, which) -> {
                    FlashNote target = targets.get(which);
                    String mediaType = message.getMediaType();
                    if (mediaType != null && !mediaType.isEmpty() && !"TEXT".equalsIgnoreCase(mediaType)) {
                        Message forwardMsg = new Message();
                        forwardMsg.setMediaType(message.getMediaType());
                        forwardMsg.setMediaUrl(message.getMediaUrl());
                        forwardMsg.setFileName(message.getFileName());
                        forwardMsg.setFileSize(message.getFileSize());
                        forwardMsg.setMediaDuration(message.getMediaDuration());
                        forwardMsg.setThumbnailUrl(message.getThumbnailUrl());
                        forwardMsg.setContent(message.getContent());
                        forwardMsg.setPayload(message.getPayload());
                        forwardMsg.setFlashNoteId(target.getId());
                        forwardMsg.setRole("user");
                        chatViewModel.sendMessageToFlashNote(target.getId(), forwardMsg, () -> {
                            if (!uiBridge.isAdded() || uiBridge.getActivity() == null) {
                                return;
                            }
                            uiBridge.getActivity().runOnUiThread(() -> {
                                android.content.Context context = uiBridge.getContext();
                                if (uiBridge.isAdded() && context != null) {
                                    android.widget.Toast.makeText(context, "已转发到 " + target.getTitle(), android.widget.Toast.LENGTH_SHORT).show();
                                }
                            });
                        });
                    } else {
                        chatViewModel.sendTextToFlashNote(target.getId(), message.getContent(), () -> {
                            if (!uiBridge.isAdded() || uiBridge.getActivity() == null) {
                                return;
                            }
                            uiBridge.getActivity().runOnUiThread(() -> {
                                android.content.Context context = uiBridge.getContext();
                                if (uiBridge.isAdded() && context != null) {
                                    android.widget.Toast.makeText(context, "已转发到 " + target.getTitle(), android.widget.Toast.LENGTH_SHORT).show();
                                }
                            });
                        });
                    }
                })
                .show();
    }

    private void showPopupNearAnchor(@NonNull View anchor,
                                     @NonNull PopupWindow popup,
                                     @NonNull View content) {
        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = content.getMeasuredWidth();
        int popupHeight = content.getMeasuredHeight();

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int viewX = location[0];
        int viewY = location[1];
        int viewWidth = anchor.getWidth();
        int viewHeight = anchor.getHeight();

        float density = uiBridge.requireContext().getResources().getDisplayMetrics().density;
        int gap = (int) (4 * density);

        int screenHeight = uiBridge.requireContext().getResources().getDisplayMetrics().heightPixels;
        boolean showAbove = viewY > screenHeight / 2;

        int popupX = viewX + viewWidth / 2 - popupWidth / 2;
        int popupY = showAbove ? viewY - popupHeight - gap : viewY + viewHeight + gap;

        int screenWidth = uiBridge.requireContext().getResources().getDisplayMetrics().widthPixels;
        if (popupX < 0) popupX = 0;
        if (popupX + popupWidth > screenWidth) popupX = screenWidth - popupWidth;
        if (popupY < 0) popupY = 0;

        popup.showAtLocation(binding.getRoot(), Gravity.NO_GRAVITY, popupX, popupY);
    }
}
