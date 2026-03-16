package com.flashnote.java.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.data.model.Message;
import com.flashnote.java.databinding.ItemChatMessageBinding;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    public interface OnMessageLongClickListener {
        void onLongClick(Message message, View clickedView);
    }

    private static final long TEN_MINUTES_IN_MILLIS = 10 * 60 * 1000;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
        holder.bind(items.get(position), position);
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

        void bind(Message message, int position) {
            boolean showTimeDivider = false;
            if (position > 0 && message.getCreatedAt() != null) {
                Message prevMessage = items.get(position - 1);
                if (prevMessage.getCreatedAt() != null) {
                    LocalDateTime currentTime = message.getCreatedAt();
                    LocalDateTime prevTime = prevMessage.getCreatedAt();
                    Duration duration = Duration.between(prevTime, currentTime);
                    if (duration.toMillis() >= TEN_MINUTES_IN_MILLIS) {
                        showTimeDivider = true;
                    }
                }
            }

            if (showTimeDivider && message.getCreatedAt() != null) {
                binding.timeDivider.setVisibility(View.VISIBLE);
                binding.timeDivider.setText(message.getCreatedAt().format(TIME_FORMATTER));
            } else {
                binding.timeDivider.setVisibility(View.GONE);
            }

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
                    listener.onLongClick(message, v);
                }
                return true;
            });
        }
    }
}
