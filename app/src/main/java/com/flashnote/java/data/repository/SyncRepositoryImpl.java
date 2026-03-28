package com.flashnote.java.data.repository;

import com.flashnote.java.DebugLog;
import com.flashnote.java.TokenManager;
import com.flashnote.java.data.local.CollectionLocalDao;
import com.flashnote.java.data.local.CollectionLocalEntity;
import com.flashnote.java.data.local.FavoriteLocalDao;
import com.flashnote.java.data.local.FavoriteLocalEntity;
import com.flashnote.java.data.local.FlashNoteLocalDao;
import com.flashnote.java.data.local.FlashNoteLocalEntity;
import com.flashnote.java.data.local.MessageLocalDao;
import com.flashnote.java.data.local.MessageLocalEntity;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.Collection;
import com.flashnote.java.data.model.FavoriteItem;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.PendingMessage;
import com.flashnote.java.data.model.SyncPullRequest;
import com.flashnote.java.data.remote.SyncService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SyncRepositoryImpl implements SyncRepository {
    private static final List<String> PUSHABLE_PENDING_STATUSES = List.of(
            PendingMessageDispatcher.STATUS_QUEUED,
            PendingMessageDispatcher.STATUS_PROCESSING,
            PendingMessageDispatcher.STATUS_UPLOADING,
            PendingMessageDispatcher.STATUS_UPLOADED,
            PendingMessageDispatcher.STATUS_SENDING,
            PendingMessageDispatcher.STATUS_FAILED
    );

    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
    private final SyncService syncService;
    private final TokenManager tokenManager;
    private final FlashNoteLocalDao flashNoteLocalDao;
    private final CollectionLocalDao collectionLocalDao;
    private final FavoriteLocalDao favoriteLocalDao;
    private final MessageLocalDao messageLocalDao;
    private final PendingMessageRepository pendingMessageRepository;
    private final FlashNoteRepository flashNoteRepository;
    private final CollectionRepository collectionRepository;
    private final FavoriteRepository favoriteRepository;
    private final MessageRepository messageRepository;

    public SyncRepositoryImpl(SyncService syncService,
                              TokenManager tokenManager,
                              FlashNoteLocalDao flashNoteLocalDao,
                              CollectionLocalDao collectionLocalDao,
                              FavoriteLocalDao favoriteLocalDao,
                              MessageLocalDao messageLocalDao,
                              PendingMessageRepository pendingMessageRepository,
                              FlashNoteRepository flashNoteRepository,
                              CollectionRepository collectionRepository,
                              FavoriteRepository favoriteRepository,
                              MessageRepository messageRepository) {
        this.syncService = syncService;
        this.tokenManager = tokenManager;
        this.flashNoteLocalDao = flashNoteLocalDao;
        this.collectionLocalDao = collectionLocalDao;
        this.favoriteLocalDao = favoriteLocalDao;
        this.messageLocalDao = messageLocalDao;
        this.pendingMessageRepository = pendingMessageRepository;
        this.flashNoteRepository = flashNoteRepository;
        this.collectionRepository = collectionRepository;
        this.favoriteRepository = favoriteRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public void bootstrap(SyncCallback callback) {
        syncService.bootstrap().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, 
                                 Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Map<String, Object>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        callback.onSuccess(apiResponse.getData());
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("SyncRepo", errMsg);
                        callback.onError(errMsg, apiResponse.getCode());
                    }
                } else {
                    String errMsg = "Bootstrap failed: " + response.code();
                    DebugLog.w("SyncRepo", errMsg);
                    callback.onError(errMsg, response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("SyncRepo", errMsg);
                callback.onError(errMsg, -1);
            }
        });
    }

    @Override
    public void pull(SyncCallback callback) {
        syncExecutor.execute(() -> {
            SyncPullRequest request = buildPullRequest();
            syncService.pull(request).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                       Response<ApiResponse<Map<String, Object>>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        ApiResponse<Map<String, Object>> apiResponse = response.body();
                        if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                            persistPulledMessages(apiResponse.getData());
                            callback.onSuccess(apiResponse.getData());
                        } else {
                            String errMsg = apiResponse.getMessage();
                            DebugLog.w("SyncRepo", errMsg);
                            callback.onError(errMsg, apiResponse.getCode());
                        }
                    } else {
                        String errMsg = "Pull failed: " + response.code();
                        DebugLog.w("SyncRepo", errMsg);
                        callback.onError(errMsg, response.code());
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                    String errMsg = "Network error: " + t.getMessage();
                    DebugLog.w("SyncRepo", errMsg);
                    callback.onError(errMsg, -1);
                }
            });
        });
    }

    @Override
    public void pullAndRefreshLocal(SyncCallback callback) {
        pull(new SyncCallback() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                flashNoteRepository.refresh();
                collectionRepository.refresh();
                favoriteRepository.refresh();
                if (callback != null) {
                    callback.onSuccess(data);
                }
            }

            @Override
            public void onError(String message, int code) {
                if (callback != null) {
                    callback.onError(message, code);
                }
            }
        });
    }

    @Override
    public void syncAll(SyncCallback callback) {
        messageRepository.retryAllPendingMessages();
        pullAndRefreshLocal(new SyncCallback() {
            @Override
            public void onSuccess(Map<String, Object> pullData) {
                syncExecutor.execute(() -> {
                    Map<String, Object> payload = buildLocalStatePayload();

                    push(payload, new SyncCallback() {
                        @Override
                        public void onSuccess(Map<String, Object> pushData) {
                            Map<String, Object> mergedResult = new HashMap<>();
                            mergedResult.put("pull", pullData == null ? Map.of() : pullData);
                            mergedResult.put("push", pushData == null ? Map.of() : pushData);
                            if (callback != null) {
                                callback.onSuccess(mergedResult);
                            }
                        }

                        @Override
                        public void onError(String message, int code) {
                            if (callback != null) {
                                callback.onError(message, code);
                            }
                        }
                    });
                });
            }

            @Override
            public void onError(String message, int code) {
                if (callback != null) {
                    callback.onError(message, code);
                }
            }
        });
    }

    @Override
    public void getPendingSyncCount(CountCallback callback) {
        syncExecutor.execute(() -> {
            int count = pendingMessageRepository == null ? 0 : pendingMessageRepository.getPendingSyncCountNow();
            if (callback != null) {
                callback.onResult(count);
            }
        });
    }

    @Override
    public void pushLocalState(SyncCallback callback) {
        syncExecutor.execute(() -> push(buildLocalStatePayload(), callback));
    }

    @Override
    public void push(Map<String, Object> payload, SyncCallback callback) {
        syncService.push(payload).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, 
                                 Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Map<String, Object>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        callback.onSuccess(apiResponse.getData());
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("SyncRepo", errMsg);
                        callback.onError(errMsg, apiResponse.getCode());
                    }
                } else {
                    String errMsg = "Push failed: " + response.code();
                    DebugLog.w("SyncRepo", errMsg);
                    callback.onError(errMsg, response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("SyncRepo", errMsg);
                callback.onError(errMsg, -1);
            }
        });
    }

    Map<String, Object> buildLocalStatePayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("notes", safeList(loadLocalNotes()));
        payload.put("collections", safeList(loadLocalCollections()));
        payload.put("messages", List.of());
        payload.put("favorites", safeList(loadLocalFavorites()));
        return payload;
    }

    private List<FlashNote> loadLocalNotes() {
        long userId = requireCurrentUserId();
        List<FlashNoteLocalEntity> entities = flashNoteLocalDao.getAllNowByUserId(userId);
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<FlashNote> result = new java.util.ArrayList<>();
        for (FlashNoteLocalEntity entity : entities) {
            FlashNote note = new FlashNote();
            note.setId(entity.getId());
            note.setUserId(entity.getUserId());
            note.setTitle(entity.getTitle());
            note.setIcon(entity.getIcon());
            note.setContent(entity.getContent());
            note.setLatestMessage(entity.getLatestMessage());
            note.setTags(entity.getTags());
            note.setDeleted(entity.getDeleted());
            note.setPinned(entity.getPinned());
            note.setHidden(entity.getHidden());
            note.setInbox(entity.getInbox());
            note.setCreatedAt(parseDateTime(entity.getCreatedAt()));
            note.setUpdatedAt(parseDateTime(entity.getUpdatedAt()));
            result.add(note);
        }
        return result;
    }

    private List<Collection> loadLocalCollections() {
        long userId = requireCurrentUserId();
        List<CollectionLocalEntity> entities = collectionLocalDao.getAllNowByUserId(userId);
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<Collection> result = new java.util.ArrayList<>();
        for (CollectionLocalEntity entity : entities) {
            Collection collection = new Collection();
            collection.setId(entity.getId());
            collection.setUserId(entity.getUserId());
            collection.setName(entity.getName());
            collection.setDescription(entity.getDescription());
            collection.setCreatedAt(parseDateTime(entity.getCreatedAt()));
            collection.setUpdatedAt(parseDateTime(entity.getUpdatedAt()));
            result.add(collection);
        }
        return result;
    }

    private List<FavoriteItem> loadLocalFavorites() {
        long userId = requireCurrentUserId();
        List<FavoriteLocalEntity> entities = favoriteLocalDao.getAllNowByUserId(userId);
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<FavoriteItem> result = new java.util.ArrayList<>();
        for (FavoriteLocalEntity entity : entities) {
            FavoriteItem favorite = new FavoriteItem();
            favorite.setId(entity.getId());
            favorite.setMessageId(entity.getMessageId());
            favorite.setFlashNoteId(entity.getFlashNoteId());
            favorite.setFlashNoteTitle(entity.getFlashNoteTitle());
            favorite.setFlashNoteIcon(entity.getFlashNoteIcon());
            favorite.setRole(entity.getRole());
            favorite.setContent(entity.getContent());
            favorite.setMediaType(entity.getMediaType());
            favorite.setMediaUrl(entity.getMediaUrl());
            favorite.setMediaDuration(entity.getMediaDuration());
            favorite.setFileName(entity.getFileName());
            favorite.setFileSize(entity.getFileSize());
            favorite.setFavoritedAt(parseDateTime(entity.getFavoritedAt()));
            favorite.setMessageCreatedAt(parseDateTime(entity.getMessageCreatedAt()));
            result.add(favorite);
        }
        return result;
    }

    private Map<String, Object> toSyncMessagePayload(PendingMessage pendingMessage) {
        Map<String, Object> payload = new HashMap<>();
        if (pendingMessage == null) {
            return payload;
        }
        Long senderId = tokenManager.getUserId();
        if (senderId != null) {
            payload.put("senderId", senderId);
        }
        if (pendingMessage.getPeerUserId() != null) {
            payload.put("receiverId", pendingMessage.getPeerUserId());
        }
        if (pendingMessage.getFlashNoteId() != null) {
            payload.put("flashNoteId", pendingMessage.getFlashNoteId());
        }
        if (pendingMessage.getClientRequestId() != null) {
            payload.put("clientRequestId", pendingMessage.getClientRequestId());
        }
        if (pendingMessage.getContent() != null) {
            payload.put("content", pendingMessage.getContent());
        }
        if (pendingMessage.getMediaType() != null) {
            payload.put("mediaType", pendingMessage.getMediaType());
        }
        if (pendingMessage.getRemoteUrl() != null) {
            payload.put("mediaUrl", pendingMessage.getRemoteUrl());
        }
        if (pendingMessage.getMediaDuration() != null) {
            payload.put("mediaDuration", pendingMessage.getMediaDuration());
        }
        if (pendingMessage.getThumbnailUrl() != null) {
            payload.put("thumbnailUrl", pendingMessage.getThumbnailUrl());
        }
        if (pendingMessage.getFileName() != null) {
            payload.put("fileName", pendingMessage.getFileName());
        }
        if (pendingMessage.getFileSize() != null) {
            payload.put("fileSize", pendingMessage.getFileSize());
        }
        if (pendingMessage.getPayloadJson() != null) {
            payload.put("payloadJson", pendingMessage.getPayloadJson());
        }
        payload.put("role", "user");
        payload.put("readStatus", false);
        payload.put("createdAt", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(pendingMessage.getCreatedAt()),
                        ZoneId.systemDefault()
                )
        ));
        return payload;
    }

    private SyncPullRequest buildPullRequest() {
        SyncPullRequest request = new SyncPullRequest();
        request.setLastMessageCreatedAt(messageLocalDao.getLatestCreatedAt());
        return request;
    }

    @SuppressWarnings("unchecked")
    private void persistPulledMessages(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        Object messagesRaw = data.get("messages");
        if (!(messagesRaw instanceof List<?> messageMaps) || messageMaps.isEmpty()) {
            return;
        }
        List<MessageLocalEntity> entities = new ArrayList<>();
        for (Object rawItem : messageMaps) {
            if (!(rawItem instanceof Map<?, ?> map)) {
                continue;
            }
            MessageLocalEntity entity = toMessageLocalEntity((Map<String, Object>) map);
            if (entity != null) {
                entities.add(entity);
            }
        }
        if (!entities.isEmpty()) {
            syncExecutor.execute(() -> messageLocalDao.upsertAll(entities));
        }
    }

    private MessageLocalEntity toMessageLocalEntity(Map<String, Object> raw) {
        Object idValue = raw.get("id");
        if (!(idValue instanceof Number idNumber)) {
            return null;
        }
        MessageLocalEntity entity = new MessageLocalEntity();
        entity.setId(idNumber.longValue());
        Object senderIdValue = raw.get("senderId");
        if (senderIdValue instanceof Number senderId) {
            entity.setSenderId(senderId.longValue());
        }
        Object receiverIdValue = raw.get("receiverId");
        if (receiverIdValue instanceof Number receiverId) {
            entity.setReceiverId(receiverId.longValue());
        }
        Object flashNoteIdValue = raw.get("flashNoteId");
        if (flashNoteIdValue instanceof Number flashNoteId) {
            entity.setFlashNoteId(flashNoteId.longValue());
        }
        if (raw.get("clientRequestId") != null) {
            entity.setClientRequestId(String.valueOf(raw.get("clientRequestId")));
        }
        if (raw.get("content") != null) {
            entity.setContent(String.valueOf(raw.get("content")));
        }
        if (raw.get("readStatus") instanceof Boolean readStatus) {
            entity.setReadStatus(readStatus);
        }
        if (raw.get("role") != null) {
            entity.setRole(String.valueOf(raw.get("role")));
        }
        if (raw.get("createdAt") != null) {
            entity.setCreatedAt(String.valueOf(raw.get("createdAt")));
        }
        if (raw.get("mediaType") != null) {
            entity.setMediaType(String.valueOf(raw.get("mediaType")));
        }
        if (raw.get("mediaUrl") != null) {
            entity.setMediaUrl(String.valueOf(raw.get("mediaUrl")));
        }
        if (raw.get("mediaDuration") instanceof Number mediaDuration) {
            entity.setMediaDuration(mediaDuration.intValue());
        }
        if (raw.get("thumbnailUrl") != null) {
            entity.setThumbnailUrl(String.valueOf(raw.get("thumbnailUrl")));
        }
        if (raw.get("fileName") != null) {
            entity.setFileName(String.valueOf(raw.get("fileName")));
        }
        if (raw.get("fileSize") instanceof Number fileSize) {
            entity.setFileSize(fileSize.longValue());
        }
        if (raw.get("payload") != null) {
            entity.setPayloadJson(new com.google.gson.Gson().toJson(raw.get("payload")));
        } else if (raw.get("payloadJson") != null) {
            entity.setPayloadJson(String.valueOf(raw.get("payloadJson")));
        }
        Long conversationKey = com.flashnote.java.util.ConversationKeyUtil.resolve(entity.getFlashNoteId(), entity.getReceiverId());
        if (conversationKey == null && entity.getSenderId() != null && entity.getReceiverId() != null) {
            Long currentUserId = tokenManager.getUserId();
            if (currentUserId != null) {
                Long peerUserId = currentUserId.equals(entity.getSenderId()) ? entity.getReceiverId() : entity.getSenderId();
                conversationKey = com.flashnote.java.util.ConversationKeyUtil.resolve(null, peerUserId);
            }
        }
        entity.setConversationKey(conversationKey == null ? 0L : conversationKey);
        return entity;
    }

    private long requireCurrentUserId() {
        return Long.parseLong(RepositoryAuthSupport.requireCurrentUserId(tokenManager));
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(value);
    }

    private <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }
}
