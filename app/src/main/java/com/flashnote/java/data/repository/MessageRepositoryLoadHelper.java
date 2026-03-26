package com.flashnote.java.data.repository;

import com.flashnote.java.DebugLog;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.MessageListRequest;
import com.flashnote.java.data.remote.MessageService;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

final class MessageRepositoryLoadHelper {

    interface LoadStateBridge {
        void onLoadingChanged(boolean loading);

        void onError(String message);

        void clearError();

        void onMessagesLoaded(long conversationKey, List<Message> messages, int page, int limit);
    }

    void loadMessages(long conversationKey,
                      Long flashNoteId,
                      Long peerUserId,
                      int page,
                      int limit,
                      MessageService messageService,
                      LoadStateBridge bridge) {
        bridge.onLoadingChanged(true);
        MessageListRequest request = new MessageListRequest(flashNoteId, peerUserId, page, limit);
        messageService.list(request).enqueue(new Callback<ApiResponse<List<Message>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Message>>> call, Response<ApiResponse<List<Message>>> response) {
                bridge.onLoadingChanged(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<Message>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        bridge.clearError();
                        bridge.onMessagesLoaded(conversationKey, apiResponse.getData(), page, limit);
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("MessageRepo", errMsg);
                        bridge.onError(errMsg);
                    }
                } else {
                    String errMsg = "Failed to load messages: " + response.code();
                    DebugLog.w("MessageRepo", errMsg);
                    bridge.onError(errMsg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Message>>> call, Throwable t) {
                bridge.onLoadingChanged(false);
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("MessageRepo", errMsg);
                bridge.onError(errMsg);
            }
        });
    }
}
