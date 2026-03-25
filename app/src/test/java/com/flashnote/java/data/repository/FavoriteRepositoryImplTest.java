package com.flashnote.java.data.repository;

import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.flashnote.java.TokenManager;
import com.flashnote.java.data.local.FavoriteLocalDao;
import com.flashnote.java.data.local.FavoriteLocalEntity;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.FavoriteItem;
import com.flashnote.java.data.remote.FavoriteService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.LEGACY)
public class FavoriteRepositoryImplTest {

    @Mock
    private FavoriteService favoriteService;

    @Mock
    private FavoriteLocalDao favoriteLocalDao;

    @Mock
    private TokenManager tokenManager;

    @Mock
    private Call<ApiResponse<List<FavoriteItem>>> mockListCall;

    @Mock
    private Call<ApiResponse<FavoriteItem>> mockAddCall;

    @Mock
    private Call<ApiResponse<Void>> mockRemoveCall;

    private FavoriteRepositoryImpl repository;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(favoriteService.list()).thenReturn(mockListCall);
        when(favoriteService.add(any(Long.class))).thenReturn(mockAddCall);
        when(favoriteService.remove(any(Long.class))).thenReturn(mockRemoveCall);
        when(tokenManager.getUserId()).thenReturn(1001L);
        when(favoriteLocalDao.observeAllByUserId(1001L)).thenReturn(new MutableLiveData<>(new ArrayList<>()));

        repository = new FavoriteRepositoryImpl(favoriteService, favoriteLocalDao, tokenManager);
    }

    @Test
    public void getFavorites_observesLocalDao() {
        LiveData<List<FavoriteItem>> favorites = repository.getFavorites();

        org.junit.Assert.assertNotNull(favorites);
        verify(favoriteLocalDao).observeAllByUserId(1001L);
    }

    @Test
    public void refresh_persistsRemoteFavoritesToLocalDao() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<List<FavoriteItem>>>> callbackCaptor = forClass(Callback.class);
        FavoriteItem item = new FavoriteItem();
        item.setId(1L);
        item.setMessageId(11L);
        item.setContent("favorite");
        item.setFavoritedAt(LocalDateTime.parse("2026-03-25T15:00:00"));

        repository.refresh();

        verify(mockListCall).enqueue(callbackCaptor.capture());
        callbackCaptor.getValue().onResponse(
                mockListCall,
                Response.success(new ApiResponse<>(0, "ok", List.of(item)))
        );

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(favoriteLocalDao).clearAllByUserId(1001L);
        verify(favoriteLocalDao, atLeastOnce()).upsertAll(any());
    }

    @Test
    public void addFavorite_success_persistsItemToLocalDao() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<FavoriteItem>>> callbackCaptor = forClass(Callback.class);
        FavoriteItem item = new FavoriteItem();
        item.setId(2L);
        item.setMessageId(22L);
        item.setContent("added");

        repository.addFavorite(22L, new FavoriteRepository.ActionCallback() {
            @Override
            public void onSuccess(String message) {
            }

            @Override
            public void onError(String message, int code) {
                throw new AssertionError("should not fail");
            }
        });

        verify(mockAddCall).enqueue(callbackCaptor.capture());
        callbackCaptor.getValue().onResponse(
                mockAddCall,
                Response.success(new ApiResponse<>(0, "ok", item))
        );

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(favoriteLocalDao).upsert(any(FavoriteLocalEntity.class));
    }

    @Test
    public void removeFavorite_success_deletesFromLocalDao() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<Void>>> callbackCaptor = forClass(Callback.class);

        repository.removeFavorite(33L, new FavoriteRepository.ActionCallback() {
            @Override
            public void onSuccess(String message) {
            }

            @Override
            public void onError(String message, int code) {
                throw new AssertionError("should not fail");
            }
        });

        verify(mockRemoveCall).enqueue(callbackCaptor.capture());
        callbackCaptor.getValue().onResponse(
                mockRemoveCall,
                Response.success(new ApiResponse<>(0, "ok", null))
        );

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(favoriteLocalDao).deleteByMessageId(33L);
        verify(favoriteLocalDao, never()).upsert(any(FavoriteLocalEntity.class));
    }
}
