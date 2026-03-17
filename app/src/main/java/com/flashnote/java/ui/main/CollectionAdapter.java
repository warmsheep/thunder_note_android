package com.flashnote.java.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.data.model.Collection;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.databinding.ItemCollectionBinding;
import com.flashnote.java.databinding.ItemCollectionNoteBinding;

import java.util.ArrayList;
import java.util.List;

public class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.CollectionViewHolder> {
    public interface OnFlashNoteClickListener {
        void onOpenFlashNote(FlashNote flashNote);

        void onEditCollection(CollectionGroup group);

        void onDeleteCollection(CollectionGroup group);
    }

    public static class CollectionGroup {
        private final Collection source;
        private final String name;
        private final List<FlashNote> notes;

        public CollectionGroup(Collection source, String name, List<FlashNote> notes) {
            this.source = source;
            this.name = name;
            this.notes = notes;
        }

        public Collection getSource() {
            return source;
        }

        public String getName() {
            return name;
        }

        public List<FlashNote> getNotes() {
            return notes;
        }
    }

    private final List<CollectionGroup> items = new ArrayList<>();
    private final OnFlashNoteClickListener listener;
    private final String[] fallbackIcons = new String[]{"💼", "📚", "🌅", "📋", "💡", "✈️", "📝", "🍀", "🎯", "📷"};

    public CollectionAdapter(OnFlashNoteClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<CollectionGroup> groups) {
        items.clear();
        if (groups != null) {
            items.addAll(groups);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CollectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCollectionBinding binding = ItemCollectionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new CollectionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CollectionViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String resolveIcon(FlashNote note) {
        String icon = note.getIcon();
        if (icon != null && !icon.trim().isEmpty()) {
            return icon;
        }
        Long id = note.getId();
        int index = id == null ? 0 : (int) (Math.abs(id) % fallbackIcons.length);
        return fallbackIcons[index];
    }

    class CollectionViewHolder extends RecyclerView.ViewHolder {
        private final ItemCollectionBinding binding;

        CollectionViewHolder(ItemCollectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(CollectionGroup group) {
            binding.nameText.setText(group.getName());
            binding.countText.setText(String.valueOf(group.getNotes().size()));
            binding.notesContainer.removeAllViews();
            binding.editButton.setVisibility(View.VISIBLE);
            binding.deleteButton.setVisibility(View.VISIBLE);
            binding.editButton.setOnClickListener(v -> listener.onEditCollection(group));
            binding.deleteButton.setOnClickListener(v -> listener.onDeleteCollection(group));

            if (group.getNotes().isEmpty()) {
                TextView emptyText = new TextView(binding.getRoot().getContext());
                emptyText.setText("暂无闪记");
                emptyText.setTextSize(14f);
                emptyText.setTextColor(binding.getRoot().getResources().getColor(android.R.color.darker_gray));
                binding.notesContainer.addView(emptyText);
                return;
            }

            LayoutInflater inflater = LayoutInflater.from(binding.getRoot().getContext());
            for (FlashNote note : group.getNotes()) {
                ItemCollectionNoteBinding noteBinding = ItemCollectionNoteBinding.inflate(inflater, binding.notesContainer, false);
                noteBinding.iconText.setText(resolveIcon(note));
                noteBinding.nameText.setText(note.getTitle());
                String summary = note.getContent();
                if (summary == null || summary.trim().isEmpty()) {
                    noteBinding.summaryText.setText("点击进入继续记录");
                } else {
                    noteBinding.summaryText.setText(summary);
                }
                noteBinding.getRoot().setOnClickListener(v -> listener.onOpenFlashNote(note));
                binding.notesContainer.addView(noteBinding.getRoot());
            }
        }
    }
}
