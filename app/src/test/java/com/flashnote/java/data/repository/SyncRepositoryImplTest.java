package com.flashnote.java.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.flashnote.java.data.model.PendingMessage;
import com.flashnote.java.data.model.SyncPullRequest;
import com.flashnote.java.data.remote.SyncService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SyncRepositoryImplTest {

    @Mock private SyncService syncService;
    @Mock private TokenManager tokenManager;
    @Mock private FlashNoteLocalDao flashNoteLocalDao;
    @Mock private CollectionLocalDao collectionLocalDao;
    @Mock private FavoriteLocalDao favoriteLocalDao;
    @Mock private MessageLocalDao messageLocalDao;
    @Mock private PendingMessageRepository pendingMessageRepository;
    @Mock private FlashNoteRepository flashNoteRepository;
    @Mock private CollectionRepository collectionRepository;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private Call<ApiResponse<Map<String, Object>>> pullCall;
    @Mock private Call<ApiResponse<Map<String, Object>>> pushCall;

    private SyncRepositoryImpl repository;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(tokenManager.getUserId()).thenReturn(7L);
        when(syncService.pull(any())).thenReturn(pullCall);
        when(syncService.push(any())).thenReturn(pushCall);
        repository = new SyncRepositoryImpl(
                syncService,
                tokenManager,
                flashNoteLocalDao,
                collectionLocalDao,
                favoriteLocalDao,
                messageLocalDao,
                pendingMessageRepository,
                flashNoteRepository,
                collectionRepository,
                favoriteRepository,
                messageRepository
        );
    }

    @Test
    public void syncAll_pullsThenPushesOnlyPendingMessages() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<Map<String, Object>>>> pullCaptor = ArgumentCaptor.forClass(Callback.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<Map<String, Object>>>> pushCaptor = ArgumentCaptor.forClass(Callback.class);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        FlashNoteLocalEntity note = new FlashNoteLocalEntity();
        note.setId(11L);
        note.setUserId(7L);
        note.setTitle("note");
        CollectionLocalEntity collection = new CollectionLocalEntity();
        collection.setId(22L);
        collection.setUserId(7L);
        collection.setName("col");
        FavoriteLocalEntity favorite = new FavoriteLocalEntity();
        favorite.setId(33L);
        favorite.setUserId(7L);
        favorite.setMessageId(44L);
        PendingMessage pendingMessage = new PendingMessage();
        pendingMessage.setLocalId(91L);
        pendingMessage.setConversationKey(100L);
        pendingMessage.setFlashNoteId(11L);
        pendingMessage.setClientRequestId("client-91");
        pendingMessage.setContent("hello");
        pendingMessage.setStatus(PendingMessageDispatcher.STATUS_FAILED);
        pendingMessage.setCreatedAt(1_700_000_000_000L);

        when(flashNoteLocalDao.getAllNowByUserId(7L)).thenReturn(List.of(note));
        when(collectionLocalDao.getAllNowByUserId(7L)).thenReturn(List.of(collection));
        when(favoriteLocalDao.getAllNowByUserId(7L)).thenReturn(List.of(favorite));
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_FAILED)).thenReturn(List.of(pendingMessage));
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_QUEUED)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_PROCESSING)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_UPLOADING)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_UPLOADED)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_SENDING)).thenReturn(List.of());

        final java.util.concurrent.atomic.AtomicReference<Map<String, Object>> resultRef = new java.util.concurrent.atomic.AtomicReference<>();
        repository.syncAll(new SyncRepository.SyncCallback() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                resultRef.set(data);
            }

            @Override
            public void onError(String message, int code) {
            }
        });

        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(pullCall).enqueue(pullCaptor.capture());
        pullCaptor.getValue().onResponse(pullCall, Response.success(new ApiResponse<>(0, "ok", Map.of("serverTime", "1"))));

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(pushCall).enqueue(pushCaptor.capture());
        verify(syncService).push(payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals(1, ((List<?>) payload.get("notes")).size());
        assertEquals(1, ((List<?>) payload.get("collections")).size());
        assertEquals(1, ((List<?>) payload.get("favorites")).size());
        assertEquals(List.of(), payload.get("messages"));

        pushCaptor.getValue().onResponse(pushCall, Response.success(new ApiResponse<>(0, "ok", Map.of("accepted", true))));

        verify(flashNoteRepository).refresh();
        verify(collectionRepository).refresh();
        verify(favoriteRepository).refresh();
        verify(messageRepository).retryAllPendingMessages();
        assertEquals(Map.of("serverTime", "1"), resultRef.get().get("pull"));
        assertEquals(Map.of("accepted", true), resultRef.get().get("push"));
    }

    @Test
    public void syncAll_stopsWhenPullFails() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<Map<String, Object>>>> pullCaptor = ArgumentCaptor.forClass(Callback.class);
        final java.util.concurrent.atomic.AtomicReference<String> errorRef = new java.util.concurrent.atomic.AtomicReference<>();

        repository.syncAll(new SyncRepository.SyncCallback() {
            @Override
            public void onSuccess(Map<String, Object> data) {
            }

            @Override
            public void onError(String message, int code) {
                errorRef.set(message + ":" + code);
            }
        });

        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(pullCall).enqueue(pullCaptor.capture());
        pullCaptor.getValue().onResponse(pullCall, Response.success(new ApiResponse<>(500, "pull failed", null)));

        verify(syncService, never()).push(any());
        assertEquals("pull failed:500", errorRef.get());
    }

    @Test
    public void getPendingSyncCount_readsPendingRepositoryOffMainThread() {
        when(pendingMessageRepository.getPendingSyncCountNow()).thenReturn(4);
        java.util.concurrent.atomic.AtomicInteger result = new java.util.concurrent.atomic.AtomicInteger(-1);

        repository.getPendingSyncCount(result::set);

        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(pendingMessageRepository).getPendingSyncCountNow();
        assertEquals(4, result.get());
    }

    @Test
    public void buildLocalStatePayload_returnsEmptyListsWhenLocalTablesEmpty() {
        when(flashNoteLocalDao.getAllNowByUserId(7L)).thenReturn(List.of());
        when(collectionLocalDao.getAllNowByUserId(7L)).thenReturn(List.of());
        when(favoriteLocalDao.getAllNowByUserId(7L)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_FAILED)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_QUEUED)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_PROCESSING)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_UPLOADING)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_UPLOADED)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_SENDING)).thenReturn(List.of());

        Map<String, Object> payload = repository.buildLocalStatePayload();

        assertEquals(List.of(), payload.get("notes"));
        assertEquals(List.of(), payload.get("collections"));
        assertEquals(List.of(), payload.get("favorites"));
        assertEquals(List.of(), payload.get("messages"));
    }

    @Test
    public void pushLocalState_usesSharedLocalPayloadBuilder() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<Map<String, Object>>>> pushCaptor = ArgumentCaptor.forClass(Callback.class);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        when(flashNoteLocalDao.getAllNowByUserId(7L)).thenReturn(List.of());
        when(collectionLocalDao.getAllNowByUserId(7L)).thenReturn(List.of());
        when(favoriteLocalDao.getAllNowByUserId(7L)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_FAILED)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_QUEUED)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_PROCESSING)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_UPLOADING)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_UPLOADED)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_SENDING)).thenReturn(List.of());

        Map<String, Object> expectedPayload = repository.buildLocalStatePayload();

        repository.pushLocalState(new SyncRepository.SyncCallback() {
            @Override
            public void onSuccess(Map<String, Object> data) {
            }

            @Override
            public void onError(String message, int code) {
            }
        });

        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(syncService).push(payloadCaptor.capture());
        verify(pushCall).enqueue(pushCaptor.capture());
        assertEquals(expectedPayload, payloadCaptor.getValue());
        assertSame(expectedPayload.get("notes"), payloadCaptor.getValue().get("notes"));
        assertSame(expectedPayload.get("collections"), payloadCaptor.getValue().get("collections"));
        assertSame(expectedPayload.get("favorites"), payloadCaptor.getValue().get("favorites"));
        assertEquals(expectedPayload.get("messages"), payloadCaptor.getValue().get("messages"));
    }

    @Test
    public void syncAll_reusesMessageRetryPipelineInsteadOfSyncMessagePayload() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<Map<String, Object>>>> pullCaptor = ArgumentCaptor.forClass(Callback.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<Map<String, Object>>>> pushCaptor = ArgumentCaptor.forClass(Callback.class);

        when(flashNoteLocalDao.getAllNowByUserId(7L)).thenReturn(List.of());
        when(collectionLocalDao.getAllNowByUserId(7L)).thenReturn(List.of());
        when(favoriteLocalDao.getAllNowByUserId(7L)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_FAILED)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_QUEUED)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_PROCESSING)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_UPLOADING)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_UPLOADED)).thenReturn(List.of());
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_SENDING)).thenReturn(List.of());

        repository.syncAll(new SyncRepository.SyncCallback() {
            @Override
            public void onSuccess(Map<String, Object> data) {
            }

            @Override
            public void onError(String message, int code) {
            }
        });

        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(pullCall).enqueue(pullCaptor.capture());
        pullCaptor.getValue().onResponse(pullCall, Response.success(new ApiResponse<>(0, "ok", Map.of("messages", List.of()))));

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(pushCall).enqueue(pushCaptor.capture());
        verify(messageRepository).retryAllPendingMessages();

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(syncService).push(payloadCaptor.capture());
        assertEquals(List.of(), payloadCaptor.getValue().get("messages"));
    }

    @Test
    public void pull_persistsPayloadJsonWhenServerReturnsPayloadJsonField() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<Map<String, Object>>>> pullCaptor = ArgumentCaptor.forClass(Callback.class);

        repository.pull(new SyncRepository.SyncCallback() {
            @Override
            public void onSuccess(Map<String, Object> data) {
            }

            @Override
            public void onError(String message, int code) {
            }
        });

        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(pullCall).enqueue(pullCaptor.capture());
        String payloadJson = "{\"title\":\"卡片标题\",\"summary\":\"正文\",\"items\":[{\"type\":\"TEXT\",\"content\":\"正文\"}]}";
        Map<String, Object> rawMessage = Map.of(
                "id", 301L,
                "senderId", 7L,
                "flashNoteId", 11L,
                "content", "[卡片消息]",
                "mediaType", "COMPOSITE",
                "payloadJson", payloadJson,
                "createdAt", "2026-03-28T12:00:00"
        );
        pullCaptor.getValue().onResponse(pullCall, Response.success(new ApiResponse<>(0, "ok", Map.of("messages", List.of(rawMessage)))));

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        ArgumentCaptor<List<MessageLocalEntity>> entitiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(messageLocalDao).upsertAll(entitiesCaptor.capture());
        List<MessageLocalEntity> entities = entitiesCaptor.getValue();
        assertEquals(1, entities.size());
        assertEquals("COMPOSITE", entities.get(0).getMediaType());
        assertEquals(payloadJson, entities.get(0).getPayloadJson());
        assertTrue(entities.get(0).getConversationKey() != 0L);
    }
}
