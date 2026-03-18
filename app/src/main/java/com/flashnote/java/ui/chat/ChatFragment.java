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
import android.text.TextWatcher;

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
import com.flashnote.java.databinding.FragmentChatBinding;
import com.flashnote.java.databinding.PopupMessageActionsBinding;
import com.flashnote.java.ui.main.FlashNoteViewModel;
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
    private Runnable recordingAmplitudeRunnable;
    private int recordingSeconds = 0;
    private long currentFlashNoteId = 0L;
    private boolean isLoadingMore = false;
    private boolean hasMoreMessages = true;
    private boolean isInitialScrollCompleted = false;

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
        currentFlashNoteId = flashNoteId;
        String title = getArguments() == null ? "闪记" : getArguments().getString(ARG_TITLE, "闪记");

        binding.titleText.setText(title);
        binding.backButton.setOnClickListener(v -> {
            if (!isAdded()) {
                return;
            }
            getParentFragmentManager().popBackStack();
        });

        FavoriteRepository favoriteRepository = FlashNoteApp.getInstance().getFavoriteRepository();
        fileRepository = FlashNoteApp.getInstance().getFileRepository();
        chatViewModel = new ViewModelProvider(requireActivity()).get(ChatViewModel.class);
        FlashNoteViewModel flashNoteViewModel = new ViewModelProvider(this).get(FlashNoteViewModel.class);
        adapter = new MessageAdapter((message, clickedView) -> showMessageActions(message, flashNoteId, favoriteRepository, chatViewModel, flashNoteViewModel, clickedView));
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);
        binding.recyclerView.setItemAnimator(null);
        binding.recyclerView.setItemViewCacheSize(24);

        FlashNoteApp.getInstance().getUserRepository().getProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile != null && profile.getAvatar() != null && !profile.getAvatar().isEmpty()) {
                adapter.setUserAvatarUrl(profile.getAvatar(), requireContext());
                adapter.notifyDataSetChanged();
            }
        });

        binding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!isInitialScrollCompleted || dy >= 0 || isLoadingMore || !hasMoreMessages) {
                    return;
                }
                if (!recyclerView.canScrollVertically(-1)) {
                    isLoadingMore = true;
                    chatViewModel.loadMore();
                }
            }
        });

        chatViewModel.bindFlashNote(flashNoteId);
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
        restoreDraft(flashNoteId);
        binding.sendButton.setOnClickListener(v -> sendMessage(chatViewModel));
        setupToolsPanel();
        setupMicButton();
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
        viewModel.sendText(text, () -> {
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
            binding.recyclerView.scrollToPosition(last);
            binding.recyclerView.post(() -> {
                if (binding != null) {
                    binding.recyclerView.scrollToPosition(last);
                }
            });
            if (isLastMediaMessage(messages, last)) {
                binding.recyclerView.postDelayed(() -> {
                    if (binding != null) {
                        binding.recyclerView.scrollToPosition(last);
                    }
                }, 180);
                binding.recyclerView.postDelayed(() -> {
                    if (binding != null) {
                        binding.recyclerView.scrollToPosition(last);
                    }
                }, 320);
            }
        });
    }

    private boolean isLastMediaMessage(@Nullable List<Message> messages, int lastIndex) {
        if (messages == null || messages.isEmpty() || lastIndex < 0 || lastIndex >= messages.size()) {
            return false;
        }
        String mediaType = messages.get(lastIndex).getMediaType();
        return "IMAGE".equalsIgnoreCase(mediaType) || "VIDEO".equalsIgnoreCase(mediaType);
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
    }

    private void toggleToolsPanel() {
        isToolsPanelVisible = !isToolsPanelVisible;
        binding.toolsPanel.setVisibility(isToolsPanelVisible ? View.VISIBLE : View.GONE);
    }

    private void hideToolsPanel() {
        isToolsPanelVisible = false;
        binding.toolsPanel.setVisibility(View.GONE);
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
        
        binding.recordWaveView.startWave();
        
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
        recordingAmplitudeRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaRecorder != null) {
                    try {
                        float amp = mediaRecorder.getMaxAmplitude() / 32768f;
                        binding.recordWaveView.updateAmplitude(amp);
                    } catch (Exception ignored) {
                    }
                }
                recordingTimerHandler.postDelayed(this, 100);
            }
        };
        recordingTimerHandler.postDelayed(recordingTimerRunnable, 1000);
        recordingTimerHandler.postDelayed(recordingAmplitudeRunnable, 100);
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
        binding.recordWaveView.stopWave();
        
        if (recordingTimerHandler != null) {
            if (recordingTimerRunnable != null) {
                recordingTimerHandler.removeCallbacks(recordingTimerRunnable);
            }
            if (recordingAmplitudeRunnable != null) {
                recordingTimerHandler.removeCallbacks(recordingAmplitudeRunnable);
            }
            recordingTimerHandler = null;
            recordingTimerRunnable = null;
            recordingAmplitudeRunnable = null;
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
        
        Message message = new Message();
        message.setMediaType("VOICE");
        message.setMediaUrl(file.getAbsolutePath());
        message.setFileName(file.getName());
        message.setFileSize(file.length());
        message.setFlashNoteId(flashNoteId);
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
                if (!isAdded()) return;
                
                message.setMediaUrl(mediaUrl);
                message.setUploading(false);

                chatViewModel.sendMedia(message, () -> {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));
                });
            }

            @Override
            public void onError(String errorMessage, int code) {
                if (!isAdded()) return;
                message.setUploading(false);
                requireActivity().runOnUiThread(() -> showToast("上传失败: " + errorMessage));
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
            message.setRole("user");
            message.setUploading(true);
            
            chatViewModel.addLocalMessage(message);
            requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));
            
            fileRepository.upload(file, new FileRepository.FileCallback() {
                @Override
                public void onSuccess(String mediaUrl) {
                    if (!isAdded()) return;
                    
                    message.setMediaUrl(mediaUrl);
                    message.setUploading(false);

                    chatViewModel.sendMedia(message, () -> {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));
                    });
                }

                @Override
                public void onError(String errorMessage, int code) {
                    if (!isAdded()) return;
                    message.setUploading(false);
                    requireActivity().runOnUiThread(() -> showToast("上传失败: " + errorMessage));
                }
            });
        });
    }

    private void handleVideoPicked(Uri uri) {
        String originalFileName = getOriginalFileName(uri);
        long flashNoteId = getArguments() == null ? 0L : getArguments().getLong(ARG_FLASH_NOTE_ID);

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
                            if (!isAdded()) {
                                return;
                            }

                            message.setMediaUrl(mediaUrl);
                            message.setFileName(originalFileName != null ? originalFileName : compressedFile.getName());
                            message.setFileSize(compressedFile.length());
                            message.setUploading(false);

                            chatViewModel.sendMedia(message, () -> {
                                if (!isAdded()) {
                                    return;
                                }
                                requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));
                            });
                        }

                        @Override
                        public void onError(String errorMessage, int code) {
                            if (!isAdded()) {
                                return;
                            }
                            message.setUploading(false);
                            requireActivity().runOnUiThread(() -> showToast("上传失败: " + errorMessage));
                        }
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    if (!isAdded()) {
                        return;
                    }
                    message.setUploading(false);
                    requireActivity().runOnUiThread(() -> showToast("视频压缩失败: " + errorMessage));
                }
            });
        });
    }

    private void handleFilePicked(Uri uri) {
        String originalFileName = getOriginalFileName(uri);
        long flashNoteId = getArguments() == null ? 0L : getArguments().getLong(ARG_FLASH_NOTE_ID);
        
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
            message.setRole("user");
            message.setUploading(true);
            
            chatViewModel.addLocalMessage(message);
            requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));
            
            fileRepository.upload(file, new FileRepository.FileCallback() {
                @Override
                public void onSuccess(String mediaUrl) {
                    if (!isAdded()) return;
                    
                    message.setMediaUrl(mediaUrl);
                    message.setUploading(false);

                    chatViewModel.sendMedia(message, () -> {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));
                    });
                }

                @Override
                public void onError(String errorMessage, int code) {
                    if (!isAdded()) return;
                    message.setUploading(false);
                    requireActivity().runOnUiThread(() -> showToast("上传失败: " + errorMessage));
                }
            });
        });
    }

    private void handleCameraPhoto(Uri uri) {
        String originalFileName = getOriginalFileName(uri);
        long flashNoteId = getArguments() == null ? 0L : getArguments().getLong(ARG_FLASH_NOTE_ID);
        
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
            message.setRole("user");
            message.setUploading(true);
            
            chatViewModel.addLocalMessage(message);
            requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));
            
            fileRepository.upload(file, new FileRepository.FileCallback() {
                @Override
                public void onSuccess(String mediaUrl) {
                    if (!isAdded()) return;
                    
                    message.setMediaUrl(mediaUrl);
                    message.setUploading(false);

                    chatViewModel.sendMedia(message, () -> {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> scrollToBottomAfterLayout(null));
                    });
                }

                @Override
                public void onError(String errorMessage, int code) {
                    if (!isAdded()) return;
                    message.setUploading(false);
                    requireActivity().runOnUiThread(() -> showToast("上传失败: " + errorMessage));
                }
            });
        });
    }

    private void copyUriToTempFile(Uri uri, String prefix, FileCallback callback) {
        new Thread(() -> {
            File tempFile = null;
            try {
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String extension = getFileExtension(uri);
                String storageDir = requireContext().getCacheDir().getAbsolutePath();
                tempFile = new File(storageDir, prefix + "_" + timeStamp + "." + extension);

                try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                     FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                    if (inputStream != null) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                }

                final File finalFile = tempFile;
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> callback.onFileReady(finalFile));
                }
            } catch (IOException e) {
                e.printStackTrace();
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> callback.onFileReady(null));
                }
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

    private void restoreDraft(long flashNoteId) {
        String draft = chatViewModel.getDraft(flashNoteId);
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
            if (recordingAmplitudeRunnable != null) {
                recordingTimerHandler.removeCallbacks(recordingAmplitudeRunnable);
            }
            recordingTimerHandler = null;
            recordingTimerRunnable = null;
            recordingAmplitudeRunnable = null;
        }
        binding = null;
    }
}
