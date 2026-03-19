package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.flashnote.java.DebugLog;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.MessageListRequest;
import com.flashnote.java.data.remote.MessageService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MessageRepositoryImpl implements MessageRepository {
    private final MessageService messageService;
    private final Map<Long, MutableLiveData<List<Message>>> conversations = new HashMap<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final Map<Long, Integer> currentPages = new HashMap<>();
    private final Map<Long, Boolean> hasMoreMap = new HashMap<>();
    private final Map<Long, MutableLiveData<Boolean>> hasMoreLiveDataMap = new HashMap<>();
    private long currentFlashNoteId = 0L;
    private long currentPeerUserId = 0L;
    private long currentConversationKey = 0L;

    public MessageRepositoryImpl(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public LiveData<List<Message>> getMessages(long flashNoteId) {
        return ensureLiveData(keyForFlashNote(flashNoteId));
    }

    @Override
    public LiveData<List<Message>> getContactMessages(long peerUserId) {
        return ensureLiveData(keyForContact(peerUserId));
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
        currentConversationKey = keyForFlashNote(flashNoteId);
        MutableLiveData<List<Message>> liveData = ensureLiveData(currentConversationKey);
        MutableLiveData<Boolean> hasMoreLiveData = ensureHasMoreLiveData(currentConversationKey);
        liveData.setValue(new ArrayList<>());
        currentPages.put(currentConversationKey, 1);
        hasMoreMap.put(currentConversationKey, true);
        hasMoreLiveData.setValue(true);
        loadMessages(currentConversationKey, flashNoteId, null, 1, 20);
    }

    @Override
    public void bindContact(long peerUserId) {
        currentPeerUserId = peerUserId;
        currentFlashNoteId = 0L;
        currentConversationKey = keyForContact(peerUserId);
        MutableLiveData<List<Message>> liveData = ensureLiveData(currentConversationKey);
        MutableLiveData<Boolean> hasMoreLiveData = ensureHasMoreLiveData(currentConversationKey);
        liveData.setValue(new ArrayList<>());
        currentPages.put(currentConversationKey, 1);
        hasMoreMap.put(currentConversationKey, true);
        hasMoreLiveData.setValue(true);
        loadMessages(currentConversationKey, null, peerUserId, 1, 20);
    }

    @Override
    public void sendText(long flashNoteId, String content, Runnable onSuccess) {
        sendTextInternal(keyForFlashNote(flashNoteId), flashNoteId, null, content, onSuccess);
    }

    @Override
    public void sendTextToContact(long peerUserId, String content, Runnable onSuccess) {
        sendTextInternal(keyForContact(peerUserId), null, peerUserId, content, onSuccess);
    }

    private void sendTextInternal(long conversationKey,
                                  Long flashNoteId,
                                  Long peerUserId,
                                  String content,
                                  Runnable onSuccess) {
        isLoading.setValue(true);
        Message message = new Message();
        message.setFlashNoteId(flashNoteId);
        message.setReceiverId(peerUserId);
        message.setContent(content);
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
                        updated.add(apiResponse.getData());
                        liveData.setValue(updated);
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    } else {
                        errorMessage.setValue(apiResponse.getMessage());
                    }
                } else {
                    errorMessage.setValue("Failed to send message: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Message>> call, Throwable t) {
                isLoading.setValue(false);
                errorMessage.setValue("Network error: " + t.getMessage());
            }
        });
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
        long conversationKey = keyForFlashNote(flashNoteId);
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
        long conversationKey = keyForContact(peerUserId);
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
        deleteMessageInternal(keyForFlashNote(flashNoteId), messageId, onSuccess);
    }

    @Override
    public void deleteContactMessage(long peerUserId, long messageId, Runnable onSuccess) {
        deleteMessageInternal(keyForContact(peerUserId), messageId, onSuccess);
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
        sendMessageInternal(keyForFlashNote(flashNoteId), flashNoteId, null, message, callback);
    }

    @Override
    public void sendMessageToContact(long peerUserId, Message message, Runnable onSuccess) {
        sendMessageToContact(peerUserId, message, runnableToCallback(onSuccess));
    }

    @Override
    public void sendMessageToContact(long peerUserId, Message message, SendCallback callback) {
        sendMessageInternal(keyForContact(peerUserId), null, peerUserId, message, callback);
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
        MutableLiveData<List<Message>> liveData = ensureLiveData(keyForFlashNote(flashNoteId));
        List<Message> current = liveData.getValue();
        List<Message> updated = current == null ? new ArrayList<>() : new ArrayList<>(current);
        updated.add(message);
        liveData.setValue(updated);
    }

    @Override
    public void addLocalContactMessage(long peerUserId, Message message) {
        MutableLiveData<List<Message>> liveData = ensureLiveData(keyForContact(peerUserId));
        List<Message> current = liveData.getValue();
        List<Message> updated = current == null ? new ArrayList<>() : new ArrayList<>(current);
        updated.add(message);
        liveData.setValue(updated);
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
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
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
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
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

    private long keyForFlashNote(long flashNoteId) {
        return flashNoteId;
    }

    private long keyForContact(long peerUserId) {
        return -1_000_000_000L - Math.abs(peerUserId);
    }
}
