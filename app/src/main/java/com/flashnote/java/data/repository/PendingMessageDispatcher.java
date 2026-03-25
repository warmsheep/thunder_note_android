package com.flashnote.java.data.repository;

import android.os.Handler;
import android.os.Looper;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.CardItem;
import com.flashnote.java.data.model.CardPayload;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.PendingMessage;
import com.flashnote.java.data.remote.MessageService;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PendingMessageDispatcher {
    private static final Gson GSON = new Gson();

    public interface Listener {
        void onPendingUpdated(PendingMessage pendingMessage, Message serverMessage);
    }

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_UPLOADING = "UPLOADING";
    public static final String STATUS_UPLOADED = "UPLOADED";
    public static final String STATUS_SENDING = "SENDING";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SENT = "SENT";
    private static final String ERROR_NETWORK = "网络错误";
    private static final String ERROR_FILE_MISSING = "本地文件不存在";
    private static final String ERROR_SERVER_REJECTED = "服务器拒绝";
    private static final String ERROR_TIMEOUT = "请求超时";
    private static final String ERROR_COMPRESSION = "压缩失败";

    private final PendingMessageRepository pendingMessageRepository;
    private final MessageService messageService;
    private final FileRepository fileRepository;
    private final VideoPreparationService videoPreparationService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public PendingMessageDispatcher(PendingMessageRepository pendingMessageRepository,
                                    MessageService messageService,
                                    FileRepository fileRepository,
                                    VideoPreparationService videoPreparationService,
                                    Listener listener) {
        this.pendingMessageRepository = pendingMessageRepository;
        this.messageService = messageService;
        this.fileRepository = fileRepository;
        this.videoPreparationService = videoPreparationService;
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

    void dispatchNow(long localId) {
        PendingMessage pendingMessage = pendingMessageRepository.findByLocalId(localId);
        if (!canDispatch(pendingMessage)) {
            return;
        }

        if (requiresUploadFirst(pendingMessage)) {
            uploadThenSend(localId, pendingMessage);
            return;
        }

        if (requiresCompositeUpload(pendingMessage)) {
            uploadCompositeThenSend(localId, pendingMessage);
            return;
        }

        if (requiresVideoUpload(pendingMessage)) {
            uploadThenSend(localId, pendingMessage);
            return;
        }

        if (requiresVideoPreparation(pendingMessage)) {
            prepareVideoThenUpload(localId, pendingMessage);
            return;
        }

        sendPendingMessage(localId, pendingMessage);
    }

    private void prepareVideoThenUpload(long localId, PendingMessage pendingMessage) {
        File localFile = pendingMessage.getLocalFilePath() == null ? null : new File(pendingMessage.getLocalFilePath());
        if (localFile == null || !localFile.exists()) {
            fail(localId, buildFailureMessage(ERROR_FILE_MISSING, null));
            return;
        }

        pendingMessage.setStatus(STATUS_PROCESSING);
        pendingMessage.setErrorMessage(null);
        pendingMessageRepository.update(pendingMessage);
        notifyListener(pendingMessage, null);

        videoPreparationService.prepareVideo(localFile, new VideoPreparationService.Callback() {
            @Override
            public void onSuccess(File processedFile) {
                executor.execute(() -> handleVideoPreparedNow(localId, processedFile));
            }

            @Override
            public void onError(String message) {
                executor.execute(() -> handleVideoPrepareFailureNow(localId, message == null ? ERROR_COMPRESSION : message));
            }
        });
    }

    void handleVideoPreparedNow(long localId, File processedFile) {
        PendingMessage latest = pendingMessageRepository.findByLocalId(localId);
        if (latest == null) {
            return;
        }
        latest.setProcessedFilePath(processedFile == null ? null : processedFile.getAbsolutePath());
        if (processedFile != null) {
            latest.setFileSize(processedFile.length());
        }
        pendingMessageRepository.update(latest);
        uploadThenSend(localId, latest);
    }

    void handleVideoPrepareFailureNow(long localId, String errorMessage) {
        fail(localId, buildFailureMessage(ERROR_COMPRESSION, errorMessage));
    }

    private void uploadThenSend(long localId, PendingMessage pendingMessage) {
        String uploadPath = pendingMessage.getProcessedFilePath() != null && !pendingMessage.getProcessedFilePath().trim().isEmpty()
                ? pendingMessage.getProcessedFilePath()
                : pendingMessage.getLocalFilePath();
        File localFile = uploadPath == null ? null : new File(uploadPath);
        if (localFile == null || !localFile.exists()) {
            fail(localId, buildFailureMessage(ERROR_FILE_MISSING, null));
            return;
        }

        pendingMessage.setStatus(STATUS_UPLOADING);
        pendingMessage.setErrorMessage(null);
        pendingMessageRepository.update(pendingMessage);
        notifyListener(pendingMessage, null);

        if (requiresThumbnailUpload(pendingMessage)) {
            File thumbnailFile = asLocalFile(pendingMessage.getThumbnailUrl());
            if (thumbnailFile != null && thumbnailFile.exists()) {
                fileRepository.upload(thumbnailFile, new FileRepository.FileCallback() {
                    @Override
                    public void onSuccess(String value) {
                        executor.execute(() -> {
                            PendingMessage latest = pendingMessageRepository.findByLocalId(localId);
                            if (latest == null) {
                                return;
                            }
                            latest.setThumbnailUrl(value);
                            pendingMessageRepository.update(latest);
                            continueUploadMedia(localId, latest, localFile);
                        });
                    }

                    @Override
                    public void onError(String message, int code) {
                        executor.execute(() -> handleUploadFailureNow(localId, message, code));
                    }
                });
                return;
            }
        }

        continueUploadMedia(localId, pendingMessage, localFile);
    }

    private void continueUploadMedia(long localId, PendingMessage pendingMessage, File localFile) {

        fileRepository.upload(localFile, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(String value) {
                executor.execute(() -> handleUploadSuccessNow(localId, value));
            }

            @Override
            public void onError(String message, int code) {
                executor.execute(() -> handleUploadFailureNow(localId, message, code));
            }
        });
    }

    private void uploadCompositeThenSend(long localId, PendingMessage pendingMessage) {
        pendingMessage.setStatus(STATUS_UPLOADING);
        pendingMessage.setErrorMessage(null);
        pendingMessageRepository.update(pendingMessage);
        notifyListener(pendingMessage, null);

        CardPayload payload = parsePayload(pendingMessage.getPayloadJson());
        if (payload == null || payload.getItems() == null) {
            fail(localId, buildFailureMessage(ERROR_FILE_MISSING, "卡片内容无效"));
            return;
        }
        uploadCompositeItemAt(localId, payload, 0);
    }

    private void uploadCompositeItemAt(long localId, CardPayload payload, int index) {
        if (payload.getItems() == null || index >= payload.getItems().size()) {
            PendingMessage latest = pendingMessageRepository.findByLocalId(localId);
            if (latest == null) {
                return;
            }
            latest.setPayloadJson(GSON.toJson(payload));
            pendingMessageRepository.update(latest);
            sendPendingMessage(localId, latest);
            return;
        }
        CardItem item = payload.getItems().get(index);
        if (item == null || item.getLocalPath() == null || item.getLocalPath().trim().isEmpty() || "TEXT".equalsIgnoreCase(item.getType())) {
            uploadCompositeItemAt(localId, payload, index + 1);
            return;
        }
        File localFile = new File(item.getLocalPath());
        if (!localFile.exists()) {
            fail(localId, buildFailureMessage(ERROR_FILE_MISSING, localFile.getName()));
            return;
        }
        uploadCompositeThumbnailIfNeeded(localId, item, localFile, () -> fileRepository.upload(localFile, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(String value) {
                executor.execute(() -> {
                    item.setUrl(value);
                    item.setLocalPath(null);
                    PendingMessage latest = pendingMessageRepository.findByLocalId(localId);
                    if (latest != null) {
                        latest.setPayloadJson(GSON.toJson(payload));
                        pendingMessageRepository.update(latest);
                        notifyListener(latest, null);
                    }
                    uploadCompositeItemAt(localId, payload, index + 1);
                });
            }

            @Override
            public void onError(String message, int code) {
                executor.execute(() -> handleUploadFailureNow(localId, message, code));
            }
        }));
    }

    private void uploadCompositeThumbnailIfNeeded(long localId, CardItem item, File localFile, Runnable continuation) {
        if (!("IMAGE".equalsIgnoreCase(item.getType()) || "VIDEO".equalsIgnoreCase(item.getType()))) {
            continuation.run();
            return;
        }
        File thumbnailFile = asLocalFile(item.getThumbnailUrl());
        if (thumbnailFile == null || !thumbnailFile.exists()) {
            thumbnailFile = ThumbnailUtils.createThumbnailFile(localFile, item.getType());
            if (thumbnailFile != null) {
                item.setThumbnailUrl(thumbnailFile.getAbsolutePath());
            }
        }
        if (thumbnailFile == null || !thumbnailFile.exists()) {
            continuation.run();
            return;
        }
        fileRepository.upload(thumbnailFile, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(String value) {
                executor.execute(() -> {
                    item.setThumbnailUrl(value);
                    continuation.run();
                });
            }

            @Override
            public void onError(String message, int code) {
                executor.execute(() -> handleUploadFailureNow(localId, message, code));
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
        handleUploadFailureNow(localId, errorMessage, -1);
    }

    void handleUploadFailureNow(long localId, String errorMessage, int code) {
        fail(localId, classifyRepositoryError(errorMessage, code));
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
        message.setThumbnailUrl(isLocalReference(pendingMessage.getThumbnailUrl()) ? null : pendingMessage.getThumbnailUrl());
        message.setFileName(pendingMessage.getFileName());
        message.setFileSize(pendingMessage.getFileSize());
        message.setRole("user");
        if ("COMPOSITE".equalsIgnoreCase(pendingMessage.getMediaType())) {
            CardPayload payload = parsePayload(pendingMessage.getPayloadJson());
            if (payload != null) {
                sanitizePayloadForServer(payload);
                message.setPayload(payload);
            }
        }

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
                        String bodyMessage = response.body() != null ? response.body().getMessage() : null;
                        fail(localId, buildFailureMessage(ERROR_SERVER_REJECTED, bodyMessage == null ? String.valueOf(response.code()) : bodyMessage));
                    }
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<Message>> call, Throwable t) {
                executor.execute(() -> fail(localId, classifyThrowable(t)));
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
        if (!("IMAGE".equalsIgnoreCase(mediaType) || "FILE".equalsIgnoreCase(mediaType) || "VOICE".equalsIgnoreCase(mediaType))) {
            return false;
        }
        String remoteUrl = pendingMessage.getRemoteUrl();
        return remoteUrl == null || remoteUrl.trim().isEmpty();
    }

    private boolean requiresVideoPreparation(PendingMessage pendingMessage) {
        if (pendingMessage == null) {
            return false;
        }
        if (!"VIDEO".equalsIgnoreCase(pendingMessage.getMediaType())) {
            return false;
        }
        String remoteUrl = pendingMessage.getRemoteUrl();
        if (remoteUrl != null && !remoteUrl.trim().isEmpty()) {
            return false;
        }
        String processedFilePath = pendingMessage.getProcessedFilePath();
        return processedFilePath == null || processedFilePath.trim().isEmpty();
    }

    private boolean requiresVideoUpload(PendingMessage pendingMessage) {
        if (pendingMessage == null) {
            return false;
        }
        if (!"VIDEO".equalsIgnoreCase(pendingMessage.getMediaType())) {
            return false;
        }
        String remoteUrl = pendingMessage.getRemoteUrl();
        if (remoteUrl != null && !remoteUrl.trim().isEmpty()) {
            return false;
        }
        String processedFilePath = pendingMessage.getProcessedFilePath();
        return processedFilePath != null && !processedFilePath.trim().isEmpty();
    }

    private boolean requiresCompositeUpload(PendingMessage pendingMessage) {
        if (pendingMessage == null || !"COMPOSITE".equalsIgnoreCase(pendingMessage.getMediaType())) {
            return false;
        }
        CardPayload payload = parsePayload(pendingMessage.getPayloadJson());
        if (payload == null || payload.getItems() == null) {
            return false;
        }
        for (CardItem item : payload.getItems()) {
            if (item != null && item.getLocalPath() != null && !item.getLocalPath().trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean requiresThumbnailUpload(PendingMessage pendingMessage) {
        if (pendingMessage == null) {
            return false;
        }
        String mediaType = pendingMessage.getMediaType();
        if (!("IMAGE".equalsIgnoreCase(mediaType) || "VIDEO".equalsIgnoreCase(mediaType))) {
            return false;
        }
        return isLocalReference(pendingMessage.getThumbnailUrl());
    }

    private boolean isLocalReference(String value) {
        return value != null && !value.trim().isEmpty() && (value.startsWith("/") || value.startsWith("file://"));
    }

    private File asLocalFile(String value) {
        if (!isLocalReference(value)) {
            return null;
        }
        String path = value.startsWith("file://") ? value.substring("file://".length()) : value;
        return new File(path);
    }

    private CardPayload parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.trim().isEmpty()) {
            return null;
        }
        try {
            return GSON.fromJson(payloadJson, CardPayload.class);
        } catch (JsonSyntaxException ignored) {
            return null;
        }
    }

    private void sanitizePayloadForServer(CardPayload payload) {
        if (payload == null || payload.getItems() == null) {
            return;
        }
        for (CardItem item : payload.getItems()) {
            if (item == null) {
                continue;
            }
            item.setLocalPath(null);
            if (isLocalReference(item.getUrl())) {
                item.setUrl(null);
            }
            if (isLocalReference(item.getThumbnailUrl())) {
                item.setThumbnailUrl(null);
            }
        }
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

    private String classifyThrowable(Throwable throwable) {
        if (throwable instanceof SocketTimeoutException) {
            return buildFailureMessage(ERROR_TIMEOUT, throwable.getMessage());
        }
        String message = throwable == null ? null : throwable.getMessage();
        if (message != null && message.toLowerCase().contains("timeout")) {
            return buildFailureMessage(ERROR_TIMEOUT, message);
        }
        return buildFailureMessage(ERROR_NETWORK, message);
    }

    private String classifyRepositoryError(String message, int code) {
        if (message != null && message.contains(ERROR_FILE_MISSING)) {
            return buildFailureMessage(ERROR_FILE_MISSING, null);
        }
        if (message != null && message.toLowerCase().contains("timeout")) {
            return buildFailureMessage(ERROR_TIMEOUT, message);
        }
        if (code > 0) {
            return buildFailureMessage(ERROR_SERVER_REJECTED, message == null ? String.valueOf(code) : message);
        }
        return buildFailureMessage(ERROR_NETWORK, message);
    }

    private String buildFailureMessage(String category, String detail) {
        if (detail == null || detail.trim().isEmpty() || category.equals(detail.trim())) {
            return category;
        }
        return category + ": " + detail.trim();
    }

    private void notifyListener(PendingMessage pendingMessage, Message serverMessage) {
        if (listener == null) {
            return;
        }
        mainHandler.post(() -> listener.onPendingUpdated(pendingMessage, serverMessage));
    }
}
