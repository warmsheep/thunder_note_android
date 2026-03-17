package com.flashnote.java.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.data.model.FavoriteItem;
import com.flashnote.java.databinding.ItemFavoriteBinding;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
            binding.titleText.setText(item.getFlashNoteTitle() == null ? "已收藏消息" : item.getFlashNoteTitle());
            binding.contentText.setText(item.getContent());
            
            String icon = item.getFlashNoteIcon();
            binding.iconText.setText(icon != null && !icon.isEmpty() ? icon : "⚡");
            
            LocalDateTime displayTime = item.getFavoritedAt() != null ? item.getFavoritedAt() : item.getMessageCreatedAt();
            if (displayTime != null) {
                binding.timeText.setText(displayTime.format(TIME_FORMATTER));
                binding.timeText.setVisibility(View.VISIBLE);
            } else {
                binding.timeText.setVisibility(View.GONE);
            }
            
            binding.getRoot().setOnClickListener(v -> listener.onOpen(item));
            binding.removeButton.setOnClickListener(v -> listener.onRemove(item));
        }
    }

    static class FavoriteDiffCallback extends DiffUtil.ItemCallback<FavoriteItem> {
        @Override
        public boolean areItemsTheSame(@NonNull FavoriteItem oldItem, @NonNull FavoriteItem newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull FavoriteItem oldItem, @NonNull FavoriteItem newItem) {
            return oldItem.getContent().equals(newItem.getContent());
        }
    }
}
