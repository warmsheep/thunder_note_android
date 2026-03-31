package com.flashnote.java.ui.chat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.DebugLog;
import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.UserProfile;
import com.flashnote.java.data.repository.FavoriteRepository;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.data.repository.UserRepository;
import com.flashnote.java.databinding.FragmentChatBinding;
import com.flashnote.java.ui.main.FlashNoteViewModel;
import com.flashnote.java.ui.ExternalFlowGestureUnlockHelper;
import com.flashnote.java.ui.FragmentUiSafe;
import com.flashnote.java.ui.navigation.ShellNavigator;
import com.flashnote.java.util.VideoCompressor;

import java.io.File;
import java.util.List;

public class ChatFragment extends Fragment {
    private static final String ARG_FLASH_NOTE_ID = "flashNoteId";
    private static final String ARG_PEER_USER_ID = "peerUserId";
    private static final String ARG_TITLE = "title";
    private static final String ARG_SCROLL_TO_MESSAGE_ID = "scrollToMessageId";
    private static final long INBOX_NOTE_ID = -1L;

    private FragmentChatBinding binding;
    private ChatViewModel chatViewModel;
    private FileRepository fileRepository;
    private MessageAdapter adapter;
    private LinearLayoutManager layoutManager;
    private ChatScrollController scrollController;
    private ChatInputHelper inputHelper;
    private final ChatMediaHelper mediaHelper = new ChatMediaHelper();
    private final ChatShareHelper shareHelper = new ChatShareHelper();
    private ChatMessageActionsHelper messageActionsHelper;
    private ChatRecordingHelper recordingHelper;
    private ChatMultiSelectHelper multiSelectHelper;
    private ChatAttachmentFlowHelper attachmentHelper;
    private long currentFlashNoteId = 0L;
    private boolean isLoadingMore = false;
    private boolean hasMoreMessages = true;
    private boolean skipNextScroll = false;

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

    public static ChatFragment newContactInstance(long peerUserId, String title) {
        ChatFragment fragment = new ChatFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PEER_USER_ID, peerUserId);
        args.putString(ARG_TITLE, title);
        fragment.setArguments(args);
        return fragment;
    }

    private final ActivityResultLauncher<Intent> mediaPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null && attachmentHelper != null) {
                        attachmentHelper.onMediaPicked(uri);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null && attachmentHelper != null) {
                        attachmentHelper.onFilePicked(uri);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && attachmentHelper != null) {
                    Uri cameraUri = attachmentHelper.getCameraPhotoUri();
                    if (cameraUri != null) {
                        attachmentHelper.onCameraPhoto(cameraUri);
                    }
                }
            }
    );

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            results -> {
                boolean allGranted = true;
                for (Boolean granted : results.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (!allGranted) {
                    showToast("需要相关权限才能使用此功能");
                }
                if (recordingHelper != null) {
                    recordingHelper.onPermissionResult(allGranted);
                }
            }
    );

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
        long peerUserId = getArguments() == null ? 0L : getArguments().getLong(ARG_PEER_USER_ID);
        boolean isContactConversation = peerUserId > 0L;
        currentFlashNoteId = flashNoteId;
        String title = getArguments() == null ? "闪记" : getArguments().getString(ARG_TITLE, "闪记");

        binding.titleText.setText(title);
        binding.backButton.setOnClickListener(v -> {
            if (!isAdded()) {
                return;
            }
            if (multiSelectHelper != null && multiSelectHelper.isMultiSelectMode()) {
                multiSelectHelper.exitMultiSelectMode();
                return;
            }
            FragmentUiSafe.navigateBack(this);
        });

        FavoriteRepository favoriteRepository = FlashNoteApp.getInstance().getFavoriteRepository();
        fileRepository = FlashNoteApp.getInstance().getFileRepository();
        chatViewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        FlashNoteViewModel flashNoteViewModel = new ViewModelProvider(this).get(FlashNoteViewModel.class);
        messageActionsHelper = new ChatMessageActionsHelper(
                binding,
                fileRepository,
                shareHelper,
                chatViewModel,
                flashNoteViewModel,
                favoriteRepository,
                new ChatMessageActionsHelper.ActionsUiBridge() {
                    @Override
                    public boolean isAdded() {
                        return ChatFragment.this.isAdded();
                    }

                    @Nullable
                    @Override
                    public Context getContext() {
                        return ChatFragment.this.getContext();
                    }

                    @NonNull
                    @Override
                    public Context requireContext() {
                        return ChatFragment.this.requireContext();
                    }

                    @Nullable
                    @Override
                    public Activity getActivity() {
                        return ChatFragment.this.getActivity();
                    }

                    @NonNull
                    @Override
                    public LayoutInflater getLayoutInflater() {
                        return ChatFragment.this.getLayoutInflater();
                    }

                    @Override
                    public void showToast(@NonNull String message) {
                        ChatFragment.this.showToast(message);
                    }

                    @Override
                    public void runIfUiAlive(@NonNull Runnable action) {
                        ChatFragment.this.runIfUiAlive(action);
                    }

                    @Override
                    public void enterMultiSelectMode(@Nullable Message firstSelected) {
                        if (multiSelectHelper != null) {
                            multiSelectHelper.enterMultiSelectMode(firstSelected);
                        }
                    }

                    @Override
                    public void markSkipNextScroll() {
                        skipNextScroll = true;
                    }
                }
        );
        adapter = new MessageAdapter(
                (message, clickedView) -> messageActionsHelper.showMessageActions(message, flashNoteId, clickedView),
                localId -> chatViewModel.retryPendingMessage(localId)
        );
        multiSelectHelper = new ChatMultiSelectHelper(binding, adapter, chatViewModel, new ChatMultiSelectHelper.MultiSelectUiBridge() {
            @Override
            public void runOnUiThread(@NonNull Runnable action) {
                runIfUiAlive(action);
            }

            @Override
            public void showToast(@NonNull String message) {
                ChatFragment.this.showToast(message);
            }

            @Override
            public void exitMultiSelectModeOnUi() {
                if (multiSelectHelper != null) {
                    multiSelectHelper.exitMultiSelectMode();
                }
            }

            @Override
            public boolean isUiReady() {
                return isAdded();
            }

            @NonNull
            @Override
            public Context requireContext() {
                return ChatFragment.this.requireContext();
            }

            @NonNull
            @Override
            public LayoutInflater getLayoutInflater() {
                return ChatFragment.this.getLayoutInflater();
            }

            @Override
            public void markSkipNextScroll() {
                skipNextScroll = true;
            }
        });
        adapter.setOnSelectionChangedListener(multiSelectHelper::updateMergeSelectionCount);
        layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        binding.recyclerView.setLayoutManager(layoutManager);
        binding.recyclerView.setAdapter(adapter);
        binding.recyclerView.setItemAnimator(null);
        binding.recyclerView.setItemViewCacheSize(24);
        scrollController = new ChatScrollController(
                new ChatScrollController.ScrollCallback() {
                    @Override
                    public void scrollToBottomImmediate() {
                        if (binding == null || adapter == null) {
                            return;
                        }
                        int count = adapter.getItemCount();
                        if (count > 0) {
                            binding.recyclerView.scrollToPosition(count - 1);
                        }
                    }

                    @Override
                    public void scrollToPosition(int position, int offset) {
                        if (layoutManager != null) {
                            layoutManager.scrollToPositionWithOffset(position, offset);
                        }
                    }

                    @Override
                    public void onLoadMoreTriggered() {
                        isLoadingMore = true;
                        chatViewModel.loadMore();
                    }

                    @Override
                    public void preloadRecentMedia(@NonNull List<Message> messages) {
                        if (isAdded()) {
                            adapter.preloadRecentMedia(messages, requireContext());
                        }
                    }
                },
                binding,
                adapter,
                layoutManager
        );
        scrollController.onViewReady();

        UserRepository userRepository = FlashNoteApp.getInstance().getUserRepository();
        userRepository.getProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile != null && profile.getAvatar() != null && !profile.getAvatar().isEmpty()) {
                adapter.setUserAvatarUrl(profile.getAvatar(), requireContext());
                adapter.refreshVisibleAvatars();
            }
        });
        
        if (isContactConversation) {
            userRepository.getContacts().observe(getViewLifecycleOwner(), contacts -> {
                if (contacts != null) {
                    for (com.flashnote.java.data.model.ContactUser contact : contacts) {
                        if (contact.getUserId() != null && contact.getUserId() == peerUserId) {
                            String avatar = contact.getAvatar();
                            if (avatar != null && !avatar.isEmpty()) {
                                adapter.setPeerAvatar(avatar);
                                adapter.refreshVisibleAvatars();
                            }
                            break;
                        }
                    }
                }
            });
        }
        userRepository.refresh();

        binding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                scrollController.onScrolled(recyclerView, dy, isLoadingMore, hasMoreMessages);
            }
        });

        if (isContactConversation) {
            chatViewModel.bindContact(peerUserId);
        } else {
            chatViewModel.bindFlashNote(flashNoteId);
        }
        long scrollToMessageId = getArguments() == null ? 0L : getArguments().getLong(ARG_SCROLL_TO_MESSAGE_ID, 0L);
        
        if (chatViewModel.getMessages() != null) {
            chatViewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
                boolean wasLoadingMore = isLoadingMore;
                isLoadingMore = false;
                boolean shouldAutoScroll = scrollController.shouldAutoScrollOnMessageUpdate(messages, wasLoadingMore, scrollToMessageId);
                adapter.submitList(messages);
                if (isAdded()) {
                    scrollController.shouldPreloadRecentMedia(messages);
                }
                // Scroll to bottom if not search location scenario
                if (scrollToMessageId <= 0 && messages != null && !messages.isEmpty() && !wasLoadingMore) {
                    if (skipNextScroll) {
                        skipNextScroll = false;
                    } else if (shouldAutoScroll) {
                        scrollController.scrollToBottomAfterLayout(messages);
                        scrollController.setAutoScrollEnabled(true);
                    } else {
                        scrollController.setAutoScrollEnabled(false);
                    }
                    scrollController.setInitialScrollCompleted(true);
                }
                if (wasLoadingMore && binding != null) {
                    scrollController.restoreAfterPrepend(messages == null ? 0 : messages.size());
                }
                // Scroll to target message if specified
                if (scrollToMessageId > 0 && messages != null) {
                    for (int i = 0; i < messages.size(); i++) {
                        if (messages.get(i).getId() != null && messages.get(i).getId() == scrollToMessageId) {
                            final int position = i;
                            binding.recyclerView.post(() -> {
                                binding.recyclerView.scrollToPosition(position);
                                scrollController.setInitialScrollCompleted(true);
                                // Highlight after scroll completes
                                binding.recyclerView.postDelayed(() -> highlightMessage(position), 300);
                            });
                            break;
                        }
                    }
                } else if (!scrollController.isInitialScrollCompleted()) {
                    scrollController.setInitialScrollCompleted(true);
                }
                scrollController.rememberRenderedTailState(messages);
            });
        }
        chatViewModel.getHasMore().observe(getViewLifecycleOwner(), hasMore -> {
            hasMoreMessages = hasMore == null || hasMore;
            if (!hasMoreMessages) {
                isLoadingMore = false;
            }
        });
        chatViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            android.content.Context context = getContext();
            if (binding != null && context != null && error != null && !error.trim().isEmpty() && isAdded()) {
                chatViewModel.clearError();
            }
        });

        inputHelper = new ChatInputHelper(binding, chatViewModel, new ChatInputHelper.InputCallbacks() {
            @Override
            public void runOnUiThread(@NonNull Runnable action) {
                runIfUiAlive(action);
            }

            @Override
            public void scrollToBottomAfterLayout() {
                if (scrollController != null) {
                    scrollController.setAutoScrollEnabled(true);
                    scrollController.scrollToBottomAfterLayout(null);
                }
            }
        });
        inputHelper.setupMessageInput();
        inputHelper.restoreDraft();
        attachmentHelper = new ChatAttachmentFlowHelper(
                binding,
                chatViewModel,
                mediaHelper,
                new ChatAttachmentFlowHelper.AttachmentUiBridge() {
                    @NonNull
                    @Override
                    public Context requireContext() {
                        return ChatFragment.this.requireContext();
                    }

                    @Override
                    public void runOnUiThread(@NonNull Runnable action) {
                        ChatFragment.this.runIfUiAlive(action);
                    }

                    @Override
                    public void showToast(@NonNull String message) {
                        ChatFragment.this.showToast(message);
                    }

                    @Override
                    public void scrollToBottomAfterLayout() {
                        if (scrollController != null) {
                            scrollController.scrollToBottomAfterLayout(null);
                        }
                    }

                    @Override
                    public void suppressGestureUnlockForExternalFlow() {
                        ExternalFlowGestureUnlockHelper.registerExternalFlow(ChatFragment.this);
                    }
                },
                new ChatAttachmentFlowHelper.LauncherBridge() {
                    @Override
                    public void openMediaPicker(@NonNull Intent intent) {
                        mediaPickerLauncher.launch(intent);
                    }

                    @Override
                    public void openFilePicker(@NonNull Intent intent) {
                        filePickerLauncher.launch(intent);
                    }

                    @Override
                    public void openCamera(@NonNull Intent intent) {
                        cameraLauncher.launch(intent);
                    }

                    @Override
                    public void openCardEditor() {
                        ChatFragment.this.openCardEditor();
                    }
                }
        );
        attachmentHelper.setupToolsPanel();
        recordingHelper = new ChatRecordingHelper(
                new ChatRecordingHelper.UiCallback() {
                    @Override
                    public void showToast(@NonNull String message) {
                        ChatFragment.this.showToast(message);
                    }

                    @Override
                    public void runIfUiAlive(@NonNull Runnable action) {
                        ChatFragment.this.runIfUiAlive(action);
                    }

                    @Override
                    public void scrollToBottomAfterLayout() {
                        if (scrollController != null) {
                            scrollController.scrollToBottomAfterLayout(null);
                        }
                    }
                },
                binding,
                chatViewModel,
                permissionLauncher
        );
        setupMicButton();
        binding.mergeCancelButton.setOnClickListener(v -> multiSelectHelper.exitMultiSelectMode());
        binding.mergeDeleteButton.setOnClickListener(v -> multiSelectHelper.handleBatchDelete());
        binding.mergeConfirmButton.setOnClickListener(v -> multiSelectHelper.handleMergeAction());
        multiSelectHelper.updateMergeSelectionCount(0);
    }

    private void highlightMessage(int position) {
        RecyclerView.ViewHolder holder = binding.recyclerView.findViewHolderForAdapterPosition(position);
        if (holder != null) {
            View itemView = holder.itemView;
            View leftContainer = itemView.findViewById(R.id.leftContainer);
            View rightContainer = itemView.findViewById(R.id.rightContainer);
            
            View targetView;
            if (rightContainer != null && rightContainer.getVisibility() == View.VISIBLE) {
                targetView = rightContainer;
            } else if (leftContainer != null) {
                targetView = leftContainer;
            } else {
                targetView = itemView;
            }
            
            int highlightColor = getResources().getColor(R.color.search_highlight, null);
            int originalColor = android.graphics.Color.TRANSPARENT;
            android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofArgb(highlightColor, originalColor);
            animator.setDuration(1500);
            animator.setRepeatCount(1);
            animator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            animator.addUpdateListener(animation -> targetView.setBackgroundColor((int) animation.getAnimatedValue()));
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    targetView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                }
            });
            animator.start();
        }
    }
    private void openCardEditor() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        long flashNoteId = getArguments() == null ? 0L : getArguments().getLong(ARG_FLASH_NOTE_ID, 0L);
        long peerUserId = getArguments() == null ? 0L : getArguments().getLong(ARG_PEER_USER_ID, 0L);
        String pageTitle = binding == null || binding.titleText.getText() == null
                ? "新建卡片"
                : binding.titleText.getText().toString();
        if (getActivity() instanceof ShellNavigator navigator) {
            navigator.openCardEditor(flashNoteId, peerUserId, pageTitle);
        }
    }

    private void setupMicButton() {
        if (recordingHelper != null) {
            recordingHelper.setupMicButton();
        }
    }

    private void runIfUiAlive(@NonNull Runnable action) {
        FragmentUiSafe.runIfUiAlive(this, binding, action);
    }

    private void showToast(String message) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null && chatViewModel != null) {
            String draft = binding.messageInput.getText() == null ? "" : binding.messageInput.getText().toString();
            chatViewModel.saveDraft(draft);
        }
        if (recordingHelper != null) {
            recordingHelper.release();
            recordingHelper = null;
        }
        if (scrollController != null) {
            scrollController.release();
        }
        scrollController = null;
        layoutManager = null;
        binding = null;
    }
}
