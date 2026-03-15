package com.flashnote.java.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.data.model.Message;
import com.flashnote.java.databinding.ItemChatMessageBinding;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    public interface OnMessageLongClickListener {
        void onFavorite(Message message);
    }

    private final List<Message> items = new ArrayList<>();
    private final OnMessageLongClickListener listener;

    public MessageAdapter(OnMessageLongClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Message> messages) {
        items.clear();
        if (messages != null) {
            items.addAll(messages);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChatMessageBinding binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new MessageViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class MessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatMessageBinding binding;

        MessageViewHolder(ItemChatMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Message message) {
            boolean mine = !"assistant".equals(message.getRole()) && !"ai".equals(message.getRole());
            binding.avatarText.setText(mine ? "😊" : "🤖");
            binding.messageText.setText(message.getContent());
            binding.bubbleCard.setCardBackgroundColor(binding.getRoot().getContext().getColor(mine ? com.flashnote.java.R.color.chat_mine : com.flashnote.java.R.color.surface));

            binding.leftContainer.setVisibility(mine ? View.GONE : View.VISIBLE);
            binding.rightContainer.setVisibility(mine ? View.VISIBLE : View.GONE);
            if (mine) {
                binding.rightMessageText.setText(message.getContent());
                binding.rightAvatarText.setText("😊");
            } else {
                binding.messageText.setText(message.getContent());
                binding.avatarText.setText("🤖");
            }

            binding.getRoot().setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onFavorite(message);
                }
                return true;
            });
        }
    }
}
