package com.flashnote.java.ui.main;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.flashnote.java.R;
import com.flashnote.java.data.model.ContactUser;
import com.flashnote.java.databinding.ItemContactBinding;
import com.flashnote.java.ui.media.MediaUrlResolver;

import java.util.ArrayList;
import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {
    public interface OnContactClickListener {
        void onOpenChat(ContactUser contact);
    }

    public interface OnDeleteClickListener {
        void onDelete(ContactUser contact, String action);
    }

    private final List<ContactUser> items = new ArrayList<>();
    private final OnContactClickListener listener;
    private Long pendingDeleteContactId = null;
    private String pendingDeleteAction = null;
    private OnDeleteClickListener deleteListener;

    public ContactAdapter(OnContactClickListener listener) {
        this.listener = listener;
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.deleteListener = listener;
    }

    public void clearPendingDelete() {
        Long prev = pendingDeleteContactId;
        pendingDeleteContactId = null;
        pendingDeleteAction = null;
        if (prev != null) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getUserId() != null && items.get(i).getUserId().equals(prev)) {
                    notifyItemChanged(i);
                    break;
                }
            }
        }
    }

    public void setPendingDeleteContactId(Long contactId, String action) {
        Long prev = pendingDeleteContactId;
        pendingDeleteContactId = contactId;
        pendingDeleteAction = action;
        if (prev != null && !prev.equals(contactId)) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getUserId() != null && items.get(i).getUserId().equals(prev)) {
                    notifyItemChanged(i);
                    break;
                }
            }
        }
        if (contactId != null) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getUserId() != null && items.get(i).getUserId().equals(contactId)) {
                    notifyItemChanged(i);
                    break;
                }
            }
        }
    }

    public Long getPendingDeleteContactId() {
        return pendingDeleteContactId;
    }

    public String getPendingDeleteAction() {
        return pendingDeleteAction;
    }

    public void submitList(List<ContactUser> contacts) {
        items.clear();
        if (contacts != null) {
            items.addAll(contacts);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemContactBinding binding = ItemContactBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ContactViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String resolveMediaUrl(String mediaPathOrUrl) {
        return MediaUrlResolver.resolve(mediaPathOrUrl);
    }

    class ContactViewHolder extends RecyclerView.ViewHolder {
        private final ItemContactBinding binding;

        ContactViewHolder(ItemContactBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ContactUser contact) {
            String nickname = normalize(contact.getNickname());
            String username = normalize(contact.getUsername());
            String relationStatus = normalize(contact.getRelationStatus());
            String latestMessage = normalize(contact.getLatestMessage());
            binding.nameText.setText(nickname == null ? (username == null ? "未命名用户" : username) : nickname);
            binding.metaText.setText("PENDING_SENT".equals(relationStatus)
                    ? "等待对方同意"
                    : (username == null ? "" : "@" + username));
            binding.subTitleText.setText(resolveLatestLine(relationStatus, latestMessage));

            String avatar = normalize(contact.getAvatar());
            Context ctx = binding.getRoot().getContext();
            if (avatar != null && !avatar.isEmpty() && (avatar.startsWith("http") || avatar.contains("/"))) {
                binding.avatarText.setVisibility(View.GONE);
                binding.avatarImage.setVisibility(View.VISIBLE);
                Glide.with(ctx)
                        .load(resolveMediaUrl(avatar))
                        .placeholder(R.drawable.bg_avatar_circle)
                        .error(R.drawable.bg_avatar_circle)
                        .circleCrop()
                        .into(binding.avatarImage);
            } else {
                binding.avatarImage.setVisibility(View.GONE);
                binding.avatarText.setVisibility(View.VISIBLE);
                if (avatar == null || avatar.isEmpty()) {
                    binding.avatarText.setText(extractInitial(nickname, username));
                } else {
                    binding.avatarText.setText(avatar);
                }
            }

            binding.getRoot().setOnClickListener(v -> listener.onOpenChat(contact));

            boolean pendingDelete = contact.getUserId() != null && contact.getUserId().equals(pendingDeleteContactId);
            binding.deleteOverlay.setVisibility(pendingDelete ? View.VISIBLE : View.GONE);
            if (pendingDelete) {
                String action = pendingDeleteAction;
                if ("PENDING_SENT".equals(action)) {
                    binding.deleteOverlay.setText("取消");
                } else if ("PENDING_RECEIVED".equals(action)) {
                    binding.deleteOverlay.setText("删除");
                } else {
                    binding.deleteOverlay.setText("删除");
                }
                binding.deleteOverlay.setOnClickListener(v -> {
                    if (deleteListener != null) {
                        deleteListener.onDelete(contact, action);
                    }
                });
            } else {
                binding.deleteOverlay.setOnClickListener(null);
            }
        }

        private String normalize(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }

        private String extractInitial(String nickname, String username) {
            String source = nickname != null ? nickname : username;
            if (source == null || source.isEmpty()) {
                return "🙂";
            }
            return String.valueOf(source.charAt(0));
        }

        private String resolveLatestLine(String relationStatus, String latestMessage) {
            if (latestMessage != null) {
                return latestMessage;
            }
            if ("PENDING_SENT".equals(relationStatus)) {
                return "已发送好友申请";
            }
            return "暂无聊天记录";
        }
    }
}
