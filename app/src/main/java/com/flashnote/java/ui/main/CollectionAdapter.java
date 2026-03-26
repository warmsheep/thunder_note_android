package com.flashnote.java.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
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
        List<CollectionGroup> newItems = groups == null ? new ArrayList<>() : new ArrayList<>(groups);
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
                return buildGroupKey(items.get(oldItemPosition)).equals(buildGroupKey(newItems.get(newItemPosition)));
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

    private String buildGroupKey(@NonNull CollectionGroup group) {
        Collection source = group.getSource();
        if (source != null && source.getId() != null) {
            return "id:" + source.getId();
        }
        return "name:" + group.getName();
    }

    private boolean hasSameContent(@NonNull CollectionGroup oldItem, @NonNull CollectionGroup newItem) {
        if (!buildGroupKey(oldItem).equals(buildGroupKey(newItem))) {
            return false;
        }
        List<FlashNote> oldNotes = oldItem.getNotes();
        List<FlashNote> newNotes = newItem.getNotes();
        if (oldNotes.size() != newNotes.size()) {
            return false;
        }
        for (int i = 0; i < oldNotes.size(); i++) {
            FlashNote oldNote = oldNotes.get(i);
            FlashNote newNote = newNotes.get(i);
            Long oldId = oldNote.getId();
            Long newId = newNote.getId();
            if (oldId == null ? newId != null : !oldId.equals(newId)) {
                return false;
            }
            if (!equalsNullable(oldNote.getTitle(), newNote.getTitle())
                    || !equalsNullable(oldNote.getContent(), newNote.getContent())
                    || !equalsNullable(oldNote.getIcon(), newNote.getIcon())) {
                return false;
            }
        }
        return true;
    }

    private boolean equalsNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
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
            binding.editButton.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                CollectionGroup group = items.get(position);
                listener.onEditCollection(group);
            });
            binding.deleteButton.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                CollectionGroup group = items.get(position);
                listener.onDeleteCollection(group);
            });
        }

        void bind(CollectionGroup group) {
            binding.nameText.setText(group.getName());
            binding.countText.setText(String.valueOf(group.getNotes().size()));
            binding.notesContainer.removeAllViews();
            boolean editable = !"未分类".equals(group.getName());
            binding.editButton.setVisibility(editable ? View.VISIBLE : View.GONE);
            binding.deleteButton.setVisibility(editable ? View.VISIBLE : View.GONE);
            binding.editButton.setEnabled(editable);
            binding.deleteButton.setEnabled(editable);

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
