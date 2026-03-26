package com.flashnote.java.ui.chat;

import android.content.Context;
import android.text.TextUtils;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.flashnote.java.R;
import com.flashnote.java.data.model.CardItem;
import com.flashnote.java.data.model.CardPayload;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.util.MarkdownRenderer;

import java.util.ArrayList;
import java.util.List;

final class MessageCompositeBinder {

    interface CompositeActionHandler {
        void hideAllMediaContainers(@NonNull MessageAdapter.MessageViewHolder holder, boolean mine);

        MessageAdapter.FrameRefs getRefs(@NonNull MessageAdapter.MessageViewHolder holder, boolean mine);

        void bindRetryState(@NonNull Message message, boolean mine, @NonNull MessageAdapter.MessageViewHolder holder);

        Object resolveMediaModel(String mediaPathOrUrl);

        void bindCompositeFiles(@NonNull MessageAdapter.FrameRefs refs, @Nullable List<CardItem> items, @NonNull Context context);

        void toggleSelection(@NonNull Message message);

        boolean isSelectionMode();

        @Nullable String getUserAvatar();

        @Nullable String getUserAvatarUrl();

        @Nullable String getPeerAvatar();

        @Nullable String getPeerAvatarUrl();
    }

    void bind(@NonNull MessageAdapter.MessageViewHolder holder,
              @NonNull Message message,
              boolean mine,
              @NonNull CompositeActionHandler actions) {
        actions.hideAllMediaContainers(holder, mine);
        MessageAdapter.FrameRefs refs = actions.getRefs(holder, mine);
        Context context = holder.binding.getRoot().getContext();

        refs.compositeContainer.setVisibility(android.view.View.VISIBLE);
        refs.messageText.setVisibility(android.view.View.GONE);

        CardPayload payload = message.getPayload();
        if (payload == null) {
            refs.compositeTitle.setText(R.string.chat_unknown_card_content);
            refs.compositeSummary.setVisibility(android.view.View.GONE);
            refs.compositeGrid.setVisibility(android.view.View.GONE);
            actions.bindRetryState(message, mine, holder);
            return;
        }

        refs.compositeTitle.setText(TextUtils.isEmpty(payload.getTitle()) ? context.getString(R.string.app_name) : payload.getTitle());

        String summaryText = buildSummary(payload.getSummary(), payload.getItems(), context);
        if (!summaryText.isEmpty()) {
            refs.compositeSummary.setVisibility(android.view.View.VISIBLE);
            MarkdownRenderer.renderIfMarkdown(refs.compositeSummary, summaryText);
        } else {
            refs.compositeSummary.setVisibility(android.view.View.GONE);
        }

        bindCompositeGrid(refs, payload.getItems(), context, actions);
        actions.bindCompositeFiles(refs, payload.getItems(), context);

        refs.compositeContainer.setOnClickListener(v -> {
            if (actions.isSelectionMode()) {
                actions.toggleSelection(message);
                return;
            }
            String detailTitle = !TextUtils.isEmpty(payload.getTitle()) ? payload.getTitle() : message.getContent();
            String detailPeerAvatar = actions.getPeerAvatarUrl() != null ? actions.getPeerAvatarUrl() : actions.getPeerAvatar();
            CardDetailActivity.start(context, detailTitle, payload, actions.getUserAvatar(), actions.getUserAvatarUrl(), detailPeerAvatar);
        });
        actions.bindRetryState(message, mine, holder);
    }

    @NonNull
    private String buildSummary(@Nullable String payloadSummary, @Nullable List<CardItem> items, @NonNull Context context) {
        StringBuilder summary = new StringBuilder();
        if (!TextUtils.isEmpty(payloadSummary)) {
            summary.append(payloadSummary);
        }
        if (summary.length() == 0 && items != null && !items.isEmpty()) {
            for (int i = 0; i < Math.min(items.size(), 3); i++) {
                String line = buildFallbackLine(items.get(i), context);
                if (!line.isEmpty()) {
                    if (summary.length() > 0) {
                        summary.append("\n");
                    }
                    summary.append(line);
                }
            }
        }
        return summary.toString();
    }

    @NonNull
    private String buildFallbackLine(@Nullable CardItem item, @NonNull Context context) {
        if (item == null) {
            return "";
        }
        if (!TextUtils.isEmpty(item.getContent())) {
            return item.getContent();
        }
        String type = item.getType();
        if ("IMAGE".equalsIgnoreCase(type)) {
            return context.getString(R.string.chat_media_image_placeholder);
        }
        if ("VIDEO".equalsIgnoreCase(type)) {
            return context.getString(R.string.chat_media_video_placeholder);
        }
        if ("FILE".equalsIgnoreCase(type)) {
            return context.getString(R.string.chat_media_file_placeholder);
        }
        if ("VOICE".equalsIgnoreCase(type)) {
            return context.getString(R.string.chat_media_voice_placeholder);
        }
        return "";
    }

    private void bindCompositeGrid(@NonNull MessageAdapter.FrameRefs refs,
                                   @Nullable List<CardItem> items,
                                   @NonNull Context context,
                                   @NonNull CompositeActionHandler actions) {
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
            refs.compositeGrid.setVisibility(android.view.View.GONE);
            return;
        }

        refs.compositeGrid.setVisibility(android.view.View.VISIBLE);
        refs.compositeGrid.removeAllViews();
        int count = Math.min(mediaUrls.size(), 9);
        int cols = count == 1 ? 1 : (count == 4 || count == 2 ? 2 : 3);
        refs.compositeGrid.setColumnCount(cols);

        int sizePx = count == 1
                ? context.getResources().getDimensionPixelSize(R.dimen.chat_media_preview_width)
                : (int) (90 * context.getResources().getDisplayMetrics().density);
        int marginPx = (int) (2 * context.getResources().getDisplayMetrics().density);

        for (int i = 0; i < count; i++) {
            ImageView imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
            params.width = sizePx;
            params.height = sizePx;
            params.setMargins(marginPx, marginPx, marginPx, marginPx);
            imageView.setLayoutParams(params);

            Glide.with(context)
                    .load(actions.resolveMediaModel(mediaUrls.get(i)))
                    .placeholder(R.drawable.bg_placeholder_card)
                    .error(R.drawable.bg_placeholder_card)
                    .override(sizePx, sizePx)
                    .into(imageView);

            refs.compositeGrid.addView(imageView);
        }
    }
}
