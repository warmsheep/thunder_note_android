package com.flashnote.java.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;

import com.flashnote.java.data.local.FlashNoteLocalDao;
import com.flashnote.java.data.local.MessageLocalDao;
import com.flashnote.java.data.local.MessageLocalEntity;
import com.flashnote.java.data.model.CardItem;
import com.flashnote.java.data.model.CardPayload;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.PageData;
import com.flashnote.java.data.model.PendingMessage;
import com.flashnote.java.data.remote.MessageService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class MessageRepositoryImplTest {

    @Mock
    MessageService messageService;

    @Mock
    Call<ApiResponse<Message>> mockSendCall;

    @Mock
    Call<ApiResponse<PageData<Message>>> mockListCall;

    @Mock
    PendingMessageRepository pendingMessageRepository;

    @Mock
    FileRepository fileRepository;

    @Mock
    VideoPreparationService videoPreparationService;

    @Mock
    MessageLocalDao messageLocalDao;

    @Mock
    FlashNoteLocalDao flashNoteLocalDao;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(messageService.list(any())).thenReturn(mockListCall);
        when(messageLocalDao.observeByConversationKey(anyLong())).thenReturn(new androidx.lifecycle.MutableLiveData<>(new java.util.ArrayList<>()));
        when(pendingMessageRepository.observeByConversationKey(anyLong())).thenReturn(new androidx.lifecycle.MutableLiveData<>(new java.util.ArrayList<>()));
    }

    @Test
    public void buildMergedMessages_filtersPendingWhenServerMessageAlreadyMapped() {
        Message remote = new Message();
        remote.setId(101L);
        remote.setContent("hello");
        remote.setRole("user");
        remote.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(2_000L), ZoneId.systemDefault()));

        PendingMessage pending = new PendingMessage();
        pending.setLocalId(1L);
        pending.setConversationKey(9L);
        pending.setContent("hello");
        pending.setMediaType("TEXT");
        pending.setStatus(PendingMessageDispatcher.STATUS_FAILED);
        pending.setCreatedAt(1_000L);
        pending.setServerMessageId(101L);

        List<Message> merged = MessageRepositoryMergeHelper.buildMergedMessages(
                List.of(remote),
                List.of(pending),
                java.util.Comparator.<Message>comparingLong(MessageRepositoryMergeHelper::messageSortTimestamp)
                        .thenComparingLong(MessageRepositoryMergeHelper::messageSourceOrder)
                        .thenComparingLong(MessageRepositoryMergeHelper::messageStableTieBreaker)
        );

        assertEquals(1, merged.size());
        assertEquals(Long.valueOf(101L), merged.get(0).getId());
    }

    @Test
    public void buildMergedMessages_keepsPendingOrderedByLocalCreatedAt() {
        Message olderRemote = new Message();
        olderRemote.setId(10L);
        olderRemote.setContent("older");
        olderRemote.setRole("user");
        olderRemote.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(1_000L), ZoneId.systemDefault()));

        Message newerRemote = new Message();
        newerRemote.setId(11L);
        newerRemote.setContent("newer");
        newerRemote.setRole("user");
        newerRemote.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(5_000L), ZoneId.systemDefault()));

        PendingMessage pending = new PendingMessage();
        pending.setLocalId(2L);
        pending.setConversationKey(9L);
        pending.setContent("pending");
        pending.setMediaType("TEXT");
        pending.setStatus(PendingMessageDispatcher.STATUS_FAILED);
        pending.setCreatedAt(3_000L);

        List<Message> merged = MessageRepositoryMergeHelper.buildMergedMessages(
                List.of(newerRemote, olderRemote),
                List.of(pending),
                java.util.Comparator.<Message>comparingLong(MessageRepositoryMergeHelper::messageSortTimestamp)
                        .thenComparingLong(MessageRepositoryMergeHelper::messageSourceOrder)
                        .thenComparingLong(MessageRepositoryMergeHelper::messageStableTieBreaker)
        );

        assertEquals(3, merged.size());
        assertEquals(Long.valueOf(10L), merged.get(0).getId());
        assertEquals(Long.valueOf(-2L), merged.get(1).getId());
        assertEquals(Long.valueOf(11L), merged.get(2).getId());
        assertTrue(merged.get(1).getLocalSortTimestamp() != null);
        assertEquals(Long.valueOf(3_000L), merged.get(1).getLocalSortTimestamp());
    }

    @Test
    public void buildMergedMessages_filtersTextPendingWhenRemoteMessageMatchesFingerprint() {
        Message remote = new Message();
        remote.setId(201L);
        remote.setContent("same text");
        remote.setMediaType("TEXT");
        remote.setRole("user");
        remote.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(10_500L), ZoneId.systemDefault()));

        PendingMessage pending = new PendingMessage();
        pending.setLocalId(3L);
        pending.setConversationKey(9L);
        pending.setContent("same text");
        pending.setMediaType("TEXT");
        pending.setStatus(PendingMessageDispatcher.STATUS_FAILED);
        pending.setCreatedAt(10_000L);

        List<Message> merged = MessageRepositoryMergeHelper.buildMergedMessages(
                List.of(remote),
                List.of(pending),
                java.util.Comparator.<Message>comparingLong(MessageRepositoryMergeHelper::messageSortTimestamp)
                        .thenComparingLong(MessageRepositoryMergeHelper::messageSourceOrder)
                        .thenComparingLong(MessageRepositoryMergeHelper::messageStableTieBreaker)
        );

        assertEquals(1, merged.size());
        assertEquals(Long.valueOf(201L), merged.get(0).getId());
    }

    @Test
    public void buildMergedMessages_filtersPendingWhenRemoteMessageMatchesClientRequestId() {
        Message remote = new Message();
        remote.setId(301L);
        remote.setContent("server copy");
        remote.setMediaType("TEXT");
        remote.setRole("user");
        remote.setClientRequestId("req-301");
        remote.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(20_000L), ZoneId.systemDefault()));

        PendingMessage pending = new PendingMessage();
        pending.setLocalId(4L);
        pending.setConversationKey(9L);
        pending.setContent("draft copy");
        pending.setMediaType("TEXT");
        pending.setClientRequestId("req-301");
        pending.setStatus(PendingMessageDispatcher.STATUS_FAILED);
        pending.setCreatedAt(19_000L);

        List<Message> merged = MessageRepositoryMergeHelper.buildMergedMessages(
                List.of(remote),
                List.of(pending),
                java.util.Comparator.<Message>comparingLong(MessageRepositoryMergeHelper::messageSortTimestamp)
                        .thenComparingLong(MessageRepositoryMergeHelper::messageSourceOrder)
                        .thenComparingLong(MessageRepositoryMergeHelper::messageStableTieBreaker)
        );

        assertEquals(1, merged.size());
        assertEquals(Long.valueOf(301L), merged.get(0).getId());
    }

    @Test
    public void applyPendingMetadataToServerMessage_copiesVoiceDurationAndFileInfo() {
        PendingMessage pending = new PendingMessage();
        pending.setCreatedAt(33_000L);
        pending.setMediaType("VOICE");
        pending.setMediaDuration(8);
        pending.setFileName("voice.m4a");
        pending.setFileSize(1234L);
        pending.setRemoteUrl("voice/object.m4a");

        Message server = new Message();
        server.setId(99L);

        MessageRepositoryImpl.applyPendingMetadataToServerMessage(server, pending);

        assertEquals(Long.valueOf(33_000L), server.getLocalSortTimestamp());
        assertEquals("VOICE", server.getMediaType());
        assertEquals(Integer.valueOf(8), server.getMediaDuration());
        assertEquals("voice.m4a", server.getFileName());
        assertEquals(Long.valueOf(1234L), server.getFileSize());
        assertEquals("voice/object.m4a", server.getMediaUrl());
    }

    @Test
    public void buildMergedMessages_keepsCompositePayloadForPendingCardMessage() {
        PendingMessage pending = new PendingMessage();
        pending.setLocalId(15L);
        pending.setConversationKey(9L);
        pending.setContent("卡片标题");
        pending.setMediaType("COMPOSITE");
        pending.setStatus(PendingMessageDispatcher.STATUS_QUEUED);
        pending.setCreatedAt(25_000L);
        pending.setPayloadJson(buildCompositePayloadJson());

        List<Message> merged = MessageRepositoryMergeHelper.buildMergedMessages(
                List.of(),
                List.of(pending),
                java.util.Comparator.<Message>comparingLong(MessageRepositoryMergeHelper::messageSortTimestamp)
                        .thenComparingLong(MessageRepositoryMergeHelper::messageSourceOrder)
                        .thenComparingLong(MessageRepositoryMergeHelper::messageStableTieBreaker)
        );

        assertEquals(1, merged.size());
        Message message = merged.get(0);
        assertEquals("COMPOSITE", message.getMediaType());
        assertEquals("卡片标题", message.getContent());
        assertEquals("卡片标题", message.getPayload().getTitle());
        assertEquals(2, message.getPayload().getItems().size());
        assertEquals("TEXT", message.getPayload().getItems().get(0).getType());
        assertEquals("正文", message.getPayload().getItems().get(0).getContent());
    }

    private String buildCompositePayloadJson() {
        CardPayload payload = new CardPayload();
        payload.setCardType("COMPOSITE_CARD");
        payload.setTitle("卡片标题");
        payload.setSummary("正文");
        List<CardItem> items = new ArrayList<>();

        CardItem text = new CardItem();
        text.setType("TEXT");
        text.setContent("正文");
        items.add(text);

        CardItem image = new CardItem();
        image.setType("IMAGE");
        image.setLocalPath("/tmp/demo.jpg");
        image.setThumbnailUrl("/tmp/demo-thumb.jpg");
        items.add(image);

        payload.setItems(items);
        return new com.google.gson.Gson().toJson(payload);
    }

    @Test
    public void retryPendingMessage_onlyRetriesFailedItemAndDispatchesSamePending() {
        PendingMessage failed = new PendingMessage();
        failed.setLocalId(9L);
        failed.setConversationKey(88L);
        failed.setStatus(PendingMessageDispatcher.STATUS_FAILED);
        failed.setErrorMessage("网络错误: timeout");

        MessageRepositoryImpl repository = new MessageRepositoryImpl(
                messageService,
                pendingMessageRepository,
                fileRepository,
                videoPreparationService,
                messageLocalDao
        );

        when(pendingMessageRepository.findByLocalId(9L)).thenReturn(failed);
        when(messageService.send(org.mockito.ArgumentMatchers.any())).thenReturn(mockSendCall);

        repository.retryPendingMessage(9L);

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(pendingMessageRepository, atLeastOnce()).findByLocalId(9L);
        verify(pendingMessageRepository, atLeastOnce()).update(same(failed));
        verify(messageService).send(org.mockito.ArgumentMatchers.any());
        assertTrue(
                PendingMessageDispatcher.STATUS_QUEUED.equals(failed.getStatus())
                        || PendingMessageDispatcher.STATUS_SENDING.equals(failed.getStatus())
        );
        assertEquals(null, failed.getErrorMessage());
    }

    @Test
    public void retryPendingMessage_ignoresNonFailedItem() {
        PendingMessage queued = new PendingMessage();
        queued.setLocalId(10L);
        queued.setConversationKey(99L);
        queued.setStatus(PendingMessageDispatcher.STATUS_QUEUED);

        MessageRepositoryImpl repository = new MessageRepositoryImpl(
                messageService,
                pendingMessageRepository,
                fileRepository,
                videoPreparationService,
                messageLocalDao
        );

        when(pendingMessageRepository.findByLocalId(10L)).thenReturn(queued);

        repository.retryPendingMessage(10L);

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(pendingMessageRepository).findByLocalId(10L);
        verify(pendingMessageRepository, never()).update(eq(queued));
    }

    @Test
    public void sendText_usesDirectSendWhenPendingFeatureDisabled() {
        MessageRepositoryImpl repository = new MessageRepositoryImpl(
                messageService,
                pendingMessageRepository,
                fileRepository,
                videoPreparationService,
                messageLocalDao,
                false
        );
        when(messageService.send(any())).thenReturn(mockSendCall);

        repository.sendText(7L, "hello", null);

        verify(messageService).send(any());
        verify(pendingMessageRepository, never()).insert(any(PendingMessage.class));
    }

    @Test
    public void sendText_usesPendingPipelineWhenPendingFeatureEnabled() {
        MessageRepositoryImpl repository = new MessageRepositoryImpl(
                messageService,
                pendingMessageRepository,
                fileRepository,
                videoPreparationService,
                messageLocalDao,
                true
        );
        when(pendingMessageRepository.insert(any(PendingMessage.class))).thenReturn(15L);
        when(pendingMessageRepository.findByLocalId(15L)).thenAnswer(invocation -> {
            PendingMessage pending = new PendingMessage();
            pending.setLocalId(15L);
            pending.setConversationKey(7L);
            pending.setStatus(PendingMessageDispatcher.STATUS_QUEUED);
            pending.setContent("hello");
            pending.setMediaType("TEXT");
            pending.setClientRequestId("req-15");
            pending.setCreatedAt(1000L);
            return pending;
        });
        when(messageService.send(any())).thenReturn(mockSendCall);

        repository.sendText(7L, "hello", null);

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(pendingMessageRepository, atLeastOnce()).insert(any(PendingMessage.class));
    }

    @Test
    public void applyPendingMetadataToServerMessage_backfillsFlashNoteIdFromPendingMessage() {
        PendingMessage pending = new PendingMessage();
        pending.setFlashNoteId(7L);
        pending.setCreatedAt(1000L);
        pending.setMediaType("TEXT");
        pending.setContent("hello");

        Message confirmed = new Message();
        confirmed.setId(701L);
        confirmed.setContent("hello");
        confirmed.setMediaType("TEXT");

        MessageRepositoryImpl.applyPendingMetadataToServerMessage(confirmed, pending);

        assertEquals(Long.valueOf(7L), confirmed.getFlashNoteId());
    }

    @Test
    public void enqueueMedia_staysOnPendingPipelineEvenWhenTextPendingFeatureDisabled() throws Exception {
        File tempFile = File.createTempFile("pending-media", ".jpg");
        tempFile.deleteOnExit();

        MessageRepositoryImpl repository = new MessageRepositoryImpl(
                messageService,
                pendingMessageRepository,
                fileRepository,
                videoPreparationService,
                messageLocalDao,
                false
        );
        when(pendingMessageRepository.insert(any(PendingMessage.class))).thenReturn(21L);
        when(pendingMessageRepository.findByLocalId(21L)).thenAnswer(invocation -> {
            PendingMessage pending = new PendingMessage();
            pending.setLocalId(21L);
            pending.setConversationKey(9L);
            pending.setStatus(PendingMessageDispatcher.STATUS_QUEUED);
            pending.setMediaType("IMAGE");
            pending.setLocalFilePath(tempFile.getAbsolutePath());
            pending.setCreatedAt(2000L);
            return pending;
        });

        repository.enqueueMedia(9L, 0L, "IMAGE", tempFile, "demo.jpg", tempFile.length(), null, null);

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(pendingMessageRepository, atLeastOnce()).insert(any(PendingMessage.class));
        verify(fileRepository).upload(eq(tempFile), any(FileRepository.FileCallback.class));
    }

    @Test
    public void getMessages_observesConfirmedMessagesFromLocalDao() {
        MessageRepositoryImpl repository = new MessageRepositoryImpl(
                messageService,
                pendingMessageRepository,
                fileRepository,
                videoPreparationService,
                messageLocalDao,
                true
        );

        repository.getMessages(7L);

        verify(messageLocalDao).observeByConversationKey(7L);
    }

    @Test
    public void addLocalMessage_persistsConfirmedMessageIntoLocalDao() {
        MessageRepositoryImpl repository = new MessageRepositoryImpl(
                messageService,
                pendingMessageRepository,
                fileRepository,
                videoPreparationService,
                messageLocalDao,
                true
        );
        Message message = new Message();
        message.setId(44L);
        message.setFlashNoteId(9L);
        message.setContent("local confirmed");

        repository.addLocalMessage(message);

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(messageLocalDao, atLeastOnce()).upsert(any(MessageLocalEntity.class));
    }

    @Test
    public void loadMessages_pageOne_persistsRemoteMessagesToLocalDao() {
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Callback<ApiResponse<PageData<Message>>>> callbackCaptor = org.mockito.ArgumentCaptor.forClass(Callback.class);
        MessageRepositoryImpl repository = new MessageRepositoryImpl(
                messageService,
                pendingMessageRepository,
                fileRepository,
                videoPreparationService,
                messageLocalDao,
                true
        );

        Message remote = new Message();
        remote.setId(88L);
        remote.setFlashNoteId(12L);
        remote.setContent("server message");
        remote.setCreatedAt(java.time.LocalDateTime.parse("2026-03-25T13:00:00"));

        repository.bindFlashNote(12L);

        verify(mockListCall).enqueue(callbackCaptor.capture());
        PageData<Message> pageData = new PageData<>();
        pageData.setRecords(java.util.List.of(remote));
        callbackCaptor.getValue().onResponse(
                mockListCall,
                retrofit2.Response.success(new ApiResponse<>(0, "ok", pageData))
        );

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(messageLocalDao, times(1)).clearConversation(12L);
        verify(messageLocalDao, times(1)).upsertAll(any());
    }

    @Test
    public void sendMessage_success_persistsConfirmedMessageToLocalDao() {
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Callback<ApiResponse<Message>>> callbackCaptor = org.mockito.ArgumentCaptor.forClass(Callback.class);
        MessageRepositoryImpl repository = new MessageRepositoryImpl(
                messageService,
                pendingMessageRepository,
                fileRepository,
                videoPreparationService,
                messageLocalDao,
                true
        );

        Message outbound = new Message();
        outbound.setContent("direct send");
        outbound.setMediaType("TEXT");

        Message confirmed = new Message();
        confirmed.setId(501L);
        confirmed.setFlashNoteId(66L);
        confirmed.setContent("direct send");
        confirmed.setCreatedAt(java.time.LocalDateTime.parse("2026-03-25T14:00:00"));

        when(messageService.send(any())).thenReturn(mockSendCall);

        repository.sendMessage(66L, outbound, (Runnable) null);

        verify(mockSendCall).enqueue(callbackCaptor.capture());
        callbackCaptor.getValue().onResponse(
                mockSendCall,
                retrofit2.Response.success(new ApiResponse<>(0, "ok", confirmed))
        );

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(messageLocalDao, atLeastOnce()).upsert(any(MessageLocalEntity.class));
    }

    @Test
    public void sendMessage_success_updatesFlashNoteLatestMessagePreviewLocally() {
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Callback<ApiResponse<Message>>> callbackCaptor = org.mockito.ArgumentCaptor.forClass(Callback.class);
        MessageRepositoryImpl repository = new MessageRepositoryImpl(
                messageService,
                pendingMessageRepository,
                fileRepository,
                videoPreparationService,
                messageLocalDao,
                flashNoteLocalDao,
                true
        );

        Message outbound = new Message();
        outbound.setContent("最新一条消息");
        outbound.setMediaType("TEXT");

        Message confirmed = new Message();
        confirmed.setId(601L);
        confirmed.setFlashNoteId(77L);
        confirmed.setContent("最新一条消息");
        confirmed.setCreatedAt(java.time.LocalDateTime.parse("2026-03-28T14:30:00"));

        when(messageService.send(any())).thenReturn(mockSendCall);

        repository.sendMessage(77L, outbound, (Runnable) null);

        verify(mockSendCall).enqueue(callbackCaptor.capture());
        callbackCaptor.getValue().onResponse(
                mockSendCall,
                retrofit2.Response.success(new ApiResponse<>(0, "ok", confirmed))
        );

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(messageLocalDao, atLeastOnce()).upsert(any(MessageLocalEntity.class));
        verify(flashNoteLocalDao).updateLatestMessage(eq(77L), eq("最新一条消息"), anyString());
    }

    @Test
    public void loadMessages_pageOne_preservesExistingVoiceDurationWhenRemoteDurationMissing() {
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Callback<ApiResponse<PageData<Message>>>> callbackCaptor = org.mockito.ArgumentCaptor.forClass(Callback.class);
        MessageRepositoryImpl repository = new MessageRepositoryImpl(
                messageService,
                pendingMessageRepository,
                fileRepository,
                videoPreparationService,
                messageLocalDao,
                true
        );

        MessageLocalEntity existing = new MessageLocalEntity();
        existing.setId(88L);
        existing.setConversationKey(12L);
        existing.setCreatedAt("2026-03-25T13:00:00");
        existing.setMediaType("VOICE");
        existing.setMediaDuration(9);
        when(messageLocalDao.getByConversationKeyNow(12L)).thenReturn(java.util.List.of(existing));
        when(messageLocalDao.observeByConversationKey(anyLong())).thenReturn(new androidx.lifecycle.MutableLiveData<>(new java.util.ArrayList<>()));

        java.util.concurrent.atomic.AtomicReference<java.util.List<MessageLocalEntity>> savedEntities = new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(invocation -> {
            savedEntities.set(invocation.getArgument(0));
            return null;
        }).when(messageLocalDao).upsertAll(any());

        Message remote = new Message();
        remote.setId(88L);
        remote.setFlashNoteId(12L);
        remote.setContent("voice message");
        remote.setMediaType("VOICE");
        remote.setCreatedAt(java.time.LocalDateTime.parse("2026-03-25T13:00:00"));
        remote.setMediaDuration(null);

        repository.bindFlashNote(12L);

        verify(mockListCall).enqueue(callbackCaptor.capture());
        PageData<Message> pageData = new PageData<>();
        pageData.setRecords(java.util.List.of(remote));
        callbackCaptor.getValue().onResponse(
                mockListCall,
                retrofit2.Response.success(new ApiResponse<>(0, "ok", pageData))
        );

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        org.junit.Assert.assertNotNull(savedEntities.get());
        org.junit.Assert.assertEquals(Integer.valueOf(9), savedEntities.get().get(0).getMediaDuration());
    }

    @Test
    public void retryPendingVoiceMessage_sendsMediaDurationToServer() {
        PendingMessage voice = new PendingMessage();
        voice.setLocalId(12L);
        voice.setConversationKey(88L);
        voice.setStatus(PendingMessageDispatcher.STATUS_FAILED);
        voice.setMediaType("VOICE");
        voice.setMediaDuration(7);
        voice.setRemoteUrl("voice/object.m4a");
        voice.setFileName("voice.m4a");
        voice.setFileSize(1234L);

        MessageRepositoryImpl repository = new MessageRepositoryImpl(
                messageService,
                pendingMessageRepository,
                fileRepository,
                videoPreparationService,
                messageLocalDao
        );

        when(pendingMessageRepository.findByLocalId(12L)).thenReturn(voice);
        when(messageService.send(any())).thenReturn(mockSendCall);

        repository.retryPendingMessage(12L);

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        org.mockito.ArgumentCaptor<Message> messageCaptor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(messageService).send(messageCaptor.capture());
        org.junit.Assert.assertEquals(Integer.valueOf(7), messageCaptor.getValue().getMediaDuration());
    }
}
