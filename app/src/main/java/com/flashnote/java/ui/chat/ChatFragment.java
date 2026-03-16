package com.flashnote.java.ui.chat;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.repository.FavoriteRepository;
import com.flashnote.java.databinding.FragmentChatBinding;
import com.flashnote.java.databinding.PopupMessageActionsBinding;
import com.flashnote.java.ui.main.FlashNoteViewModel;

import java.util.ArrayList;
import java.util.List;

public class ChatFragment extends Fragment {
    private static final String ARG_FLASH_NOTE_ID = "flashNoteId";
    private static final String ARG_TITLE = "title";
    private static final String ARG_SCROLL_TO_MESSAGE_ID = "scrollToMessageId";

    private FragmentChatBinding binding;

    public static ChatFragment newInstance(long flashNoteId, String title) {
        ChatFragment fragment = new ChatFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_FLASH_NOTE_ID, flashNoteId);
        args.putString(ARG_TITLE, title);
        fragment.setArguments(args);
        return fragment;
    }

    public static ChatFragment newInstance(long flashNoteId, String title, long scrollToMessageId) {
        ChatFragment fragment = new ChatFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_FLASH_NOTE_ID, flashNoteId);
        args.putString(ARG_TITLE, title);
        args.putLong(ARG_SCROLL_TO_MESSAGE_ID, scrollToMessageId);
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
        MessageAdapter adapter = new MessageAdapter((message, clickedView) -> showMessageActions(message, flashNoteId, favoriteRepository, viewModel, flashNoteViewModel, clickedView));
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        viewModel.bindFlashNote(flashNoteId);
        long scrollToMessageId = getArguments() == null ? 0L : getArguments().getLong(ARG_SCROLL_TO_MESSAGE_ID, 0L);
        
        if (viewModel.getMessages() != null) {
            viewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
                adapter.submitList(messages);
                // Scroll to target message if specified
                if (scrollToMessageId > 0 && messages != null) {
                    for (int i = 0; i < messages.size(); i++) {
                        if (messages.get(i).getId() != null && messages.get(i).getId() == scrollToMessageId) {
                            final int position = i;
                            binding.recyclerView.post(() -> {
                                binding.recyclerView.scrollToPosition(position);
                                // Highlight after scroll completes
                                binding.recyclerView.postDelayed(() -> highlightMessage(position), 300);
                            });
                            break;
                        }
                    }
                }
            });
        }
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            android.content.Context context = getContext();
            if (binding != null && context != null && error != null && !error.trim().isEmpty() && isAdded()) {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
            }
        });

        setupMessageInput(viewModel);
        binding.sendButton.setOnClickListener(v -> sendMessage(viewModel));
    }

    private void setupMessageInput(@NonNull ChatViewModel viewModel) {
        int maxHeight = getResources().getDisplayMetrics().heightPixels / 3;
        binding.messageInput.setMaxHeight(maxHeight);
        binding.messageInput.setHorizontallyScrolling(false);
        binding.messageInput.setVerticalScrollBarEnabled(true);
        binding.messageInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                if (event.isShiftPressed()) {
                    return false;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    sendMessage(viewModel);
                }
                return true;
            }
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                sendMessage(viewModel);
                return true;
            }
            return false;
        });
    }

    private void sendMessage(@NonNull ChatViewModel viewModel) {
        if (binding == null) {
            return;
        }
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
    }

    private void highlightMessage(int position) {
        RecyclerView.ViewHolder holder = binding.recyclerView.findViewHolderForAdapterPosition(position);
        if (holder != null) {
            View itemView = holder.itemView;
            int highlightColor = getResources().getColor(R.color.search_highlight, null);
            int originalColor = android.graphics.Color.TRANSPARENT;
            android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofArgb(highlightColor, originalColor);
            animator.setDuration(1500);
            animator.setRepeatCount(1);
            animator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            animator.addUpdateListener(animation -> itemView.setBackgroundColor((int) animation.getAnimatedValue()));
            animator.start();
        }
    }

    private void showMessageActions(Message message,
                                    long currentFlashNoteId,
                                    FavoriteRepository favoriteRepository,
                                    ChatViewModel chatViewModel,
                                    FlashNoteViewModel flashNoteViewModel,
                                    View clickedView) {
        if (!isAdded() || binding == null) {
            return;
        }

        PopupMessageActionsBinding popupBinding = PopupMessageActionsBinding.inflate(getLayoutInflater());
        
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
            showForwardDialog(message, currentFlashNoteId, chatViewModel, flashNoteViewModel);
        });

        popupBinding.actionFavorite.setOnClickListener(v -> {
            popupWindow.dismiss();
            handleFavorite(message, favoriteRepository);
        });

        popupBinding.actionDelete.setOnClickListener(v -> {
            popupWindow.dismiss();
            handleDelete(message, chatViewModel);
        });

        popupBinding.getRoot().measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = popupBinding.getRoot().getMeasuredWidth();
        int popupHeight = popupBinding.getRoot().getMeasuredHeight();

        int[] location = new int[2];
        clickedView.getLocationOnScreen(location);
        int viewX = location[0];
        int viewY = location[1];
        int viewHeight = clickedView.getHeight();

        int screenHeight = binding.getRoot().getResources().getDisplayMetrics().heightPixels;
        boolean showAbove = viewY > screenHeight / 2;

        int xOff = -popupWidth / 2;
        int yOff = showAbove ? -viewHeight - popupHeight - 10 : 10;

        popupWindow.showAtLocation(clickedView, Gravity.NO_GRAVITY, viewX + clickedView.getWidth() / 2 + xOff, viewY + yOff);
    }

    private void handleDelete(Message message, ChatViewModel chatViewModel) {
        if (message.getId() == null) {
            android.content.Context context = getContext();
            if (context != null) {
                Toast.makeText(context, "消息尚未保存，无法删除", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("删除消息")
                .setMessage("确定要删除这条消息吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    chatViewModel.deleteMessage(message.getId(), () -> {
                        if (!isAdded() || getActivity() == null) {
                            return;
                        }
                        getActivity().runOnUiThread(() -> {
                            android.content.Context context = getContext();
                            if (isAdded() && context != null) {
                                Toast.makeText(context, "消息已删除", Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                })
                .setNegativeButton("取消", null)
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
