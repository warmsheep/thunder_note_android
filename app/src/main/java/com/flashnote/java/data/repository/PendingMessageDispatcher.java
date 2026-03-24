package com.flashnote.java.data.repository;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.PendingMessage;
import com.flashnote.java.data.remote.MessageService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PendingMessageDispatcher {

    public interface Listener {
        void onPendingUpdated(PendingMessage pendingMessage, Message serverMessage);
    }

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_SENDING = "SENDING";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SENT = "SENT";

    private final PendingMessageRepository pendingMessageRepository;
    private final MessageService messageService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Listener listener;

    public PendingMessageDispatcher(PendingMessageRepository pendingMessageRepository,
                                    MessageService messageService,
                                    Listener listener) {
        this.pendingMessageRepository = pendingMessageRepository;
        this.messageService = messageService;
        this.listener = listener;
    }

    public void dispatch(long localId) {
        executor.execute(() -> {
            PendingMessage pendingMessage = pendingMessageRepository.findByLocalId(localId);
            if (pendingMessage == null) {
                return;
            }
            if (!STATUS_QUEUED.equals(pendingMessage.getStatus()) && !STATUS_FAILED.equals(pendingMessage.getStatus())) {
                return;
            }

            pendingMessage.setStatus(STATUS_SENDING);
            pendingMessage.setErrorMessage(null);
            pendingMessageRepository.update(pendingMessage);
            if (listener != null) {
                listener.onPendingUpdated(pendingMessage, null);
            }

            Message message = new Message();
            message.setFlashNoteId(pendingMessage.getFlashNoteId());
            message.setReceiverId(pendingMessage.getPeerUserId());
            message.setContent(pendingMessage.getContent());
            message.setRole("user");

            messageService.send(message).enqueue(new Callback<ApiResponse<Message>>() {
                @Override
                public void onResponse(Call<ApiResponse<Message>> call, Response<ApiResponse<Message>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess() && response.body().getData() != null) {
                        PendingMessage latest = pendingMessageRepository.findByLocalId(localId);
                        if (latest == null) {
                            return;
                        }
                        latest.setStatus(STATUS_SENT);
                        latest.setErrorMessage(null);
                        Message serverMessage = response.body().getData();
                        if (serverMessage.getId() != null) {
                            latest.setServerMessageId(serverMessage.getId());
                        }
                        pendingMessageRepository.update(latest);
                        if (listener != null) {
                            listener.onPendingUpdated(latest, serverMessage);
                        }
                        pendingMessageRepository.delete(latest);
                    } else {
                        fail(localId, response.body() != null ? response.body().getMessage() : "发送失败");
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<Message>> call, Throwable t) {
                    fail(localId, t == null ? "网络错误" : "网络错误: " + t.getMessage());
                }
            });
        });
    }

    private void fail(long localId, String errorMessage) {
        PendingMessage latest = pendingMessageRepository.findByLocalId(localId);
        if (latest == null) {
            return;
        }
        latest.setStatus(STATUS_FAILED);
        latest.setErrorMessage(errorMessage);
        latest.setAttemptCount(latest.getAttemptCount() + 1);
        pendingMessageRepository.update(latest);
        if (listener != null) {
            listener.onPendingUpdated(latest, null);
        }
    }
}
