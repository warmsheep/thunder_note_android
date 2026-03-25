package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import android.os.Looper;

import com.flashnote.java.DebugLog;
import com.flashnote.java.data.local.MessageLocalDao;
import com.flashnote.java.data.local.MessageLocalEntity;
import com.flashnote.java.data.model.CardItem;
import com.flashnote.java.data.model.CardPayload;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.MessageBatchDeleteRequest;
import com.flashnote.java.data.model.MessageListRequest;
import com.flashnote.java.data.model.PendingMessage;
import com.flashnote.java.data.remote.MessageService;
import com.flashnote.java.util.ConversationKeyUtil;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.HashMap;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MessageRepositoryImpl implements MessageRepository {
    private static final String MEDIA_TYPE_TEXT = "TEXT";
    private static final Gson GSON = new Gson();
    private static final Comparator<Message> MERGED_MESSAGE_COMPARATOR =
            Comparator.<Message>comparingLong(message -> messageSortTimestamp(message))
                    .thenComparingLong(message -> messageSourceOrder(message))
                    .thenComparingLong(message -> messageStableTieBreaker(message));
    private final MessageService messageService;
    private final PendingMessageRepository pendingMessageRepository;
    private final FileRepository fileRepository;
    private final VideoPreparationService videoPreparationService;
    private final MessageLocalDao messageLocalDao;
    private final PendingMessageDispatcher pendingMessageDispatcher;
    private final boolean enableTextPendingPipeline;
    private final ExecutorService pendingStorageExecutor = Executors.newSingleThreadExecutor();
    private final Map<Long, MutableLiveData<List<Message>>> conversations = new HashMap<>();
    private final Map<Long, MutableLiveData<List<Message>>> mergedConversations = new HashMap<>();
    private final Map<Long, LiveData<List<PendingMessage>>> pendingConversationSources = new HashMap<>();
    private final Map<Long, Observer<List<PendingMessage>>> pendingConversationObservers = new HashMap<>();
    private final Map<Long, List<PendingMessage>> pendingConversationCache = new HashMap<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final Map<Long, Integer> currentPages = new HashMap<>();
    private final Map<Long, Boolean> hasMoreMap = new HashMap<>();
    private final Map<Long, MutableLiveData<Boolean>> hasMoreLiveDataMap = new HashMap<>();
    private long currentFlashNoteId = 0L;
    private long currentPeerUserId = 0L;
    private long currentConversationKey = 0L;

    public MessageRepositoryImpl(MessageService messageService,
                                 PendingMessageRepository pendingMessageRepository,
                                 FileRepository fileRepository,
                                 VideoPreparationService videoPreparationService,
                                 MessageLocalDao messageLocalDao) {
        this(messageService, pendingMessageRepository, fileRepository, videoPreparationService, messageLocalDao,
                MessageFeatureFlags.ENABLE_TEXT_PENDING_PIPELINE);
    }

    MessageRepositoryImpl(MessageService messageService,
                         PendingMessageRepository pendingMessageRepository,
                         FileRepository fileRepository,
                         VideoPreparationService videoPreparationService,
                         MessageLocalDao messageLocalDao,
                         boolean enableTextPendingPipeline) {
        this.messageService = messageService;
        this.pendingMessageRepository = pendingMessageRepository;
        this.fileRepository = fileRepository;
        this.videoPreparationService = videoPreparationService;
        this.messageLocalDao = messageLocalDao;
        this.enableTextPendingPipeline = enableTextPendingPipeline;
        this.pendingMessageDispatcher = new PendingMessageDispatcher(pendingMessageRepository, messageService, fileRepository, videoPreparationService, this::onPendingUpdated);
    }

    @Override
    public LiveData<List<Message>> getMessages(long flashNoteId) {
        long conversationKey = ConversationKeyUtil.forFlashNote(flashNoteId);
        observeConfirmedConversation(conversationKey);
        return ensureMergedLiveData(conversationKey);
    }

    @Override
    public LiveData<List<Message>> getContactMessages(long peerUserId) {
        long conversationKey = ConversationKeyUtil.forContact(peerUserId);
        observeConfirmedConversation(conversationKey);
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
        observeConfirmedConversation(currentConversationKey);
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
        pendingMessageDispatcher.dispatchConversation(currentConversationKey);
    }

    @Override
    public void bindContact(long peerUserId) {
        currentPeerUserId = peerUserId;
        currentFlashNoteId = 0L;
        currentConversationKey = ConversationKeyUtil.forContact(peerUserId);
        observeConfirmedConversation(currentConversationKey);
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
        pendingMessageDispatcher.dispatchConversation(currentConversationKey);
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
    public void enqueueMedia(long flashNoteId, long peerUserId, String mediaType, File localFile, String fileName, Long fileSize, Integer mediaDuration, Runnable onSuccess) {
        if (localFile == null || !localFile.exists() || mediaType == null || mediaType.trim().isEmpty()) {
            setLiveDataValue(errorMessage, "本地文件不存在");
            return;
        }
        long conversationKey = peerUserId > 0L
                ? ConversationKeyUtil.forContact(peerUserId)
                : ConversationKeyUtil.forFlashNote(flashNoteId);
        PendingMessage pendingMessage = new PendingMessage();
        pendingMessage.setConversationKey(conversationKey);
        pendingMessage.setFlashNoteId(peerUserId > 0L ? null : flashNoteId);
        pendingMessage.setPeerUserId(peerUserId > 0L ? peerUserId : null);
        pendingMessage.setClientRequestId(UUID.randomUUID().toString());
        pendingMessage.setMediaType(mediaType);
        pendingMessage.setLocalFilePath(localFile.getAbsolutePath());
        pendingMessage.setProcessedFilePath(localFile.getAbsolutePath());
        pendingMessage.setFileName(fileName);
        pendingMessage.setFileSize(fileSize);
        pendingMessage.setMediaDuration(mediaDuration);
        if ("IMAGE".equalsIgnoreCase(mediaType) || "VIDEO".equalsIgnoreCase(mediaType)) {
            File thumbnailFile = ThumbnailUtils.createThumbnailFile(localFile, mediaType);
            if (thumbnailFile != null) {
                pendingMessage.setThumbnailUrl(thumbnailFile.getAbsolutePath());
            }
        }
        if ("VIDEO".equalsIgnoreCase(mediaType)) {
            pendingMessage.setProcessedFilePath(null);
        }
        pendingMessage.setContent(defaultMediaContent(mediaType));
        pendingMessage.setStatus(PendingMessageDispatcher.STATUS_QUEUED);
        pendingMessage.setCreatedAt(System.currentTimeMillis());
        if (onSuccess != null) {
            onSuccess.run();
        }
        pendingStorageExecutor.execute(() -> {
            long localId = pendingMessageRepository.insert(pendingMessage);
            pendingMessage.setLocalId(localId);
            pendingMessageDispatcher.dispatch(localId);
        });
    }

    @Override
    public void enqueueCompositeMessage(long flashNoteId, long peerUserId, Message message, SendCallback callback) {
        if (message == null || message.getPayload() == null) {
            if (callback != null) {
                callback.onError("卡片内容不能为空");
            }
            return;
        }
        long conversationKey = peerUserId > 0L
                ? ConversationKeyUtil.forContact(peerUserId)
                : ConversationKeyUtil.forFlashNote(flashNoteId);
        PendingMessage pendingMessage = new PendingMessage();
        pendingMessage.setConversationKey(conversationKey);
        pendingMessage.setFlashNoteId(peerUserId > 0L ? null : flashNoteId);
        pendingMessage.setPeerUserId(peerUserId > 0L ? peerUserId : null);
        pendingMessage.setClientRequestId(UUID.randomUUID().toString());
        pendingMessage.setMediaType("COMPOSITE");
        pendingMessage.setContent(message.getContent());
        pendingMessage.setFileName(message.getFileName());
        pendingMessage.setPayloadJson(GSON.toJson(message.getPayload()));
        pendingMessage.setStatus(PendingMessageDispatcher.STATUS_QUEUED);
        pendingMessage.setCreatedAt(System.currentTimeMillis());
        if (callback != null) {
            callback.onSuccess();
        }
        pendingStorageExecutor.execute(() -> {
            long localId = pendingMessageRepository.insert(pendingMessage);
            pendingMessage.setLocalId(localId);
            pendingMessageDispatcher.dispatch(localId);
        });
    }

    @Override
    public void retryPendingMessage(long localId) {
        pendingStorageExecutor.execute(() -> {
            PendingMessage pendingMessage = pendingMessageRepository.findByLocalId(localId);
            if (pendingMessage == null
                    || !PendingMessageDispatcher.STATUS_FAILED.equals(pendingMessage.getStatus())) {
                return;
            }
            pendingMessage.setStatus(PendingMessageDispatcher.STATUS_QUEUED);
            pendingMessage.setErrorMessage(null);
            pendingMessageRepository.update(pendingMessage);
            pendingMessageDispatcher.dispatch(localId);
        });
    }

    @Override
    public void retryPendingForCurrentConversation() {
        long conversationKey = currentConversationKey;
        if (conversationKey == 0L) {
            return;
        }
        pendingMessageDispatcher.dispatchConversation(conversationKey);
    }

    @Override
    public void retryAllPendingMessages() {
        pendingMessageDispatcher.dispatchAllPending();
    }

    private void sendTextInternal(long conversationKey,
                                  Long flashNoteId,
                                  Long peerUserId,
                                  String content,
                                  Runnable onSuccess) {
        if (!enableTextPendingPipeline) {
            Message message = new Message();
            message.setContent(content);
            message.setMediaType(MEDIA_TYPE_TEXT);
            sendMessageInternal(conversationKey, flashNoteId, peerUserId, message, runnableToCallback(onSuccess));
            return;
        }
        PendingMessage pendingMessage = new PendingMessage();
        pendingMessage.setConversationKey(conversationKey);
        pendingMessage.setFlashNoteId(flashNoteId);
        pendingMessage.setPeerUserId(peerUserId);
        pendingMessage.setClientRequestId(UUID.randomUUID().toString());
        pendingMessage.setMediaType(MEDIA_TYPE_TEXT);
        pendingMessage.setContent(content);
        pendingMessage.setStatus(PendingMessageDispatcher.STATUS_QUEUED);
        pendingMessage.setCreatedAt(System.currentTimeMillis());
        if (onSuccess != null) {
            onSuccess.run();
        }
        pendingStorageExecutor.execute(() -> {
            long localId = pendingMessageRepository.insert(pendingMessage);
            pendingMessage.setLocalId(localId);
            pendingMessageDispatcher.dispatch(localId);
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
                        List<Message> newMessages = apiResponse.getData();
                        persistRemoteMessages(conversationKey, newMessages, page == 1);
                        
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

    @Override
    public void deleteMessages(List<Long> messageIds, Runnable onSuccess) {
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
            pendingStorageExecutor.execute(() -> {
                for (Long pendingLocalId : pendingLocalIds) {
                    PendingMessage pendingMessage = pendingMessageRepository.findByLocalId(pendingLocalId);
                    if (pendingMessage != null) {
                        pendingMessageRepository.delete(pendingMessage);
                    }
                }
            });
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }
        isLoading.setValue(true);
        MessageBatchDeleteRequest request = new MessageBatchDeleteRequest();
        request.setIds(remoteIds);
        messageService.deleteBatch(request).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                isLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    clearError();
                    pendingStorageExecutor.execute(() -> {
                        for (Long id : remoteIds) {
                            if (id != null) {
                                messageLocalDao.deleteById(id);
                            }
                        }
                        for (Long pendingLocalId : pendingLocalIds) {
                            PendingMessage pendingMessage = pendingMessageRepository.findByLocalId(pendingLocalId);
                            if (pendingMessage != null) {
                                pendingMessageRepository.delete(pendingMessage);
                            }
                        }
                    });
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                    return;
                }
                String errMsg = response.body() == null ? "Failed to delete messages" : response.body().getMessage();
                DebugLog.w("MessageRepo", errMsg);
                errorMessage.setValue(errMsg);
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
    public void clearInboxMessages(Runnable onSuccess) {
        isLoading.setValue(true);
        long conversationKey = ConversationKeyUtil.forFlashNote(-1L);
        messageService.clearInbox().enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                isLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    clearError();
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
                errorMessage.setValue(errMsg);
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
                        pendingStorageExecutor.execute(() -> messageLocalDao.deleteById(messageId));
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
                        Message confirmed = apiResponse.getData();
                        persistSingleMessage(conversationKey, confirmed);
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
        persistSingleMessage(ConversationKeyUtil.forFlashNote(flashNoteId), message);
    }

    @Override
    public void removeLocalMessage(Message message) {
        if (message == null || message.getId() == null) {
            return;
        }
        pendingStorageExecutor.execute(() -> messageLocalDao.deleteById(message.getId()));
    }

    @Override
    public void addLocalContactMessage(long peerUserId, Message message) {
        persistSingleMessage(ConversationKeyUtil.forContact(peerUserId), message);
    }

    private MutableLiveData<List<Message>> ensureMergedLiveData(long conversationKey) {
        MutableLiveData<List<Message>> existing = mergedConversations.get(conversationKey);
        if (existing != null) {
            return existing;
        }
        MutableLiveData<List<Message>> created = new MutableLiveData<>(new ArrayList<>());
        mergedConversations.put(conversationKey, created);
        observePendingConversation(conversationKey);
        refreshMergedConversation(conversationKey);
        return created;
    }

    private void refreshMergedConversation(long conversationKey) {
        refreshMergedConversation(conversationKey, ensureLiveData(conversationKey).getValue(), pendingConversationCache.get(conversationKey));
    }

    private void refreshMergedConversation(long conversationKey, List<Message> remoteOverride) {
        refreshMergedConversation(conversationKey, remoteOverride, pendingConversationCache.get(conversationKey));
    }

    private void refreshMergedConversation(long conversationKey,
                                           List<Message> remoteOverride,
                                           List<PendingMessage> pendingOverride) {
        MutableLiveData<List<Message>> mergedLiveData = ensureMergedLiveDataInternal(conversationKey);
        List<Message> merged = buildMergedMessages(remoteOverride, pendingOverride);
        setLiveDataValue(mergedLiveData, merged);
    }

    static List<Message> buildMergedMessages(List<Message> remoteMessages,
                                             List<PendingMessage> pendingMessages) {
        List<Message> merged = new ArrayList<>();
        HashSet<Long> remoteIds = new HashSet<>();
        HashSet<String> remoteClientRequestIds = new HashSet<>();
        List<Message> normalizedRemote = remoteMessages == null ? Collections.emptyList() : remoteMessages;
        for (Message remoteMessage : normalizedRemote) {
            if (remoteMessage == null) {
                continue;
            }
            if (remoteMessage.getId() != null) {
                if (!remoteIds.add(remoteMessage.getId())) {
                    continue;
                }
            }
            String clientRequestId = normalizeClientRequestId(remoteMessage.getClientRequestId());
            if (!clientRequestId.isEmpty()) {
                remoteClientRequestIds.add(clientRequestId);
            }
            merged.add(remoteMessage);
        }
        if (pendingMessages != null) {
            for (PendingMessage pendingMessage : pendingMessages) {
                if (pendingMessage == null || PendingMessageDispatcher.STATUS_SENT.equals(pendingMessage.getStatus())) {
                    continue;
                }
                Long serverMessageId = pendingMessage.getServerMessageId();
                if (serverMessageId != null && remoteIds.contains(serverMessageId)) {
                    continue;
                }
                String pendingClientRequestId = normalizeClientRequestId(pendingMessage.getClientRequestId());
                if (!pendingClientRequestId.isEmpty() && remoteClientRequestIds.contains(pendingClientRequestId)) {
                    continue;
                }
                if (matchesRemoteFingerprint(merged, pendingMessage)) {
                    continue;
                }
                merged.add(toUiMessage(pendingMessage));
            }
        }
        merged.sort(MERGED_MESSAGE_COMPARATOR);
        return merged;
    }

    private static boolean matchesRemoteFingerprint(List<Message> remoteMessages, PendingMessage pendingMessage) {
        if (remoteMessages == null || remoteMessages.isEmpty() || pendingMessage == null) {
            return false;
        }
        if (!MEDIA_TYPE_TEXT.equalsIgnoreCase(nullToEmpty(pendingMessage.getMediaType()))) {
            return false;
        }
        String pendingContent = nullToEmpty(pendingMessage.getContent()).trim();
        if (pendingContent.isEmpty()) {
            return false;
        }
        long pendingCreatedAt = pendingMessage.getCreatedAt();
        for (Message remoteMessage : remoteMessages) {
            if (remoteMessage == null) {
                continue;
            }
            if (!"user".equalsIgnoreCase(nullToEmpty(remoteMessage.getRole()))) {
                continue;
            }
            if (!MEDIA_TYPE_TEXT.equalsIgnoreCase(nullToEmpty(remoteMessage.getMediaType()))) {
                continue;
            }
            if (!pendingContent.equals(nullToEmpty(remoteMessage.getContent()).trim())) {
                continue;
            }
            long remoteTimestamp = messageSortTimestamp(remoteMessage);
            if (remoteTimestamp == Long.MAX_VALUE) {
                continue;
            }
            if (Math.abs(remoteTimestamp - pendingCreatedAt) <= 2_000L) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeClientRequestId(String value) {
        return nullToEmpty(value).trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void observePendingConversation(long conversationKey) {
        if (pendingConversationObservers.containsKey(conversationKey)) {
            return;
        }
        LiveData<List<PendingMessage>> source = pendingMessageRepository.observeByConversationKey(conversationKey);
        Observer<List<PendingMessage>> observer = pendingMessages -> {
            List<PendingMessage> snapshot = pendingMessages == null
                    ? Collections.emptyList()
                    : new ArrayList<>(pendingMessages);
            pendingConversationCache.put(conversationKey, snapshot);
            refreshMergedConversation(conversationKey, ensureLiveData(conversationKey).getValue(), snapshot);
        };
        pendingConversationSources.put(conversationKey, source);
        pendingConversationObservers.put(conversationKey, observer);
        source.observeForever(observer);
    }

    private void observeConfirmedConversation(long conversationKey) {
        if (conversations.containsKey(conversationKey)) {
            return;
        }
        MutableLiveData<List<Message>> liveData = new MutableLiveData<>(new ArrayList<>());
        conversations.put(conversationKey, liveData);
        LiveData<List<MessageLocalEntity>> source = messageLocalDao.observeByConversationKey(conversationKey);
        source.observeForever(entities -> {
            List<Message> confirmed = toMessageList(entities);
            setLiveDataValue(liveData, confirmed);
            refreshMergedConversation(conversationKey, confirmed, pendingConversationCache.get(conversationKey));
        });
    }

    private static long messageSourceOrder(Message message) {
        return isPendingUiMessage(message) ? 1L : 0L;
    }

    private static long messageSortTimestamp(Message message) {
        if (message == null) {
            return Long.MAX_VALUE;
        }
        Long localSortTimestamp = message.getLocalSortTimestamp();
        if (localSortTimestamp != null) {
            return localSortTimestamp;
        }
        LocalDateTime createdAt = message.getCreatedAt();
        if (createdAt == null) {
            return Long.MAX_VALUE;
        }
        return createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
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

    private static Message toUiMessage(PendingMessage pendingMessage) {
        Message message = new Message();
        message.setId(-pendingMessage.getLocalId());
        message.setFlashNoteId(pendingMessage.getFlashNoteId());
        message.setReceiverId(pendingMessage.getPeerUserId());
        message.setClientRequestId(pendingMessage.getClientRequestId());
        message.setContent(pendingMessage.getContent());
        message.setMediaType(pendingMessage.getMediaType());
        message.setMediaUrl(pendingMessage.getRemoteUrl() != null ? pendingMessage.getRemoteUrl() : pendingMessage.getLocalFilePath());
        message.setThumbnailUrl(pendingMessage.getThumbnailUrl() != null ? pendingMessage.getThumbnailUrl() : pendingMessage.getRemoteUrl());
        message.setFileName(pendingMessage.getFileName());
        message.setFileSize(pendingMessage.getFileSize());
        message.setMediaDuration(pendingMessage.getMediaDuration());
        message.setRole("user");
        message.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(pendingMessage.getCreatedAt()), ZoneId.systemDefault()));
        message.setLocalSortTimestamp(pendingMessage.getCreatedAt());
        message.setUploading(!PendingMessageDispatcher.STATUS_FAILED.equals(pendingMessage.getStatus()));
        if (pendingMessage.getPayloadJson() != null && !pendingMessage.getPayloadJson().trim().isEmpty()) {
            try {
                message.setPayload(GSON.fromJson(pendingMessage.getPayloadJson(), CardPayload.class));
            } catch (JsonSyntaxException ex) {
                DebugLog.w("MessageRepo", "Failed to parse pending payloadJson: " + ex.getMessage());
            }
        }
        return message;
    }

    private String defaultMediaContent(String mediaType) {
        if ("IMAGE".equalsIgnoreCase(mediaType)) {
            return "[图片]";
        }
        if ("FILE".equalsIgnoreCase(mediaType)) {
            return "[文件]";
        }
        if ("VIDEO".equalsIgnoreCase(mediaType)) {
            return "[视频]";
        }
        if ("VOICE".equalsIgnoreCase(mediaType)) {
            return "[语音]";
        }
        if ("COMPOSITE".equalsIgnoreCase(mediaType)) {
            return "[卡片消息]";
        }
        return "[附件]";
    }

    private void onPendingUpdated(PendingMessage pendingMessage, Message serverMessage) {
        long conversationKey = pendingMessage.getConversationKey();
        if (serverMessage != null) {
            applyPendingMetadataToServerMessage(serverMessage, pendingMessage);
            persistSingleMessage(conversationKey, serverMessage);
            setLiveDataValue(errorMessage, null);
        } else if (PendingMessageDispatcher.STATUS_FAILED.equals(pendingMessage.getStatus())) {
            setLiveDataValue(errorMessage, pendingMessage.getErrorMessage());
            refreshMergedConversation(conversationKey);
        }
        setLiveDataValue(isLoading, PendingMessageDispatcher.STATUS_SENDING.equals(pendingMessage.getStatus()));
    }

    static void applyPendingMetadataToServerMessage(Message serverMessage, PendingMessage pendingMessage) {
        if (serverMessage == null || pendingMessage == null) {
            return;
        }
        serverMessage.setLocalSortTimestamp(pendingMessage.getCreatedAt());
        if (pendingMessage.getCreatedAt() > 0L) {
            serverMessage.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(pendingMessage.getCreatedAt()), ZoneId.systemDefault()));
        }
        if (serverMessage.getMediaType() == null) {
            serverMessage.setMediaType(pendingMessage.getMediaType());
        }
        if (serverMessage.getMediaUrl() == null) {
            serverMessage.setMediaUrl(pendingMessage.getRemoteUrl());
        }
        if (serverMessage.getThumbnailUrl() == null) {
            serverMessage.setThumbnailUrl(pendingMessage.getThumbnailUrl() != null ? pendingMessage.getThumbnailUrl() : pendingMessage.getRemoteUrl());
        }
        if (serverMessage.getMediaDuration() == null) {
            serverMessage.setMediaDuration(pendingMessage.getMediaDuration());
        }
        if (serverMessage.getFileName() == null) {
            serverMessage.setFileName(pendingMessage.getFileName());
        }
        if (serverMessage.getFileSize() == null) {
            serverMessage.setFileSize(pendingMessage.getFileSize());
        }
        if (serverMessage.getPayload() == null && pendingMessage.getPayloadJson() != null && !pendingMessage.getPayloadJson().trim().isEmpty()) {
            try {
                serverMessage.setPayload(GSON.fromJson(pendingMessage.getPayloadJson(), CardPayload.class));
            } catch (JsonSyntaxException ex) {
                DebugLog.w("MessageRepo", "Failed to apply pending payloadJson: " + ex.getMessage());
            }
        }
    }

    private <T> void setLiveDataValue(MutableLiveData<T> liveData, T value) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            liveData.setValue(value);
        } else {
            liveData.postValue(value);
        }
    }

    private void persistRemoteMessages(long conversationKey, List<Message> messages, boolean replaceConversation) {
        List<MessageLocalEntity> entities = toLocalMessageList(conversationKey, messages);
        pendingStorageExecutor.execute(() -> {
            Map<String, String> createdAtByClientRequestId = new HashMap<>();
            Map<Long, String> createdAtByMessageId = new HashMap<>();
            if (replaceConversation) {
                List<MessageLocalEntity> existingEntities = messageLocalDao.getByConversationKeyNow(conversationKey);
                for (MessageLocalEntity existing : existingEntities) {
                    if (existing == null) {
                        continue;
                    }
                    if (existing.getClientRequestId() != null && !existing.getClientRequestId().trim().isEmpty() && existing.getCreatedAt() != null) {
                        createdAtByClientRequestId.put(existing.getClientRequestId(), existing.getCreatedAt());
                    }
                    if (existing.getId() != null && existing.getCreatedAt() != null) {
                        createdAtByMessageId.put(existing.getId(), existing.getCreatedAt());
                    }
                }
            }
            for (MessageLocalEntity entity : entities) {
                if (entity == null) {
                    continue;
                }
                String preservedCreatedAt = null;
                if (entity.getClientRequestId() != null && !entity.getClientRequestId().trim().isEmpty()) {
                    preservedCreatedAt = createdAtByClientRequestId.get(entity.getClientRequestId());
                }
                if (preservedCreatedAt == null && entity.getId() != null) {
                    preservedCreatedAt = createdAtByMessageId.get(entity.getId());
                }
                if (preservedCreatedAt != null && !preservedCreatedAt.trim().isEmpty()) {
                    entity.setCreatedAt(preservedCreatedAt);
                }
            }
            if (replaceConversation) {
                messageLocalDao.clearConversation(conversationKey);
            }
            if (!entities.isEmpty()) {
                messageLocalDao.upsertAll(entities);
            }
        });
    }

    private void persistSingleMessage(long conversationKey, Message message) {
        MessageLocalEntity entity = toLocalMessage(conversationKey, message);
        if (entity == null) {
            return;
        }
        pendingStorageExecutor.execute(() -> messageLocalDao.upsert(entity));
    }

    private List<MessageLocalEntity> toLocalMessageList(long conversationKey, List<Message> messages) {
        List<MessageLocalEntity> entities = new ArrayList<>();
        if (messages == null) {
            return entities;
        }
        for (Message message : messages) {
            MessageLocalEntity entity = toLocalMessage(conversationKey, message);
            if (entity != null) {
                entities.add(entity);
            }
        }
        return entities;
    }

    private MessageLocalEntity toLocalMessage(long conversationKey, Message message) {
        if (message == null || message.getId() == null) {
            return null;
        }
        MessageLocalEntity entity = new MessageLocalEntity();
        entity.setId(message.getId());
        entity.setConversationKey(conversationKey);
        entity.setSenderId(message.getSenderId());
        entity.setReceiverId(message.getReceiverId());
        entity.setFlashNoteId(message.getFlashNoteId());
        entity.setClientRequestId(message.getClientRequestId());
        entity.setContent(message.getContent());
        entity.setReadStatus(message.getReadStatus());
        entity.setRole(message.getRole());
        entity.setCreatedAt(message.getCreatedAt() == null ? null : message.getCreatedAt().toString());
        entity.setMediaType(message.getMediaType());
        entity.setMediaUrl(message.getMediaUrl());
        entity.setMediaDuration(message.getMediaDuration());
        entity.setThumbnailUrl(message.getThumbnailUrl());
        entity.setFileName(message.getFileName());
        entity.setFileSize(message.getFileSize());
        entity.setPayloadJson(message.getPayload() == null ? null : GSON.toJson(message.getPayload()));
        return entity;
    }

    private List<Message> toMessageList(List<MessageLocalEntity> entities) {
        List<Message> result = new ArrayList<>();
        if (entities == null) {
            return result;
        }
        for (MessageLocalEntity entity : entities) {
            Message message = new Message();
            message.setId(entity.getId());
            message.setSenderId(entity.getSenderId());
            message.setReceiverId(entity.getReceiverId());
            message.setFlashNoteId(entity.getFlashNoteId());
            message.setClientRequestId(entity.getClientRequestId());
            message.setContent(entity.getContent());
            message.setReadStatus(entity.getReadStatus());
            message.setRole(entity.getRole());
            if (entity.getCreatedAt() != null && !entity.getCreatedAt().trim().isEmpty()) {
                message.setCreatedAt(LocalDateTime.parse(entity.getCreatedAt()));
            }
            message.setMediaType(entity.getMediaType());
            message.setMediaUrl(entity.getMediaUrl());
            message.setMediaDuration(entity.getMediaDuration());
            message.setThumbnailUrl(entity.getThumbnailUrl());
            message.setFileName(entity.getFileName());
            message.setFileSize(entity.getFileSize());
            if (entity.getPayloadJson() != null && !entity.getPayloadJson().trim().isEmpty()) {
                try {
                    message.setPayload(GSON.fromJson(entity.getPayloadJson(), CardPayload.class));
                } catch (JsonSyntaxException ex) {
                    DebugLog.w("MessageRepo", "Failed to parse payloadJson: " + ex.getMessage());
                }
            }
            result.add(message);
        }
        return result;
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
