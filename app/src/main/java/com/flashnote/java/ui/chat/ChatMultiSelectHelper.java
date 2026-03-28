package com.flashnote.java.ui.chat;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.repository.MessageRepository;
import com.flashnote.java.databinding.DialogMergeCardTitleBinding;
import com.flashnote.java.databinding.FragmentChatBinding;

import java.util.ArrayList;
import java.util.List;

public class ChatMultiSelectHelper {

    public interface MultiSelectUiBridge {
        void runOnUiThread(@NonNull Runnable action);

        void showToast(@NonNull String message);

        void exitMultiSelectModeOnUi();

        boolean isUiReady();

        @NonNull
        Context requireContext();

        @NonNull
        LayoutInflater getLayoutInflater();

        void markSkipNextScroll();
    }

    private final FragmentChatBinding binding;
    private final MessageAdapter adapter;
    private final ChatViewModel chatViewModel;
    private final MultiSelectUiBridge uiBridge;

    private boolean isMultiSelectMode = false;
    private final List<Long> selectedIds = new ArrayList<>();

    public ChatMultiSelectHelper(@NonNull FragmentChatBinding binding,
                                 @NonNull MessageAdapter adapter,
                                 @NonNull ChatViewModel chatViewModel,
                                 @NonNull MultiSelectUiBridge uiBridge) {
        this.binding = binding;
        this.adapter = adapter;
        this.chatViewModel = chatViewModel;
        this.uiBridge = uiBridge;
    }

    public boolean isMultiSelectMode() {
        return isMultiSelectMode;
    }

    public void enterMultiSelectMode(@Nullable Message firstSelectedMessage) {
        if (adapter == null || binding == null) return;
        isMultiSelectMode = true;
        adapter.setSelectionMode(true);
        if (firstSelectedMessage != null) {
            adapter.toggleSelection(firstSelectedMessage);
        }
        binding.inputContainer.setVisibility(View.GONE);
        binding.toolsPanel.setVisibility(View.GONE);
        binding.mergePanel.setVisibility(View.VISIBLE);
        updateMergeSelectionCount(adapter.getSelectedCount());
    }

    public void exitMultiSelectMode() {
        if (adapter == null || binding == null) return;
        isMultiSelectMode = false;
        adapter.setSelectionMode(false);
        binding.inputContainer.setVisibility(View.VISIBLE);
        binding.mergePanel.setVisibility(View.GONE);
        updateMergeSelectionCount(0);
    }

    public void handleMergeAction() {
        if (adapter == null || chatViewModel == null || !uiBridge.isUiReady()) return;
        selectedIds.clear();
        selectedIds.addAll(adapter.getSelectedIds());
        int selectedCount = selectedIds.size();
        if (selectedCount <= 0) {
            uiBridge.showToast("请先选择消息");
            return;
        }

        if (selectedCount < 2) {
            uiBridge.showToast("请至少选择 2 条消息进行合并");
            return;
        }

        DialogMergeCardTitleBinding dialogBinding = DialogMergeCardTitleBinding.inflate(uiBridge.getLayoutInflater());
        dialogBinding.mergeHintText.setText("将 " + selectedCount + " 条消息整理为一张可分享的卡片");
        dialogBinding.titleInput.setText("闪记卡片消息（" + selectedCount + "条）");
        if (dialogBinding.titleInput.getText() != null) {
            dialogBinding.titleInput.setSelection(dialogBinding.titleInput.getText().length());
        }

        new AlertDialog.Builder(uiBridge.requireContext())
                .setView(dialogBinding.getRoot())
                .setNegativeButton("取消", null)
                .setPositiveButton("合并", (dialog, which) -> submitMerge(new ArrayList<>(selectedIds), dialogBinding.titleInput.getText() == null ? "" : dialogBinding.titleInput.getText().toString().trim()))
                .show();
    }

    public void submitMerge(@NonNull List<Long> selectedIds, @NonNull String title) {
        if (chatViewModel == null) {
            return;
        }
        if (title.isEmpty()) {
            uiBridge.showToast("请输入卡片标题");
            return;
        }

        chatViewModel.mergeMessages(selectedIds, title, new MessageRepository.MergeCallback() {
            @Override
            public void onSuccess(Message mergedMessage) {
                uiBridge.runOnUiThread(() -> {
                    chatViewModel.addLocalMessage(mergedMessage);
                    uiBridge.exitMultiSelectModeOnUi();
                });
            }

            @Override
            public void onError(String errorMessage) {
                uiBridge.runOnUiThread(() -> uiBridge.showToast("合并失败: " + errorMessage));
            }
        });
    }

    public void handleBatchDelete() {
        if (adapter == null || chatViewModel == null || !uiBridge.isUiReady()) {
            return;
        }
        selectedIds.clear();
        selectedIds.addAll(adapter.getSelectedIds());
        if (selectedIds.isEmpty()) {
            uiBridge.showToast("请先选择消息");
            return;
        }
        new AlertDialog.Builder(uiBridge.requireContext())
                .setTitle("批量删除消息")
                .setMessage("确定要删除选中的 " + selectedIds.size() + " 条消息吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    uiBridge.markSkipNextScroll();
                    chatViewModel.deleteMessages(new ArrayList<>(selectedIds), () -> {
                        uiBridge.runOnUiThread(uiBridge::exitMultiSelectModeOnUi);
                    });
                })
                .show();
    }

    public void updateMergeSelectionCount(int selectedCount) {
        if (binding == null) {
            return;
        }
        binding.mergeTitleText.setText("已选择 " + selectedCount + " 条消息");
    }
}
