package com.flashnote.java.ui.main;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.data.model.FriendRequest;
import com.flashnote.java.databinding.ItemFriendRequestBinding;

import java.util.ArrayList;
import java.util.List;

public class FriendRequestAdapter extends RecyclerView.Adapter<FriendRequestAdapter.RequestViewHolder> {
    public interface OnActionListener {
        void onAccept(FriendRequest request);
        void onReject(FriendRequest request);
    }

    private final List<FriendRequest> items = new ArrayList<>();
    private final OnActionListener listener;

    public FriendRequestAdapter(OnActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<FriendRequest> requests) {
        items.clear();
        if (requests != null) {
            items.addAll(requests);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFriendRequestBinding binding = ItemFriendRequestBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new RequestViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class RequestViewHolder extends RecyclerView.ViewHolder {
        private final ItemFriendRequestBinding binding;

        RequestViewHolder(ItemFriendRequestBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FriendRequest request) {
            String display = request.getNickname() != null && !request.getNickname().isBlank() ? request.getNickname() : request.getUsername();
            binding.nameText.setText(display == null ? "未命名用户" : display);
            binding.subTitleText.setText(request.getUsername() == null ? "" : "@" + request.getUsername());
            binding.acceptButton.setOnClickListener(v -> listener.onAccept(request));
            binding.rejectButton.setOnClickListener(v -> listener.onReject(request));
        }
    }
}
