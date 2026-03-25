package com.flashnote.java.data.repository;

import android.os.Handler;
import android.os.Looper;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.PendingMessage;
import com.flashnote.java.data.remote.MessageService;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PendingMessageDispatcher {

    public interface Listener {
        void onPendingUpdated(PendingMessage pendingMessage, Message serverMessage);
    }

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_UPLOADING = "UPLOADING";
    public static final String STATUS_UPLOADED = "UPLOADED";
    public static final String STATUS_SENDING = "SENDING";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SENT = "SENT";

    private final PendingMessageRepository pendingMessageRepository;
    private final MessageService messageService;
    private final FileRepository fileRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public PendingMessageDispatcher(PendingMessageRepository pendingMessageRepository,
                                    MessageService messageService,
                                    FileRepository fileRepository,
                                    Listener listener) {
        this.pendingMessageRepository = pendingMessageRepository;
        this.messageService = messageService;
        this.fileRepository = fileRepository;
        this.listener = listener;
    }

    public void dispatch(long localId) {
        executor.execute(() -> dispatchNow(localId));
    }

    public void dispatchConversation(long conversationKey) {
        executor.execute(() -> {
            dispatchConversationNow(conversationKey);
        });
    }

    public void dispatchAllPending() {
        executor.execute(() -> {
            dispatchAllPendingNow();
        });
    }

    void dispatchConversationNow(long conversationKey) {
        List<PendingMessage> pendingMessages = pendingMessageRepository.getByConversationKey(conversationKey);
        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return;
        }
        for (PendingMessage pendingMessage : pendingMessages) {
            if (pendingMessage == null || !canDispatch(pendingMessage)) {
                continue;
            }
            dispatchNow(pendingMessage.getLocalId());
        }
    }

    void dispatchAllPendingNow() {
        dispatchPendingList(pendingMessageRepository.getByStatus(STATUS_QUEUED));
        dispatchPendingList(pendingMessageRepository.getByStatus(STATUS_FAILED));
    }

    private void dispatchPendingList(List<PendingMessage> pendingMessages) {
        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return;
        }
        for (PendingMessage pendingMessage : pendingMessages) {
            if (pendingMessage == null || !canDispatch(pendingMessage)) {
                continue;
            }
            dispatchNow(pendingMessage.getLocalId());
        }
    }

    private void dispatchNow(long localId) {
        PendingMessage pendingMessage = pendingMessageRepository.findByLocalId(localId);
        if (!canDispatch(pendingMessage)) {
            return;
        }

        if (requiresUploadFirst(pendingMessage)) {
            uploadThenSend(localId, pendingMessage);
            return;
        }

        sendPendingMessage(localId, pendingMessage);
    }

    private void uploadThenSend(long localId, PendingMessage pendingMessage) {
        File localFile = pendingMessage.getLocalFilePath() == null ? null : new File(pendingMessage.getLocalFilePath());
        if (localFile == null || !localFile.exists()) {
            fail(localId, "本地文件不存在");
            return;
        }

        pendingMessage.setStatus(STATUS_UPLOADING);
        pendingMessage.setErrorMessage(null);
        pendingMessageRepository.update(pendingMessage);
        notifyListener(pendingMessage, null);

        fileRepository.upload(localFile, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(String value) {
                executor.execute(() -> handleUploadSuccessNow(localId, value));
            }

            @Override
            public void onError(String message, int code) {
                executor.execute(() -> handleUploadFailureNow(localId, message == null ? "上传失败" : message));
            }
        });
    }

    void handleUploadSuccessNow(long localId, String remoteUrl) {
        PendingMessage latest = pendingMessageRepository.findByLocalId(localId);
        if (latest == null) {
            return;
        }
        latest.setRemoteUrl(remoteUrl);
        latest.setStatus(STATUS_UPLOADED);
        latest.setErrorMessage(null);
        pendingMessageRepository.update(latest);
        notifyListener(latest, null);
        sendPendingMessage(localId, latest);
    }

    void handleUploadFailureNow(long localId, String errorMessage) {
        fail(localId, errorMessage == null ? "上传失败" : errorMessage);
    }

    private void sendPendingMessage(long localId, PendingMessage pendingMessage) {

        pendingMessage.setStatus(STATUS_SENDING);
        pendingMessage.setErrorMessage(null);
        pendingMessageRepository.update(pendingMessage);
        notifyListener(pendingMessage, null);

        Message message = new Message();
        message.setFlashNoteId(pendingMessage.getFlashNoteId());
        message.setReceiverId(pendingMessage.getPeerUserId());
        message.setContent(pendingMessage.getContent());
        message.setClientRequestId(pendingMessage.getClientRequestId());
        message.setMediaType(pendingMessage.getMediaType());
        message.setMediaUrl(pendingMessage.getRemoteUrl());
        message.setFileName(pendingMessage.getFileName());
        message.setFileSize(pendingMessage.getFileSize());
        message.setRole("user");

        messageService.send(message).enqueue(new Callback<ApiResponse<Message>>() {
            @Override
            public void onResponse(Call<ApiResponse<Message>> call, Response<ApiResponse<Message>> response) {
                executor.execute(() -> {
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
                        notifyListener(latest, serverMessage);
                        pendingMessageRepository.delete(latest);
                    } else {
                        fail(localId, response.body() != null ? response.body().getMessage() : "发送失败");
                    }
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<Message>> call, Throwable t) {
                executor.execute(() -> fail(localId, t == null ? "网络错误" : "网络错误: " + t.getMessage()));
            }
        });
    }

    private boolean canDispatch(PendingMessage pendingMessage) {
        if (pendingMessage == null) {
            return false;
        }
        String status = pendingMessage.getStatus();
        return STATUS_QUEUED.equals(status) || STATUS_FAILED.equals(status);
    }

    private boolean requiresUploadFirst(PendingMessage pendingMessage) {
        if (pendingMessage == null) {
            return false;
        }
        String mediaType = pendingMessage.getMediaType();
        if (!("IMAGE".equalsIgnoreCase(mediaType) || "FILE".equalsIgnoreCase(mediaType))) {
            return false;
        }
        String remoteUrl = pendingMessage.getRemoteUrl();
        return remoteUrl == null || remoteUrl.trim().isEmpty();
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
        notifyListener(latest, null);
    }

    private void notifyListener(PendingMessage pendingMessage, Message serverMessage) {
        if (listener == null) {
            return;
        }
        mainHandler.post(() -> listener.onPendingUpdated(pendingMessage, serverMessage));
    }
}
