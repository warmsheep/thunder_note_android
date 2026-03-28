package com.flashnote.java.ui.chat;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;

import com.flashnote.java.databinding.FragmentChatBinding;

public class ChatInputHelper {

    public interface InputCallbacks {
        void runOnUiThread(@NonNull Runnable action);

        void scrollToBottomAfterLayout();
    }

    private final FragmentChatBinding binding;
    private final ChatViewModel chatViewModel;
    private final InputCallbacks callbacks;

    public ChatInputHelper(@NonNull FragmentChatBinding binding,
                           @NonNull ChatViewModel chatViewModel,
                           @NonNull InputCallbacks callbacks) {
        this.binding = binding;
        this.chatViewModel = chatViewModel;
        this.callbacks = callbacks;
    }

    public void setupMessageInput() {
        int maxHeight = binding.getRoot().getResources().getDisplayMetrics().heightPixels / 3;
        binding.messageInput.setMaxHeight(maxHeight);
        binding.messageInput.setHorizontallyScrolling(false);
        binding.messageInput.setVerticalScrollBarEnabled(true);
        binding.messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                chatViewModel.saveDraft(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        binding.messageInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                sendMessage();
                return true;
            }
            return false;
        });
        binding.sendButton.setOnClickListener(v -> sendMessage());
    }

    public void sendMessage() {
        String text = binding.messageInput.getText() == null ? "" : binding.messageInput.getText().toString();
        chatViewModel.sendTextToCurrentConversation(text, () -> {
            callbacks.runOnUiThread(() -> {
                binding.messageInput.setText(null);
                chatViewModel.clearDraft();
                callbacks.scrollToBottomAfterLayout();
            });
        });
    }

    public void restoreDraft() {
        String draft = chatViewModel.getCurrentDraft();
        if (draft.isEmpty()) {
            return;
        }
        binding.messageInput.setText(draft);
        binding.messageInput.setSelection(draft.length());
    }
}
