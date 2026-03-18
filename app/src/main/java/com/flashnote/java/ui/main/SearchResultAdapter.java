package com.flashnote.java.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.model.FlashNoteSearchResult;
import com.flashnote.java.data.model.MatchedMessageInfo;
import com.flashnote.java.databinding.ItemSearchResultBinding;
import com.flashnote.java.databinding.ItemSearchSectionBinding;

import java.util.ArrayList;
import java.util.List;

public class SearchResultAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_SECTION = 0;
    private static final int TYPE_NOTE = 1;
    private static final int TYPE_MESSAGE = 2;

    public interface OnSearchResultClickListener {
        void onResultClick(FlashNote flashNote, Long messageId);
    }

    private final List<RowItem> items = new ArrayList<>();
    private final OnSearchResultClickListener listener;
    private final String[] emojis = new String[]{"💼", "📚", "🌅", "📋", "💡", "✈️", "📝", "🍀", "🎯", "📷"};

    private static class RowItem {
        final int type;
        final String sectionTitle;
        final FlashNote flashNote;
        final MatchedMessageInfo matchedMessage;

        RowItem(int type, String sectionTitle, FlashNote flashNote, MatchedMessageInfo matchedMessage) {
            this.type = type;
            this.sectionTitle = sectionTitle;
            this.flashNote = flashNote;
            this.matchedMessage = matchedMessage;
        }
    }

    public SearchResultAdapter(OnSearchResultClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<FlashNoteSearchResult> noteNameResults, List<FlashNoteSearchResult> messageContentResults) {
        items.clear();
        if (noteNameResults != null && !noteNameResults.isEmpty()) {
            items.add(new RowItem(TYPE_SECTION, "闪记", null, null));
            for (FlashNoteSearchResult result : noteNameResults) {
                FlashNote note = result.getFlashNote();
                if (note != null) {
                    items.add(new RowItem(TYPE_NOTE, null, note, null));
                }
            }
        }
        if (messageContentResults != null && !messageContentResults.isEmpty()) {
            items.add(new RowItem(TYPE_SECTION, "闪记消息", null, null));
            for (FlashNoteSearchResult result : messageContentResults) {
                FlashNote note = result.getFlashNote();
                List<MatchedMessageInfo> matchedMessages = result.getMatchedMessages();
                if (note != null && matchedMessages != null) {
                    for (MatchedMessageInfo matched : matchedMessages) {
                        items.add(new RowItem(TYPE_MESSAGE, null, note, matched));
                    }
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SECTION) {
            ItemSearchSectionBinding binding = ItemSearchSectionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new SectionViewHolder(binding);
        }
        ItemSearchResultBinding binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new SearchResultViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RowItem item = items.get(position);
        if (holder instanceof SectionViewHolder sectionViewHolder) {
            sectionViewHolder.bind(item.sectionTitle);
            return;
        }
        ((SearchResultViewHolder) holder).bind(item);
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

    static class SectionViewHolder extends RecyclerView.ViewHolder {
        private final ItemSearchSectionBinding binding;

        SectionViewHolder(ItemSearchSectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(String title) {
            binding.sectionTitle.setText(title == null ? "" : title);
        }
    }

    class SearchResultViewHolder extends RecyclerView.ViewHolder {
        private final ItemSearchResultBinding binding;

        SearchResultViewHolder(ItemSearchResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(RowItem item) {
            binding.flashNoteIcon.setText(resolveIcon(item.flashNote));
            binding.flashNoteTitle.setText(item.flashNote.getTitle());
            if (item.type == TYPE_NOTE) {
                binding.matchedContent.setVisibility(View.GONE);
                binding.contextContainer.setVisibility(View.GONE);
                binding.getRoot().setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onResultClick(item.flashNote, null);
                    }
                });
                return;
            }
            binding.matchedContent.setVisibility(View.VISIBLE);
            binding.contextContainer.setVisibility(View.GONE);
            String snippet = item.matchedMessage == null ? "" : item.matchedMessage.getSnippet();
            binding.matchedContent.setText(snippet == null ? "" : snippet);
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onResultClick(item.flashNote, item.matchedMessage == null ? null : item.matchedMessage.getMessageId());
                }
            });
        }
    }
}
