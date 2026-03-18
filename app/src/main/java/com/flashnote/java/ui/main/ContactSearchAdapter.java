package com.flashnote.java.ui.main;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
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
        items.clear();
        if (users != null) {
            items.addAll(users);
        }
        notifyDataSetChanged();
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
