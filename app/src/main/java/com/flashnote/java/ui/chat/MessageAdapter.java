package com.flashnote.java.ui.chat;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.TokenManager;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.databinding.ItemChatMessageBinding;
import com.flashnote.java.ui.media.ImageViewerActivity;
import com.flashnote.java.ui.media.MediaUrlResolver;
import com.flashnote.java.ui.media.VideoPlayerActivity;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    public interface OnMessageLongClickListener {
        void onLongClick(Message message, View clickedView);
    }

    private static final long TEN_MINUTES_IN_MILLIS = 10 * 60 * 1000;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final List<Message> items = new ArrayList<>();
    private final OnMessageLongClickListener listener;
    private final TokenManager tokenManager;
    private final FileRepository fileRepository;

    private MediaPlayer mediaPlayer;
    private Long currentPlayingMessageId;

    public MessageAdapter(OnMessageLongClickListener listener) {
        this.listener = listener;
        FlashNoteApp app = FlashNoteApp.getInstance();
        this.tokenManager = app.getTokenManager();
        this.fileRepository = app.getFileRepository();
    }

    public void submitList(List<Message> messages) {
        items.clear();
        if (messages != null) {
            items.addAll(messages);
        }
        notifyDataSetChanged();
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

    @Override
    public void onViewRecycled(@NonNull MessageViewHolder holder) {
        super.onViewRecycled(holder);
        holder.binding.voicePlayBtn.setOnClickListener(null);
        holder.binding.rightVoicePlayBtn.setOnClickListener(null);
        holder.binding.mediaImageContainer.setOnClickListener(null);
        holder.binding.rightMediaImageContainer.setOnClickListener(null);
        holder.binding.fileContainer.setOnClickListener(null);
        holder.binding.rightFileContainer.setOnClickListener(null);
    }

    private GlideUrl buildGlideUrl(Context context, String mediaPathOrUrl) {
        String token = tokenManager.getAccessToken();
        String requestUrl = MediaUrlResolver.resolve(mediaPathOrUrl);
        if (TextUtils.isEmpty(token)) {
            return new GlideUrl(requestUrl);
        }
        return new GlideUrl(requestUrl, new LazyHeaders.Builder()
                .addHeader("Authorization", "Bearer " + token)
                .build());
    }

    private void showTextOnly(MessageViewHolder holder, Message message, boolean mine) {
        hideAllMediaContainers(holder, mine);
        TextView messageText = mine ? holder.binding.rightMessageText : holder.binding.messageText;
        messageText.setVisibility(View.VISIBLE);
        messageText.setText(safeText(message.getContent()));
    }

    private void showImageMessage(MessageViewHolder holder, Message message, boolean mine) {
        hideAllMediaContainers(holder, mine);
        FrameRefs refs = getRefs(holder, mine);

        refs.imageContainer.setVisibility(View.VISIBLE);
        refs.playIcon.setVisibility(View.GONE);
        bindTextForMediaMessage(refs.messageText, message.getContent());

        Glide.with(holder.binding.getRoot().getContext())
                .load(buildGlideUrl(holder.binding.getRoot().getContext(), message.getMediaUrl()))
                .placeholder(R.drawable.bg_placeholder_card)
                .error(R.drawable.bg_placeholder_card)
                .into(refs.imageView);

        refs.imageContainer.setOnClickListener(v -> {
            Context context = v.getContext();
            Intent intent = new Intent(context, ImageViewerActivity.class);
            intent.putExtra(ImageViewerActivity.EXTRA_MEDIA_URL, message.getMediaUrl());
            context.startActivity(intent);
        });
    }

    private void showVideoMessage(MessageViewHolder holder, Message message, boolean mine) {
        hideAllMediaContainers(holder, mine);
        FrameRefs refs = getRefs(holder, mine);

        refs.imageContainer.setVisibility(View.VISIBLE);
        refs.playIcon.setVisibility(View.VISIBLE);
        bindTextForMediaMessage(refs.messageText, message.getContent());

        String preview = TextUtils.isEmpty(message.getThumbnailUrl()) ? message.getMediaUrl() : message.getThumbnailUrl();
        Glide.with(holder.binding.getRoot().getContext())
                .load(buildGlideUrl(holder.binding.getRoot().getContext(), preview))
                .placeholder(R.drawable.bg_placeholder_card)
                .error(R.drawable.bg_placeholder_card)
                .into(refs.imageView);

        refs.imageContainer.setOnClickListener(v -> {
            Context context = v.getContext();
            Intent intent = new Intent(context, VideoPlayerActivity.class);
            intent.putExtra(VideoPlayerActivity.EXTRA_MEDIA_URL, message.getMediaUrl());
            context.startActivity(intent);
        });
    }

    private void showVoiceMessage(MessageViewHolder holder, Message message, boolean mine) {
        hideAllMediaContainers(holder, mine);
        FrameRefs refs = getRefs(holder, mine);

        refs.voiceContainer.setVisibility(View.VISIBLE);
        bindTextForMediaMessage(refs.messageText, message.getContent());

        Integer duration = message.getMediaDuration();
        refs.voiceDuration.setText((duration == null || duration <= 0 ? 0 : duration) + "s");

        boolean isPlayingThis = message.getId() != null
                && currentPlayingMessageId != null
                && currentPlayingMessageId.equals(message.getId())
                && mediaPlayer != null
                && mediaPlayer.isPlaying();
        refs.voicePlayBtn.setImageResource(isPlayingThis ? R.drawable.ic_pause : R.drawable.ic_play_arrow);

        refs.voicePlayBtn.setOnClickListener(v -> toggleVoicePlayback(v.getContext(), message, refs.voicePlayBtn));
    }

    private void showFileMessage(MessageViewHolder holder, Message message, boolean mine) {
        hideAllMediaContainers(holder, mine);
        FrameRefs refs = getRefs(holder, mine);

        refs.fileContainer.setVisibility(View.VISIBLE);
        bindTextForMediaMessage(refs.messageText, message.getContent());

        String fileName = !TextUtils.isEmpty(message.getFileName()) ? message.getFileName() : fallbackFileName(message.getMediaUrl());
        refs.fileNameText.setText(fileName);
        refs.fileSizeText.setText(formatFileSize(message.getFileSize()));
        refs.fileIcon.setImageResource(resolveFileIcon(fileName));

        refs.fileContainer.setOnClickListener(v -> openFileMessage(v.getContext(), message));
    }

    private void toggleVoicePlayback(Context context, Message message, ImageView playBtn) {
        if (TextUtils.isEmpty(message.getMediaUrl())) {
            Toast.makeText(context, "语音地址无效", Toast.LENGTH_SHORT).show();
            return;
        }

        Long messageId = message.getId();
        if (messageId != null && messageId.equals(currentPlayingMessageId) && mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            playBtn.setImageResource(R.drawable.ic_play_arrow);
            currentPlayingMessageId = null;
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
                playBtn.setImageResource(R.drawable.ic_pause);
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

    private void openFileMessage(Context context, Message message) {
        String objectName = message.getMediaUrl();
        if (TextUtils.isEmpty(objectName)) {
            Toast.makeText(context, "文件地址无效", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(context, "正在下载文件...", Toast.LENGTH_SHORT).show();
        fileRepository.download(objectName, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(String path) {
                File file = new File(path);
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

            @Override
            public void onError(String message, int code) {
                Toast.makeText(context, "文件下载失败: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void hideAllMediaContainers(MessageViewHolder holder, boolean mine) {
        FrameRefs refs = getRefs(holder, mine);
        refs.imageContainer.setVisibility(View.GONE);
        refs.playIcon.setVisibility(View.GONE);
        refs.voiceContainer.setVisibility(View.GONE);
        refs.fileContainer.setVisibility(View.GONE);
    }

    private FrameRefs getRefs(MessageViewHolder holder, boolean mine) {
        if (mine) {
            return new FrameRefs(
                    holder.binding.rightMediaImageContainer,
                    holder.binding.rightMediaImage,
                    holder.binding.rightPlayIcon,
                    holder.binding.rightVoiceContainer,
                    holder.binding.rightVoicePlayBtn,
                    holder.binding.rightVoiceDuration,
                    holder.binding.rightFileContainer,
                    holder.binding.rightFileIcon,
                    holder.binding.rightFileNameText,
                    holder.binding.rightFileSizeText,
                    holder.binding.rightMessageText
            );
        }
        return new FrameRefs(
                holder.binding.mediaImageContainer,
                holder.binding.mediaImage,
                holder.binding.playIcon,
                holder.binding.voiceContainer,
                holder.binding.voicePlayBtn,
                holder.binding.voiceDuration,
                holder.binding.fileContainer,
                holder.binding.fileIcon,
                holder.binding.fileNameText,
                holder.binding.fileSizeText,
                holder.binding.messageText
        );
    }

    private void bindTextForMediaMessage(TextView textView, String content) {
        if (TextUtils.isEmpty(content)) {
            textView.setVisibility(View.GONE);
            textView.setText("");
            return;
        }
        textView.setVisibility(View.VISIBLE);
        textView.setText(content);
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
            if (position > 0 && message.getCreatedAt() != null) {
                Message prevMessage = items.get(position - 1);
                if (prevMessage.getCreatedAt() != null) {
                    LocalDateTime currentTime = message.getCreatedAt();
                    LocalDateTime prevTime = prevMessage.getCreatedAt();
                    Duration duration = Duration.between(prevTime, currentTime);
                    if (duration.toMillis() >= TEN_MINUTES_IN_MILLIS) {
                        showTimeDivider = true;
                    }
                }
            }

            if (showTimeDivider && message.getCreatedAt() != null) {
                binding.timeDivider.setVisibility(View.VISIBLE);
                binding.timeDivider.setText(message.getCreatedAt().format(TIME_FORMATTER));
            } else {
                binding.timeDivider.setVisibility(View.GONE);
            }

            boolean mine = !"assistant".equals(message.getRole()) && !"ai".equals(message.getRole());
            binding.bubbleCard.setCardBackgroundColor(binding.getRoot().getContext().getColor(mine ? R.color.chat_mine : R.color.surface));

            binding.leftContainer.setVisibility(mine ? View.GONE : View.VISIBLE);
            binding.rightContainer.setVisibility(mine ? View.VISIBLE : View.GONE);
            binding.avatarText.setText("🤖");
            binding.rightAvatarText.setText("😊");

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
            } else {
                showTextOnly(this, message, mine);
            }

            binding.getRoot().setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onLongClick(message, v);
                }
                return true;
            });
        }
    }

    private static final class FrameRefs {
        final View imageContainer;
        final ImageView imageView;
        final ImageView playIcon;
        final View voiceContainer;
        final ImageView voicePlayBtn;
        final TextView voiceDuration;
        final View fileContainer;
        final ImageView fileIcon;
        final TextView fileNameText;
        final TextView fileSizeText;
        final TextView messageText;

        FrameRefs(View imageContainer,
                  ImageView imageView,
                  ImageView playIcon,
                  View voiceContainer,
                  ImageView voicePlayBtn,
                  TextView voiceDuration,
                  View fileContainer,
                  ImageView fileIcon,
                  TextView fileNameText,
                  TextView fileSizeText,
                  TextView messageText) {
            this.imageContainer = imageContainer;
            this.imageView = imageView;
            this.playIcon = playIcon;
            this.voiceContainer = voiceContainer;
            this.voicePlayBtn = voicePlayBtn;
            this.voiceDuration = voiceDuration;
            this.fileContainer = fileContainer;
            this.fileIcon = fileIcon;
            this.fileNameText = fileNameText;
            this.fileSizeText = fileSizeText;
            this.messageText = messageText;
        }
    }
}
