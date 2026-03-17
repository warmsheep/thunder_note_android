package com.flashnote.java.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.R;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.model.FlashNoteSearchResult;
import com.flashnote.java.data.model.MatchedMessageInfo;
import com.flashnote.java.databinding.ItemSearchResultBinding;

import java.util.ArrayList;
import java.util.List;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.SearchResultViewHolder> {
    
    public interface OnSearchResultClickListener {
        void onResultClick(FlashNote flashNote, Long messageId);
    }
    
    private final List<SearchResultItem> items = new ArrayList<>();
    private final OnSearchResultClickListener listener;
    private final String[] emojis = new String[]{"💼", "📚", "🌅", "📋", "💡", "✈️", "📝", "🍀", "🎯", "📷"};
    
    public static class SearchResultItem {
        public final FlashNote flashNote;
        public final MatchedMessageInfo matchedMessage;
        
        public SearchResultItem(FlashNote flashNote, MatchedMessageInfo matchedMessage) {
            this.flashNote = flashNote;
            this.matchedMessage = matchedMessage;
        }
    }
    
    public SearchResultAdapter(OnSearchResultClickListener listener) {
        this.listener = listener;
    }
    
    public void submitList(List<FlashNoteSearchResult> results) {
        items.clear();
        if (results != null) {
            for (FlashNoteSearchResult result : results) {
                FlashNote flashNote = result.getFlashNote();
                List<MatchedMessageInfo> matchedMessages = result.getMatchedMessages();
                if (flashNote != null && matchedMessages != null) {
                    for (MatchedMessageInfo msg : matchedMessages) {
                        items.add(new SearchResultItem(flashNote, msg));
                    }
                }
            }
        }
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public SearchResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSearchResultBinding binding = ItemSearchResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new SearchResultViewHolder(binding);
    }
    
    @Override
    public void onBindViewHolder(@NonNull SearchResultViewHolder holder, int position) {
        SearchResultItem item = items.get(position);
        holder.bind(item);
    }
    
    @Override
    public int getItemCount() {
        return items.size();
    }
    
    private String resolveIcon(FlashNote item) {
        String icon = item.getIcon();
        if (icon != null && !icon.trim().isEmpty()) {
            return icon;
        }
        Long id = item.getId();
        int index = id == null ? 0 : (int) (Math.abs(id) % emojis.length);
        return emojis[index];
    }
    
    class SearchResultViewHolder extends RecyclerView.ViewHolder {
        private final ItemSearchResultBinding binding;
        
        SearchResultViewHolder(ItemSearchResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        
        void bind(SearchResultItem item) {
            binding.flashNoteIcon.setText(resolveIcon(item.flashNote));
            binding.flashNoteTitle.setText(item.flashNote.getTitle());
            
            String snippet = item.matchedMessage.getSnippet();
            binding.matchedContent.setText(snippet != null ? snippet : "");
            
            binding.contextContainer.removeAllViews();
            
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onResultClick(item.flashNote, item.matchedMessage.getMessageId());
                }
            });
        }
    }
}
