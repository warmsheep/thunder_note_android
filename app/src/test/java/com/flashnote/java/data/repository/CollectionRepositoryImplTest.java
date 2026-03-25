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
import com.flashnote.java.data.local.CollectionLocalDao;
import com.flashnote.java.data.local.CollectionLocalEntity;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.Collection;
import com.flashnote.java.data.remote.CollectionService;

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
public class CollectionRepositoryImplTest {

    @Mock
    private CollectionService collectionService;

    @Mock
    private CollectionLocalDao collectionLocalDao;

    @Mock
    private TokenManager tokenManager;

    @Mock
    private Call<ApiResponse<List<Collection>>> mockListCall;

    @Mock
    private Call<ApiResponse<Collection>> mockCreateCall;

    @Mock
    private Call<ApiResponse<Collection>> mockUpdateCall;

    @Mock
    private Call<ApiResponse<Void>> mockDeleteCall;

    private CollectionRepositoryImpl repository;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(collectionService.list()).thenReturn(mockListCall);
        when(collectionService.create(any(Collection.class))).thenReturn(mockCreateCall);
        when(collectionService.update(any(Long.class), any(Collection.class))).thenReturn(mockUpdateCall);
        when(collectionService.delete(any(Long.class))).thenReturn(mockDeleteCall);
        when(tokenManager.getUserId()).thenReturn(1001L);
        when(collectionLocalDao.observeAllByUserId(1001L)).thenReturn(new MutableLiveData<>(new ArrayList<>()));

        repository = new CollectionRepositoryImpl(collectionService, collectionLocalDao, tokenManager);
    }

    @Test
    public void getCollections_observesLocalDao() {
        LiveData<List<Collection>> collections = repository.getCollections();

        org.junit.Assert.assertNotNull(collections);
        verify(collectionLocalDao).observeAllByUserId(1001L);
    }

    @Test
    public void refresh_persistsRemoteCollectionsToLocalDao() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<List<Collection>>>> callbackCaptor = forClass(Callback.class);
        Collection item = new Collection();
        item.setId(1L);
        item.setName("工作");
        item.setUpdatedAt(LocalDateTime.parse("2026-03-25T16:00:00"));

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

        verify(collectionLocalDao).clearAllByUserId(1001L);
        verify(collectionLocalDao, atLeastOnce()).upsertAll(any());
    }

    @Test
    public void create_success_persistsCollectionToLocalDao() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<Collection>>> callbackCaptor = forClass(Callback.class);
        Collection item = new Collection();
        item.setId(2L);
        item.setName("学习");

        repository.createCollection("学习", "描述", null);

        verify(mockCreateCall).enqueue(callbackCaptor.capture());
        callbackCaptor.getValue().onResponse(
                mockCreateCall,
                Response.success(new ApiResponse<>(0, "ok", item))
        );

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(collectionLocalDao).upsert(any(CollectionLocalEntity.class));
    }

    @Test
    public void delete_success_deletesCollectionFromLocalDao() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<Void>>> callbackCaptor = forClass(Callback.class);

        repository.deleteCollection(3L, null);

        verify(mockDeleteCall).enqueue(callbackCaptor.capture());
        callbackCaptor.getValue().onResponse(
                mockDeleteCall,
                Response.success(new ApiResponse<>(0, "ok", null))
        );

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(collectionLocalDao).deleteById(3L);
        verify(collectionLocalDao, never()).upsert(any(CollectionLocalEntity.class));
    }
}
