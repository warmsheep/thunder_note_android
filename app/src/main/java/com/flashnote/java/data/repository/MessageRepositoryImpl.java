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

    public MessageRepositoryImpl(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public LiveData<List<Message>> getMessages(long flashNoteId) {
        return ensureLiveData(flashNoteId);
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
        boolean hasExistingConversation = conversations.containsKey(flashNoteId);
        MutableLiveData<List<Message>> liveData = ensureLiveData(flashNoteId);
        MutableLiveData<Boolean> hasMoreLiveData = ensureHasMoreLiveData(flashNoteId);
        if (hasExistingConversation) {
            Boolean hasMore = hasMoreMap.get(flashNoteId);
            hasMoreLiveData.setValue(hasMore == null || hasMore);
            return;
        }
        if (liveData.getValue() == null || liveData.getValue().isEmpty()) {
            currentPages.put(flashNoteId, 1);
            hasMoreMap.put(flashNoteId, true);
            hasMoreLiveData.setValue(true);
            loadMessages(flashNoteId, 1, 20);
            return;
        }
        Boolean hasMore = hasMoreMap.get(flashNoteId);
        hasMoreLiveData.setValue(hasMore == null || hasMore);
    }

    @Override
    public void sendText(long flashNoteId, String content, Runnable onSuccess) {
        isLoading.setValue(true);
        Message message = new Message();
        message.setFlashNoteId(flashNoteId);
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
                        MutableLiveData<List<Message>> liveData = ensureLiveData(flashNoteId);
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

    private void loadMessages(long flashNoteId, int page, int limit) {
        isLoading.setValue(true);
        MessageListRequest request = new MessageListRequest(flashNoteId, page, limit);
        messageService.list(request).enqueue(new Callback<ApiResponse<List<Message>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Message>>> call, 
                                 Response<ApiResponse<List<Message>>> response) {
                isLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<Message>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        MutableLiveData<List<Message>> liveData = ensureLiveData(flashNoteId);
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
                        hasMoreMap.put(flashNoteId, hasMore);
                        MutableLiveData<Boolean> hasMoreLiveData = ensureHasMoreLiveData(flashNoteId);
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
        Boolean hasMore = hasMoreMap.get(flashNoteId);
        if (hasMore == null || !hasMore) {
            return;
        }
        
        Boolean loading = isLoading.getValue();
        if (loading != null && loading) {
            return;
        }
        
        int currentPage = currentPages.getOrDefault(flashNoteId, 1);
        int nextPage = currentPage + 1;
        currentPages.put(flashNoteId, nextPage);
        loadMessages(flashNoteId, nextPage, 20);
    }

    @Override
    public LiveData<Boolean> getHasMore() {
        return ensureHasMoreLiveData(currentFlashNoteId);
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
        isLoading.setValue(true);
        messageService.delete(messageId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, 
                                 Response<ApiResponse<Void>> response) {
                isLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Void> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        MutableLiveData<List<Message>> liveData = ensureLiveData(flashNoteId);
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
        isLoading.setValue(true);
        message.setFlashNoteId(flashNoteId);
        message.setRole("user");
        
        messageService.send(message).enqueue(new Callback<ApiResponse<Message>>() {
            @Override
            public void onResponse(Call<ApiResponse<Message>> call, 
                                 Response<ApiResponse<Message>> response) {
                isLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Message> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        MutableLiveData<List<Message>> liveData = ensureLiveData(flashNoteId);
                        List<Message> current = liveData.getValue();
                        List<Message> updated = current == null ? new ArrayList<>() : new ArrayList<>(current);
                        updated.remove(message);
                        updated.add(apiResponse.getData());
                        liveData.setValue(updated);
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("MessageRepo", errMsg);
                        errorMessage.setValue(errMsg);
                    }
                } else {
                    String errMsg = "Failed to send message: " + response.code();
                    DebugLog.w("MessageRepo", errMsg);
                    errorMessage.setValue(errMsg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Message>> call, Throwable t) {
                isLoading.setValue(false);
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("MessageRepo", errMsg);
                errorMessage.setValue(errMsg);
            }
        });
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
        MutableLiveData<List<Message>> liveData = ensureLiveData(flashNoteId);
        List<Message> current = liveData.getValue();
        List<Message> updated = current == null ? new ArrayList<>() : new ArrayList<>(current);
        updated.add(message);
        liveData.setValue(updated);
    }
}
