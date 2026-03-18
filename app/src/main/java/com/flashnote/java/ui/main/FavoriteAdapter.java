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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.data.model.FavoriteItem;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.databinding.ItemFavoriteBinding;
import com.flashnote.java.databinding.PopupFavoriteActionsBinding;
import com.flashnote.java.ui.media.FilePreviewActivity;
import com.flashnote.java.ui.media.ImageViewerActivity;
import com.flashnote.java.ui.media.MediaUrlResolver;
import com.flashnote.java.ui.media.VideoPlayerActivity;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
            binding.mediaPreviewContainer.setVisibility(View.GONE);
            binding.mediaPlayIcon.setVisibility(View.GONE);
            binding.fileInfoContainer.setVisibility(View.GONE);
            binding.voiceInfoContainer.setVisibility(View.GONE);

            binding.contentText.setOnClickListener(null);
            binding.mediaPreviewContainer.setOnClickListener(null);
            binding.fileInfoContainer.setOnClickListener(null);
            binding.voiceInfoContainer.setOnClickListener(null);
            binding.contentText.setOnLongClickListener(null);
            binding.mediaPreviewContainer.setOnLongClickListener(null);
            binding.fileInfoContainer.setOnLongClickListener(null);
            binding.voiceInfoContainer.setOnLongClickListener(null);

            String mediaType = item.getMediaType();
            if (mediaType == null || mediaType.isEmpty() || "TEXT".equalsIgnoreCase(mediaType)) {
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

            binding.getRoot().setOnClickListener(v -> listener.onOpen(item));
            binding.getRoot().setOnLongClickListener(v -> {
                showFavoriteActions(item, v);
                return true;
            });
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
            String token = FlashNoteApp.getInstance().getTokenManager().getAccessToken();
            String requestUrl = MediaUrlResolver.resolve(mediaUrl);
            GlideUrl glideUrl;
            if (token != null && !token.isEmpty()) {
                glideUrl = new GlideUrl(requestUrl,
                        new LazyHeaders.Builder()
                                .addHeader("Authorization", "Bearer " + token)
                                .build());
            } else {
                glideUrl = new GlideUrl(requestUrl);
            }
            Glide.with(binding.getRoot().getContext())
                    .load(glideUrl)
                    .placeholder(R.drawable.bg_placeholder_card)
                    .error(R.drawable.bg_placeholder_card)
                    .fitCenter()
                    .into(binding.mediaPreviewImage);
        }

        private void openImagePreview(Context context, FavoriteItem item) {
            Intent intent = new Intent(context, ImageViewerActivity.class);
            intent.putExtra(ImageViewerActivity.EXTRA_MEDIA_URL, item.getMediaUrl());
            context.startActivity(intent);
        }

        private void openVideoPlayer(Context context, FavoriteItem item) {
            Intent intent = new Intent(context, VideoPlayerActivity.class);
            intent.putExtra(VideoPlayerActivity.EXTRA_MEDIA_URL, item.getMediaUrl());
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
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else {
                Intent intent = new Intent(context, FilePreviewActivity.class);
                intent.putExtra(FilePreviewActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
                intent.putExtra(FilePreviewActivity.EXTRA_FILE_NAME, fileName);
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
