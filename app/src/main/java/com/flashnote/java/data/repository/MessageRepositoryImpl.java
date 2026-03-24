package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import android.os.Looper;

import com.flashnote.java.DebugLog;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.MessageListRequest;
import com.flashnote.java.data.model.PendingMessage;
import com.flashnote.java.data.remote.MessageService;
import com.flashnote.java.util.ConversationKeyUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MessageRepositoryImpl implements MessageRepository {
    private static final String MEDIA_TYPE_TEXT = "TEXT";
    private static final long MESSAGE_SOURCE_REMOTE = 0L;
    private static final long MESSAGE_SOURCE_PENDING = 1L;
    private static final Comparator<Message> MERGED_MESSAGE_COMPARATOR =
            Comparator.<Message>comparingLong(message -> messageSourceOrder(message))
                    .thenComparing(message -> message == null ? null : message.getCreatedAt(),
                            Comparator.nullsLast(LocalDateTime::compareTo))
                    .thenComparingLong(message -> messageStableTieBreaker(message));
    private final MessageService messageService;
    private final PendingMessageRepository pendingMessageRepository;
    private final PendingMessageDispatcher pendingMessageDispatcher;
    private final Map<Long, MutableLiveData<List<Message>>> conversations = new HashMap<>();
    private final Map<Long, MutableLiveData<List<Message>>> mergedConversations = new HashMap<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final Map<Long, Integer> currentPages = new HashMap<>();
    private final Map<Long, Boolean> hasMoreMap = new HashMap<>();
    private final Map<Long, MutableLiveData<Boolean>> hasMoreLiveDataMap = new HashMap<>();
    private long currentFlashNoteId = 0L;
    private long currentPeerUserId = 0L;
    private long currentConversationKey = 0L;

    public MessageRepositoryImpl(MessageService messageService,
                                 PendingMessageRepository pendingMessageRepository) {
        this.messageService = messageService;
        this.pendingMessageRepository = pendingMessageRepository;
        this.pendingMessageDispatcher = new PendingMessageDispatcher(pendingMessageRepository, messageService, this::onPendingUpdated);
    }

    @Override
    public LiveData<List<Message>> getMessages(long flashNoteId) {
        long conversationKey = ConversationKeyUtil.forFlashNote(flashNoteId);
        return ensureMergedLiveData(conversationKey);
    }

    @Override
    public LiveData<List<Message>> getContactMessages(long peerUserId) {
        long conversationKey = ConversationKeyUtil.forContact(peerUserId);
        return ensureMergedLiveData(conversationKey);
    }

    @Override
    public LiveData<Boolean> isLoading() {
        return isLoading;
    }

    @Override
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    @Override
    public void clearError() {
        errorMessage.setValue(null);
    }

    @Override
    public List<Message> getCachedMessages() {
        Map<Long, Message> deduplicated = new LinkedHashMap<>();
        List<Message> transientMessages = new ArrayList<>();
        for (MutableLiveData<List<Message>> liveData : conversations.values()) {
            List<Message> messages = liveData.getValue();
            if (messages == null) {
                continue;
            }
            for (Message message : messages) {
                if (message == null) {
                    continue;
                }
                Long id = message.getId();
                if (id == null) {
                    transientMessages.add(message);
                    continue;
                }
                deduplicated.put(id, message);
            }
        }
        List<Message> result = new ArrayList<>(deduplicated.values());
        result.addAll(transientMessages);
        return result;
    }

    @Override
    public void bindFlashNote(long flashNoteId) {
        currentFlashNoteId = flashNoteId;
        currentPeerUserId = 0L;
        currentConversationKey = ConversationKeyUtil.forFlashNote(flashNoteId);
        MutableLiveData<List<Message>> liveData = ensureLiveData(currentConversationKey);
        MutableLiveData<Boolean> hasMoreLiveData = ensureHasMoreLiveData(currentConversationKey);
        List<Message> current = liveData.getValue();
        boolean hasCachedMessages = current != null && !current.isEmpty();
        if (!currentPages.containsKey(currentConversationKey)) {
            currentPages.put(currentConversationKey, 1);
        }
        if (!hasMoreMap.containsKey(currentConversationKey)) {
            hasMoreMap.put(currentConversationKey, true);
        }
        hasMoreLiveData.setValue(hasMoreMap.get(currentConversationKey));
        if (!hasCachedMessages) {
            loadMessages(currentConversationKey, flashNoteId, null, 1, 20);
        }
    }

    @Override
    public void bindContact(long peerUserId) {
        currentPeerUserId = peerUserId;
        currentFlashNoteId = 0L;
        currentConversationKey = ConversationKeyUtil.forContact(peerUserId);
        MutableLiveData<List<Message>> liveData = ensureLiveData(currentConversationKey);
        MutableLiveData<Boolean> hasMoreLiveData = ensureHasMoreLiveData(currentConversationKey);
        List<Message> current = liveData.getValue();
        boolean hasCachedMessages = current != null && !current.isEmpty();
        if (!currentPages.containsKey(currentConversationKey)) {
            currentPages.put(currentConversationKey, 1);
        }
        if (!hasMoreMap.containsKey(currentConversationKey)) {
            hasMoreMap.put(currentConversationKey, true);
        }
        hasMoreLiveData.setValue(hasMoreMap.get(currentConversationKey));
        if (!hasCachedMessages) {
            loadMessages(currentConversationKey, null, peerUserId, 1, 20);
        }
    }

    @Override
    public void sendText(long flashNoteId, String content, Runnable onSuccess) {
        sendTextInternal(ConversationKeyUtil.forFlashNote(flashNoteId), flashNoteId, null, content, onSuccess);
    }

    @Override
    public void sendTextToContact(long peerUserId, String content, Runnable onSuccess) {
        sendTextInternal(ConversationKeyUtil.forContact(peerUserId), null, peerUserId, content, onSuccess);
    }

    @Override
    public void retryPendingText(long localId) {
        PendingMessage pendingMessage = pendingMessageRepository.findByLocalId(localId);
        if (pendingMessage == null) {
            return;
        }
        pendingMessage.setStatus(PendingMessageDispatcher.STATUS_QUEUED);
        pendingMessage.setErrorMessage(null);
        pendingMessageRepository.update(pendingMessage);
        refreshMergedConversation(pendingMessage.getConversationKey());
        pendingMessageDispatcher.dispatch(localId);
    }

    private void sendTextInternal(long conversationKey,
                                  Long flashNoteId,
                                  Long peerUserId,
                                  String content,
                                  Runnable onSuccess) {
        PendingMessage pendingMessage = new PendingMessage();
        pendingMessage.setConversationKey(conversationKey);
        pendingMessage.setFlashNoteId(flashNoteId);
        pendingMessage.setPeerUserId(peerUserId);
        pendingMessage.setClientRequestId(UUID.randomUUID().toString());
        pendingMessage.setMediaType(MEDIA_TYPE_TEXT);
        pendingMessage.setContent(content);
        pendingMessage.setStatus(PendingMessageDispatcher.STATUS_QUEUED);
        pendingMessage.setCreatedAt(System.currentTimeMillis());
        long localId = pendingMessageRepository.insert(pendingMessage);
        pendingMessage.setLocalId(localId);
        refreshMergedConversation(conversationKey);
        if (onSuccess != null) {
            onSuccess.run();
        }
        pendingMessageDispatcher.dispatch(localId);
    }

    private void loadMessages(long conversationKey, Long flashNoteId, Long peerUserId, int page, int limit) {
        isLoading.setValue(true);
        MessageListRequest request = new MessageListRequest(flashNoteId, peerUserId, page, limit);
        messageService.list(request).enqueue(new Callback<ApiResponse<List<Message>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Message>>> call, 
                                 Response<ApiResponse<List<Message>>> response) {
                isLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<Message>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        clearError();
                        MutableLiveData<List<Message>> liveData = ensureLiveData(conversationKey);
                        List<Message> newMessages = apiResponse.getData();
                        
                        if (page == 1) {
                            liveData.setValue(newMessages);
                        } else {
                            List<Message> current = liveData.getValue();
                            List<Message> updated = new ArrayList<>(newMessages);
                            if (current != null) {
                                updated.addAll(current);
                            }
                            liveData.setValue(updated);
                        }
                        refreshMergedConversation(conversationKey);
                        
                        boolean hasMore = newMessages.size() >= limit;
                        hasMoreMap.put(conversationKey, hasMore);
                        MutableLiveData<Boolean> hasMoreLiveData = ensureHasMoreLiveData(conversationKey);
                        hasMoreLiveData.setValue(hasMore);
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("MessageRepo", errMsg);
                        errorMessage.setValue(errMsg);
                    }
                } else {
                    String errMsg = "Failed to load messages: " + response.code();
                    DebugLog.w("MessageRepo", errMsg);
                    errorMessage.setValue(errMsg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Message>>> call, Throwable t) {
                isLoading.setValue(false);
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("MessageRepo", errMsg);
                errorMessage.setValue(errMsg);
            }
        });
    }

    @Override
    public void loadMoreMessages(long flashNoteId) {
        long conversationKey = ConversationKeyUtil.forFlashNote(flashNoteId);
        Boolean hasMore = hasMoreMap.get(conversationKey);
        if (hasMore == null || !hasMore) {
            return;
        }
        
        Boolean loading = isLoading.getValue();
        if (loading != null && loading) {
            return;
        }
        
        int currentPage = currentPages.getOrDefault(conversationKey, 1);
        int nextPage = currentPage + 1;
        currentPages.put(conversationKey, nextPage);
        loadMessages(conversationKey, flashNoteId, null, nextPage, 20);
    }

    @Override
    public void loadMoreContactMessages(long peerUserId) {
        long conversationKey = ConversationKeyUtil.forContact(peerUserId);
        Boolean hasMore = hasMoreMap.get(conversationKey);
        if (hasMore == null || !hasMore) {
            return;
        }

        Boolean loading = isLoading.getValue();
        if (loading != null && loading) {
            return;
        }

        int currentPage = currentPages.getOrDefault(conversationKey, 1);
        int nextPage = currentPage + 1;
        currentPages.put(conversationKey, nextPage);
        loadMessages(conversationKey, null, peerUserId, nextPage, 20);
    }

    @Override
    public LiveData<Boolean> getHasMore() {
        return ensureHasMoreLiveData(currentConversationKey);
    }

    private MutableLiveData<Boolean> ensureHasMoreLiveData(long flashNoteId) {
        MutableLiveData<Boolean> existing = hasMoreLiveDataMap.get(flashNoteId);
        if (existing != null) {
            return existing;
        }
        MutableLiveData<Boolean> created = new MutableLiveData<>(true);
        hasMoreLiveDataMap.put(flashNoteId, created);
        return created;
    }

    public LiveData<Boolean> getHasMoreForFlashNote(long flashNoteId) {
        return ensureHasMoreLiveData(flashNoteId);
    }

    private MutableLiveData<List<Message>> ensureLiveData(long flashNoteId) {
        MutableLiveData<List<Message>> existing = conversations.get(flashNoteId);
        if (existing != null) {
            return existing;
        }
        MutableLiveData<List<Message>> created = new MutableLiveData<>(new ArrayList<>());
        conversations.put(flashNoteId, created);
        return created;
    }

    @Override
    public void deleteMessage(long flashNoteId, long messageId, Runnable onSuccess) {
        deleteMessageInternal(ConversationKeyUtil.forFlashNote(flashNoteId), messageId, onSuccess);
    }

    @Override
    public void deleteContactMessage(long peerUserId, long messageId, Runnable onSuccess) {
        deleteMessageInternal(ConversationKeyUtil.forContact(peerUserId), messageId, onSuccess);
    }

    private void deleteMessageInternal(long conversationKey, long messageId, Runnable onSuccess) {
        isLoading.setValue(true);
        messageService.delete(messageId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, 
                                 Response<ApiResponse<Void>> response) {
                isLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Void> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        clearError();
                        MutableLiveData<List<Message>> liveData = ensureLiveData(conversationKey);
                        List<Message> current = liveData.getValue();
                        if (current != null) {
                            List<Message> updated = new ArrayList<>();
                            for (Message msg : current) {
                                if (msg.getId() == null || msg.getId() != messageId) {
                                    updated.add(msg);
                                }
                            }
                            liveData.setValue(updated);
                            refreshMergedConversation(conversationKey);
                        }
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("MessageRepo", errMsg);
                        errorMessage.setValue(errMsg);
                    }
                } else {
                    String errMsg = "Failed to delete message: " + response.code();
                    DebugLog.w("MessageRepo", errMsg);
                    errorMessage.setValue(errMsg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                isLoading.setValue(false);
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("MessageRepo", errMsg);
                errorMessage.setValue(errMsg);
            }
        });
    }

    @Override
    public void sendMessage(long flashNoteId, Message message, Runnable onSuccess) {
        sendMessage(flashNoteId, message, runnableToCallback(onSuccess));
    }

    @Override
    public void sendMessage(long flashNoteId, Message message, SendCallback callback) {
        sendMessageInternal(ConversationKeyUtil.forFlashNote(flashNoteId), flashNoteId, null, message, callback);
    }

    @Override
    public void sendMessageToContact(long peerUserId, Message message, Runnable onSuccess) {
        sendMessageToContact(peerUserId, message, runnableToCallback(onSuccess));
    }

    @Override
    public void sendMessageToContact(long peerUserId, Message message, SendCallback callback) {
        sendMessageInternal(ConversationKeyUtil.forContact(peerUserId), null, peerUserId, message, callback);
    }

    private void sendMessageInternal(long conversationKey,
                                     Long flashNoteId,
                                     Long peerUserId,
                                     Message message,
                                     SendCallback callback) {
        isLoading.setValue(true);
        message.setFlashNoteId(flashNoteId);
        message.setReceiverId(peerUserId);
        message.setRole("user");
        
        messageService.send(message).enqueue(new Callback<ApiResponse<Message>>() {
            @Override
            public void onResponse(Call<ApiResponse<Message>> call, 
                                 Response<ApiResponse<Message>> response) {
                isLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Message> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        clearError();
                        MutableLiveData<List<Message>> liveData = ensureLiveData(conversationKey);
                        List<Message> current = liveData.getValue();
                        List<Message> updated = current == null ? new ArrayList<>() : new ArrayList<>(current);
                        updated.remove(message);
                        updated.add(apiResponse.getData());
                        liveData.setValue(updated);
                        refreshMergedConversation(conversationKey);
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("MessageRepo", errMsg);
                        errorMessage.setValue(errMsg);
                        if (callback != null) {
                            callback.onError(errMsg);
                        }
                    }
                } else {
                    String errMsg = "Failed to send message: " + response.code();
                    DebugLog.w("MessageRepo", errMsg);
                    errorMessage.setValue(errMsg);
                    if (callback != null) {
                        callback.onError(errMsg);
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Message>> call, Throwable t) {
                isLoading.setValue(false);
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("MessageRepo", errMsg);
                errorMessage.setValue(errMsg);
                if (callback != null) {
                    callback.onError(errMsg);
                }
            }
        });
    }

    private SendCallback runnableToCallback(Runnable onSuccess) {
        return new SendCallback() {
            @Override
            public void onSuccess() {
                if (onSuccess != null) {
                    onSuccess.run();
                }
            }

            @Override
            public void onError(String message) {
            }
        };
    }

    @Override
    public void countMessages(CountCallback callback) {
        messageService.countMessages().enqueue(new Callback<ApiResponse<Integer>>() {
            @Override
            public void onResponse(Call<ApiResponse<Integer>> call, Response<ApiResponse<Integer>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(response.body().getData().longValue());
                } else {
                    int code = response.body() == null ? response.code() : response.body().getCode();
                    String message = response.body() == null ? "Failed to get count" : response.body().getMessage();
                    callback.onError(message, code);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Integer>> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage(), -1);
            }
        });
    }

    @Override
    public void addLocalMessage(Message message) {
        Long flashNoteId = message.getFlashNoteId();
        if (flashNoteId == null) {
            return;
        }
        MutableLiveData<List<Message>> liveData = ensureLiveData(ConversationKeyUtil.forFlashNote(flashNoteId));
        List<Message> current = liveData.getValue();
        List<Message> updated = current == null ? new ArrayList<>() : new ArrayList<>(current);
        updated.add(message);
        liveData.setValue(updated);
        refreshMergedConversation(ConversationKeyUtil.forFlashNote(flashNoteId));
    }

    @Override
    public void removeLocalMessage(Message message) {
        Long flashNoteId = message.getFlashNoteId();
        if (flashNoteId == null) {
            return;
        }
        MutableLiveData<List<Message>> liveData = ensureLiveData(ConversationKeyUtil.forFlashNote(flashNoteId));
        List<Message> current = liveData.getValue();
        if (current == null || current.isEmpty()) {
            return;
        }
        List<Message> updated = new ArrayList<>(current);
        if (updated.remove(message)) {
            liveData.setValue(updated);
            refreshMergedConversation(ConversationKeyUtil.forFlashNote(flashNoteId));
        }
    }

    @Override
    public void addLocalContactMessage(long peerUserId, Message message) {
        MutableLiveData<List<Message>> liveData = ensureLiveData(ConversationKeyUtil.forContact(peerUserId));
        List<Message> current = liveData.getValue();
        List<Message> updated = current == null ? new ArrayList<>() : new ArrayList<>(current);
        updated.add(message);
        liveData.setValue(updated);
        refreshMergedConversation(ConversationKeyUtil.forContact(peerUserId));
    }

    private MutableLiveData<List<Message>> ensureMergedLiveData(long conversationKey) {
        MutableLiveData<List<Message>> existing = mergedConversations.get(conversationKey);
        if (existing != null) {
            return existing;
        }
        MutableLiveData<List<Message>> created = new MutableLiveData<>(new ArrayList<>());
        mergedConversations.put(conversationKey, created);
        refreshMergedConversation(conversationKey);
        return created;
    }

    private void refreshMergedConversation(long conversationKey) {
        refreshMergedConversation(conversationKey, ensureLiveData(conversationKey).getValue());
    }

    private void refreshMergedConversation(long conversationKey, List<Message> remoteOverride) {
        MutableLiveData<List<Message>> mergedLiveData = ensureMergedLiveDataInternal(conversationKey);
        List<Message> remote = remoteOverride;
        List<PendingMessage> pendingMessages = pendingMessageRepository.getByConversationKey(conversationKey);

        List<Message> merged = remote == null ? new ArrayList<>() : new ArrayList<>(remote);
        if (pendingMessages != null) {
            for (PendingMessage pendingMessage : pendingMessages) {
                if (PendingMessageDispatcher.STATUS_SENT.equals(pendingMessage.getStatus())) {
                    continue;
                }
                merged.add(toUiMessage(pendingMessage));
            }
        }
        merged.sort(MERGED_MESSAGE_COMPARATOR);
        setLiveDataValue(mergedLiveData, merged);
    }

    private static long messageSourceOrder(Message message) {
        return isPendingUiMessage(message) ? MESSAGE_SOURCE_PENDING : MESSAGE_SOURCE_REMOTE;
    }

    private static long messageStableTieBreaker(Message message) {
        if (message == null || message.getId() == null) {
            return Long.MAX_VALUE;
        }
        long id = message.getId();
        if (id < 0L) {
            return -id;
        }
        return id;
    }

    private static boolean isPendingUiMessage(Message message) {
        return message != null && message.getId() != null && message.getId() < 0L;
    }

    private MutableLiveData<List<Message>> ensureMergedLiveDataInternal(long conversationKey) {
        MutableLiveData<List<Message>> existing = mergedConversations.get(conversationKey);
        if (existing != null) {
            return existing;
        }
        MutableLiveData<List<Message>> created = new MutableLiveData<>(new ArrayList<>());
        mergedConversations.put(conversationKey, created);
        return created;
    }

    private Message toUiMessage(PendingMessage pendingMessage) {
        Message message = new Message();
        message.setId(-pendingMessage.getLocalId());
        message.setFlashNoteId(pendingMessage.getFlashNoteId());
        message.setReceiverId(pendingMessage.getPeerUserId());
        message.setContent(pendingMessage.getContent());
        message.setMediaType(pendingMessage.getMediaType());
        message.setRole("user");
        message.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(pendingMessage.getCreatedAt()), ZoneId.systemDefault()));
        message.setUploading(!PendingMessageDispatcher.STATUS_FAILED.equals(pendingMessage.getStatus()));
        return message;
    }

    private void onPendingUpdated(PendingMessage pendingMessage, Message serverMessage) {
        long conversationKey = pendingMessage.getConversationKey();
        if (serverMessage != null) {
            MutableLiveData<List<Message>> liveData = ensureLiveData(conversationKey);
            List<Message> current = liveData.getValue();
            List<Message> updated = current == null ? new ArrayList<>() : new ArrayList<>(current);
            updated.add(serverMessage);
            setLiveDataValue(liveData, updated);
            setLiveDataValue(errorMessage, null);
            refreshMergedConversation(conversationKey, updated);
        } else if (PendingMessageDispatcher.STATUS_FAILED.equals(pendingMessage.getStatus())) {
            setLiveDataValue(errorMessage, pendingMessage.getErrorMessage());
            refreshMergedConversation(conversationKey);
        }
        setLiveDataValue(isLoading, PendingMessageDispatcher.STATUS_SENDING.equals(pendingMessage.getStatus()));
    }

    private <T> void setLiveDataValue(MutableLiveData<T> liveData, T value) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            liveData.setValue(value);
        } else {
            liveData.postValue(value);
        }
    }

    @Override
    public void mergeMessages(long flashNoteId, List<Long> messageIds, String title, MergeCallback callback) {
        com.flashnote.java.data.model.MessageMergeRequest request = new com.flashnote.java.data.model.MessageMergeRequest();
        request.setFlashNoteId(flashNoteId);
        request.setMessageIds(messageIds);
        request.setTitle(title);

        messageService.merge(request).enqueue(new Callback<ApiResponse<Message>>() {
            @Override
            public void onResponse(Call<ApiResponse<Message>> call, Response<ApiResponse<Message>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    if (callback != null) {
                        callback.onSuccess(response.body().getData());
                    }
                } else {
                    if (callback != null) {
                        callback.onError(response.body() != null ? response.body().getMessage() : "Merge failed");
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Message>> call, Throwable t) {
                if (callback != null) {
                    callback.onError(t.getMessage());
                }
            }
        });
    }

    @Override
    public void mergeContactMessages(long peerUserId, List<Long> messageIds, String title, MergeCallback callback) {
        com.flashnote.java.data.model.MessageMergeRequest request = new com.flashnote.java.data.model.MessageMergeRequest();
        request.setReceiverId(peerUserId);
        request.setMessageIds(messageIds);
        request.setTitle(title);

        messageService.merge(request).enqueue(new Callback<ApiResponse<Message>>() {
            @Override
            public void onResponse(Call<ApiResponse<Message>> call, Response<ApiResponse<Message>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    if (callback != null) {
                        callback.onSuccess(response.body().getData());
                    }
                } else {
                    if (callback != null) {
                        callback.onError(response.body() != null ? response.body().getMessage() : "Merge failed");
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Message>> call, Throwable t) {
                if (callback != null) {
                    callback.onError(t.getMessage());
                }
            }
        });
    }

}
