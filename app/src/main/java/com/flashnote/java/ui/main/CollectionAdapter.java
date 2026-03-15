package com.flashnote.java.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.data.model.Collection;
import com.flashnote.java.databinding.ItemCollectionBinding;

public class CollectionAdapter extends ListAdapter<Collection, CollectionAdapter.CollectionViewHolder> {
    private final OnCollectionClickListener listener;

    public interface OnCollectionClickListener {
        void onEdit(Collection collection);
        void onDelete(Collection collection);
    }

    public CollectionAdapter(OnCollectionClickListener listener) {
        super(new CollectionDiffCallback());
        this.listener = listener;
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
        holder.bind(getItem(position));
    }

    class CollectionViewHolder extends RecyclerView.ViewHolder {
        private final ItemCollectionBinding binding;

        CollectionViewHolder(ItemCollectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Collection collection) {
            binding.nameText.setText(collection.getName());
            
            String description = collection.getDescription();
            if (description != null && !description.isEmpty()) {
                binding.descriptionText.setText(description);
                binding.descriptionText.setVisibility(View.VISIBLE);
            } else {
                binding.descriptionText.setVisibility(View.GONE);
            }

            binding.editButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEdit(collection);
                }
            });

            binding.deleteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDelete(collection);
                }
            });
        }
    }

    static class CollectionDiffCallback extends DiffUtil.ItemCallback<Collection> {
        @Override
        public boolean areItemsTheSame(@NonNull Collection oldItem, @NonNull Collection newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Collection oldItem, @NonNull Collection newItem) {
            return oldItem.getName().equals(newItem.getName()) &&
                   (oldItem.getDescription() == null ? newItem.getDescription() == null : 
                    oldItem.getDescription().equals(newItem.getDescription()));
        }
    }
}
