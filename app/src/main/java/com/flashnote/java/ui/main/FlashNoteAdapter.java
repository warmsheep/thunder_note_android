package com.flashnote.java.ui.main;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.R;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.databinding.ItemFlashNoteBinding;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FlashNoteAdapter extends RecyclerView.Adapter<FlashNoteAdapter.FlashNoteViewHolder> {
    public interface OnItemActionListener {
        void onOpenChat(FlashNote flashNote);

        void onEdit(FlashNote flashNote);

        void onLongPress(FlashNote flashNote, android.view.View anchor);

        void onDelete(FlashNote flashNote);
    }

    private final List<FlashNote> items = new ArrayList<>();
    private final OnItemActionListener listener;
    private final String[] emojis = new String[]{"💼", "📚", "🌅", "📋", "💡", "✈️", "📝", "🍀", "🎯", "📷"};
    private Long pendingDeleteNoteId;

    public FlashNoteAdapter(OnItemActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<FlashNote> notes) {
        List<FlashNote> newItems = notes == null ? new ArrayList<>() : new ArrayList<>(notes);
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
                FlashNote oldItem = items.get(oldItemPosition);
                FlashNote newItem = newItems.get(newItemPosition);
                Long oldId = oldItem.getId();
                Long newId = newItem.getId();
                return oldId != null && oldId.equals(newId);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return hasSameContent(items.get(oldItemPosition), newItems.get(newItemPosition));
            }
        });
        items.clear();
        items.addAll(newItems);
        diffResult.dispatchUpdatesTo(this);
    }

    public void setPendingDeleteNoteId(Long noteId) {
        Long previous = pendingDeleteNoteId;
        this.pendingDeleteNoteId = noteId;
        notifyPendingDeleteChanged(previous);
        notifyPendingDeleteChanged(noteId);
    }

    public void clearPendingDelete() {
        if (pendingDeleteNoteId == null) {
            return;
        }
        Long previous = pendingDeleteNoteId;
        pendingDeleteNoteId = null;
        notifyPendingDeleteChanged(previous);
    }

    @NonNull
    @Override
    public FlashNoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFlashNoteBinding binding = ItemFlashNoteBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new FlashNoteViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull FlashNoteViewHolder holder, int position) {
        FlashNote item = items.get(position);
        holder.bind(item, resolveIcon(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String resolveIcon(FlashNote item) {
        String icon = item.getIcon();
        if (icon != null && !icon.trim().isEmpty()) {
            return icon;
        }
        Long id = item.getId();
        int index = id == null ? 0 : (int) (Math.abs(id) % emojis.length);
        return emojis[index];
    }

    private String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        LocalDateTime yesterday = today.minusDays(1);
        
        if (dateTime.isAfter(today)) {
            return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        } else if (dateTime.isAfter(yesterday)) {
            return "昨天";
        } else if (dateTime.getYear() == now.getYear()) {
            return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("M月d日"));
        } else {
            return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/M/d"));
        }
    }

    private void notifyPendingDeleteChanged(Long noteId) {
        if (noteId == null) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            Long itemId = items.get(i).getId();
            if (itemId != null && itemId.equals(noteId)) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    private boolean hasSameContent(@NonNull FlashNote oldItem, @NonNull FlashNote newItem) {
        return equalsNullable(oldItem.getTitle(), newItem.getTitle())
                && equalsNullable(oldItem.getIcon(), newItem.getIcon())
                && equalsNullable(oldItem.getTags(), newItem.getTags())
                && equalsNullable(oldItem.getLatestMessage(), newItem.getLatestMessage())
                && equalsNullable(oldItem.getContent(), newItem.getContent())
                && equalsNullable(oldItem.getPinned(), newItem.getPinned())
                && equalsNullable(oldItem.getHidden(), newItem.getHidden())
                && equalsNullable(oldItem.getInbox(), newItem.getInbox())
                && equalsNullable(oldItem.getUpdatedAt(), newItem.getUpdatedAt())
                && equalsNullable(oldItem.getCreatedAt(), newItem.getCreatedAt());
    }

    private boolean equalsNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    class FlashNoteViewHolder extends RecyclerView.ViewHolder {
        private final ItemFlashNoteBinding binding;

        FlashNoteViewHolder(ItemFlashNoteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FlashNote item, String emoji) {
            binding.noteRoot.setBackgroundColor(binding.getRoot().getContext().getColor(
                    Boolean.TRUE.equals(item.getPinned()) ? R.color.flashnote_pinned_bg : R.color.surface));
            binding.emojiText.setText(emoji);
            binding.titleText.setText(buildTitleLine(item));
            String content = firstNonBlank(item.getLatestMessage(), item.getContent());
            if (content != null && !content.trim().isEmpty()) {
                binding.summaryText.setText(content);
            } else {
                binding.summaryText.setText("暂无消息");
            }

            LocalDateTime time = item.getUpdatedAt() != null ? item.getUpdatedAt() : item.getCreatedAt();
            binding.timeText.setText(formatTime(time));

            boolean pendingDelete = item.getId() != null && item.getId().equals(pendingDeleteNoteId);
            binding.deleteOverlay.setVisibility(pendingDelete ? android.view.View.VISIBLE : android.view.View.GONE);
            binding.deleteOverlay.setOnClickListener(v -> listener.onDelete(item));

            binding.getRoot().setOnClickListener(v -> listener.onOpenChat(item));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onLongPress(item, v);
                return true;
            });
        }

        private CharSequence buildTitleLine(FlashNote item) {
            String title = normalize(item.getTitle());
            if (title == null) {
                title = "未命名闪记";
            }
            if (Boolean.TRUE.equals(item.getPinned())) {
                title = "📌 " + title;
            }
            String collection = normalize(item.getTags());
            if (collection == null) {
                collection = "未分类";
            }
            String suffix = String.format(Locale.getDefault(), "  #%s", collection);
            SpannableStringBuilder builder = new SpannableStringBuilder(title).append(suffix);
            int start = builder.length() - suffix.length();
            builder.setSpan(
                    new ForegroundColorSpan(binding.getRoot().getContext().getColor(R.color.text_secondary)),
                    start,
                    builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return builder;
        }

        private String normalize(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }

        private String firstNonBlank(String primary, String fallback) {
            String normalizedPrimary = normalize(primary);
            if (normalizedPrimary != null) {
                return normalizedPrimary;
            }
            return normalize(fallback);
        }
    }
}
