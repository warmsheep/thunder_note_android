package com.flashnote.java.data.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.flashnote.java.data.model.PendingMessage;
import com.flashnote.java.data.remote.MessageService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.List;
import java.io.File;

import retrofit2.Call;
import retrofit2.Callback;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.LEGACY)
public class PendingMessageDispatcherTest {

    @Mock
    private PendingMessageRepository pendingMessageRepository;

    @Mock
    private MessageService messageService;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private Call mockSendCall;

    @Mock
    private PendingMessageDispatcher.Listener listener;

    private PendingMessageDispatcher dispatcher;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(messageService.send(any())).thenReturn(mockSendCall);
        dispatcher = new PendingMessageDispatcher(pendingMessageRepository, messageService, fileRepository, listener);
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

    @Test
    public void dispatchConversationNow_uploadsImageBeforeSending() throws Exception {
        File tempFile = File.createTempFile("pending-image", ".jpg");
        tempFile.deleteOnExit();

        PendingMessage imagePending = buildPending(31L, 88L, PendingMessageDispatcher.STATUS_QUEUED);
        imagePending.setMediaType("IMAGE");
        imagePending.setLocalFilePath(tempFile.getAbsolutePath());

        when(pendingMessageRepository.getByConversationKey(88L)).thenReturn(List.of(imagePending));
        when(pendingMessageRepository.findByLocalId(eq(31L))).thenReturn(imagePending);

        dispatcher.dispatchConversationNow(88L);

        verify(fileRepository).upload(eq(tempFile), any(FileRepository.FileCallback.class));
    }

    @Test
    public void dispatchConversationNow_marksImageFailedWhenUploadFails() throws Exception {
        File tempFile = File.createTempFile("pending-image", ".jpg");
        tempFile.deleteOnExit();

        PendingMessage imagePending = buildPending(41L, 77L, PendingMessageDispatcher.STATUS_QUEUED);
        imagePending.setMediaType("IMAGE");
        imagePending.setLocalFilePath(tempFile.getAbsolutePath());

        when(pendingMessageRepository.getByConversationKey(77L)).thenReturn(List.of(imagePending));
        when(pendingMessageRepository.findByLocalId(eq(41L))).thenReturn(imagePending);

        dispatcher.dispatchConversationNow(77L);

        ArgumentCaptor<FileRepository.FileCallback> callbackCaptor = ArgumentCaptor.forClass(FileRepository.FileCallback.class);
        verify(fileRepository).upload(eq(tempFile), callbackCaptor.capture());
        dispatcher.handleUploadFailureNow(41L, "upload failed");

        verify(pendingMessageRepository, times(2)).update(eq(imagePending));
        org.junit.Assert.assertEquals(PendingMessageDispatcher.STATUS_FAILED, imagePending.getStatus());
        org.junit.Assert.assertEquals("upload failed", imagePending.getErrorMessage());
    }

    @Test
    public void dispatchConversationNow_setsRemoteUrlAndSendsAfterUploadSuccess() throws Exception {
        File tempFile = File.createTempFile("pending-file", ".bin");
        tempFile.deleteOnExit();

        PendingMessage filePending = buildPending(51L, 66L, PendingMessageDispatcher.STATUS_QUEUED);
        filePending.setMediaType("FILE");
        filePending.setLocalFilePath(tempFile.getAbsolutePath());
        filePending.setFileName("demo.bin");
        filePending.setFileSize(123L);

        when(pendingMessageRepository.getByConversationKey(66L)).thenReturn(List.of(filePending));
        when(pendingMessageRepository.findByLocalId(eq(51L))).thenReturn(filePending);

        dispatcher.dispatchConversationNow(66L);

        ArgumentCaptor<FileRepository.FileCallback> callbackCaptor = ArgumentCaptor.forClass(FileRepository.FileCallback.class);
        verify(fileRepository).upload(eq(tempFile), callbackCaptor.capture());
        dispatcher.handleUploadSuccessNow(51L, "obj/51.bin");

        org.junit.Assert.assertEquals("obj/51.bin", filePending.getRemoteUrl());
        verify(messageService).send(any());
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
