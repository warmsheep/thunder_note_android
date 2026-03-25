package com.flashnote.java.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.same;

import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.ApiResponse;
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
import java.util.List;

import retrofit2.Call;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class MessageRepositoryImplTest {

    @Mock
    MessageService messageService;

    @Mock
    Call<ApiResponse<Message>> mockSendCall;

    @Mock
    PendingMessageRepository pendingMessageRepository;

    @Mock
    FileRepository fileRepository;

    @Mock
    VideoPreparationService videoPreparationService;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
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

        List<Message> merged = MessageRepositoryImpl.buildMergedMessages(
                List.of(remote),
                List.of(pending)
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

        List<Message> merged = MessageRepositoryImpl.buildMergedMessages(
                List.of(newerRemote, olderRemote),
                List.of(pending)
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

        List<Message> merged = MessageRepositoryImpl.buildMergedMessages(
                List.of(remote),
                List.of(pending)
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

        List<Message> merged = MessageRepositoryImpl.buildMergedMessages(
                List.of(remote),
                List.of(pending)
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
                videoPreparationService
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
                videoPreparationService
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
}
