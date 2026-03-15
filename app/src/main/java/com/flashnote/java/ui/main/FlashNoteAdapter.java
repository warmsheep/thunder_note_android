package com.flashnote.java.ui.main;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.databinding.ItemFlashNoteBinding;

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
        holder.bind(item, emojis[(int) (Math.abs(item.getId()) % emojis.length)]);
    }

    @Override
    public int getItemCount() {
        return items.size();
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
            binding.summaryText.setText(content != null ? content : "");
            binding.getRoot().setOnClickListener(v -> listener.onOpenChat(item));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onEdit(item);
                return true;
            });
        }
    }
}
