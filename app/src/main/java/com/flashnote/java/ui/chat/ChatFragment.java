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
import com.flashnote.java.ui.navigation.ShellNavigator;
import com.flashnote.java.util.VideoCompressor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    private FragmentChatBinding binding;
    private ChatViewModel chatViewModel;
    private FileRepository fileRepository;
    private MessageAdapter adapter;
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
        chatViewModel = new ViewModelProvider(requireActivity()).get(ChatViewModel.class);
        FlashNoteViewModel flashNoteViewModel = new ViewModelProvider(this).get(FlashNoteViewModel.class);
        adapter = new MessageAdapter((message, clickedView) -> showMessageActions(message, flashNoteId, favoriteRepository, chatViewModel, flashNoteViewModel, clickedView));
        adapter.setOnSelectionChangedListener(this::updateMergeSelectionCount);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);
        binding.recyclerView.setItemAnimator(null);
        binding.recyclerView.setItemViewCacheSize(24);

        UserRepository userRepository = FlashNoteApp.getInstance().getUserRepository();
        userRepository.getProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile != null && profile.getAvatar() != null && !profile.getAvatar().isEmpty()) {
                adapter.setUserAvatarUrl(profile.getAvatar(), requireContext());
                adapter.notifyDataSetChanged();
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
                                adapter.notifyDataSetChanged();
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
                adapter.submitList(messages);
                if (isAdded()) {
                    adapter.preloadRecentMedia(messages, requireContext());
                }
                // Scroll to bottom if not search location scenario
                if (scrollToMessageId <= 0 && messages != null && !messages.isEmpty() && !wasLoadingMore) {
                    scrollToBottomAfterLayout(messages);
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
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
                chatViewModel.clearError();
            }
        });

        setupMessageInput(chatViewModel);
        restoreDraft();
        binding.sendButton.setOnClickListener(v -> sendMessage(chatViewModel));
        setupToolsPanel();
        setupMicButton();
        binding.mergeCancelButton.setOnClickListener(v -> exitMultiSelectMode());
        binding.mergeConfirmButton.setOnClickListener(v -> handleMergeAction());
        updateMergeSelectionCount(0);
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
            if (!isAdded() || getActivity() == null || binding == null) {
                return;
            }
            getActivity().runOnUiThread(() -> {
                if (binding != null) {
                    binding.messageInput.setText(null);
                    viewModel.clearDraft();
                }
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
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    chatViewModel.addLocalMessage(mergedMessage);
                    showToast("合并成功");
                    exitMultiSelectMode();
                });
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    showToast("合并失败: " + errorMessage);
                });
            }
        });
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
        String mediaType = message.getMediaType();
        if (TextUtils.isEmpty(mediaType)
                || "TEXT".equalsIgnoreCase(mediaType)
                || TextUtils.isEmpty(message.getMediaUrl())) {
            showToast("仅支持分享图片/视频/语音/文件");
            return;
        }

        File localFile = tryResolveLocalFile(message.getMediaUrl());
        if (localFile != null && localFile.exists()) {
            shareFileByIntent(localFile, message);
            return;
        }

        String objectName = extractObjectNameForDownload(message.getMediaUrl());
        if (TextUtils.isEmpty(objectName)) {
            showToast("文件地址无效，无法分享");
            return;
        }
        showToast("正在准备分享文件...");
        fileRepository.download(objectName, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(String path) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    File file = new File(path);
                    if (!file.exists()) {
                        showToast("分享文件不存在");
                        return;
                    }
                    shareFileByIntent(file, message);
                });
            }

            @Override
            public void onError(String errorMessage, int code) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> showToast("准备分享失败: " + errorMessage));
            }
        });
    }

    @Nullable
    private File tryResolveLocalFile(String mediaUrl) {
        if (TextUtils.isEmpty(mediaUrl)) {
            return null;
        }
        File direct = new File(mediaUrl);
        if (direct.exists()) {
            return direct;
        }
        String objectName = extractObjectNameForDownload(mediaUrl);
        if (TextUtils.isEmpty(objectName)) {
            return null;
        }
        Context context = requireContext();
        File cached = new File(context.getCacheDir(), objectName.replace('/', '_'));
        if (cached.exists()) {
            return cached;
        }
        File externalCache = context.getExternalCacheDir() == null
                ? null
                : new File(context.getExternalCacheDir(), objectName.replace('/', '_'));
        if (externalCache != null && externalCache.exists()) {
            return externalCache;
        }
        String originalName = sanitizeFileName(fallbackFileNameFromObjectName(objectName));
        File shared = new File(new File(context.getCacheDir(), "share"), originalName);
        return shared.exists() ? shared : null;
    }

    @Nullable
    private String extractObjectNameForDownload(String mediaUrl) {
        if (TextUtils.isEmpty(mediaUrl)) {
            return null;
        }
        if (mediaUrl.startsWith("http")) {
            Uri uri = Uri.parse(mediaUrl);
            String objectName = uri.getQueryParameter("objectName");
            if (!TextUtils.isEmpty(objectName)) {
                return Uri.decode(objectName);
            }
            String path = uri.getPath();
            if (!TextUtils.isEmpty(path) && path.startsWith("/")) {
                return path.substring(1);
            }
            return null;
        }
        return mediaUrl;
    }

    @NonNull
    private String fallbackFileNameFromObjectName(@NonNull String objectName) {
        int slashIndex = objectName.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < objectName.length() - 1) {
            return objectName.substring(slashIndex + 1);
        }
        return objectName;
    }

    private void shareFileByIntent(@NonNull File file, @NonNull Message message) {
        if (!isAdded()) {
            return;
        }
        Context context = requireContext();
        File shareFile = prepareShareFile(file, message.getFileName());
        Uri contentUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                shareFile
        );
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(resolveShareMimeType(shareFile, message));
        intent.putExtra(Intent.EXTRA_STREAM, contentUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(android.content.ClipData.newRawUri("share", contentUri));
        try {
            startActivity(Intent.createChooser(intent, "分享到"));
        } catch (ActivityNotFoundException e) {
            showToast("未找到可分享的应用");
        }
    }

    @NonNull
    private File prepareShareFile(@NonNull File sourceFile, @Nullable String originalFileName) {
        if (TextUtils.isEmpty(originalFileName)) {
            return sourceFile;
        }
        String safeName = sanitizeFileName(originalFileName);
        if (safeName.equals(sourceFile.getName())) {
            return sourceFile;
        }
        File shareDir = new File(requireContext().getCacheDir(), "share");
        if (!shareDir.exists()) {
            shareDir.mkdirs();
        }
        File target = new File(shareDir, safeName);
        try (InputStream inputStream = java.nio.file.Files.newInputStream(sourceFile.toPath());
             FileOutputStream outputStream = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return target;
        } catch (IOException ignored) {
            return sourceFile;
        }
    }

    @NonNull
    private String sanitizeFileName(@NonNull String fileName) {
        String sanitized = fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (sanitized.isEmpty()) {
            return "shared_file";
        }
        return sanitized;
    }

    private String resolveShareMimeType(@NonNull File file, @NonNull Message message) {
        String mediaType = message.getMediaType();
        if ("IMAGE".equalsIgnoreCase(mediaType)) {
            return "image/*";
        }
        if ("VIDEO".equalsIgnoreCase(mediaType)) {
            return "video/*";
        }
        if ("VOICE".equalsIgnoreCase(mediaType)) {
            return "audio/*";
        }

        String extension = MimeTypeMap.getFileExtensionFromUrl(file.getName());
        if (TextUtils.isEmpty(extension) && !TextUtils.isEmpty(message.getFileName())) {
            extension = MimeTypeMap.getFileExtensionFromUrl(message.getFileName());
        }
        if (!TextUtils.isEmpty(extension)) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.ROOT));
            if (!TextUtils.isEmpty(mime)) {
                return mime;
            }
        }
        return "*/*";
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
            e.printStackTrace();
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
                e.printStackTrace();
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

        long flashNoteId = getArguments() == null ? 0L : getArguments().getLong(ARG_FLASH_NOTE_ID);
        long peerUserId = getArguments() == null ? 0L : getArguments().getLong(ARG_PEER_USER_ID);
        
        Message message = new Message();
        message.setMediaType("VOICE");
        message.setMediaUrl(file.getAbsolutePath());
        message.setFileName(file.getName());
        message.setFileSize(file.length());
        message.setFlashNoteId(flashNoteId);
        message.setReceiverId(peerUserId > 0L ? peerUserId : null);
        message.setRole("user");
        message.setUploading(true);
        
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(filePath);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration != null) {
                message.setMediaDuration(Integer.parseInt(duration) / 1000);
            }
            retriever.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        chatViewModel.addLocalMessage(message);
        requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));

        fileRepository.upload(file, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(String mediaUrl) {
                message.setMediaUrl(mediaUrl);
                message.setUploading(false);

                chatViewModel.sendMedia(message, () -> {
                    if (isAdded() && binding != null && getActivity() != null) {
                        requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));
                    }
                });
            }

            @Override
            public void onError(String errorMessage, int code) {
                message.setUploading(false);
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> showToast("上传失败: " + errorMessage));
                }
            }
        });
    }

    private void openMediaPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        mediaPickerLauncher.launch(intent);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        filePickerLauncher.launch(intent);
    }

    private void openCamera() {
        if (!requireContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            showToast("设备没有相机");
            return;
        }

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = null;
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String storageDir = requireContext().getCacheDir().getAbsolutePath();
            photoFile = new File(storageDir, "IMG_" + timeStamp + ".jpg");
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        if (photoFile != null) {
            cameraPhotoUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    photoFile
            );
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
        String mimeType = requireContext().getContentResolver().getType(uri);
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
        String originalFileName = getOriginalFileName(uri);
        long flashNoteId = getArguments() == null ? 0L : getArguments().getLong(ARG_FLASH_NOTE_ID);
        long peerUserId = getArguments() == null ? 0L : getArguments().getLong(ARG_PEER_USER_ID);
        
        copyUriToTempFile(uri, "image", file -> {
            if (file == null) {
                showToast("文件处理失败");
                return;
            }
            
            Message message = new Message();
            message.setMediaType("IMAGE");
            message.setMediaUrl(file.getAbsolutePath());
            message.setFileName(originalFileName != null ? originalFileName : file.getName());
            message.setFileSize(file.length());
            message.setFlashNoteId(flashNoteId);
            message.setReceiverId(peerUserId > 0L ? peerUserId : null);
            message.setRole("user");
            message.setUploading(true);
            
            chatViewModel.addLocalMessage(message);
            requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));
            
            fileRepository.upload(file, new FileRepository.FileCallback() {
                @Override
                public void onSuccess(String mediaUrl) {
                    message.setMediaUrl(mediaUrl);
                    message.setUploading(false);

                    chatViewModel.sendMedia(message, () -> {
                        if (isAdded() && binding != null && getActivity() != null) {
                            requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));
                        }
                    });
                }

                @Override
                public void onError(String errorMessage, int code) {
                    message.setUploading(false);
                    if (isAdded() && getActivity() != null) {
                        requireActivity().runOnUiThread(() -> showToast("上传失败: " + errorMessage));
                    }
                }
            });
        });
    }

    private void handleVideoPicked(Uri uri) {
        String originalFileName = getOriginalFileName(uri);
        long flashNoteId = getArguments() == null ? 0L : getArguments().getLong(ARG_FLASH_NOTE_ID);
        long peerUserId = getArguments() == null ? 0L : getArguments().getLong(ARG_PEER_USER_ID);

        copyUriToTempFile(uri, "video", file -> {
            if (file == null) {
                showToast("文件处理失败");
                return;
            }

            Message message = new Message();
            message.setMediaType("VIDEO");
            message.setMediaUrl(file.getAbsolutePath());
            message.setFileName(originalFileName != null ? originalFileName : file.getName());
            message.setFileSize(file.length());
            message.setFlashNoteId(flashNoteId);
            message.setReceiverId(peerUserId > 0L ? peerUserId : null);
            message.setRole("user");
            message.setUploading(true);

            chatViewModel.addLocalMessage(message);
            requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));

            VideoCompressor.compress(requireContext(), file, new VideoCompressor.CompressCallback() {
                @Override
                public void onSuccess(File compressedFile) {
                    fileRepository.upload(compressedFile, new FileRepository.FileCallback() {
                        @Override
                        public void onSuccess(String mediaUrl) {
                            message.setMediaUrl(mediaUrl);
                            message.setFileName(originalFileName != null ? originalFileName : compressedFile.getName());
                            message.setFileSize(compressedFile.length());
                            message.setUploading(false);

                            chatViewModel.sendMedia(message, () -> {
                                if (isAdded() && binding != null && getActivity() != null) {
                                    requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));
                                }
                            });
                        }

                        @Override
                        public void onError(String errorMessage, int code) {
                            message.setUploading(false);
                            if (isAdded() && getActivity() != null) {
                                requireActivity().runOnUiThread(() -> showToast("上传失败: " + errorMessage));
                            }
                        }
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    message.setUploading(false);
                    if (isAdded() && getActivity() != null) {
                        requireActivity().runOnUiThread(() -> showToast("视频压缩失败: " + errorMessage));
                    }
                }
            });
        });
    }

    private void handleFilePicked(Uri uri) {
        String originalFileName = getOriginalFileName(uri);
        long flashNoteId = getArguments() == null ? 0L : getArguments().getLong(ARG_FLASH_NOTE_ID);
        long peerUserId = getArguments() == null ? 0L : getArguments().getLong(ARG_PEER_USER_ID);
        
        copyUriToTempFile(uri, "file", file -> {
            if (file == null) {
                showToast("文件处理失败");
                return;
            }
            
            Message message = new Message();
            message.setMediaType("FILE");
            message.setMediaUrl(file.getAbsolutePath());
            message.setFileName(originalFileName != null ? originalFileName : file.getName());
            message.setFileSize(file.length());
            message.setFlashNoteId(flashNoteId);
            message.setReceiverId(peerUserId > 0L ? peerUserId : null);
            message.setRole("user");
            message.setUploading(true);
            
            chatViewModel.addLocalMessage(message);
            requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));
            
            fileRepository.upload(file, new FileRepository.FileCallback() {
                @Override
                public void onSuccess(String mediaUrl) {
                    message.setMediaUrl(mediaUrl);
                    message.setUploading(false);

                    chatViewModel.sendMedia(message, () -> {
                        if (isAdded() && binding != null && getActivity() != null) {
                            requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));
                        }
                    });
                }

                @Override
                public void onError(String errorMessage, int code) {
                    message.setUploading(false);
                    if (isAdded() && getActivity() != null) {
                        requireActivity().runOnUiThread(() -> showToast("上传失败: " + errorMessage));
                    }
                }
            });
        });
    }

    private void handleCameraPhoto(Uri uri) {
        String originalFileName = getOriginalFileName(uri);
        long flashNoteId = getArguments() == null ? 0L : getArguments().getLong(ARG_FLASH_NOTE_ID);
        long peerUserId = getArguments() == null ? 0L : getArguments().getLong(ARG_PEER_USER_ID);
        
        copyUriToTempFile(uri, "image", file -> {
            if (file == null) {
                showToast("文件处理失败");
                return;
            }
            
            Message message = new Message();
            message.setMediaType("IMAGE");
            message.setMediaUrl(file.getAbsolutePath());
            message.setFileName(originalFileName != null ? originalFileName : file.getName());
            message.setFileSize(file.length());
            message.setFlashNoteId(flashNoteId);
            message.setReceiverId(peerUserId > 0L ? peerUserId : null);
            message.setRole("user");
            message.setUploading(true);
            
            chatViewModel.addLocalMessage(message);
            requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));
            
            fileRepository.upload(file, new FileRepository.FileCallback() {
                @Override
                public void onSuccess(String mediaUrl) {
                    message.setMediaUrl(mediaUrl);
                    message.setUploading(false);

                    chatViewModel.sendMedia(message, () -> {
                        if (isAdded() && binding != null && getActivity() != null) {
                            requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));
                        }
                    });
                }

                @Override
                public void onError(String errorMessage, int code) {
                    message.setUploading(false);
                    if (isAdded() && getActivity() != null) {
                        requireActivity().runOnUiThread(() -> showToast("上传失败: " + errorMessage));
                    }
                }
            });
        });
    }

    private void copyUriToTempFile(Uri uri, String prefix, FileCallback callback) {
        Context appContext = requireContext().getApplicationContext();
        new Thread(() -> {
            File tempFile = null;
            try {
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String extension = getFileExtension(uri);
                String storageDir = appContext.getCacheDir().getAbsolutePath();
                tempFile = new File(storageDir, prefix + "_" + timeStamp + "." + extension);

                try (InputStream inputStream = appContext.getContentResolver().openInputStream(uri);
                     FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                    if (inputStream != null) {
                        byte[] buffer = new byte[65536];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                }

                final File finalFile = tempFile;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onFileReady(finalFile));
            } catch (IOException e) {
                e.printStackTrace();
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onFileReady(null));
            }
        }).start();
    }

    private String getFileExtension(Uri uri) {
        ContentResolver resolver = requireContext().getContentResolver();
        String mimeType = resolver.getType(uri);
        if (mimeType != null) {
            switch (mimeType) {
                case "image/jpeg":
                    return "jpg";
                case "image/png":
                    return "png";
                case "image/gif":
                    return "gif";
                case "video/mp4":
                    return "mp4";
                case "video/3gpp":
                    return "3gp";
                case "video/webm":
                    return "webm";
                case "application/pdf":
                    return "pdf";
                case "application/msword":
                    return "doc";
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                    return "docx";
                default:
                    break;
            }
        }
        
        String path = uri.getPath();
        if (path != null) {
            int lastDot = path.lastIndexOf('.');
            if (lastDot > 0) {
                return path.substring(lastDot + 1);
            }
        }
        return "bin";
    }

    private String getOriginalFileName(Uri uri) {
        String displayName = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        displayName = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (displayName == null) {
            displayName = uri.getLastPathSegment();
        }
        return displayName;
    }

    interface FileCallback {
        void onFileReady(File file);
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
