package com.flashnote.java.ui.main;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.databinding.ItemFlashNoteBinding;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class FlashNoteAdapter extends RecyclerView.Adapter<FlashNoteAdapter.FlashNoteViewHolder> {
    public interface OnItemActionListener {
        void onOpenChat(FlashNote flashNote);

        void onEdit(FlashNote flashNote);

        void onDelete(FlashNote flashNote);
    }

    private final List<FlashNote> items = new ArrayList<>();
    private final OnItemActionListener listener;
    private final String[] emojis = new String[]{"💼", "📚", "🌅", "📋", "💡", "✈️", "📝", "🍀", "🎯", "📷"};

    public FlashNoteAdapter(OnItemActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<FlashNote> notes) {
        items.clear();
        if (notes != null) {
            items.addAll(notes);
        }
        notifyDataSetChanged();
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

    class FlashNoteViewHolder extends RecyclerView.ViewHolder {
        private final ItemFlashNoteBinding binding;

        FlashNoteViewHolder(ItemFlashNoteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FlashNote item, String emoji) {
            binding.emojiText.setText(emoji);
            binding.titleText.setText(item.getTitle());
            String content = item.getContent();
            String collectionName = item.getTags();
            if (content != null && !content.trim().isEmpty()) {
                binding.summaryText.setText(content);
            } else if (collectionName != null && !collectionName.trim().isEmpty()) {
                binding.summaryText.setText("合集 · " + collectionName.trim());
            } else {
                binding.summaryText.setText("点击进入继续记录");
            }
            
            LocalDateTime time = item.getUpdatedAt() != null ? item.getUpdatedAt() : item.getCreatedAt();
            binding.timeText.setText(formatTime(time));
            
            binding.getRoot().setOnClickListener(v -> listener.onOpenChat(item));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onEdit(item);
                return true;
            });
        }
    }
}
