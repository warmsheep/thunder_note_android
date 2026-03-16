package com.flashnote.java.ui.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.repository.FavoriteRepository;
import com.flashnote.java.databinding.FragmentChatBinding;
import com.flashnote.java.ui.main.FlashNoteViewModel;

import java.util.ArrayList;
import java.util.List;

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
        binding.backButton.setOnClickListener(v -> {
            if (!isAdded()) {
                return;
            }
            getParentFragmentManager().popBackStack();
        });

        FavoriteRepository favoriteRepository = FlashNoteApp.getInstance().getFavoriteRepository();
        ChatViewModel viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        FlashNoteViewModel flashNoteViewModel = new ViewModelProvider(this).get(FlashNoteViewModel.class);
        MessageAdapter adapter = new MessageAdapter(message -> showMessageActions(message, flashNoteId, favoriteRepository, viewModel, flashNoteViewModel));
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        viewModel.bindFlashNote(flashNoteId);
        if (viewModel.getMessages() != null) {
            viewModel.getMessages().observe(getViewLifecycleOwner(), adapter::submitList);
        }
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            android.content.Context context = getContext();
            if (binding != null && context != null && error != null && !error.trim().isEmpty() && isAdded()) {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
            }
        });

        binding.sendButton.setOnClickListener(v -> {
            String text = binding.messageInput.getText() == null ? "" : binding.messageInput.getText().toString();
            viewModel.sendText(text, () -> {
                if (!isAdded() || getActivity() == null || binding == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        binding.messageInput.setText(null);
                    }
                });
            });
        });
    }

    private void showMessageActions(Message message,
                                    long currentFlashNoteId,
                                    FavoriteRepository favoriteRepository,
                                    ChatViewModel chatViewModel,
                                    FlashNoteViewModel flashNoteViewModel) {
        if (!isAdded()) {
            return;
        }

        String[] actions = new String[]{"收藏", "转发"};
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("消息操作")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        handleFavorite(message, favoriteRepository);
                    } else if (which == 1) {
                        showForwardDialog(message, currentFlashNoteId, chatViewModel, flashNoteViewModel);
                    }
                })
                .show();
    }

    private void handleFavorite(Message message, FavoriteRepository favoriteRepository) {
        if (message.getId() == null) {
            android.content.Context context = getContext();
            if (context != null) {
                Toast.makeText(context, "消息尚未保存，无法收藏", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        favoriteRepository.addFavorite(message.getId(), new FavoriteRepository.ActionCallback() {
            @Override
            public void onSuccess(String messageText) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    android.content.Context context = getContext();
                    if (isAdded() && context != null) {
                        Toast.makeText(context, messageText, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String messageText, int code) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    android.content.Context context = getContext();
                    if (isAdded() && context != null) {
                        Toast.makeText(context, messageText, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showForwardDialog(Message message,
                                   long currentFlashNoteId,
                                   ChatViewModel chatViewModel,
                                   FlashNoteViewModel flashNoteViewModel) {
        List<FlashNote> notes = flashNoteViewModel.getNotes().getValue();
        if (notes == null || notes.isEmpty()) {
            android.content.Context context = getContext();
            if (context != null) {
                Toast.makeText(context, "暂无可转发的闪记会话", Toast.LENGTH_SHORT).show();
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
            android.content.Context context = getContext();
            if (context != null) {
                Toast.makeText(context, "暂无其他闪记会话可转发", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("转发到闪记")
                .setItems(titles.toArray(new String[0]), (dialog, which) -> {
                    FlashNote target = targets.get(which);
                    chatViewModel.sendTextToFlashNote(target.getId(), message.getContent(), () -> {
                        if (!isAdded() || getActivity() == null) {
                            return;
                        }
                        getActivity().runOnUiThread(() -> {
                            android.content.Context context = getContext();
                            if (isAdded() && context != null) {
                                Toast.makeText(context, "已转发到 " + target.getTitle(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
