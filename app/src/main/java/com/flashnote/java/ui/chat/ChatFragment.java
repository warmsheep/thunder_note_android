package com.flashnote.java.ui.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.data.repository.FavoriteRepository;
import com.flashnote.java.databinding.FragmentChatBinding;
import android.widget.Toast;

public class ChatFragment extends Fragment {
    private static final String ARG_FLASH_NOTE_ID = "flashNoteId";
    private static final String ARG_TITLE = "title";

    private FragmentChatBinding binding;

    public static ChatFragment newInstance(long flashNoteId, String title) {
        ChatFragment fragment = new ChatFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_FLASH_NOTE_ID, flashNoteId);
        args.putString(ARG_TITLE, title);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        long flashNoteId = getArguments() == null ? 0L : getArguments().getLong(ARG_FLASH_NOTE_ID);
        String title = getArguments() == null ? "闪记" : getArguments().getString(ARG_TITLE, "闪记");

        binding.titleText.setText(title);
        binding.backButton.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        FavoriteRepository favoriteRepository = FlashNoteApp.getInstance().getFavoriteRepository();
        MessageAdapter adapter = new MessageAdapter(message -> {
            if (message.getId() == null) {
                Toast.makeText(requireContext(), "消息尚未保存，无法收藏", Toast.LENGTH_SHORT).show();
                return;
            }
            favoriteRepository.addFavorite(message.getId(), new FavoriteRepository.ActionCallback() {
                @Override
                public void onSuccess(String message) {
                    if (!isAdded()) {
                        return;
                    }
                    requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onError(String message, int code) {
                    if (!isAdded()) {
                        return;
                    }
                    requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show());
                }
            });
        });
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        ChatViewModel viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        viewModel.bindFlashNote(flashNoteId);
        if (viewModel.getMessages() != null) {
            viewModel.getMessages().observe(getViewLifecycleOwner(), adapter::submitList);
        }
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.trim().isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });

        binding.sendButton.setOnClickListener(v -> {
            String text = binding.messageInput.getText() == null ? "" : binding.messageInput.getText().toString();
            viewModel.sendText(text, () -> {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> binding.messageInput.setText(null));
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
