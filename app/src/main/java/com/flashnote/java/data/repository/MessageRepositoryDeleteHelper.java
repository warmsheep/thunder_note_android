package com.flashnote.java.data.repository;

import androidx.annotation.Nullable;

import com.flashnote.java.DebugLog;
import com.flashnote.java.data.local.MessageLocalDao;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.MessageBatchDeleteRequest;
import com.flashnote.java.data.model.PendingMessage;
import com.flashnote.java.data.remote.MessageService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

final class MessageRepositoryDeleteHelper {

    interface DeleteStateBridge {
        void onLoadingChanged(boolean loading);

        void onError(String message);

        void clearError();
    }

    void deleteMessages(List<Long> messageIds,
                        @Nullable Runnable onSuccess,
                        MessageService messageService,
                        PendingMessageRepository pendingMessageRepository,
                        MessageLocalDao messageLocalDao,
                        ExecutorService pendingStorageExecutor,
                        DeleteStateBridge bridge) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }

        List<Long> remoteIds = new ArrayList<>();
        List<Long> pendingLocalIds = new ArrayList<>();
        for (Long id : messageIds) {
            if (id == null) {
                continue;
            }
            if (id > 0L) {
                remoteIds.add(id);
            } else if (id < 0L) {
                pendingLocalIds.add(Math.abs(id));
            }
        }

        if (remoteIds.isEmpty()) {
            pendingStorageExecutor.execute(() -> deletePendingLocals(pendingLocalIds, pendingMessageRepository));
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        bridge.onLoadingChanged(true);
        MessageBatchDeleteRequest request = new MessageBatchDeleteRequest();
        request.setIds(remoteIds);
        messageService.deleteBatch(request).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                bridge.onLoadingChanged(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    bridge.clearError();
                    pendingStorageExecutor.execute(() -> {
                        for (Long id : remoteIds) {
                            if (id != null) {
                                messageLocalDao.deleteById(id);
                            }
                        }
                        deletePendingLocals(pendingLocalIds, pendingMessageRepository);
                    });
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                    return;
                }
                String errMsg = response.body() == null ? "Failed to delete messages" : response.body().getMessage();
                DebugLog.w("MessageRepo", errMsg);
                bridge.onError(errMsg);
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                bridge.onLoadingChanged(false);
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("MessageRepo", errMsg);
                bridge.onError(errMsg);
            }
        });
    }

    void clearInboxMessages(long conversationKey,
                            @Nullable Runnable onSuccess,
                            MessageService messageService,
                            PendingMessageRepository pendingMessageRepository,
                            MessageLocalDao messageLocalDao,
                            ExecutorService pendingStorageExecutor,
                            DeleteStateBridge bridge) {
        bridge.onLoadingChanged(true);
        messageService.clearInbox().enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                bridge.onLoadingChanged(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    bridge.clearError();
                    pendingStorageExecutor.execute(() -> {
                        messageLocalDao.clearConversation(conversationKey);
                        pendingMessageRepository.clearConversation(conversationKey);
                    });
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                    return;
                }
                String errMsg = response.body() == null ? "Failed to clear inbox" : response.body().getMessage();
                DebugLog.w("MessageRepo", errMsg);
                bridge.onError(errMsg);
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                bridge.onLoadingChanged(false);
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("MessageRepo", errMsg);
                bridge.onError(errMsg);
            }
        });
    }

    void deleteSingleMessage(long messageId,
                             @Nullable Runnable onSuccess,
                             MessageService messageService,
                             MessageLocalDao messageLocalDao,
                             ExecutorService pendingStorageExecutor,
                             DeleteStateBridge bridge) {
        bridge.onLoadingChanged(true);
        messageService.delete(messageId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                bridge.onLoadingChanged(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Void> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        bridge.clearError();
                        pendingStorageExecutor.execute(() -> messageLocalDao.deleteById(messageId));
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("MessageRepo", errMsg);
                        bridge.onError(errMsg);
                    }
                } else {
                    String errMsg = "Failed to delete message: " + response.code();
                    DebugLog.w("MessageRepo", errMsg);
                    bridge.onError(errMsg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                bridge.onLoadingChanged(false);
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("MessageRepo", errMsg);
                bridge.onError(errMsg);
            }
        });
    }

    private void deletePendingLocals(List<Long> pendingLocalIds, PendingMessageRepository pendingMessageRepository) {
        for (Long pendingLocalId : pendingLocalIds) {
            PendingMessage pendingMessage = pendingMessageRepository.findByLocalId(pendingLocalId);
            if (pendingMessage != null) {
                pendingMessageRepository.delete(pendingMessage);
            }
        }
    }
}
