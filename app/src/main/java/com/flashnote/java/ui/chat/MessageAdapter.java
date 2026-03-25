package com.flashnote.java.ui.chat;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.TokenManager;
import com.flashnote.java.data.model.CardItem;
import com.flashnote.java.data.model.CardPayload;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.databinding.ItemChatMessageBinding;
import com.flashnote.java.ui.media.FilePreviewActivity;
import com.flashnote.java.ui.media.ImageViewerActivity;
import com.flashnote.java.ui.media.MediaUrlResolver;
import com.flashnote.java.ui.media.VideoPlayerActivity;
import com.flashnote.java.util.MarkdownRenderer;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    public interface OnMessageLongClickListener {
        void onLongClick(Message message, View clickedView);
    }

    public interface OnRetryPendingMessageListener {
        void onRetry(long localId);
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    private static final long TEN_MINUTES_IN_MILLIS = 10 * 60 * 1000;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final List<Message> items = new ArrayList<>();
    private final OnMessageLongClickListener listener;
    @Nullable
    private final OnRetryPendingMessageListener retryListener;
    private final TokenManager tokenManager;
    private final FileRepository fileRepository;

    private String userAvatar = "😊";
    private String userAvatarUrl = null;
    private File localAvatarFile = null;
    private String peerAvatar = null;
    private String peerAvatarUrl = null;

    private MediaPlayer mediaPlayer;
    private Long currentPlayingMessageId;

    private boolean selectionMode = false;
    private final java.util.Set<Long> selectedIds = new java.util.LinkedHashSet<>();
    @Nullable
    private OnSelectionChangedListener selectionChangedListener;

    public MessageAdapter(OnMessageLongClickListener listener,
                          @Nullable OnRetryPendingMessageListener retryListener) {
        this.listener = listener;
        this.retryListener = retryListener;
        FlashNoteApp app = FlashNoteApp.getInstance();
        this.tokenManager = app.getTokenManager();
        this.fileRepository = app.getFileRepository();
    }

    public void submitList(List<Message> messages) {
        items.clear();
        if (messages != null) {
            items.addAll(messages);
        }
        if (selectionMode) {
            java.util.Set<Long> existingIds = new java.util.LinkedHashSet<>();
            for (Message item : items) {
                if (item != null && item.getId() != null) {
                    existingIds.add(item.getId());
                }
            }
            selectedIds.retainAll(existingIds);
            dispatchSelectionChanged();
        }
        notifyDataSetChanged();
    }

    public void setOnSelectionChangedListener(@Nullable OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }

    public void setSelectionMode(boolean mode) {
        if (this.selectionMode == mode) {
            return;
        }
        this.selectionMode = mode;
        if (!mode) {
            selectedIds.clear();
        }
        dispatchSelectionChanged();
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public java.util.List<Long> getSelectedIds() {
        return new java.util.ArrayList<>(selectedIds);
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    public void toggleSelection(@Nullable Message message) {
        if (message == null || message.getId() == null) {
            return;
        }
        Long id = message.getId();
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            selectedIds.add(id);
        }
        dispatchSelectionChanged();
        notifyDataSetChanged();
    }

    private void dispatchSelectionChanged() {
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(selectedIds.size());
        }
    }

    public void setUserAvatar(String avatar) {
        if (avatar != null && !avatar.isEmpty()) {
            this.userAvatar = avatar;
        }
    }

    public void setUserAvatarUrl(String avatarUrl, Context context) {
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            if (avatarUrl.startsWith("http") || avatarUrl.contains("/") || avatarUrl.contains(".")) {
                this.userAvatarUrl = avatarUrl;
                this.userAvatar = null;
                this.localAvatarFile = null;
            } else {
                this.userAvatar = avatarUrl;
                this.userAvatarUrl = null;
                this.localAvatarFile = null;
                return;
            }
        }
        if (context != null && this.userAvatarUrl != null) {
            File avatarFile = new File(context.getFilesDir(), "avatar.jpg");
            if (avatarFile.exists()) {
                this.localAvatarFile = avatarFile;
            }
        }
    }

    public void setPeerAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            this.peerAvatar = null;
            this.peerAvatarUrl = null;
            return;
        }
        if (avatarUrl.startsWith("http") || avatarUrl.contains("/")) {
            this.peerAvatarUrl = avatarUrl;
            this.peerAvatar = null;
        } else {
            this.peerAvatar = avatarUrl;
            this.peerAvatarUrl = null;
        }
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChatMessageBinding binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new MessageViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        holder.bind(items.get(position), position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void preloadRecentMedia(@Nullable List<Message> messages, @NonNull Context context) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        int maxWidthPx = context.getResources().getDimensionPixelSize(R.dimen.chat_media_preview_width);
        int maxHeightPx = context.getResources().getDimensionPixelSize(R.dimen.chat_media_preview_max_height);
        int start = Math.max(0, messages.size() - 12);
        for (int i = start; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message == null || message.isUploading()) {
                continue;
            }
            String mediaType = message.getMediaType();
            if (TextUtils.isEmpty(mediaType)) {
                continue;
            }
            String preloadUrl;
            if ("IMAGE".equalsIgnoreCase(mediaType)) {
                preloadUrl = message.getMediaUrl();
            } else if ("VIDEO".equalsIgnoreCase(mediaType)) {
                preloadUrl = TextUtils.isEmpty(message.getThumbnailUrl()) ? message.getMediaUrl() : message.getThumbnailUrl();
            } else {
                continue;
            }
            if (TextUtils.isEmpty(preloadUrl)) {
                continue;
            }
            if (isLocalOnlyMediaPath(preloadUrl)) {
                continue;
            }
            Glide.with(context)
                    .load(resolveMediaUrl(preloadUrl))
                    .fitCenter()
                    .dontAnimate()
                    .override(maxWidthPx, maxHeightPx)
                    .preload(maxWidthPx, maxHeightPx);
        }
    }

    @Override
    public void onViewRecycled(@NonNull MessageViewHolder holder) {
        super.onViewRecycled(holder);
        holder.binding.getRoot().setOnClickListener(null);
        holder.binding.leftContainer.setOnClickListener(null);
        holder.binding.bubbleCard.setOnClickListener(null);
        holder.binding.rightContainer.setOnClickListener(null);
        holder.binding.voicePlayBtn.setOnClickListener(null);
        holder.binding.rightVoicePlayBtn.setOnClickListener(null);
        holder.binding.voiceContainer.setOnClickListener(null);
        holder.binding.rightVoiceContainer.setOnClickListener(null);
        holder.binding.mediaImageContainer.setOnClickListener(null);
        holder.binding.rightMediaImageContainer.setOnClickListener(null);
        holder.binding.fileContainer.setOnClickListener(null);
        holder.binding.rightFileContainer.setOnClickListener(null);
        holder.binding.compositeContainer.setOnClickListener(null);
        holder.binding.rightCompositeContainer.setOnClickListener(null);
        holder.binding.rightRetryIcon.setOnClickListener(null);
        holder.binding.checkbox.setOnClickListener(null);
        holder.binding.rightCheckbox.setOnClickListener(null);
        stopVoiceWaveAnimation(holder.binding.voiceWaveform);
        stopVoiceWaveAnimation(holder.binding.rightVoiceWaveform);
    }

    private String resolveMediaUrl(String mediaPathOrUrl) {
        return MediaUrlResolver.resolve(mediaPathOrUrl);
    }

    private Object resolveMediaModel(String mediaPathOrUrl) {
        if (isLocalOnlyMediaPath(mediaPathOrUrl)) {
            String path = mediaPathOrUrl.startsWith("file://") ? mediaPathOrUrl.substring("file://".length()) : mediaPathOrUrl;
            return new File(path);
        }
        return resolveMediaUrl(mediaPathOrUrl);
    }

    private boolean isLocalOnlyMediaPath(@Nullable String mediaPathOrUrl) {
        if (TextUtils.isEmpty(mediaPathOrUrl)) {
            return false;
        }
        String value = mediaPathOrUrl.trim();
        return value.startsWith("file://") || value.startsWith("/");
    }

    private void showTextOnly(MessageViewHolder holder, Message message, boolean mine) {
        hideAllMediaContainers(holder, mine);
        TextView messageText = mine ? holder.binding.rightMessageText : holder.binding.messageText;
        messageText.setVisibility(View.VISIBLE);
        MarkdownRenderer.renderIfMarkdown(messageText, safeText(message.getContent()));
        bindRetryState(holder, message, mine);
    }

    private void bindRetryState(MessageViewHolder holder, Message message, boolean mine) {
        holder.binding.rightRetryIcon.setVisibility(View.GONE);
        holder.binding.rightRetryIcon.setOnClickListener(null);
        if (!mine || retryListener == null) {
            return;
        }
        Long messageId = message.getId();
        if (messageId == null || messageId >= 0L || message.isUploading()) {
            return;
        }
        long localId = Math.abs(messageId);
        holder.binding.rightRetryIcon.setVisibility(View.VISIBLE);
        holder.binding.rightRetryIcon.setOnClickListener(v -> retryListener.onRetry(localId));
    }

    private void showImageMessage(MessageViewHolder holder, Message message, boolean mine) {
        hideAllMediaContainers(holder, mine);
        FrameRefs refs = getRefs(holder, mine);
        Context context = holder.binding.getRoot().getContext();
        int maxWidthPx = context.getResources().getDimensionPixelSize(R.dimen.chat_media_preview_width);
        int maxHeightPx = context.getResources().getDimensionPixelSize(R.dimen.chat_media_preview_max_height);

        refs.imageContainer.setVisibility(View.VISIBLE);
        refs.playIcon.setVisibility(View.GONE);
        refs.uploadProgress.setVisibility(message.isUploading() ? View.VISIBLE : View.GONE);
        bindTextForMediaMessage(refs.messageText, message.getContent());

        String preview = TextUtils.isEmpty(message.getThumbnailUrl()) ? message.getMediaUrl() : message.getThumbnailUrl();
        if (!TextUtils.isEmpty(preview)) {
            Glide.with(context)
                    .load(resolveMediaModel(preview))
                    .placeholder(R.drawable.bg_placeholder_card)
                    .error(R.drawable.bg_placeholder_card)
                    .fitCenter()
                    .dontAnimate()
                    .thumbnail(0.25f)
                    .override(maxWidthPx, maxHeightPx)
                    .into(refs.imageView);

            refs.imageContainer.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), ImageViewerActivity.class);
                if (isLocalOnlyMediaPath(message.getMediaUrl())) {
                    String localPath = message.getMediaUrl().startsWith("file://")
                            ? message.getMediaUrl().substring("file://".length())
                            : message.getMediaUrl();
                    intent.putExtra(ImageViewerActivity.EXTRA_FILE_PATH, localPath);
                } else {
                    intent.putExtra(ImageViewerActivity.EXTRA_MEDIA_URL, message.getMediaUrl());
                }
                v.getContext().startActivity(intent);
            });
        }
        bindRetryState(holder, message, mine);
    }

    private void showVideoMessage(MessageViewHolder holder, Message message, boolean mine) {
        hideAllMediaContainers(holder, mine);
        FrameRefs refs = getRefs(holder, mine);
        Context context = holder.binding.getRoot().getContext();
        int maxWidthPx = context.getResources().getDimensionPixelSize(R.dimen.chat_media_preview_width);
        int maxHeightPx = context.getResources().getDimensionPixelSize(R.dimen.chat_media_preview_max_height);

        refs.imageContainer.setVisibility(View.VISIBLE);
        refs.playIcon.setVisibility(View.VISIBLE);
        refs.uploadProgress.setVisibility(message.isUploading() ? View.VISIBLE : View.GONE);
        bindTextForMediaMessage(refs.messageText, message.getContent());

        String preview = TextUtils.isEmpty(message.getThumbnailUrl()) ? message.getMediaUrl() : message.getThumbnailUrl();
        refs.imageContainer.setOnClickListener(null);
        if (!message.isUploading() && isLocalOnlyMediaPath(message.getMediaUrl())) {
            refs.imageContainer.setOnClickListener(v -> Toast.makeText(context, "视频发送失败，请重试", Toast.LENGTH_SHORT).show());
        }
        if (!message.isUploading() && !isLocalOnlyMediaPath(preview)) {
            Glide.with(context)
                    .load(resolveMediaModel(preview))
                    .placeholder(R.drawable.bg_placeholder_card)
                    .error(R.drawable.bg_placeholder_card)
                    .fitCenter()
                    .dontAnimate()
                    .thumbnail(0.25f)
                    .override(maxWidthPx, maxHeightPx)
                    .into(refs.imageView);

            refs.imageContainer.setOnClickListener(v -> {
                Context clickContext = v.getContext();
                Intent intent = new Intent(clickContext, VideoPlayerActivity.class);
                intent.putExtra(VideoPlayerActivity.EXTRA_MEDIA_URL, message.getMediaUrl());
                clickContext.startActivity(intent);
            });
        }
        bindRetryState(holder, message, mine);
    }

    private void showVoiceMessage(MessageViewHolder holder, Message message, boolean mine) {
        hideAllMediaContainers(holder, mine);
        FrameRefs refs = getRefs(holder, mine);

        refs.voiceContainer.setVisibility(View.VISIBLE);
        refs.voiceUploadProgress.setVisibility(message.isUploading() ? View.VISIBLE : View.GONE);
        bindTextForMediaMessage(refs.messageText, message.getContent());

        Integer duration = message.getMediaDuration();
        refs.voiceDuration.setText((duration == null || duration <= 0 ? 0 : duration) + "s");

        refs.voiceContainer.setOnClickListener(null);
        if (!message.isUploading()) {
            refs.voicePlayBtn.setVisibility(View.GONE);
            if (isLocalOnlyMediaPath(message.getMediaUrl())) {
                refs.voiceContainer.setOnClickListener(v -> Toast.makeText(v.getContext(), "语音发送失败，请重试", Toast.LENGTH_SHORT).show());
            } else {
                refs.voiceContainer.setOnClickListener(v -> toggleVoicePlayback(v.getContext(), message, refs.voiceContainer, refs.voiceWaveform));
            }
            
            boolean isPlayingThis = message.getId() != null
                    && currentPlayingMessageId != null
                    && currentPlayingMessageId.equals(message.getId())
                    && mediaPlayer != null
                    && mediaPlayer.isPlaying();
            
            if (isPlayingThis) {
                startVoiceWaveAnimation(refs.voiceWaveform);
            } else {
                stopVoiceWaveAnimation(refs.voiceWaveform);
            }
        }
        bindRetryState(holder, message, mine);
    }

    private void showFileMessage(MessageViewHolder holder, Message message, boolean mine) {
        hideAllMediaContainers(holder, mine);
        FrameRefs refs = getRefs(holder, mine);

        refs.fileContainer.setVisibility(View.VISIBLE);
        refs.fileUploadProgress.setVisibility(message.isUploading() ? View.VISIBLE : View.GONE);
        bindTextForMediaMessage(refs.messageText, message.getContent());

        if (!message.isUploading()) {
            String fileName = !TextUtils.isEmpty(message.getFileName()) ? message.getFileName() : fallbackFileName(message.getMediaUrl());
            refs.fileNameText.setText(fileName);
            refs.fileSizeText.setText(formatFileSize(message.getFileSize()));
            refs.fileIcon.setImageResource(resolveFileIcon(fileName));

            refs.fileContainer.setOnClickListener(v -> openFileMessage(v.getContext(), message));
        }
        bindRetryState(holder, message, mine);
    }

    private void showCompositeMessage(MessageViewHolder holder, Message message, boolean mine) {
        hideAllMediaContainers(holder, mine);
        FrameRefs refs = getRefs(holder, mine);
        Context context = holder.binding.getRoot().getContext();

        refs.compositeContainer.setVisibility(View.VISIBLE);
        refs.messageText.setVisibility(View.GONE);

        CardPayload payload = message.getPayload();
        if (payload == null) {
            refs.compositeTitle.setText("未知的卡片内容");
            refs.compositeSummary.setVisibility(View.GONE);
            refs.compositeGrid.setVisibility(View.GONE);
            bindRetryState(holder, message, mine);
            return;
        }

        refs.compositeTitle.setText(TextUtils.isEmpty(payload.getTitle()) ? "闪记" : payload.getTitle());

        StringBuilder summary = new StringBuilder();
        if (!TextUtils.isEmpty(payload.getSummary())) {
            summary.append(payload.getSummary());
        }
        List<CardItem> items = payload.getItems();
        if (summary.length() == 0 && items != null && !items.isEmpty()) {
            for (int i = 0; i < Math.min(items.size(), 3); i++) {
                CardItem item = items.get(i);
                if (!TextUtils.isEmpty(item.getContent())) {
                    if (summary.length() > 0) summary.append("\n");
                    summary.append(item.getContent());
                } else if ("IMAGE".equalsIgnoreCase(item.getType())) {
                    if (summary.length() > 0) summary.append("\n");
                    summary.append("[图片]");
                } else if ("VIDEO".equalsIgnoreCase(item.getType())) {
                    if (summary.length() > 0) summary.append("\n");
                    summary.append("[视频]");
                } else if ("FILE".equalsIgnoreCase(item.getType())) {
                    if (summary.length() > 0) summary.append("\n");
                    summary.append("[文件]");
                } else if ("VOICE".equalsIgnoreCase(item.getType())) {
                    if (summary.length() > 0) summary.append("\n");
                    summary.append("[语音]");
                }
            }
        }
        
        if (summary.length() > 0) {
            refs.compositeSummary.setVisibility(View.VISIBLE);
            MarkdownRenderer.renderIfMarkdown(refs.compositeSummary, summary.toString());
        } else {
            refs.compositeSummary.setVisibility(View.GONE);
        }

        List<String> mediaUrls = new ArrayList<>();
        if (items != null) {
            for (CardItem item : items) {
                if ("IMAGE".equalsIgnoreCase(item.getType())) {
                    mediaUrls.add(item.getUrl());
                } else if ("VIDEO".equalsIgnoreCase(item.getType())) {
                    mediaUrls.add(TextUtils.isEmpty(item.getThumbnailUrl()) ? item.getUrl() : item.getThumbnailUrl());
                }
            }
        }

        if (mediaUrls.isEmpty()) {
            refs.compositeGrid.setVisibility(View.GONE);
        } else {
            refs.compositeGrid.setVisibility(View.VISIBLE);
            refs.compositeGrid.removeAllViews();
            
            int count = Math.min(mediaUrls.size(), 9);
            int cols = count == 1 ? 1 : (count == 4 || count == 2 ? 2 : 3);
            refs.compositeGrid.setColumnCount(cols);
            
            int sizePx = count == 1 ? context.getResources().getDimensionPixelSize(R.dimen.chat_media_preview_width) :
                    (int) (90 * context.getResources().getDisplayMetrics().density);
            int marginPx = (int) (2 * context.getResources().getDisplayMetrics().density);

            for (int i = 0; i < count; i++) {
                ImageView iv = new ImageView(context);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                
                android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
                params.width = sizePx;
                params.height = sizePx;
                params.setMargins(marginPx, marginPx, marginPx, marginPx);
                iv.setLayoutParams(params);
                
                Glide.with(context)
                        .load(resolveMediaModel(mediaUrls.get(i)))
                        .placeholder(R.drawable.bg_placeholder_card)
                        .error(R.drawable.bg_placeholder_card)
                        .override(sizePx, sizePx)
                        .into(iv);
                        
                refs.compositeGrid.addView(iv);
            }
        }

        bindCompositeFiles(refs, items, context);

        refs.compositeContainer.setOnClickListener(v -> {
            if (selectionMode) {
                toggleSelection(message);
                return;
            }
            String detailTitle = payload != null && !TextUtils.isEmpty(payload.getTitle())
                    ? payload.getTitle()
                    : message.getContent();
            String detailPeerAvatar = peerAvatarUrl != null ? peerAvatarUrl : peerAvatar;
            CardDetailActivity.start(context, detailTitle, payload, userAvatar, userAvatarUrl, detailPeerAvatar);
        });
        bindRetryState(holder, message, mine);
    }

    private void toggleVoicePlayback(Context context, Message message, View voiceContainer, View voiceWaveform) {
        if (TextUtils.isEmpty(message.getMediaUrl())) {
            Toast.makeText(context, "语音地址无效", Toast.LENGTH_SHORT).show();
            return;
        }

        Long messageId = message.getId();
        if (messageId != null && messageId.equals(currentPlayingMessageId) && mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            stopVoiceWaveAnimation(voiceWaveform);
            notifyDataSetChanged();
            return;
        }

        stopVoicePlayback();
        mediaPlayer = new MediaPlayer();
        String token = tokenManager.getAccessToken();
        try {
            String requestUrl = MediaUrlResolver.resolve(message.getMediaUrl());
            if (!TextUtils.isEmpty(token)) {
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                mediaPlayer.setDataSource(context, Uri.parse(requestUrl), headers);
            } else {
                mediaPlayer.setDataSource(context, Uri.parse(requestUrl));
            }

            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                currentPlayingMessageId = messageId;
                startVoiceWaveAnimation(voiceWaveform);
                notifyDataSetChanged();
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                stopVoicePlayback();
                notifyDataSetChanged();
            });
            mediaPlayer.prepareAsync();
        } catch (Exception exception) {
            stopVoicePlayback();
            Toast.makeText(context, "语音播放失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopVoicePlayback() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException ignored) {
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        currentPlayingMessageId = null;
    }

    private java.util.Map<View, android.animation.AnimatorSet> waveAnimators = new java.util.WeakHashMap<>();

    private void startVoiceWaveAnimation(View voiceWaveform) {
        stopVoiceWaveAnimation(voiceWaveform);
        
        if (voiceWaveform instanceof VoiceWaveView) {
            ((VoiceWaveView) voiceWaveform).startAnimation();
        }
    }

    private void stopVoiceWaveAnimation(View voiceWaveform) {
        if (voiceWaveform == null) return;
        
        if (voiceWaveform instanceof VoiceWaveView) {
            ((VoiceWaveView) voiceWaveform).stopAnimation();
        } else {
            android.animation.AnimatorSet animator = waveAnimators.remove(voiceWaveform);
            if (animator != null) {
                animator.cancel();
            }
            voiceWaveform.setScaleX(1f);
            voiceWaveform.setScaleY(1f);
            voiceWaveform.setAlpha(1f);
        }
    }

    private void openFileMessage(Context context, Message message) {
        String objectName = message.getMediaUrl();
        if (TextUtils.isEmpty(objectName)) {
            Toast.makeText(context, "文件地址无效", Toast.LENGTH_SHORT).show();
            return;
        }

        File cacheFile = new File(context.getCacheDir(), objectName.replace('/', '_'));
        if (cacheFile.exists() && cacheFile.length() > 0) {
            openCachedFile(context, message, cacheFile);
            return;
        }

        Toast.makeText(context, "正在下载文件...", Toast.LENGTH_SHORT).show();
        fileRepository.download(objectName, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(String path) {
                openCachedFile(context, message, new File(path));
            }

            @Override
            public void onError(String errorMsg, int code) {
                Toast.makeText(context, "文件下载失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openCachedFile(Context context, Message message, File file) {
        String fileName = !TextUtils.isEmpty(message.getFileName())
                ? message.getFileName()
                : fallbackFileName(message.getMediaUrl());
        String ext = getFileExtension(fileName);

        if (isImageExtension(ext)) {
            Intent intent = new Intent(context, ImageViewerActivity.class);
            intent.putExtra(ImageViewerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        }

        if (isTextPreviewExtension(ext) || "pdf".equals(ext)) {
            Intent intent = new Intent(context, FilePreviewActivity.class);
            intent.putExtra(FilePreviewActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            intent.putExtra(FilePreviewActivity.EXTRA_FILE_NAME, fileName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        }

        openFileWithExternalApp(context, file);
    }

    private void openFileWithExternalApp(Context context, File file) {
        Uri uri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file
        );
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, resolveMimeType(file.getName()));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "未找到可打开该文件的应用", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isImageExtension(String ext) {
        return "jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext) || "gif".equals(ext);
    }

    private boolean isTextPreviewExtension(String ext) {
        return "txt".equals(ext)
                || "json".equals(ext)
                || "xml".equals(ext)
                || "csv".equals(ext)
                || "log".equals(ext)
                || "md".equals(ext)
                || "html".equals(ext)
                || "java".equals(ext)
                || "py".equals(ext)
                || "js".equals(ext);
    }

    private void hideAllMediaContainers(MessageViewHolder holder, boolean mine) {
        FrameRefs refs = getRefs(holder, mine);
        refs.imageContainer.setVisibility(View.GONE);
        refs.playIcon.setVisibility(View.GONE);
        refs.uploadProgress.setVisibility(View.GONE);
        refs.voiceContainer.setVisibility(View.GONE);
        refs.voiceUploadProgress.setVisibility(View.GONE);
        refs.fileContainer.setVisibility(View.GONE);
        refs.fileUploadProgress.setVisibility(View.GONE);
        refs.compositeContainer.setVisibility(View.GONE);
        refs.compositeFileList.setVisibility(View.GONE);
        refs.compositeFileList.removeAllViews();
        stopVoiceWaveAnimation(refs.voiceWaveform);
    }

    private FrameRefs getRefs(MessageViewHolder holder, boolean mine) {
        if (mine) {
            return new FrameRefs(
                    holder.binding.rightMediaImageContainer,
                    holder.binding.rightMediaImage,
                    holder.binding.rightPlayIcon,
                    holder.binding.rightUploadProgress,
                    holder.binding.rightVoiceContainer,
                    holder.binding.rightVoicePlayBtn,
                    holder.binding.rightVoiceDuration,
                    holder.binding.rightVoiceWaveform,
                    holder.binding.rightVoiceUploadProgress,
                    holder.binding.rightFileContainer,
                    holder.binding.rightFileIcon,
                    holder.binding.rightFileNameText,
                    holder.binding.rightFileSizeText,
                    holder.binding.rightFileUploadProgress,
                    holder.binding.rightMessageText,
                    holder.binding.rightCompositeContainer,
                    holder.binding.rightCompositeTitle,
                    holder.binding.rightCompositeSummary,
                    holder.binding.rightCompositeGrid,
                    holder.binding.rightCompositeFileList
            );
        }
        return new FrameRefs(
                holder.binding.mediaImageContainer,
                holder.binding.mediaImage,
                holder.binding.playIcon,
                holder.binding.uploadProgress,
                holder.binding.voiceContainer,
                holder.binding.voicePlayBtn,
                holder.binding.voiceDuration,
                holder.binding.voiceWaveform,
                holder.binding.voiceUploadProgress,
                holder.binding.fileContainer,
                holder.binding.fileIcon,
                holder.binding.fileNameText,
                holder.binding.fileSizeText,
                holder.binding.fileUploadProgress,
                holder.binding.messageText,
                holder.binding.compositeContainer,
                holder.binding.compositeTitle,
                holder.binding.compositeSummary,
                holder.binding.compositeGrid,
                holder.binding.compositeFileList
        );
    }

    private void bindTextForMediaMessage(TextView textView, String content) {
        if (TextUtils.isEmpty(content) 
            || "[图片]".equals(content) 
            || "[视频]".equals(content) 
            || "[语音]".equals(content) 
            || "[文件]".equals(content)) {
            textView.setVisibility(View.GONE);
            textView.setText("");
            return;
        }
        textView.setVisibility(View.VISIBLE);
        MarkdownRenderer.renderIfMarkdown(textView, content);
    }

    private void bindCompositeFiles(FrameRefs refs, @Nullable List<CardItem> items, Context context) {
        refs.compositeFileList.removeAllViews();
        if (items == null || items.isEmpty()) {
            refs.compositeFileList.setVisibility(View.GONE);
            return;
        }

        int iconSize = context.getResources().getDimensionPixelSize(R.dimen.chat_file_icon_size);
        int spacing = context.getResources().getDimensionPixelSize(R.dimen.chat_media_spacing_small);
        float bodyTextSize = context.getResources().getDimension(R.dimen.text_size_body_small);
        float tinyTextSize = context.getResources().getDimension(R.dimen.text_size_tiny);
        boolean hasFiles = false;

        for (CardItem item : items) {
            if (!"FILE".equalsIgnoreCase(item.getType())) {
                continue;
            }
            hasFiles = true;

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, spacing / 2, 0, spacing / 2);

            ImageView icon = new ImageView(context);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            icon.setLayoutParams(iconParams);
            String fileName = !TextUtils.isEmpty(item.getFileName()) ? item.getFileName() : fallbackFileName(item.getUrl());
            icon.setImageResource(resolveFileIcon(fileName));
            row.addView(icon);

            LinearLayout textColumn = new LinearLayout(context);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textParams.setMarginStart(spacing);
            textColumn.setLayoutParams(textParams);

            TextView nameView = new TextView(context);
            nameView.setText(fileName);
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            nameView.setTextColor(context.getColor(R.color.text_primary));
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_PX, bodyTextSize);
            textColumn.addView(nameView);

            if (item.getFileSize() != null && item.getFileSize() > 0) {
                TextView sizeView = new TextView(context);
                sizeView.setText(formatFileSize(item.getFileSize()));
                sizeView.setTextColor(context.getColor(R.color.text_secondary));
                sizeView.setTextSize(TypedValue.COMPLEX_UNIT_PX, tinyTextSize);
                textColumn.addView(sizeView);
            }

            row.addView(textColumn);
            if (!TextUtils.isEmpty(item.getUrl())) {
                row.setOnClickListener(v -> {
                    if (selectionMode) {
                        return;
                    }
                    Message fileMessage = new Message();
                    fileMessage.setMediaType("FILE");
                    fileMessage.setMediaUrl(item.getUrl());
                    fileMessage.setFileName(fileName);
                    fileMessage.setFileSize(item.getFileSize());
                    openFileMessage(context, fileMessage);
                });
            }
            refs.compositeFileList.addView(row);
        }

        refs.compositeFileList.setVisibility(hasFiles ? View.VISIBLE : View.GONE);
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private String fallbackFileName(String mediaUrl) {
        if (TextUtils.isEmpty(mediaUrl)) {
            return "unknown_file";
        }
        int slash = mediaUrl.lastIndexOf('/');
        if (slash >= 0 && slash < mediaUrl.length() - 1) {
            return mediaUrl.substring(slash + 1);
        }
        return mediaUrl;
    }

    private int resolveFileIcon(String fileName) {
        String extension = getFileExtension(fileName);
        switch (extension) {
            case "pdf":
                return R.drawable.ic_file_pdf;
            case "doc":
            case "docx":
                return R.drawable.ic_file_doc;
            case "xls":
            case "xlsx":
                return R.drawable.ic_file_xls;
            case "ppt":
            case "pptx":
                return R.drawable.ic_file_ppt;
            default:
                return R.drawable.ic_file_generic;
        }
    }

    private String getFileExtension(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveMimeType(String fileName) {
        String ext = getFileExtension(fileName);
        switch (ext) {
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "mp4":
                return "video/mp4";
            case "m4a":
                return "audio/mp4";
            default:
                return "*/*";
        }
    }

    private String formatFileSize(Long fileSize) {
        if (fileSize == null || fileSize <= 0) {
            return "0 B";
        }
        double size = fileSize;
        if (size < 1024) {
            return String.format(Locale.getDefault(), "%d B", fileSize);
        }
        size /= 1024.0;
        if (size < 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", size);
        }
        size /= 1024.0;
        if (size < 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", size);
        }
        size /= 1024.0;
        return String.format(Locale.getDefault(), "%.1f GB", size);
    }

    class MessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatMessageBinding binding;

        MessageViewHolder(ItemChatMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Message message, int position) {
            boolean showTimeDivider = false;
            LocalDateTime displayTime = getDisplayTime(message);
            if (position > 0 && displayTime != null) {
                Message prevMessage = items.get(position - 1);
                LocalDateTime prevDisplayTime = getDisplayTime(prevMessage);
                if (prevDisplayTime != null) {
                    LocalDateTime currentTime = displayTime;
                    LocalDateTime prevTime = prevDisplayTime;
                    Duration duration = Duration.between(prevTime, currentTime);
                    if (duration.toMillis() >= TEN_MINUTES_IN_MILLIS) {
                        showTimeDivider = true;
                    }
                }
            }

            if (showTimeDivider && displayTime != null) {
                binding.timeDivider.setVisibility(View.VISIBLE);
                binding.timeDivider.setText(displayTime.format(TIME_FORMATTER));
            } else {
                binding.timeDivider.setVisibility(View.GONE);
            }

            Long currentUserId = tokenManager.getUserId();
            boolean mine;
            if (currentUserId != null && message.getSenderId() != null) {
                mine = currentUserId.equals(message.getSenderId());
            } else {
                mine = !"assistant".equals(message.getRole()) && !"ai".equals(message.getRole());
            }
            binding.bubbleCard.setCardBackgroundColor(binding.getRoot().getContext().getColor(mine ? R.color.chat_mine : R.color.surface));

            binding.leftContainer.setVisibility(mine ? View.GONE : View.VISIBLE);
            binding.rightContainer.setVisibility(mine ? View.VISIBLE : View.GONE);
            binding.rightRetryIcon.setVisibility(View.GONE);
            
            if (!mine) {
                if (peerAvatarUrl != null) {
                    binding.avatarImage.setVisibility(View.VISIBLE);
                    binding.avatarText.setVisibility(View.GONE);
                    Glide.with(binding.getRoot().getContext())
                            .load(resolveMediaUrl(peerAvatarUrl))
                            .placeholder(R.drawable.bg_avatar_circle)
                            .error(R.drawable.bg_avatar_circle)
                            .circleCrop()
                            .into(binding.avatarImage);
                } else {
                    binding.avatarImage.setVisibility(View.GONE);
                    binding.avatarText.setVisibility(View.VISIBLE);
                    binding.avatarText.setText(peerAvatar == null || peerAvatar.isEmpty() ? "🤖" : peerAvatar);
                }
            }
            
            if (mine) {
                if (userAvatarUrl != null) {
                    binding.rightAvatarText.setVisibility(View.GONE);
                    binding.rightAvatarImage.setVisibility(View.VISIBLE);
                    Glide.with(binding.getRoot().getContext())
                            .load(resolveMediaUrl(userAvatarUrl))
                            .placeholder(R.drawable.bg_avatar_circle)
                            .error(R.drawable.bg_avatar_circle)
                            .circleCrop()
                            .into(binding.rightAvatarImage);
                } else if (localAvatarFile != null && localAvatarFile.exists()) {
                    binding.rightAvatarText.setVisibility(View.GONE);
                    binding.rightAvatarImage.setVisibility(View.VISIBLE);
                    Glide.with(binding.getRoot().getContext())
                            .load(localAvatarFile)
                            .placeholder(R.drawable.bg_avatar_circle)
                            .error(R.drawable.bg_avatar_circle)
                            .circleCrop()
                            .into(binding.rightAvatarImage);
                } else {
                    binding.rightAvatarImage.setVisibility(View.GONE);
                    binding.rightAvatarText.setVisibility(View.VISIBLE);
                    binding.rightAvatarText.setText(userAvatar == null || userAvatar.isEmpty() ? "😊" : userAvatar);
                }
            }

            String mediaType = message.getMediaType();
            if (TextUtils.isEmpty(mediaType) || "TEXT".equalsIgnoreCase(mediaType)) {
                showTextOnly(this, message, mine);
            } else if ("IMAGE".equalsIgnoreCase(mediaType)) {
                showImageMessage(this, message, mine);
            } else if ("VIDEO".equalsIgnoreCase(mediaType)) {
                showVideoMessage(this, message, mine);
            } else if ("VOICE".equalsIgnoreCase(mediaType)) {
                showVoiceMessage(this, message, mine);
            } else if ("FILE".equalsIgnoreCase(mediaType)) {
                showFileMessage(this, message, mine);
            } else if ("COMPOSITE".equalsIgnoreCase(mediaType) || "CARD".equalsIgnoreCase(mediaType)) {
                showCompositeMessage(this, message, mine);
            } else {
                showTextOnly(this, message, mine);
            }

            View.OnLongClickListener longClickListener = v -> {
                if (selectionMode) {
                    toggleSelection(message);
                    return true;
                }
                if (listener != null) {
                    View bubbleView = mine ? binding.rightContainer : binding.bubbleCard;
                    listener.onLongClick(message, bubbleView);
                }
                return true;
            };
            binding.bubbleCard.setOnLongClickListener(longClickListener);
            binding.rightContainer.setOnLongClickListener(longClickListener);
            binding.mediaImageContainer.setOnLongClickListener(longClickListener);
            binding.rightMediaImageContainer.setOnLongClickListener(longClickListener);
            binding.voiceContainer.setOnLongClickListener(longClickListener);
            binding.rightVoiceContainer.setOnLongClickListener(longClickListener);
            binding.fileContainer.setOnLongClickListener(longClickListener);
            binding.rightFileContainer.setOnLongClickListener(longClickListener);
            binding.compositeContainer.setOnLongClickListener(longClickListener);
            binding.rightCompositeContainer.setOnLongClickListener(longClickListener);

            if (selectionMode) {
                boolean selected = message.getId() != null && selectedIds.contains(message.getId());
                binding.checkbox.setChecked(selected);
                binding.rightCheckbox.setChecked(selected);

                if (mine) {
                    binding.checkbox.setVisibility(View.GONE);
                    binding.rightCheckbox.setVisibility(View.VISIBLE);
                } else {
                    binding.checkbox.setVisibility(View.VISIBLE);
                    binding.rightCheckbox.setVisibility(View.GONE);
                }
                
                View.OnClickListener selectClickListener = v -> {
                    toggleSelection(message);
                };
                binding.getRoot().setOnClickListener(selectClickListener);
                binding.leftContainer.setOnClickListener(selectClickListener);
                binding.bubbleCard.setOnClickListener(selectClickListener);
                binding.rightContainer.setOnClickListener(selectClickListener);
                binding.mediaImageContainer.setOnClickListener(selectClickListener);
                binding.rightMediaImageContainer.setOnClickListener(selectClickListener);
                binding.voiceContainer.setOnClickListener(selectClickListener);
                binding.rightVoiceContainer.setOnClickListener(selectClickListener);
                binding.fileContainer.setOnClickListener(selectClickListener);
                binding.rightFileContainer.setOnClickListener(selectClickListener);
                binding.compositeContainer.setOnClickListener(selectClickListener);
                binding.rightCompositeContainer.setOnClickListener(selectClickListener);
                binding.checkbox.setOnClickListener(selectClickListener);
                binding.rightCheckbox.setOnClickListener(selectClickListener);
                binding.rightRetryIcon.setOnClickListener(null);
                binding.rightRetryIcon.setVisibility(View.GONE);
                
                binding.bubbleCard.setOnLongClickListener(null);
                binding.rightContainer.setOnLongClickListener(null);
            } else {
                binding.checkbox.setVisibility(View.GONE);
                binding.rightCheckbox.setVisibility(View.GONE);
                binding.getRoot().setOnClickListener(null);
                binding.leftContainer.setOnClickListener(null);
                binding.bubbleCard.setOnClickListener(null);
                binding.rightContainer.setOnClickListener(null);
                binding.checkbox.setOnClickListener(null);
                binding.rightCheckbox.setOnClickListener(null);
            }
        }
    }

    @Nullable
    private LocalDateTime getDisplayTime(@Nullable Message message) {
        if (message == null) {
            return null;
        }
        Long localSortTimestamp = message.getLocalSortTimestamp();
        if (localSortTimestamp != null) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(localSortTimestamp), ZoneId.systemDefault());
        }
        return message.getCreatedAt();
    }

    private static final class FrameRefs {
        final View imageContainer;
        final ImageView imageView;
        final ImageView playIcon;
        final ProgressBar uploadProgress;
        final View voiceContainer;
        final ImageView voicePlayBtn;
        final TextView voiceDuration;
        final VoiceWaveView voiceWaveform;
        final ProgressBar voiceUploadProgress;
        final View fileContainer;
        final ImageView fileIcon;
        final TextView fileNameText;
        final TextView fileSizeText;
        final ProgressBar fileUploadProgress;
        final TextView messageText;
        final View compositeContainer;
        final TextView compositeTitle;
        final TextView compositeSummary;
        final android.widget.GridLayout compositeGrid;
        final LinearLayout compositeFileList;

        FrameRefs(View imageContainer,
                  ImageView imageView,
                  ImageView playIcon,
                  ProgressBar uploadProgress,
                  View voiceContainer,
                  ImageView voicePlayBtn,
                  TextView voiceDuration,
                  VoiceWaveView voiceWaveform,
                  ProgressBar voiceUploadProgress,
                  View fileContainer,
                  ImageView fileIcon,
                  TextView fileNameText,
                  TextView fileSizeText,
                  ProgressBar fileUploadProgress,
                  TextView messageText,
                  View compositeContainer,
                  TextView compositeTitle,
                  TextView compositeSummary,
                  android.widget.GridLayout compositeGrid,
                  LinearLayout compositeFileList) {
            this.imageContainer = imageContainer;
            this.imageView = imageView;
            this.playIcon = playIcon;
            this.uploadProgress = uploadProgress;
            this.voiceContainer = voiceContainer;
            this.voicePlayBtn = voicePlayBtn;
            this.voiceDuration = voiceDuration;
            this.voiceWaveform = voiceWaveform;
            this.voiceUploadProgress = voiceUploadProgress;
            this.fileContainer = fileContainer;
            this.fileIcon = fileIcon;
            this.fileNameText = fileNameText;
            this.fileSizeText = fileSizeText;
            this.fileUploadProgress = fileUploadProgress;
            this.messageText = messageText;
            this.compositeContainer = compositeContainer;
            this.compositeTitle = compositeTitle;
            this.compositeSummary = compositeSummary;
            this.compositeGrid = compositeGrid;
            this.compositeFileList = compositeFileList;
        }
    }
}
