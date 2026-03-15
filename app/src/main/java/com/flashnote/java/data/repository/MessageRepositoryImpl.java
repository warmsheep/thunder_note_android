package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.MessageListRequest;
import com.flashnote.java.data.remote.MessageService;

import java.util.ArrayList;
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
    public void bindFlashNote(long flashNoteId) {
        MutableLiveData<List<Message>> liveData = ensureLiveData(flashNoteId);
        if (liveData.getValue() == null || liveData.getValue().isEmpty()) {
            loadMessages(flashNoteId);
        }
    }

    @Override
    public void sendText(long flashNoteId, String content) {
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

    private void loadMessages(long flashNoteId) {
        isLoading.setValue(true);
        messageService.list(new MessageListRequest(flashNoteId)).enqueue(new Callback<ApiResponse<List<Message>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Message>>> call, 
                                 Response<ApiResponse<List<Message>>> response) {
                isLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<Message>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        MutableLiveData<List<Message>> liveData = ensureLiveData(flashNoteId);
                        liveData.setValue(apiResponse.getData());
                    } else {
                        errorMessage.setValue(apiResponse.getMessage());
                    }
                } else {
                    errorMessage.setValue("Failed to load messages: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Message>>> call, Throwable t) {
                isLoading.setValue(false);
                errorMessage.setValue("Network error: " + t.getMessage());
            }
        });
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
}
