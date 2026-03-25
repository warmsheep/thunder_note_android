package com.flashnote.java.data.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flashnote.java.data.model.PendingMessage;
import com.flashnote.java.data.remote.MessageService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.List;

import retrofit2.Call;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.LEGACY)
public class PendingMessageDispatcherTest {

    @Mock
    private PendingMessageRepository pendingMessageRepository;

    @Mock
    private MessageService messageService;

    @Mock
    private Call mockSendCall;

    @Mock
    private PendingMessageDispatcher.Listener listener;

    private PendingMessageDispatcher dispatcher;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(messageService.send(any())).thenReturn(mockSendCall);
        dispatcher = new PendingMessageDispatcher(pendingMessageRepository, messageService, listener);
    }

    @Test
    public void dispatchConversation_enqueuesQueuedAndFailedMessagesOnly() {
        PendingMessage queued = buildPending(11L, 99L, PendingMessageDispatcher.STATUS_QUEUED);
        PendingMessage failed = buildPending(12L, 99L, PendingMessageDispatcher.STATUS_FAILED);
        PendingMessage sending = buildPending(13L, 99L, PendingMessageDispatcher.STATUS_SENDING);

        when(pendingMessageRepository.getByConversationKey(99L))
                .thenReturn(List.of(queued, failed, sending));
        when(pendingMessageRepository.findByLocalId(eq(11L))).thenReturn(queued);
        when(pendingMessageRepository.findByLocalId(eq(12L))).thenReturn(failed);
        when(pendingMessageRepository.findByLocalId(eq(13L))).thenReturn(sending);

        dispatcher.dispatchConversationNow(99L);

        verify(pendingMessageRepository).getByConversationKey(99L);
        verify(pendingMessageRepository).findByLocalId(11L);
        verify(pendingMessageRepository).findByLocalId(12L);
        verify(pendingMessageRepository, never()).findByLocalId(13L);
    }

    @Test
    public void dispatchAllPending_enqueuesQueuedAndFailedLists() {
        PendingMessage queued = buildPending(21L, 101L, PendingMessageDispatcher.STATUS_QUEUED);
        PendingMessage failed = buildPending(22L, 102L, PendingMessageDispatcher.STATUS_FAILED);

        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_QUEUED))
                .thenReturn(List.of(queued));
        when(pendingMessageRepository.getByStatus(PendingMessageDispatcher.STATUS_FAILED))
                .thenReturn(List.of(failed));
        when(pendingMessageRepository.findByLocalId(eq(21L))).thenReturn(queued);
        when(pendingMessageRepository.findByLocalId(eq(22L))).thenReturn(failed);

        dispatcher.dispatchAllPendingNow();

        verify(pendingMessageRepository).getByStatus(PendingMessageDispatcher.STATUS_QUEUED);
        verify(pendingMessageRepository).getByStatus(PendingMessageDispatcher.STATUS_FAILED);
        verify(pendingMessageRepository).findByLocalId(21L);
        verify(pendingMessageRepository).findByLocalId(22L);
    }

    private PendingMessage buildPending(long localId, long conversationKey, String status) {
        PendingMessage pending = new PendingMessage();
        pending.setLocalId(localId);
        pending.setConversationKey(conversationKey);
        pending.setStatus(status);
        pending.setClientRequestId("req-" + localId);
        pending.setContent("hello-" + localId);
        pending.setCreatedAt(1000L + localId);
        return pending;
    }
}
