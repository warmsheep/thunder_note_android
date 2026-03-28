package com.flashnote.java.ui.chat;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.data.model.Message;
import com.flashnote.java.databinding.FragmentChatBinding;

import java.util.List;

public class ChatScrollController {

    public interface ScrollCallback {
        void scrollToBottomImmediate();

        void scrollToPosition(int position, int offset);

        void onLoadMoreTriggered();

        void preloadRecentMedia(@NonNull List<Message> messages);
    }

    private final ScrollCallback callback;
    @Nullable
    private FragmentChatBinding binding;
    private final MessageAdapter adapter;
    private final LinearLayoutManager layoutManager;

    private boolean autoScrollEnabled = true;
    private boolean isInitialScrollCompleted = false;
    private int lastRenderedMessageCount = 0;
    private String lastRenderedTailKey = "";
    private boolean lastRenderedTailUploading = false;
    private String lastPreloadedMediaKey = "";
    private int loadMoreAnchorPosition = RecyclerView.NO_POSITION;
    private int loadMoreAnchorOffset = 0;
    private int messageCountBeforeLoadMore = 0;

    public ChatScrollController(@NonNull ScrollCallback callback,
                                @NonNull FragmentChatBinding binding,
                                @NonNull MessageAdapter adapter,
                                @NonNull LinearLayoutManager layoutManager) {
        this.callback = callback;
        this.binding = binding;
        this.adapter = adapter;
        this.layoutManager = layoutManager;
    }

    public void onViewReady() {
        isInitialScrollCompleted = false;
    }

    public void onScrolled(@NonNull RecyclerView recyclerView, int dy, boolean isLoadingMore, boolean hasMoreMessages) {
        if (dy < 0) {
            autoScrollEnabled = false;
        } else if (dy > 0) {
            autoScrollEnabled = isNearBottom();
        }
        triggerLoadMoreIfNeeded(recyclerView, dy, isLoadingMore, hasMoreMessages);
    }

    private void triggerLoadMoreIfNeeded(@NonNull RecyclerView recyclerView, int dy, boolean isLoadingMore, boolean hasMoreMessages) {
        if (!isInitialScrollCompleted || dy >= 0 || isLoadingMore || !hasMoreMessages) {
            return;
        }
        if (!recyclerView.canScrollVertically(-1)) {
            LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
            if (manager != null) {
                loadMoreAnchorPosition = manager.findFirstVisibleItemPosition();
                View anchorView = manager.findViewByPosition(loadMoreAnchorPosition);
                loadMoreAnchorOffset = anchorView == null ? 0 : anchorView.getTop();
            }
            messageCountBeforeLoadMore = adapter.getItemCount();
            callback.onLoadMoreTriggered();
        }
    }

    public void scrollToBottomAfterLayout(@Nullable List<Message> messages) {
        if (binding == null || binding.recyclerView == null || adapter == null) {
            return;
        }
        int count = adapter.getItemCount();
        if (count <= 0) {
            return;
        }
        autoScrollEnabled = true;
        int last = count - 1;
        binding.recyclerView.post(() -> {
            if (binding == null) {
                return;
            }
            ensureBottomVisible(last, isLastComplexMessage(messages, last) ? 10 : 4);
            binding.recyclerView.postDelayed(() -> ensureBottomVisible(last, isLastComplexMessage(messages, last) ? 4 : 2), 420);
        });
    }

    public void ensureBottomVisible(int lastPosition, int attempts) {
        if (binding == null || attempts <= 0) {
            return;
        }
        RecyclerView recyclerView = binding.recyclerView;
        recyclerView.scrollToPosition(lastPosition);
        recyclerView.postDelayed(() -> {
            if (binding == null) {
                return;
            }
            RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
            if (!(manager instanceof LinearLayoutManager layoutManager)) {
                return;
            }
            int lastVisible = layoutManager.findLastCompletelyVisibleItemPosition();
            if (lastVisible >= lastPosition && !recyclerView.canScrollVertically(1)) {
                return;
            }
            ensureBottomVisible(lastPosition, attempts - 1);
        }, 120);
    }

    public boolean isLastComplexMessage(@Nullable List<Message> messages, int lastIndex) {
        if (messages == null || messages.isEmpty() || lastIndex < 0 || lastIndex >= messages.size()) {
            return false;
        }
        String mediaType = messages.get(lastIndex).getMediaType();
        return mediaType != null && !mediaType.isBlank() && !"TEXT".equalsIgnoreCase(mediaType);
    }

    public boolean shouldAutoScrollOnMessageUpdate(@Nullable List<Message> messages, boolean wasLoadingMore, long scrollToMessageId) {
        if (scrollToMessageId > 0 || wasLoadingMore || messages == null || messages.isEmpty()) {
            return false;
        }
        boolean nearBottom = isNearBottom();
        boolean tailAppended = messages.size() > lastRenderedMessageCount;
        String newTailKey = buildTailKey(messages.get(messages.size() - 1));
        boolean tailResolvedSuccess = !newTailKey.isEmpty()
                && newTailKey.equals(lastRenderedTailKey)
                && lastRenderedTailUploading
                && !messages.get(messages.size() - 1).isUploading();
        return (autoScrollEnabled || nearBottom) && (tailAppended || tailResolvedSuccess || !isInitialScrollCompleted);
    }

    public void rememberRenderedTailState(@Nullable List<Message> messages) {
        lastRenderedMessageCount = messages == null ? 0 : messages.size();
        if (messages == null || messages.isEmpty()) {
            lastRenderedTailKey = "";
            lastRenderedTailUploading = false;
            return;
        }
        Message tail = messages.get(messages.size() - 1);
        lastRenderedTailKey = buildTailKey(tail);
        lastRenderedTailUploading = tail != null && tail.isUploading();
    }

    @NonNull
    public String buildTailKey(@Nullable Message message) {
        if (message == null) {
            return "";
        }
        if (message.getClientRequestId() != null && !message.getClientRequestId().isBlank()) {
            return "crid:" + message.getClientRequestId().trim();
        }
        if (message.getId() != null) {
            return "id:" + message.getId();
        }
        Long localSortTimestamp = message.getLocalSortTimestamp();
        return localSortTimestamp == null ? "" : "ts:" + localSortTimestamp;
    }

    public boolean isNearBottom() {
        if (binding == null || binding.recyclerView == null || adapter == null) {
            return true;
        }
        int itemCount = adapter.getItemCount();
        if (itemCount <= 0) {
            return true;
        }
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        return lastVisible >= itemCount - 2;
    }

    public void restoreAfterPrepend(int totalMessageCount) {
        if (binding == null || loadMoreAnchorPosition == RecyclerView.NO_POSITION) {
            return;
        }
        int addedCount = Math.max(0, totalMessageCount - messageCountBeforeLoadMore);
        int target = loadMoreAnchorPosition + addedCount;
        binding.recyclerView.post(() -> {
            if (binding != null) {
                callback.scrollToPosition(target, loadMoreAnchorOffset);
            }
        });
    }

    public boolean shouldPreloadRecentMedia(@Nullable List<Message> messages) {
        String mediaKey = buildRecentMediaKey(messages);
        if (mediaKey.equals(lastPreloadedMediaKey)) {
            return false;
        }
        lastPreloadedMediaKey = mediaKey;
        if (!mediaKey.isEmpty() && messages != null) {
            callback.preloadRecentMedia(messages);
        }
        return !mediaKey.isEmpty();
    }

    @NonNull
    public String buildRecentMediaKey(@Nullable List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int start = Math.max(0, messages.size() - 12);
        for (int i = start; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message == null || message.isUploading()) {
                continue;
            }
            String mediaType = message.getMediaType();
            if (!"IMAGE".equalsIgnoreCase(mediaType) && !"VIDEO".equalsIgnoreCase(mediaType)) {
                continue;
            }
            builder.append(message.getId()).append(':')
                    .append(message.getMediaUrl()).append(':')
                    .append(message.getThumbnailUrl()).append('|');
        }
        return builder.toString();
    }

    public void setAutoScrollEnabled(boolean enabled) {
        this.autoScrollEnabled = enabled;
    }

    public boolean isAutoScrollEnabled() {
        return autoScrollEnabled;
    }

    public void setInitialScrollCompleted(boolean initialScrollCompleted) {
        isInitialScrollCompleted = initialScrollCompleted;
    }

    public boolean isInitialScrollCompleted() {
        return isInitialScrollCompleted;
    }

    public void release() {
        binding = null;
    }
}
