package com.flashnote.java.ui.main;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.data.model.ContactSearchUser;
import com.flashnote.java.databinding.ItemContactSearchBinding;

import java.util.ArrayList;
import java.util.List;

public class ContactSearchAdapter extends RecyclerView.Adapter<ContactSearchAdapter.SearchViewHolder> {
    public interface OnAddClickListener {
        void onAdd(ContactSearchUser user);
    }

    private final List<ContactSearchUser> items = new ArrayList<>();
    private final OnAddClickListener listener;

    public ContactSearchAdapter(OnAddClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<ContactSearchUser> users) {
        List<ContactSearchUser> newItems = users == null ? new ArrayList<>() : new ArrayList<>(users);
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
                ContactSearchUser oldItem = items.get(oldItemPosition);
                ContactSearchUser newItem = newItems.get(newItemPosition);
                Long oldId = oldItem.getUserId();
                Long newId = newItem.getUserId();
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

    private boolean hasSameContent(@NonNull ContactSearchUser oldItem, @NonNull ContactSearchUser newItem) {
        return equalsNullable(oldItem.getNickname(), newItem.getNickname())
                && equalsNullable(oldItem.getUsername(), newItem.getUsername())
                && equalsNullable(oldItem.getRelationStatus(), newItem.getRelationStatus());
    }

    private boolean equalsNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    @NonNull
    @Override
    public SearchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemContactSearchBinding binding = ItemContactSearchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new SearchViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class SearchViewHolder extends RecyclerView.ViewHolder {
        private final ItemContactSearchBinding binding;

        SearchViewHolder(ItemContactSearchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ContactSearchUser user) {
            String display = user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : user.getUsername();
            binding.nameText.setText(display == null ? "未命名用户" : display);
            binding.subTitleText.setText(user.getUsername() == null ? "" : "@" + user.getUsername());
            String status = user.getRelationStatus();
            if ("FRIEND".equals(status)) {
                binding.addButton.setEnabled(false);
                binding.addButton.setText("已添加");
            } else if ("PENDING_SENT".equals(status)) {
                binding.addButton.setEnabled(false);
                binding.addButton.setText("等待同意");
            } else if ("PENDING_RECEIVED".equals(status)) {
                binding.addButton.setEnabled(false);
                binding.addButton.setText("待你处理");
            } else {
                binding.addButton.setEnabled(true);
                binding.addButton.setText("添加好友");
                binding.addButton.setOnClickListener(v -> listener.onAdd(user));
            }
        }
    }
}
