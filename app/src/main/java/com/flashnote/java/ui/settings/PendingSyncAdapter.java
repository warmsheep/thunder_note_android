package com.flashnote.java.ui.settings;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.R;
import com.flashnote.java.data.model.PendingMessage;
import com.flashnote.java.databinding.ItemPendingMessageBinding;
import com.flashnote.java.data.repository.PendingMessageDispatcher;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PendingSyncAdapter extends RecyclerView.Adapter<PendingSyncAdapter.PendingSyncViewHolder> {
    private final List<PendingMessage> items = new ArrayList<>();

    public void submitList(List<PendingMessage> pendingMessages) {
        List<PendingMessage> newItems = pendingMessages == null ? new ArrayList<>() : new ArrayList<>(pendingMessages);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return items.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return items.get(oldItemPosition).getLocalId() == newItems.get(newItemPosition).getLocalId();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                PendingMessage oldItem = items.get(oldItemPosition);
                PendingMessage newItem = newItems.get(newItemPosition);
                return oldItem.getLocalId() == newItem.getLocalId()
                        && equalsNullable(oldItem.getStatus(), newItem.getStatus())
                        && equalsNullable(oldItem.getContent(), newItem.getContent())
                        && equalsNullable(oldItem.getMediaType(), newItem.getMediaType())
                        && equalsNullable(oldItem.getFileName(), newItem.getFileName())
                        && equalsNullable(oldItem.getErrorMessage(), newItem.getErrorMessage())
                        && oldItem.getAttemptCount() == newItem.getAttemptCount()
                        && oldItem.getCreatedAt() == newItem.getCreatedAt()
                        && equalsNullable(oldItem.getFlashNoteId(), newItem.getFlashNoteId())
                        && equalsNullable(oldItem.getPeerUserId(), newItem.getPeerUserId());
            }
        });
        items.clear();
        items.addAll(newItems);
        diffResult.dispatchUpdatesTo(this);
    }

    private boolean equalsNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    @NonNull
    @Override
    public PendingSyncViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPendingMessageBinding binding = ItemPendingMessageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PendingSyncViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PendingSyncViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class PendingSyncViewHolder extends RecyclerView.ViewHolder {
        private final ItemPendingMessageBinding binding;

        PendingSyncViewHolder(@NonNull ItemPendingMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull PendingMessage item) {
            binding.contentText.setText(resolveContentText(item));
            binding.targetText.setText(resolveTargetText(item));
            binding.statusText.setText(binding.getRoot().getContext().getString(
                    R.string.pending_sync_status,
                    resolveStatusLabel(item.getStatus())
            ));
            binding.statusText.setTextColor(resolveStatusColor(item.getStatus()));
            binding.timeText.setText(DateFormat.format("yyyy-MM-dd HH:mm", new Date(item.getCreatedAt())));

            if (item.getAttemptCount() > 0) {
                binding.attemptText.setVisibility(View.VISIBLE);
                binding.attemptText.setText(binding.getRoot().getContext().getString(
                        R.string.pending_sync_attempt_count,
                        item.getAttemptCount()
                ));
            } else {
                binding.attemptText.setVisibility(View.GONE);
            }

            if (item.getErrorMessage() != null && !item.getErrorMessage().trim().isEmpty()) {
                binding.errorText.setVisibility(View.VISIBLE);
                binding.errorText.setText(item.getErrorMessage());
            } else {
                binding.errorText.setVisibility(View.GONE);
            }
        }

        private CharSequence resolveContentText(@NonNull PendingMessage item) {
            if (item.getContent() != null && !item.getContent().trim().isEmpty()) {
                return item.getContent().trim();
            }
            if (item.getFileName() != null && !item.getFileName().trim().isEmpty()) {
                return item.getFileName().trim();
            }
            String mediaType = item.getMediaType();
            if (mediaType == null || mediaType.trim().isEmpty()) {
                return binding.getRoot().getContext().getString(R.string.pending_sync_content_text);
            }
            String normalized = mediaType.trim().toUpperCase(Locale.ROOT);
            if ("IMAGE".equals(normalized)) {
                return binding.getRoot().getContext().getString(R.string.pending_sync_content_image);
            }
            if ("VIDEO".equals(normalized)) {
                return binding.getRoot().getContext().getString(R.string.pending_sync_content_video);
            }
            if ("FILE".equals(normalized)) {
                return binding.getRoot().getContext().getString(R.string.pending_sync_content_file);
            }
            if ("VOICE".equals(normalized)) {
                return binding.getRoot().getContext().getString(R.string.pending_sync_content_voice);
            }
            return binding.getRoot().getContext().getString(R.string.pending_sync_content_media);
        }

        private CharSequence resolveTargetText(@NonNull PendingMessage item) {
            if (item.getPeerUserId() != null && item.getPeerUserId() > 0L) {
                return binding.getRoot().getContext().getString(R.string.pending_sync_target_contact, item.getPeerUserId());
            }
            if (item.getFlashNoteId() != null) {
                long flashNoteId = item.getFlashNoteId();
                if (flashNoteId == -1L) {
                    return binding.getRoot().getContext().getString(R.string.pending_sync_target_inbox);
                }
                if (flashNoteId > 0L) {
                    return binding.getRoot().getContext().getString(R.string.pending_sync_target_flashnote, flashNoteId);
                }
            }
            return binding.getRoot().getContext().getString(R.string.pending_sync_target_unknown);
        }

        private String resolveStatusLabel(String status) {
            if (PendingMessageDispatcher.STATUS_QUEUED.equals(status)) {
                return binding.getRoot().getContext().getString(R.string.pending_sync_status_queued);
            }
            if (PendingMessageDispatcher.STATUS_PROCESSING.equals(status)) {
                return binding.getRoot().getContext().getString(R.string.pending_sync_status_processing);
            }
            if (PendingMessageDispatcher.STATUS_UPLOADING.equals(status)) {
                return binding.getRoot().getContext().getString(R.string.pending_sync_status_uploading);
            }
            if (PendingMessageDispatcher.STATUS_UPLOADED.equals(status)) {
                return binding.getRoot().getContext().getString(R.string.pending_sync_status_uploaded);
            }
            if (PendingMessageDispatcher.STATUS_SENDING.equals(status)) {
                return binding.getRoot().getContext().getString(R.string.pending_sync_status_sending);
            }
            if (PendingMessageDispatcher.STATUS_FAILED.equals(status)) {
                return binding.getRoot().getContext().getString(R.string.pending_sync_status_failed);
            }
            return status == null
                    ? binding.getRoot().getContext().getString(R.string.pending_sync_status_unknown)
                    : status;
        }

        private int resolveStatusColor(String status) {
            if (PendingMessageDispatcher.STATUS_FAILED.equals(status)) {
                return binding.getRoot().getResources().getColor(android.R.color.holo_red_dark);
            }
            return binding.getRoot().getResources().getColor(android.R.color.holo_orange_dark);
        }
    }
}
