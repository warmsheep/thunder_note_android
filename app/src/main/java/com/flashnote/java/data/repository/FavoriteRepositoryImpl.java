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
    private final FavoriteLocalMapper localMapper = new FavoriteLocalMapper();
    private final RepositoryStateStore stateStore = new RepositoryStateStore();
    private final LiveData<List<FavoriteItem>> favoritesLiveData;
    private final ExecutorService localExecutor = Executors.newSingleThreadExecutor();

    public FavoriteRepositoryImpl(FavoriteService favoriteService, FavoriteLocalDao favoriteLocalDao, TokenManager tokenManager) {
        this.favoriteService = favoriteService;
        this.favoriteLocalDao = favoriteLocalDao;
        this.tokenManager = tokenManager;
        long currentUserId = requireCurrentUserId();
        this.favoritesLiveData = Transformations.map(favoriteLocalDao.observeAllByUserId(currentUserId), entities -> {
            List<FavoriteItem> favorites = localMapper.toModelList(entities);
            sortFavorites(favorites);
            return favorites;
        });
    }

    @Override
    public LiveData<List<FavoriteItem>> getFavorites() {
        return favoritesLiveData;
    }

    @Override
    public LiveData<Boolean> isLoading() {
        return stateStore.isLoading();
    }

    @Override
    public LiveData<String> getErrorMessage() {
        return stateStore.getErrorMessage();
    }

    @Override
    public void clearError() {
        stateStore.clearError();
    }

    @Override
    public void refresh() {
        stateStore.setLoading(true);
        favoriteService.list().enqueue(new Callback<ApiResponse<List<FavoriteItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<FavoriteItem>>> call, Response<ApiResponse<List<FavoriteItem>>> response) {
                stateStore.setLoading(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    clearError();
                    List<FavoriteItem> data = response.body().getData() == null ? new ArrayList<>() : new ArrayList<>(response.body().getData());
                    sortFavorites(data);
                    persistRemoteFavorites(data);
                    return;
                }
                String message = response.body() == null ? "加载收藏失败" : response.body().getMessage();
                DebugLog.w("FavoriteRepo", message);
                stateStore.setError(message);
            }

            @Override
            public void onFailure(Call<ApiResponse<List<FavoriteItem>>> call, Throwable t) {
                stateStore.setLoading(false);
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("FavoriteRepo", errMsg);
                stateStore.setError(errMsg);
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
        long currentUserId = requireCurrentUserId();
        List<FavoriteLocalEntity> entities = localMapper.toLocalList(items, currentUserId);
        localExecutor.execute(() -> {
            favoriteLocalDao.clearAllByUserId(currentUserId);
            if (!entities.isEmpty()) {
                favoriteLocalDao.upsertAll(entities);
            }
        });
    }

    private void persistSingleFavorite(FavoriteItem item) {
        FavoriteLocalEntity entity = localMapper.toLocal(item, requireCurrentUserId());
        if (entity == null) {
            return;
        }
        localExecutor.execute(() -> favoriteLocalDao.upsert(entity));
    }

    private long requireCurrentUserId() {
        Long userId = tokenManager.getUserId();
        return userId == null ? -1L : userId;
    }
}
