package com.flashnote.java.ui.chat;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.flashnote.java.R;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.ui.media.ImageViewerActivity;
import com.flashnote.java.ui.media.VideoPlayerActivity;

final class MessageMediaBinder {

    interface MediaActionHandler {
        void bindRetryState(@NonNull Message message, boolean mine, @NonNull MessageAdapter.MessageViewHolder holder);

        void bindTextForMediaMessage(@NonNull android.widget.TextView textView, String content);

        boolean isLocalOnlyMediaPath(String mediaPathOrUrl);

        Object resolveMediaModel(String mediaPathOrUrl);

        void hideAllMediaContainers(@NonNull MessageAdapter.MessageViewHolder holder, boolean mine);

        MessageAdapter.FrameRefs getRefs(@NonNull MessageAdapter.MessageViewHolder holder, boolean mine);

        void toggleVoicePlayback(@NonNull Context context, @NonNull Message message, @NonNull View voiceContainer, @NonNull View voiceWaveform);

        void openFileMessage(@NonNull Context context, @NonNull Message message);
    }

    void bindImage(@NonNull MessageAdapter.MessageViewHolder holder,
                   @NonNull Message message,
                   boolean mine,
                   @NonNull MediaActionHandler actions) {
        actions.hideAllMediaContainers(holder, mine);
        MessageAdapter.FrameRefs refs = actions.getRefs(holder, mine);
        Context context = holder.binding.getRoot().getContext();
        int maxWidthPx = context.getResources().getDimensionPixelSize(R.dimen.chat_media_preview_width);
        int maxHeightPx = context.getResources().getDimensionPixelSize(R.dimen.chat_media_preview_max_height);

        refs.imageContainer.setVisibility(View.VISIBLE);
        refs.playIcon.setVisibility(View.GONE);
        refs.uploadProgress.setVisibility(message.isUploading() ? View.VISIBLE : View.GONE);
        actions.bindTextForMediaMessage(refs.messageText, message.getContent());

        String preview = TextUtils.isEmpty(message.getThumbnailUrl()) ? message.getMediaUrl() : message.getThumbnailUrl();
        if (!TextUtils.isEmpty(preview)) {
            Glide.with(context)
                    .load(actions.resolveMediaModel(preview))
                    .placeholder(R.drawable.bg_placeholder_card)
                    .error(R.drawable.bg_placeholder_card)
                    .fitCenter()
                    .dontAnimate()
                    .thumbnail(0.25f)
                    .override(maxWidthPx, maxHeightPx)
                    .into(refs.imageView);

            refs.imageContainer.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), ImageViewerActivity.class);
                if (actions.isLocalOnlyMediaPath(message.getMediaUrl())) {
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
        actions.bindRetryState(message, mine, holder);
    }

    void bindVideo(@NonNull MessageAdapter.MessageViewHolder holder,
                   @NonNull Message message,
                   boolean mine,
                   @NonNull MediaActionHandler actions) {
        actions.hideAllMediaContainers(holder, mine);
        MessageAdapter.FrameRefs refs = actions.getRefs(holder, mine);
        Context context = holder.binding.getRoot().getContext();
        int maxWidthPx = context.getResources().getDimensionPixelSize(R.dimen.chat_media_preview_width);
        int maxHeightPx = context.getResources().getDimensionPixelSize(R.dimen.chat_media_preview_max_height);

        refs.imageContainer.setVisibility(View.VISIBLE);
        refs.playIcon.setVisibility(View.VISIBLE);
        refs.uploadProgress.setVisibility(message.isUploading() ? View.VISIBLE : View.GONE);
        actions.bindTextForMediaMessage(refs.messageText, message.getContent());

        String preview = TextUtils.isEmpty(message.getThumbnailUrl()) ? message.getMediaUrl() : message.getThumbnailUrl();
        refs.imageContainer.setOnClickListener(null);
        if (!message.isUploading() && actions.isLocalOnlyMediaPath(message.getMediaUrl())) {
            refs.imageContainer.setOnClickListener(v -> Toast.makeText(context, R.string.chat_video_send_failed, Toast.LENGTH_SHORT).show());
        }
        if (!TextUtils.isEmpty(preview)) {
            Glide.with(context)
                    .load(actions.resolveMediaModel(preview))
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
        actions.bindRetryState(message, mine, holder);
    }

    void bindVoice(@NonNull MessageAdapter.MessageViewHolder holder,
                   @NonNull Message message,
                   boolean mine,
                   @NonNull MediaActionHandler actions,
                   boolean isPlayingThis) {
        actions.hideAllMediaContainers(holder, mine);
        MessageAdapter.FrameRefs refs = actions.getRefs(holder, mine);

        refs.voiceContainer.setVisibility(View.VISIBLE);
        refs.voiceUploadProgress.setVisibility(message.isUploading() ? View.VISIBLE : View.GONE);
        actions.bindTextForMediaMessage(refs.messageText, message.getContent());

        Integer duration = message.getMediaDuration();
        refs.voiceDuration.setText((duration == null || duration <= 0 ? 0 : duration) + "s");

        refs.voiceContainer.setOnClickListener(null);
        if (!message.isUploading()) {
            refs.voicePlayBtn.setVisibility(View.GONE);
            if (actions.isLocalOnlyMediaPath(message.getMediaUrl())) {
                refs.voiceContainer.setOnClickListener(v -> Toast.makeText(v.getContext(), R.string.chat_voice_send_failed, Toast.LENGTH_SHORT).show());
            } else {
                refs.voiceContainer.setOnClickListener(v -> actions.toggleVoicePlayback(v.getContext(), message, refs.voiceContainer, refs.voiceWaveform));
            }

            if (isPlayingThis) {
                refs.voiceWaveform.startAnimation();
            } else {
                refs.voiceWaveform.stopAnimation();
            }
        }
        actions.bindRetryState(message, mine, holder);
    }

    void bindFile(@NonNull MessageAdapter.MessageViewHolder holder,
                  @NonNull Message message,
                  boolean mine,
                  @NonNull MediaActionHandler actions,
                  @NonNull String fileName,
                  @NonNull String fileSize,
                  int fileIconRes) {
        actions.hideAllMediaContainers(holder, mine);
        MessageAdapter.FrameRefs refs = actions.getRefs(holder, mine);

        refs.fileContainer.setVisibility(View.VISIBLE);
        refs.fileUploadProgress.setVisibility(message.isUploading() ? View.VISIBLE : View.GONE);
        actions.bindTextForMediaMessage(refs.messageText, message.getContent());

        if (!message.isUploading()) {
            refs.fileNameText.setText(fileName);
            refs.fileSizeText.setText(fileSize);
            refs.fileIcon.setImageResource(fileIconRes);

            refs.fileContainer.setOnClickListener(v -> actions.openFileMessage(v.getContext(), message));
        }
        actions.bindRetryState(message, mine, holder);
    }
}
