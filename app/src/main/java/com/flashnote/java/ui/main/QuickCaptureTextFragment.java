package com.flashnote.java.ui.main;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.ui.main.FlashNoteViewModel;
import com.flashnote.java.databinding.FragmentQuickCaptureTextBinding;
import com.flashnote.java.data.repository.MessageRepository;
import androidx.lifecycle.ViewModelProvider;

public class QuickCaptureTextFragment extends Fragment {
    private static final long COLLECTION_BOX_NOTE_ID = -1L;

    private FragmentQuickCaptureTextBinding binding;
    private MessageRepository messageRepository;
    private FlashNoteViewModel flashNoteViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentQuickCaptureTextBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        messageRepository = FlashNoteApp.getInstance().getMessageRepository();
        flashNoteViewModel = new ViewModelProvider(requireActivity()).get(FlashNoteViewModel.class);

        binding.cancelButton.setOnClickListener(v -> navigateBack());
        binding.saveButton.setOnClickListener(v -> saveCaptureText());

        binding.formatBold.setOnClickListener(v -> insertToken("**", "**"));
        binding.formatItalic.setOnClickListener(v -> insertToken("*", "*"));
        binding.formatQuote.setOnClickListener(v -> insertLinePrefix("> "));
        binding.formatTodo.setOnClickListener(v -> insertLinePrefix("- [ ] "));
    }

    private void saveCaptureText() {
        if (binding == null || messageRepository == null) {
            return;
        }
        String content = binding.contentInput.getText() == null
                ? ""
                : binding.contentInput.getText().toString().trim();
        if (content.isEmpty()) {
            showToast("请输入内容");
            return;
        }
        messageRepository.sendText(COLLECTION_BOX_NOTE_ID, content, () -> {
            runIfUiAlive(() -> {
                if (flashNoteViewModel != null) {
                    flashNoteViewModel.updateInboxPreviewLocally(content);
                }
                Bundle result = new Bundle();
                result.putString("inbox_preview", content);
                getParentFragmentManager().setFragmentResult("quick_capture_saved", result);
                navigateBack();
            });
        });
    }

    private void insertToken(@NonNull String prefix, @NonNull String suffix) {
        if (binding == null || binding.contentInput.getText() == null) {
            return;
        }
        Editable editable = binding.contentInput.getText();
        int start = Math.max(0, binding.contentInput.getSelectionStart());
        int end = Math.max(0, binding.contentInput.getSelectionEnd());
        if (start > end) {
            int temp = start;
            start = end;
            end = temp;
        }
        String selected = editable.subSequence(start, end).toString();
        String replacement = prefix + selected + suffix;
        editable.replace(start, end, replacement);
        int cursor = start + replacement.length();
        binding.contentInput.setSelection(Math.min(cursor, editable.length()));
    }

    private void insertLinePrefix(@NonNull String prefix) {
        if (binding == null || binding.contentInput.getText() == null) {
            return;
        }
        Editable editable = binding.contentInput.getText();
        int start = Math.max(0, binding.contentInput.getSelectionStart());
        int lineStart = start;
        while (lineStart > 0 && editable.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        editable.insert(lineStart, prefix);
        binding.contentInput.setSelection(Math.min(start + prefix.length(), editable.length()));
    }

    private void navigateBack() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        getActivity().getSupportFragmentManager().popBackStack();
    }

    private void showToast(@NonNull String text) {
        if (!isAdded() || getContext() == null || TextUtils.isEmpty(text)) {
            return;
        }
        Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
    }

    private void runIfUiAlive(@NonNull Runnable action) {
        if (!isAdded() || binding == null) {
            return;
        }
        binding.getRoot().post(() -> {
            if (!isAdded() || binding == null) {
                return;
            }
            action.run();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
