package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.flashnote.java.DebugLog;
import com.flashnote.java.TokenManager;
import com.flashnote.java.data.local.FavoriteLocalDao;
import com.flashnote.java.data.local.FavoriteLocalEntity;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.FavoriteItem;
import com.flashnote.java.data.remote.FavoriteService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FavoriteRepositoryImpl implements FavoriteRepository {
    private final FavoriteService favoriteService;
    private final FavoriteLocalDao favoriteLocalDao;
    private final TokenManager tokenManager;
    private final LiveData<List<FavoriteItem>> favoritesLiveData;
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final ExecutorService localExecutor = Executors.newSingleThreadExecutor();

    public FavoriteRepositoryImpl(FavoriteService favoriteService, FavoriteLocalDao favoriteLocalDao, TokenManager tokenManager) {
        this.favoriteService = favoriteService;
        this.favoriteLocalDao = favoriteLocalDao;
        this.tokenManager = tokenManager;
        long currentUserId = requireCurrentUserId();
        this.favoritesLiveData = Transformations.map(favoriteLocalDao.observeAllByUserId(currentUserId), this::toModelList);
    }

    @Override
    public LiveData<List<FavoriteItem>> getFavorites() {
        return favoritesLiveData;
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
    public void refresh() {
        isLoading.setValue(true);
        favoriteService.list().enqueue(new Callback<ApiResponse<List<FavoriteItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<FavoriteItem>>> call, Response<ApiResponse<List<FavoriteItem>>> response) {
                isLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    clearError();
                    List<FavoriteItem> data = response.body().getData() == null ? new ArrayList<>() : new ArrayList<>(response.body().getData());
                    sortFavorites(data);
                    persistRemoteFavorites(data);
                    return;
                }
                String message = response.body() == null ? "加载收藏失败" : response.body().getMessage();
                DebugLog.w("FavoriteRepo", message);
                errorMessage.setValue(message);
            }

            @Override
            public void onFailure(Call<ApiResponse<List<FavoriteItem>>> call, Throwable t) {
                isLoading.setValue(false);
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("FavoriteRepo", errMsg);
                errorMessage.setValue(errMsg);
            }
        });
    }

    @Override
    public void addFavorite(Long messageId, ActionCallback callback) {
        favoriteService.add(messageId).enqueue(new Callback<ApiResponse<FavoriteItem>>() {
            @Override
            public void onResponse(Call<ApiResponse<FavoriteItem>> call, Response<ApiResponse<FavoriteItem>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    FavoriteItem item = response.body().getData();
                    if (item != null) {
                        persistSingleFavorite(item);
                    }
                    callback.onSuccess("已加入收藏");
                    return;
                }
                int code = response.body() == null ? response.code() : response.body().getCode();
                String message = response.body() == null ? "收藏失败" : response.body().getMessage();
                DebugLog.w("FavoriteRepo", message);
                callback.onError(message, code);
            }

            @Override
            public void onFailure(Call<ApiResponse<FavoriteItem>> call, Throwable t) {
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("FavoriteRepo", errMsg);
                callback.onError(errMsg, -1);
            }
        });
    }

    @Override
    public void removeFavorite(Long messageId, ActionCallback callback) {
        favoriteService.remove(messageId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    localExecutor.execute(() -> favoriteLocalDao.deleteByMessageId(messageId));
                    callback.onSuccess("已取消收藏");
                    return;
                }
                int code = response.body() == null ? response.code() : response.body().getCode();
                String message = response.body() == null ? "取消收藏失败" : response.body().getMessage();
                DebugLog.w("FavoriteRepo", message);
                callback.onError(message, code);
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("FavoriteRepo", errMsg);
                callback.onError(errMsg, -1);
            }
        });
    }

    private void sortFavorites(List<FavoriteItem> items) {
        items.sort((left, right) -> {
            LocalDateTime leftTime = resolveFavoriteSortTime(left);
            LocalDateTime rightTime = resolveFavoriteSortTime(right);
            if (leftTime == null && rightTime == null) {
                Long leftId = left.getId();
                Long rightId = right.getId();
                if (leftId == null && rightId == null) {
                    return 0;
                }
                if (leftId == null) {
                    return 1;
                }
                if (rightId == null) {
                    return -1;
                }
                return rightId.compareTo(leftId);
            }
            if (leftTime == null) {
                return 1;
            }
            if (rightTime == null) {
                return -1;
            }
            int compareTime = rightTime.compareTo(leftTime);
            if (compareTime != 0) {
                return compareTime;
            }
            Long leftId = left.getId();
            Long rightId = right.getId();
            if (leftId == null && rightId == null) {
                return 0;
            }
            if (leftId == null) {
                return 1;
            }
            if (rightId == null) {
                return -1;
            }
            return rightId.compareTo(leftId);
        });
    }

    private static LocalDateTime resolveFavoriteSortTime(FavoriteItem item) {
        if (item.getFavoritedAt() != null) {
            return item.getFavoritedAt();
        }
        return item.getMessageCreatedAt();
    }

    private void persistRemoteFavorites(List<FavoriteItem> items) {
        List<FavoriteLocalEntity> entities = toLocalList(items);
        long currentUserId = requireCurrentUserId();
        localExecutor.execute(() -> {
            favoriteLocalDao.clearAllByUserId(currentUserId);
            if (!entities.isEmpty()) {
                favoriteLocalDao.upsertAll(entities);
            }
        });
    }

    private void persistSingleFavorite(FavoriteItem item) {
        FavoriteLocalEntity entity = toLocal(item);
        if (entity == null) {
            return;
        }
        localExecutor.execute(() -> favoriteLocalDao.upsert(entity));
    }

    private List<FavoriteLocalEntity> toLocalList(List<FavoriteItem> items) {
        List<FavoriteLocalEntity> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        for (FavoriteItem item : items) {
            FavoriteLocalEntity entity = toLocal(item);
            if (entity != null) {
                result.add(entity);
            }
        }
        return result;
    }

    private FavoriteLocalEntity toLocal(FavoriteItem item) {
        if (item == null || item.getId() == null) {
            return null;
        }
        FavoriteLocalEntity entity = new FavoriteLocalEntity();
        entity.setId(item.getId());
        entity.setMessageId(item.getMessageId());
        entity.setUserId(requireCurrentUserId());
        entity.setFlashNoteId(item.getFlashNoteId());
        entity.setFlashNoteTitle(item.getFlashNoteTitle());
        entity.setRole(item.getRole());
        entity.setContent(item.getContent());
        entity.setFlashNoteIcon(item.getFlashNoteIcon());
        entity.setMediaType(item.getMediaType());
        entity.setMediaUrl(item.getMediaUrl());
        entity.setFileName(item.getFileName());
        entity.setFileSize(item.getFileSize());
        entity.setMediaDuration(item.getMediaDuration());
        entity.setFavoritedAt(item.getFavoritedAt() == null ? null : item.getFavoritedAt().toString());
        entity.setMessageCreatedAt(item.getMessageCreatedAt() == null ? null : item.getMessageCreatedAt().toString());
        return entity;
    }

    private List<FavoriteItem> toModelList(List<FavoriteLocalEntity> entities) {
        List<FavoriteItem> result = new ArrayList<>();
        if (entities == null) {
            return result;
        }
        for (FavoriteLocalEntity entity : entities) {
            FavoriteItem item = new FavoriteItem();
            item.setId(entity.getId());
            item.setMessageId(entity.getMessageId());
            item.setFlashNoteId(entity.getFlashNoteId());
            item.setFlashNoteTitle(entity.getFlashNoteTitle());
            item.setRole(entity.getRole());
            item.setContent(entity.getContent());
            item.setFlashNoteIcon(entity.getFlashNoteIcon());
            item.setMediaType(entity.getMediaType());
            item.setMediaUrl(entity.getMediaUrl());
            item.setFileName(entity.getFileName());
            item.setFileSize(entity.getFileSize());
            item.setMediaDuration(entity.getMediaDuration());
            if (entity.getFavoritedAt() != null && !entity.getFavoritedAt().trim().isEmpty()) {
                item.setFavoritedAt(LocalDateTime.parse(entity.getFavoritedAt()));
            }
            if (entity.getMessageCreatedAt() != null && !entity.getMessageCreatedAt().trim().isEmpty()) {
                item.setMessageCreatedAt(LocalDateTime.parse(entity.getMessageCreatedAt()));
            }
            result.add(item);
        }
        sortFavorites(result);
        return result;
    }

    private long requireCurrentUserId() {
        Long userId = tokenManager.getUserId();
        return userId == null ? -1L : userId;
    }
}
