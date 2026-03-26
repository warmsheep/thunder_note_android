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
import com.flashnote.java.data.remote.SyncService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SyncRepositoryImpl implements SyncRepository {
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
        syncService.pull().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
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
    }

    @Override
    public void pullAndRefreshLocal(SyncCallback callback) {
        pull(new SyncCallback() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                flashNoteRepository.refresh();
                collectionRepository.refresh();
                favoriteRepository.refresh();
                messageRepository.retryAllPendingMessages();
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
        pullAndRefreshLocal(new SyncCallback() {
            @Override
            public void onSuccess(Map<String, Object> pullData) {
                syncExecutor.execute(() -> {
                    List<FlashNote> notes = loadLocalNotes();
                    List<Collection> collections = loadLocalCollections();
                    List<FavoriteItem> favorites = loadLocalFavorites();
                    List<Message> messages = loadLocalMessages();

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("notes", notes == null ? List.of() : notes);
                    payload.put("collections", collections == null ? List.of() : collections);
                    payload.put("messages", messages == null ? List.of() : messages);
                    payload.put("favorites", favorites == null ? List.of() : favorites);

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
        List<FlashNote> notes = loadLocalNotes();
        List<Collection> collections = loadLocalCollections();
        List<FavoriteItem> favorites = loadLocalFavorites();
        List<Message> messages = loadLocalMessages();

        Map<String, Object> payload = new HashMap<>();
        payload.put("notes", notes == null ? List.of() : notes);
        payload.put("collections", collections == null ? List.of() : collections);
        payload.put("messages", messages == null ? List.of() : messages);
        payload.put("favorites", favorites == null ? List.of() : favorites);
        push(payload, callback);
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

    private List<Message> loadLocalMessages() {
        List<MessageLocalEntity> entities = messageLocalDao.getAllNow();
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<Message> result = new java.util.ArrayList<>();
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
            message.setCreatedAt(parseDateTime(entity.getCreatedAt()));
            message.setMediaType(entity.getMediaType());
            message.setMediaUrl(entity.getMediaUrl());
            message.setMediaDuration(entity.getMediaDuration());
            message.setThumbnailUrl(entity.getThumbnailUrl());
            message.setFileName(entity.getFileName());
            message.setFileSize(entity.getFileSize());
            result.add(message);
        }
        return result;
    }

    private long requireCurrentUserId() {
        Long userId = tokenManager.getUserId();
        return userId == null ? -1L : userId;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(value);
    }
}
