package com.flashnote.java.ui.main;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.Toast;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.data.model.CardItem;
import com.flashnote.java.data.model.CardPayload;
import com.flashnote.java.data.model.FavoriteItem;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.databinding.ItemFavoriteBinding;
import com.flashnote.java.databinding.PopupFavoriteActionsBinding;
import com.flashnote.java.ui.chat.CardDetailActivity;
import com.flashnote.java.ui.media.FilePreviewActivity;
import com.flashnote.java.ui.media.ImageViewerActivity;
import com.flashnote.java.ui.media.MediaUrlResolver;
import com.flashnote.java.ui.media.VideoPlayerActivity;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class FavoriteAdapter extends ListAdapter<FavoriteItem, FavoriteAdapter.FavoriteViewHolder> {
    public interface OnFavoriteActionListener {
        void onOpen(FavoriteItem item);

        void onRemove(FavoriteItem item);
    }

    private final OnFavoriteActionListener listener;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public FavoriteAdapter(OnFavoriteActionListener listener) {
        super(new FavoriteDiffCallback());
        this.listener = listener;
    }

    @NonNull
    @Override
    public FavoriteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFavoriteBinding binding = ItemFavoriteBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new FavoriteViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull FavoriteViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class FavoriteViewHolder extends RecyclerView.ViewHolder {
        private final ItemFavoriteBinding binding;

        FavoriteViewHolder(ItemFavoriteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FavoriteItem item) {
            if (item.getFlashNoteId() != null && item.getFlashNoteId() == -1L) {
                binding.titleText.setText("收集箱");
            } else {
                binding.titleText.setText(item.getFlashNoteTitle() == null ? "已收藏消息" : item.getFlashNoteTitle());
            }

            String icon = item.getFlashNoteIcon();
            binding.iconText.setText(icon != null && !icon.isEmpty() ? icon : "⚡");

            LocalDateTime displayTime = item.getFavoritedAt() != null ? item.getFavoritedAt() : item.getMessageCreatedAt();
            if (displayTime != null) {
                binding.timeText.setText(displayTime.format(TIME_FORMATTER));
                binding.timeText.setVisibility(View.VISIBLE);
            } else {
                binding.timeText.setVisibility(View.GONE);
            }

            binding.contentText.setVisibility(View.GONE);
            binding.compositeContainer.setVisibility(View.GONE);
            binding.mediaPreviewContainer.setVisibility(View.GONE);
            binding.mediaPlayIcon.setVisibility(View.GONE);
            binding.fileInfoContainer.setVisibility(View.GONE);
            binding.voiceInfoContainer.setVisibility(View.GONE);

            binding.contentText.setOnClickListener(null);
            binding.mediaPreviewContainer.setOnClickListener(null);
            binding.fileInfoContainer.setOnClickListener(null);
            binding.voiceInfoContainer.setOnClickListener(null);
            binding.contentText.setOnLongClickListener(null);
            binding.compositeContainer.setOnClickListener(null);
            binding.compositeContainer.setOnLongClickListener(null);
            binding.mediaPreviewContainer.setOnLongClickListener(null);
            binding.fileInfoContainer.setOnLongClickListener(null);
            binding.voiceInfoContainer.setOnLongClickListener(null);

            CardPayload payload = item.getPayload();
            String mediaType = item.getMediaType();
            if (isCompositeFavorite(item)) {
                bindCompositeCard(item, payload);
            } else if (mediaType == null || mediaType.isEmpty() || "TEXT".equalsIgnoreCase(mediaType)) {
                binding.contentText.setVisibility(View.VISIBLE);
                binding.contentText.setText(item.getContent() != null ? item.getContent() : "");
            } else if ("IMAGE".equalsIgnoreCase(mediaType)) {
                binding.mediaPreviewContainer.setVisibility(View.VISIBLE);
                loadMediaThumbnail(item.getMediaUrl());
                binding.mediaPreviewContainer.setOnClickListener(v -> openImagePreview(v.getContext(), item));
                binding.mediaPreviewContainer.setOnLongClickListener(v -> {
                    showFavoriteActions(item, v);
                    return true;
                });
            } else if ("VIDEO".equalsIgnoreCase(mediaType)) {
                binding.mediaPreviewContainer.setVisibility(View.VISIBLE);
                binding.mediaPlayIcon.setVisibility(View.VISIBLE);
                loadMediaThumbnail(item.getMediaUrl());
                binding.mediaPreviewContainer.setOnClickListener(v -> openVideoPlayer(v.getContext(), item));
                binding.mediaPreviewContainer.setOnLongClickListener(v -> {
                    showFavoriteActions(item, v);
                    return true;
                });
            } else if ("FILE".equalsIgnoreCase(mediaType)) {
                binding.fileInfoContainer.setVisibility(View.VISIBLE);
                binding.fileInfoName.setText(item.getFileName() != null ? item.getFileName() : "文件");
                binding.fileInfoIcon.setImageResource(resolveFileIcon(item.getFileName()));
                binding.fileInfoContainer.setOnClickListener(v -> openFile(v.getContext(), item));
                binding.fileInfoContainer.setOnLongClickListener(v -> {
                    showFavoriteActions(item, v);
                    return true;
                });
            } else if ("VOICE".equalsIgnoreCase(mediaType)) {
                binding.voiceInfoContainer.setVisibility(View.VISIBLE);
                Integer duration = item.getMediaDuration();
                binding.voiceInfoDuration.setText((duration != null && duration > 0 ? duration : 0) + "s");
                binding.voiceInfoContainer.setOnLongClickListener(v -> {
                    showFavoriteActions(item, v);
                    return true;
                });
            } else {
                binding.contentText.setVisibility(View.VISIBLE);
                binding.contentText.setText(item.getContent() != null ? item.getContent() : "");
            }

            binding.getRoot().setOnClickListener(v -> {
                if (isCompositeFavorite(item)) {
                    String detailTitle = item.getPayload().getTitle() == null || item.getPayload().getTitle().isEmpty()
                            ? item.getContent()
                            : item.getPayload().getTitle();
                    CardDetailActivity.start(v.getContext(), detailTitle, item.getPayload());
                    return;
                }
                listener.onOpen(item);
            });
            binding.getRoot().setOnLongClickListener(v -> {
                showFavoriteActions(item, v);
                return true;
            });
        }

        private boolean isCompositeFavorite(FavoriteItem item) {
            CardPayload payload = item.getPayload();
            if (payload == null) {
                return false;
            }
            String mediaType = item.getMediaType();
            return "COMPOSITE".equalsIgnoreCase(mediaType)
                    || "CARD".equalsIgnoreCase(mediaType)
                    || (payload.getItems() != null && !payload.getItems().isEmpty())
                    || (payload.getTitle() != null && !payload.getTitle().isEmpty());
        }

        private void bindCompositeCard(FavoriteItem item, CardPayload payload) {
            binding.compositeContainer.setVisibility(View.VISIBLE);
            binding.compositeTitle.setText(payload.getTitle() == null || payload.getTitle().isEmpty() ? "闪记" : payload.getTitle());

            String summaryText = buildCompositeSummary(payload);
            if (summaryText.isEmpty()) {
                binding.compositeSummary.setVisibility(View.GONE);
            } else {
                binding.compositeSummary.setVisibility(View.VISIBLE);
                binding.compositeSummary.setText(summaryText);
            }

            bindCompositeGrid(payload);

            binding.compositeContainer.setOnClickListener(v -> {
                String detailTitle = payload.getTitle() == null || payload.getTitle().isEmpty() ? item.getContent() : payload.getTitle();
                CardDetailActivity.start(v.getContext(), detailTitle, payload);
            });
            binding.compositeContainer.setOnLongClickListener(v -> {
                showFavoriteActions(item, v);
                return true;
            });
        }

        private String buildCompositeSummary(CardPayload payload) {
            if (payload.getSummary() != null && !payload.getSummary().isEmpty()) {
                return payload.getSummary();
            }
            List<CardItem> items = payload.getItems();
            if (items == null || items.isEmpty()) {
                return "";
            }
            StringBuilder summary = new StringBuilder();
            for (int i = 0; i < Math.min(items.size(), 3); i++) {
                CardItem cardItem = items.get(i);
                String line = cardItem.getContent();
                if (line == null || line.isEmpty()) {
                    if ("IMAGE".equalsIgnoreCase(cardItem.getType())) {
                        line = "[图片]";
                    } else if ("VIDEO".equalsIgnoreCase(cardItem.getType())) {
                        line = "[视频]";
                    } else if ("FILE".equalsIgnoreCase(cardItem.getType())) {
                        line = "[文件]";
                    } else if ("VOICE".equalsIgnoreCase(cardItem.getType())) {
                        line = "[语音]";
                    }
                }
                if (line == null || line.isEmpty()) {
                    continue;
                }
                if (summary.length() > 0) {
                    summary.append('\n');
                }
                summary.append(line);
            }
            return summary.toString();
        }

        private void bindCompositeGrid(CardPayload payload) {
            List<CardItem> items = payload.getItems();
            List<String> mediaUrls = new ArrayList<>();
            if (items != null) {
                for (CardItem item : items) {
                    if (item == null) {
                        continue;
                    }
                    String type = item.getType();
                    String mediaUrl = item.getUrl();
                    if ("IMAGE".equalsIgnoreCase(type) && mediaUrl != null && !mediaUrl.isEmpty()) {
                        mediaUrls.add(mediaUrl);
                    } else if ("VIDEO".equalsIgnoreCase(type)) {
                        String preview = item.getThumbnailUrl() == null || item.getThumbnailUrl().isEmpty() ? mediaUrl : item.getThumbnailUrl();
                        if (preview != null && !preview.isEmpty()) {
                            mediaUrls.add(preview);
                        }
                    }
                    if (mediaUrls.size() >= 9) {
                        break;
                    }
                }
            }

            if (mediaUrls.isEmpty()) {
                binding.compositeGrid.setVisibility(View.GONE);
                binding.compositeGrid.removeAllViews();
                return;
            }

            binding.compositeGrid.setVisibility(View.VISIBLE);
            binding.compositeGrid.removeAllViews();

            Context context = binding.getRoot().getContext();
            int count = Math.min(mediaUrls.size(), 9);
            int cols = count == 1 ? 1 : (count == 2 || count == 4 ? 2 : 3);
            binding.compositeGrid.setColumnCount(cols);
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
                        .load(resolveMediaUrl(mediaUrls.get(i)))
                        .placeholder(R.drawable.bg_placeholder_card)
                        .error(R.drawable.bg_placeholder_card)
                        .override(sizePx, sizePx)
                        .into(imageView);
                binding.compositeGrid.addView(imageView);
            }
        }

        private void showFavoriteActions(FavoriteItem item, View anchorView) {
            Context ctx = binding.getRoot().getContext();
            PopupFavoriteActionsBinding popupBinding = PopupFavoriteActionsBinding.inflate(LayoutInflater.from(ctx));
            PopupWindow popupWindow = new PopupWindow(
                    popupBinding.getRoot(),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
            );
            popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popupWindow.setOutsideTouchable(true);

            popupBinding.actionCopy.setOnClickListener(v -> {
                popupWindow.dismiss();
                String textToCopy = item.getContent();
                if (textToCopy == null || textToCopy.isEmpty()) {
                    textToCopy = item.getFileName() != null ? item.getFileName() : "";
                }
                ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("favorite", textToCopy);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show();
            });

            popupBinding.actionDelete.setOnClickListener(v -> {
                popupWindow.dismiss();
                new AlertDialog.Builder(ctx)
                        .setTitle("删除收藏")
                        .setMessage("确定要删除该收藏吗？")
                        .setPositiveButton("删除", (dialog, which) -> listener.onRemove(item))
                        .setNegativeButton("取消", null)
                        .show();
            });

            anchorView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int popupWidth = popupBinding.getRoot().getMeasuredWidth();
            int popupHeight = popupBinding.getRoot().getMeasuredHeight();

            int[] location = new int[2];
            anchorView.getLocationOnScreen(location);
            int viewX = location[0];
            int viewY = location[1];
            int viewWidth = anchorView.getWidth();
            int viewHeight = anchorView.getHeight();

            float density = ctx.getResources().getDisplayMetrics().density;
            int gap = (int) (4 * density);

            int screenHeight = ctx.getResources().getDisplayMetrics().heightPixels;
            boolean showAbove = viewY > screenHeight / 2;

            int popupX = viewX + viewWidth / 2 - popupWidth / 2;
            int popupY = showAbove ? viewY - popupHeight - gap : viewY + viewHeight + gap;

            int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
            if (popupX < 0) popupX = 0;
            if (popupX + popupWidth > screenWidth) popupX = screenWidth - popupWidth;
            if (popupY < 0) popupY = 0;

            popupWindow.showAtLocation(binding.getRoot(), Gravity.NO_GRAVITY, popupX, popupY);
        }

        private void loadMediaThumbnail(String mediaUrl) {
            if (mediaUrl == null || mediaUrl.isEmpty()) {
                binding.mediaPreviewImage.setImageResource(R.drawable.bg_placeholder_card);
                return;
            }
            Context context = binding.getRoot().getContext();
            File cachedFile = MediaUrlResolver.resolveCachedFile(context, mediaUrl);
            if (cachedFile != null) {
                Glide.with(context)
                        .load(cachedFile)
                        .placeholder(R.drawable.bg_placeholder_card)
                        .error(R.drawable.bg_placeholder_card)
                        .fitCenter()
                        .listener(new RequestListener<android.graphics.drawable.Drawable>() {
                            @Override
                            public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e,
                                                        Object model,
                                                        Target<android.graphics.drawable.Drawable> target,
                                                        boolean isFirstResource) {
                                Glide.with(context)
                                        .load(resolveMediaUrl(mediaUrl))
                                        .placeholder(R.drawable.bg_placeholder_card)
                                        .error(R.drawable.bg_placeholder_card)
                                        .fitCenter()
                                        .into(binding.mediaPreviewImage);
                                return true;
                            }

                            @Override
                            public boolean onResourceReady(android.graphics.drawable.Drawable resource,
                                                           Object model,
                                                           Target<android.graphics.drawable.Drawable> target,
                                                           com.bumptech.glide.load.DataSource dataSource,
                                                           boolean isFirstResource) {
                                return false;
                            }
                        })
                        .into(binding.mediaPreviewImage);
                return;
            }
            Glide.with(context)
                    .load(resolveMediaUrl(mediaUrl))
                    .placeholder(R.drawable.bg_placeholder_card)
                    .error(R.drawable.bg_placeholder_card)
                    .fitCenter()
                    .into(binding.mediaPreviewImage);
        }

        private String resolveMediaUrl(String mediaUrl) {
            return MediaUrlResolver.resolve(mediaUrl);
        }

        private void openImagePreview(Context context, FavoriteItem item) {
            Intent intent = new Intent(context, ImageViewerActivity.class);
            intent.putExtra(ImageViewerActivity.EXTRA_MEDIA_URL, item.getMediaUrl());
            intent.putExtra(ImageViewerActivity.EXTRA_DISPLAY_NAME, item.getFileName());
            context.startActivity(intent);
        }

        private void openVideoPlayer(Context context, FavoriteItem item) {
            Intent intent = new Intent(context, VideoPlayerActivity.class);
            intent.putExtra(VideoPlayerActivity.EXTRA_MEDIA_URL, item.getMediaUrl());
            intent.putExtra(VideoPlayerActivity.EXTRA_DISPLAY_NAME, item.getFileName());
            context.startActivity(intent);
        }

        private void openFile(Context context, FavoriteItem item) {
            FileRepository fileRepository = FlashNoteApp.getInstance().getFileRepository();
            String objectName = item.getMediaUrl();
            if (objectName == null || objectName.isEmpty()) {
                return;
            }

            File cacheFile = new File(context.getCacheDir(), objectName.replace('/', '_'));
            if (cacheFile.exists() && cacheFile.length() > 0) {
                openCachedFile(context, item, cacheFile);
                return;
            }

            Toast.makeText(context, "正在下载文件...", Toast.LENGTH_SHORT).show();
            fileRepository.download(objectName, new FileRepository.FileCallback() {
                @Override
                public void onSuccess(String path) {
                    openCachedFile(context, item, new File(path));
                }

                @Override
                public void onError(String errorMsg, int code) {
                    Toast.makeText(context, "文件下载失败", Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void openCachedFile(Context context, FavoriteItem item, File file) {
            String fileName = item.getFileName() != null ? item.getFileName() : "file";
            String ext = getFileExtension(fileName);
            if ("jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext) || "gif".equals(ext)) {
                Intent intent = new Intent(context, ImageViewerActivity.class);
                intent.putExtra(ImageViewerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
                intent.putExtra(ImageViewerActivity.EXTRA_DISPLAY_NAME, fileName);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else {
                Intent intent = new Intent(context, FilePreviewActivity.class);
                intent.putExtra(FilePreviewActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
                intent.putExtra(FilePreviewActivity.EXTRA_FILE_NAME, fileName);
                intent.putExtra(FilePreviewActivity.EXTRA_DISPLAY_NAME, fileName);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        }

        private int resolveFileIcon(String fileName) {
            String ext = getFileExtension(fileName);
            switch (ext) {
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
            if (fileName == null || fileName.isEmpty()) {
                return "";
            }
            int dot = fileName.lastIndexOf('.');
            if (dot < 0 || dot == fileName.length() - 1) {
                return "";
            }
            return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
    }

    static class FavoriteDiffCallback extends DiffUtil.ItemCallback<FavoriteItem> {
        @Override
        public boolean areItemsTheSame(@NonNull FavoriteItem oldItem, @NonNull FavoriteItem newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull FavoriteItem oldItem, @NonNull FavoriteItem newItem) {
            return Objects.equals(oldItem.getContent(), newItem.getContent())
                    && Objects.equals(oldItem.getFlashNoteIcon(), newItem.getFlashNoteIcon())
                    && Objects.equals(oldItem.getMediaType(), newItem.getMediaType())
                    && Objects.equals(oldItem.getMediaUrl(), newItem.getMediaUrl())
                    && Objects.equals(oldItem.getFileName(), newItem.getFileName())
                    && Objects.equals(oldItem.getFileSize(), newItem.getFileSize())
                    && Objects.equals(oldItem.getMediaDuration(), newItem.getMediaDuration())
                    && Objects.equals(oldItem.getFavoritedAt(), newItem.getFavoritedAt())
                    && Objects.equals(oldItem.getMessageCreatedAt(), newItem.getMessageCreatedAt());
        }
    }
}
