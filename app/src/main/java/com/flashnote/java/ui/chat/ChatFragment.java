package com.flashnote.java.ui.chat;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.PopupWindow;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.webkit.MimeTypeMap;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.DebugLog;
import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.UserProfile;
import com.flashnote.java.data.repository.FavoriteRepository;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.data.repository.UserRepository;
import com.flashnote.java.databinding.DialogMergeCardTitleBinding;
import com.flashnote.java.databinding.FragmentChatBinding;
import com.flashnote.java.databinding.PopupMessageActionsBinding;
import com.flashnote.java.ui.main.FlashNoteViewModel;
import com.flashnote.java.ui.FragmentUiSafe;
import com.flashnote.java.ui.navigation.ShellNavigator;
import com.flashnote.java.util.VideoCompressor;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
    private final ChatMediaHelper mediaHelper = new ChatMediaHelper();
    private final ChatShareHelper shareHelper = new ChatShareHelper();
    private boolean isToolsPanelVisible = false;
    private boolean isRecording = false;
    private MediaRecorder mediaRecorder;
    private String currentRecordingPath;
    private Uri cameraPhotoUri;
    
    // Recording UI state
    private Handler recordingTimerHandler;
    private Runnable recordingTimerRunnable;
    private int recordingSeconds = 0;
    private long currentFlashNoteId = 0L;
    private boolean isLoadingMore = false;
    private boolean hasMoreMessages = true;
    private boolean isInitialScrollCompleted = false;
    private int loadMoreAnchorPosition = RecyclerView.NO_POSITION;
    private int loadMoreAnchorOffset = 0;
    private int messageCountBeforeLoadMore = 0;
    private boolean isMultiSelectMode = false;
    private boolean skipNextScroll = false;
    private boolean autoScrollEnabled = true;
    private int lastRenderedMessageCount = 0;
    private String lastRenderedTailKey = "";
    private boolean lastRenderedTailUploading = false;
    private String lastPreloadedMediaKey = "";

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
                    if (uri != null) {
                        handleMediaPicked(uri);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        handleFilePicked(uri);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && cameraPhotoUri != null) {
                    handleCameraPhoto(cameraPhotoUri);
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
            if (isMultiSelectMode) {
                exitMultiSelectMode();
                return;
            }
            getParentFragmentManager().popBackStack();
        });

        FavoriteRepository favoriteRepository = FlashNoteApp.getInstance().getFavoriteRepository();
        fileRepository = FlashNoteApp.getInstance().getFileRepository();
        chatViewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        FlashNoteViewModel flashNoteViewModel = new ViewModelProvider(this).get(FlashNoteViewModel.class);
        adapter = new MessageAdapter(
                (message, clickedView) -> showMessageActions(message, flashNoteId, favoriteRepository, chatViewModel, flashNoteViewModel, clickedView),
                localId -> chatViewModel.retryPendingMessage(localId)
        );
        adapter.setOnSelectionChangedListener(this::updateMergeSelectionCount);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);
        binding.recyclerView.setItemAnimator(null);
        binding.recyclerView.setItemViewCacheSize(24);

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
                if (dy < 0) {
                    autoScrollEnabled = false;
                } else if (dy > 0) {
                    autoScrollEnabled = isNearBottom();
                }
                if (!isInitialScrollCompleted || dy >= 0 || isLoadingMore || !hasMoreMessages) {
                    return;
                }
                if (!recyclerView.canScrollVertically(-1)) {
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        loadMoreAnchorPosition = layoutManager.findFirstVisibleItemPosition();
                        View anchorView = layoutManager.findViewByPosition(loadMoreAnchorPosition);
                        loadMoreAnchorOffset = anchorView == null ? 0 : anchorView.getTop();
                    }
                    messageCountBeforeLoadMore = adapter.getItemCount();
                    isLoadingMore = true;
                    chatViewModel.loadMore();
                }
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
                boolean shouldAutoScroll = shouldAutoScrollOnMessageUpdate(messages, wasLoadingMore, scrollToMessageId);
                adapter.submitList(messages);
                if (isAdded() && shouldPreloadRecentMedia(messages)) {
                    adapter.preloadRecentMedia(messages, requireContext());
                }
                // Scroll to bottom if not search location scenario
                if (scrollToMessageId <= 0 && messages != null && !messages.isEmpty() && !wasLoadingMore) {
                    if (skipNextScroll) {
                        skipNextScroll = false;
                    } else if (shouldAutoScroll) {
                        scrollToBottomAfterLayout(messages);
                        autoScrollEnabled = true;
                    } else {
                        autoScrollEnabled = false;
                    }
                    isInitialScrollCompleted = true;
                }
                if (wasLoadingMore && binding != null) {
                    restoreAfterPrepend(messages == null ? 0 : messages.size());
                }
                // Scroll to target message if specified
                if (scrollToMessageId > 0 && messages != null) {
                    for (int i = 0; i < messages.size(); i++) {
                        if (messages.get(i).getId() != null && messages.get(i).getId() == scrollToMessageId) {
                            final int position = i;
                            binding.recyclerView.post(() -> {
                                binding.recyclerView.scrollToPosition(position);
                                isInitialScrollCompleted = true;
                                // Highlight after scroll completes
                                binding.recyclerView.postDelayed(() -> highlightMessage(position), 300);
                            });
                            break;
                        }
                    }
                } else if (!isInitialScrollCompleted) {
                    isInitialScrollCompleted = true;
                }
                rememberRenderedTailState(messages);
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

        setupMessageInput(chatViewModel);
        restoreDraft();
        binding.sendButton.setOnClickListener(v -> sendMessage(chatViewModel));
        setupToolsPanel();
        setupMicButton();
        binding.mergeCancelButton.setOnClickListener(v -> exitMultiSelectMode());
        binding.mergeDeleteButton.setOnClickListener(v -> handleBatchDelete());
        binding.mergeConfirmButton.setOnClickListener(v -> handleMergeAction());
        updateMergeSelectionCount(0);
    }

    private boolean shouldPreloadRecentMedia(@Nullable List<Message> messages) {
        String mediaKey = buildRecentMediaKey(messages);
        if (mediaKey.equals(lastPreloadedMediaKey)) {
            return false;
        }
        lastPreloadedMediaKey = mediaKey;
        return !mediaKey.isEmpty();
    }

    @NonNull
    private String buildRecentMediaKey(@Nullable List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int start = Math.max(0, messages.size() - 12);
        for (int i = start; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message == null || message.isUploading()) {
                continue;
            }
            String mediaType = message.getMediaType();
            if (!"IMAGE".equalsIgnoreCase(mediaType) && !"VIDEO".equalsIgnoreCase(mediaType)) {
                continue;
            }
            builder.append(message.getId()).append(':')
                    .append(message.getMediaUrl()).append(':')
                    .append(message.getThumbnailUrl()).append('|');
        }
        return builder.toString();
    }

    private void setupMessageInput(@NonNull ChatViewModel viewModel) {
        int maxHeight = getResources().getDisplayMetrics().heightPixels / 3;
        binding.messageInput.setMaxHeight(maxHeight);
        binding.messageInput.setHorizontallyScrolling(false);
        binding.messageInput.setVerticalScrollBarEnabled(true);
        binding.messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.saveDraft(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        binding.messageInput.setOnEditorActionListener((textView, actionId, event) -> {
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
        viewModel.sendTextToCurrentConversation(text, () -> {
            runIfUiAlive(() -> {
                if (binding != null) {
                    binding.messageInput.setText(null);
                    viewModel.clearDraft();
                }
                autoScrollEnabled = true;
                scrollToBottomAfterLayout(null);
            });
        });
    }

    private void scrollToBottomAfterLayout(@Nullable List<Message> messages) {
        if (binding == null || binding.recyclerView == null || adapter == null) {
            return;
        }
        int count = adapter.getItemCount();
        if (count <= 0) {
            return;
        }
        autoScrollEnabled = true;
        int last = count - 1;
        binding.recyclerView.post(() -> {
            if (binding == null) {
                return;
            }
            ensureBottomVisible(last, isLastComplexMessage(messages, last) ? 10 : 4);
            binding.recyclerView.postDelayed(() -> ensureBottomVisible(last, isLastComplexMessage(messages, last) ? 4 : 2), 420);
        });
    }

    private void ensureBottomVisible(int lastPosition, int attempts) {
        if (binding == null || attempts <= 0) {
            return;
        }
        RecyclerView recyclerView = binding.recyclerView;
        recyclerView.scrollToPosition(lastPosition);
        recyclerView.postDelayed(() -> {
            if (binding == null) {
                return;
            }
            RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
            if (!(manager instanceof LinearLayoutManager layoutManager)) {
                return;
            }
            int lastVisible = layoutManager.findLastCompletelyVisibleItemPosition();
            if (lastVisible >= lastPosition && !recyclerView.canScrollVertically(1)) {
                return;
            }
            ensureBottomVisible(lastPosition, attempts - 1);
        }, 120);
    }

    private boolean isLastComplexMessage(@Nullable List<Message> messages, int lastIndex) {
        if (messages == null || messages.isEmpty() || lastIndex < 0 || lastIndex >= messages.size()) {
            return false;
        }
        String mediaType = messages.get(lastIndex).getMediaType();
        return mediaType != null && !mediaType.isBlank() && !"TEXT".equalsIgnoreCase(mediaType);
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

    private boolean shouldAutoScrollOnMessageUpdate(@Nullable List<Message> messages, boolean wasLoadingMore, long scrollToMessageId) {
        if (scrollToMessageId > 0 || wasLoadingMore || messages == null || messages.isEmpty()) {
            return false;
        }
        boolean nearBottom = isNearBottom();
        boolean tailAppended = messages.size() > lastRenderedMessageCount;
        String newTailKey = buildTailKey(messages.get(messages.size() - 1));
        boolean tailResolvedSuccess = !newTailKey.isEmpty()
                && newTailKey.equals(lastRenderedTailKey)
                && lastRenderedTailUploading
                && !messages.get(messages.size() - 1).isUploading();
        return (autoScrollEnabled || nearBottom) && (tailAppended || tailResolvedSuccess || !isInitialScrollCompleted);
    }

    private void rememberRenderedTailState(@Nullable List<Message> messages) {
        lastRenderedMessageCount = messages == null ? 0 : messages.size();
        if (messages == null || messages.isEmpty()) {
            lastRenderedTailKey = "";
            lastRenderedTailUploading = false;
            return;
        }
        Message tail = messages.get(messages.size() - 1);
        lastRenderedTailKey = buildTailKey(tail);
        lastRenderedTailUploading = tail != null && tail.isUploading();
    }

    @NonNull
    private String buildTailKey(@Nullable Message message) {
        if (message == null) {
            return "";
        }
        if (message.getClientRequestId() != null && !message.getClientRequestId().isBlank()) {
            return "crid:" + message.getClientRequestId().trim();
        }
        if (message.getId() != null) {
            return "id:" + message.getId();
        }
        Long localSortTimestamp = message.getLocalSortTimestamp();
        return localSortTimestamp == null ? "" : "ts:" + localSortTimestamp;
    }

    private boolean isNearBottom() {
        if (binding == null || binding.recyclerView == null || adapter == null) {
            return true;
        }
        RecyclerView.LayoutManager manager = binding.recyclerView.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager layoutManager)) {
            return true;
        }
        int itemCount = adapter.getItemCount();
        if (itemCount <= 0) {
            return true;
        }
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        return lastVisible >= itemCount - 2;
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
                requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("message", textToCopy);
            clipboard.setPrimaryClip(clip);
            if (isAdded() && getContext() != null) {
                Toast.makeText(getContext(), "已复制", Toast.LENGTH_SHORT).show();
            }
        });

        popupBinding.actionShare.setOnClickListener(v -> {
            popupWindow.dismiss();
            shareMessageToExternalApp(message);
        });

        popupBinding.actionFavorite.setOnClickListener(v -> {
            popupWindow.dismiss();
            handleFavorite(message, favoriteRepository);
        });

        popupBinding.actionDelete.setOnClickListener(v -> {
            popupWindow.dismiss();
            handleDelete(message, chatViewModel);
        });

        popupBinding.actionSelect.setOnClickListener(v -> {
            popupWindow.dismiss();
            enterMultiSelectMode(message);
        });

        popupBinding.getRoot().measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = popupBinding.getRoot().getMeasuredWidth();
        int popupHeight = popupBinding.getRoot().getMeasuredHeight();

        int[] location = new int[2];
        clickedView.getLocationOnScreen(location);
        int viewX = location[0];
        int viewY = location[1];
        int viewWidth = clickedView.getWidth();
        int viewHeight = clickedView.getHeight();

        float density = getResources().getDisplayMetrics().density;
        int gap = (int) (4 * density);

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        boolean showAbove = viewY > screenHeight / 2;

        int popupX = viewX + viewWidth / 2 - popupWidth / 2;
        int popupY = showAbove ? viewY - popupHeight - gap : viewY + viewHeight + gap;

        // Ensure popup stays within screen bounds
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        if (popupX < 0) popupX = 0;
        if (popupX + popupWidth > screenWidth) popupX = screenWidth - popupWidth;
        if (popupY < 0) popupY = 0;

        popupWindow.showAtLocation(binding.getRoot(), Gravity.NO_GRAVITY, popupX, popupY);
    }

    private void enterMultiSelectMode(@Nullable Message firstSelectedMessage) {
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

    private void exitMultiSelectMode() {
        if (adapter == null || binding == null) return;
        isMultiSelectMode = false;
        adapter.setSelectionMode(false);
        binding.inputContainer.setVisibility(View.VISIBLE);
        binding.mergePanel.setVisibility(View.GONE);
        updateMergeSelectionCount(0);
    }

    private void handleMergeAction() {
        if (adapter == null || chatViewModel == null || !isAdded()) return;
        List<Long> selectedIds = new ArrayList<>(adapter.getSelectedIds());
        int selectedCount = selectedIds.size();
        if (selectedCount <= 0) {
            showToast("请先选择消息");
            return;
        }

        if (selectedCount < 2) {
            showToast("请至少选择 2 条消息进行合并");
            return;
        }

        DialogMergeCardTitleBinding dialogBinding = DialogMergeCardTitleBinding.inflate(getLayoutInflater());
        dialogBinding.mergeHintText.setText("将 " + selectedCount + " 条消息整理为一张可分享的卡片");
        dialogBinding.titleInput.setText("闪记卡片消息（" + selectedCount + "条）");
        if (dialogBinding.titleInput.getText() != null) {
            dialogBinding.titleInput.setSelection(dialogBinding.titleInput.getText().length());
        }

        new android.app.AlertDialog.Builder(requireContext())
                .setView(dialogBinding.getRoot())
                .setNegativeButton("取消", null)
                .setPositiveButton("合并", (dialog, which) -> submitMerge(selectedIds, dialogBinding.titleInput.getText() == null ? "" : dialogBinding.titleInput.getText().toString().trim()))
                .show();
    }

    private void submitMerge(List<Long> selectedIds, String title) {
        if (chatViewModel == null) {
            return;
        }
        if (title.isEmpty()) {
            showToast("请输入卡片标题");
            return;
        }

        chatViewModel.mergeMessages(selectedIds, title, new com.flashnote.java.data.repository.MessageRepository.MergeCallback() {
            @Override
            public void onSuccess(Message mergedMessage) {
                runIfUiAlive(() -> {
                    chatViewModel.addLocalMessage(mergedMessage);
                    exitMultiSelectMode();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runIfUiAlive(() -> showToast("合并失败: " + errorMessage));
            }
        });
    }

    private void handleBatchDelete() {
        if (adapter == null || chatViewModel == null || !isAdded()) {
            return;
        }
        List<Long> selectedIds = new ArrayList<>(adapter.getSelectedIds());
        if (selectedIds.isEmpty()) {
            showToast("请先选择消息");
            return;
        }
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("批量删除消息")
                .setMessage("确定要删除选中的 " + selectedIds.size() + " 条消息吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    skipNextScroll = true;
                    chatViewModel.deleteMessages(selectedIds, () -> {
                        runIfUiAlive(() -> {
                            exitMultiSelectMode();
                        });
                    });
                })
                .show();
    }

    private void updateMergeSelectionCount(int selectedCount) {
        if (binding == null) {
            return;
        }
        binding.mergeTitleText.setText("已选择 " + selectedCount + " 条消息");
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
                    skipNextScroll = true;
                    chatViewModel.deleteMessage(message.getId(), () -> { });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void restoreAfterPrepend(int totalMessageCount) {
        if (binding == null || loadMoreAnchorPosition == RecyclerView.NO_POSITION) {
            return;
        }
        int addedCount = Math.max(0, totalMessageCount - messageCountBeforeLoadMore);
        LinearLayoutManager layoutManager = (LinearLayoutManager) binding.recyclerView.getLayoutManager();
        if (layoutManager == null) {
            return;
        }
        int target = loadMoreAnchorPosition + addedCount;
        binding.recyclerView.post(() -> {
            if (binding != null) {
                layoutManager.scrollToPositionWithOffset(target, loadMoreAnchorOffset);
            }
        });
    }

    private void shareMessageToExternalApp(Message message) {
        if (!isAdded() || getContext() == null || message == null) {
            return;
        }
        Context context = requireContext();
        String mediaType = message.getMediaType();
        if (TextUtils.isEmpty(mediaType)
                || "TEXT".equalsIgnoreCase(mediaType)
                || TextUtils.isEmpty(message.getMediaUrl())) {
            showToast("仅支持分享图片/视频/语音/文件");
            return;
        }

        File localFile = shareHelper.tryResolveLocalFile(context, message.getMediaUrl());
        if (localFile != null && localFile.exists()) {
            try {
                shareHelper.shareFileByIntent(context, localFile, message);
            } catch (ActivityNotFoundException e) {
                showToast("未找到可分享的应用");
            }
            return;
        }

        String objectName = shareHelper.extractObjectNameForDownload(message.getMediaUrl());
        if (TextUtils.isEmpty(objectName)) {
            showToast("文件地址无效，无法分享");
            return;
        }
        fileRepository.download(objectName, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(String path) {
                runIfUiAlive(() -> {
                    File file = new File(path);
                    if (!file.exists()) {
                        showToast("分享文件不存在");
                        return;
                    }
                    try {
                        shareHelper.shareFileByIntent(context, file, message);
                    } catch (ActivityNotFoundException e) {
                        showToast("未找到可分享的应用");
                    }
                });
            }

            @Override
            public void onError(String errorMessage, int code) {
                runIfUiAlive(() -> showToast("准备分享失败: " + errorMessage));
            }
        });
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
                runIfUiAlive(() -> {
                    android.content.Context context = getContext();
                    if (context != null) {
                        Toast.makeText(context, messageText, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String messageText, int code) {
                runIfUiAlive(() -> {
                    android.content.Context context = getContext();
                    if (context != null) {
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
                    } else {
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
                    }
                })
                .show();
    }

    private void setupToolsPanel() {
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
            openCardEditor();
        });
    }

    private void toggleToolsPanel() {
        isToolsPanelVisible = !isToolsPanelVisible;
        binding.toolsPanel.setVisibility(isToolsPanelVisible ? View.VISIBLE : View.GONE);
    }

    private void hideToolsPanel() {
        isToolsPanelVisible = false;
        binding.toolsPanel.setVisibility(View.GONE);
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
        binding.micButton.setOnLongClickListener(v -> {
            if (checkRecordPermission()) {
                startRecordingWithUI();
            } else {
                requestRecordPermission();
            }
            return true;
        });
        
        binding.micButton.setOnClickListener(v -> {
            showToast("长按开始录音");
        });
        
        binding.recordCancelBtn.setOnClickListener(v -> cancelRecording());
        binding.recordSendBtn.setOnClickListener(v -> confirmSendRecording());
    }

    private boolean checkRecordPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) 
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRecordPermission() {
        permissionLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
    }

    private void startRecording() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String storageDir = requireContext().getCacheDir().getAbsolutePath();
            currentRecordingPath = storageDir + "/voice_" + timeStamp + ".m4a";

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(currentRecordingPath);
            mediaRecorder.prepare();
            mediaRecorder.start();
            
            isRecording = true;
            binding.micButton.setColorFilter(0xFFFF0000);
            showToast("录音中...");
        } catch (IOException e) {
            DebugLog.e("ChatFragment", "Failed to start recording", e);
            showToast("录音失败");
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            } catch (RuntimeException e) {
                DebugLog.e("ChatFragment", "Failed to stop recording cleanly", e);
            }
        }
        isRecording = false;
        binding.micButton.clearColorFilter();
    }

    private void startRecordingWithUI() {
        startRecording();
        if (!isRecording) {
            return;
        }
        
        binding.recordingOverlay.setVisibility(View.VISIBLE);
        binding.toolsPanel.setVisibility(View.GONE);
        binding.messageInput.setVisibility(View.GONE);
        binding.addButton.setVisibility(View.GONE);
        binding.sendButton.setVisibility(View.GONE);
        binding.micButton.setVisibility(View.GONE);
        
        binding.recordWaveView.startAnimation();
        
        recordingSeconds = 0;
        binding.recordTimerText.setText("0:00");
        
        recordingTimerHandler = new Handler(Looper.getMainLooper());
        recordingTimerRunnable = new Runnable() {
            @Override
            public void run() {
                recordingSeconds++;
                binding.recordTimerText.setText(formatRecordingTime(recordingSeconds));
                recordingTimerHandler.postDelayed(this, 1000);
            }
        };
        recordingTimerHandler.postDelayed(recordingTimerRunnable, 1000);
    }

    private void cancelRecording() {
        stopRecordingUI();
        stopRecording();
        if (currentRecordingPath != null) {
            File file = new File(currentRecordingPath);
            if (file.exists()) {
                file.delete();
            }
            currentRecordingPath = null;
        }
    }

    private void confirmSendRecording() {
        stopRecordingUI();
        if (isRecording) {
            stopRecording();
        }
        if (currentRecordingPath != null) {
            sendVoiceMessage(currentRecordingPath);
            currentRecordingPath = null;
        }
    }

    private void stopRecordingUI() {
        binding.recordingOverlay.setVisibility(View.GONE);
        binding.toolsPanel.setVisibility(View.GONE);
        binding.messageInput.setVisibility(View.VISIBLE);
        binding.addButton.setVisibility(View.VISIBLE);
        binding.sendButton.setVisibility(View.VISIBLE);
        binding.micButton.setVisibility(View.VISIBLE);
        binding.recordWaveView.stopAnimation();
        
        if (recordingTimerHandler != null) {
            if (recordingTimerRunnable != null) {
                recordingTimerHandler.removeCallbacks(recordingTimerRunnable);
            }
            recordingTimerHandler = null;
            recordingTimerRunnable = null;
        }
    }

    private String formatRecordingTime(int seconds) {
        int mins = seconds / 60;
        int secs = seconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", mins, secs);
    }

    private void showRecordingDialog() {
        if (currentRecordingPath == null || !new File(currentRecordingPath).exists()) {
            showToast("录音文件无效");
            return;
        }

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("语音消息")
                .setMessage("发送这段录音吗？")
                .setPositiveButton("发送", (dialog, which) -> {
                    sendVoiceMessage(currentRecordingPath);
                    currentRecordingPath = null;
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    if (currentRecordingPath != null) {
                        File file = new File(currentRecordingPath);
                        if (file.exists()) {
                            file.delete();
                        }
                    }
                    currentRecordingPath = null;
                })
                .show();
    }

    private void sendVoiceMessage(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            showToast("文件不存在");
            return;
        }
        Integer durationSeconds = null;
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(filePath);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration != null) {
                durationSeconds = Integer.parseInt(duration) / 1000;
            }
            retriever.release();
        } catch (Exception e) {
            DebugLog.e("ChatFragment", "Failed to resolve voice duration", e);
        }

        Integer finalDurationSeconds = durationSeconds;
        chatViewModel.enqueueMedia(
                "VOICE",
                file,
                file.getName(),
                file.length(),
                finalDurationSeconds,
                () -> runIfUiAlive(() -> scrollToBottomAfterLayout(null))
        );
    }

    private void openMediaPicker() {
        suppressNextGestureUnlockForExternalFlow();
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        mediaPickerLauncher.launch(intent);
    }

    private void suppressNextGestureUnlockForExternalFlow() {
        if (getActivity() instanceof ShellNavigator navigator) {
            navigator.registerExternalFlowForGestureUnlockSkip();
        }
    }

    private void openFilePicker() {
        suppressNextGestureUnlockForExternalFlow();
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        filePickerLauncher.launch(intent);
    }

    private void openCamera() {
        Context context = requireContext();
        if (!mediaHelper.hasCamera(context)) {
            showToast("设备没有相机");
            return;
        }

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = mediaHelper.prepareCameraPhotoFile(context);

        if (photoFile != null) {
            suppressNextGestureUnlockForExternalFlow();
            cameraPhotoUri = mediaHelper.buildCameraUri(context, photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                cameraLauncher.launch(takePictureIntent);
            } catch (ActivityNotFoundException e) {
                showToast("无法打开相机");
            }
        }
    }

    private void handleMediaPicked(Uri uri) {
        String mimeType = mediaHelper.resolveMimeType(requireContext(), uri);
        if (mimeType == null) {
            showToast("无法识别文件类型");
            return;
        }

        if (mimeType.startsWith("image/")) {
            handleImagePicked(uri);
        } else if (mimeType.startsWith("video/")) {
            handleVideoPicked(uri);
        } else {
            showToast("请选择图片或视频");
        }
    }

    private void handleImagePicked(Uri uri) {
        String originalFileName = mediaHelper.getOriginalFileName(requireContext(), uri);
        mediaHelper.copyUriToTempFile(requireContext().getApplicationContext(), this::runIfUiAlive, uri, "image", file -> {
            if (file == null) {
                showToast("文件处理失败");
                return;
            }

            chatViewModel.enqueueMedia(
                    "IMAGE",
                    file,
                    originalFileName != null ? originalFileName : file.getName(),
                    file.length(),
                    null,
                    () -> runIfUiAlive(() -> scrollToBottomAfterLayout(null))
            );
        });
    }

    private void handleVideoPicked(Uri uri) {
        String originalFileName = mediaHelper.getOriginalFileName(requireContext(), uri);

        mediaHelper.copyUriToTempFile(requireContext().getApplicationContext(), this::runIfUiAlive, uri, "video", file -> {
            if (file == null) {
                showToast("文件处理失败");
                return;
            }

            chatViewModel.enqueueMedia(
                    "VIDEO",
                    file,
                    originalFileName != null ? originalFileName : file.getName(),
                    file.length(),
                    null,
                    () -> runIfUiAlive(() -> scrollToBottomAfterLayout(null))
            );
        });
    }

    private void handleFilePicked(Uri uri) {
        String originalFileName = mediaHelper.getOriginalFileName(requireContext(), uri);
        mediaHelper.copyUriToTempFile(requireContext().getApplicationContext(), this::runIfUiAlive, uri, "file", file -> {
            if (file == null) {
                showToast("文件处理失败");
                return;
            }

            chatViewModel.enqueueMedia(
                    "FILE",
                    file,
                    originalFileName != null ? originalFileName : file.getName(),
                    file.length(),
                    null,
                    () -> runIfUiAlive(() -> scrollToBottomAfterLayout(null))
            );
        });
    }

    private void handleCameraPhoto(Uri uri) {
        String originalFileName = mediaHelper.getOriginalFileName(requireContext(), uri);
        mediaHelper.copyUriToTempFile(requireContext().getApplicationContext(), this::runIfUiAlive, uri, "image", file -> {
            if (file == null) {
                showToast("文件处理失败");
                return;
            }

            chatViewModel.enqueueMedia(
                    "IMAGE",
                    file,
                    originalFileName != null ? originalFileName : file.getName(),
                    file.length(),
                    null,
                    () -> runIfUiAlive(() -> scrollToBottomAfterLayout(null))
            );
        });
    }

    private void runIfUiAlive(@NonNull Runnable action) {
        FragmentUiSafe.runIfUiAlive(this, binding, action);
    }

    private void sendMediaToCapturedConversation(@NonNull Message message,
                                                 long targetFlashNoteId,
                                                 long targetPeerUserId,
                                                 @NonNull Runnable onSuccess) {
        if (targetPeerUserId > 0L) {
            chatViewModel.sendMessageToContact(targetPeerUserId, message, onSuccess);
            return;
        }
        if (targetFlashNoteId > 0L || targetFlashNoteId == INBOX_NOTE_ID) {
            chatViewModel.sendMessageToFlashNote(targetFlashNoteId, message, onSuccess);
        }
    }

    private void showToast(String message) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void restoreDraft() {
        String draft = chatViewModel.getCurrentDraft();
        if (draft.isEmpty()) {
            return;
        }
        binding.messageInput.setText(draft);
        binding.messageInput.setSelection(draft.length());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null && chatViewModel != null) {
            String draft = binding.messageInput.getText() == null ? "" : binding.messageInput.getText().toString();
            chatViewModel.saveDraft(draft);
        }
        if (isRecording) {
            stopRecording();
        }
        if (recordingTimerHandler != null) {
            if (recordingTimerRunnable != null) {
                recordingTimerHandler.removeCallbacks(recordingTimerRunnable);
            }
            recordingTimerHandler = null;
            recordingTimerRunnable = null;
        }
        binding = null;
    }
}
