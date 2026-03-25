package com.flashnote.java.data.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.junit.Assert.assertEquals;

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
    private VideoPreparationService videoPreparationService;

    @Mock
    private Call mockSendCall;

    @Mock
    private PendingMessageDispatcher.Listener listener;

    private PendingMessageDispatcher dispatcher;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(messageService.send(any())).thenReturn(mockSendCall);
        dispatcher = new PendingMessageDispatcher(pendingMessageRepository, messageService, fileRepository, videoPreparationService, listener);
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
        verify(pendingMessageRepository, never()).findByLocalId(12L);
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
        verify(pendingMessageRepository).findByLocalId(21L);
        verify(pendingMessageRepository, never()).findByLocalId(22L);
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
        assertEquals(PendingMessageDispatcher.STATUS_FAILED, imagePending.getStatus());
        assertEquals("网络错误: upload failed", imagePending.getErrorMessage());
        assertEquals(1, imagePending.getAttemptCount());
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

    @Test
    public void dispatchConversationNow_uploadsVoiceBeforeSending() throws Exception {
        File tempFile = File.createTempFile("pending-voice", ".m4a");
        tempFile.deleteOnExit();

        PendingMessage voicePending = buildPending(61L, 55L, PendingMessageDispatcher.STATUS_QUEUED);
        voicePending.setMediaType("VOICE");
        voicePending.setLocalFilePath(tempFile.getAbsolutePath());
        voicePending.setProcessedFilePath(tempFile.getAbsolutePath());
        voicePending.setMediaDuration(8);

        when(pendingMessageRepository.getByConversationKey(55L)).thenReturn(List.of(voicePending));
        when(pendingMessageRepository.findByLocalId(eq(61L))).thenReturn(voicePending);

        dispatcher.dispatchConversationNow(55L);

        verify(fileRepository).upload(eq(tempFile), any(FileRepository.FileCallback.class));
    }

    @Test
    public void dispatchConversationNow_marksVideoFailedWhenCompressionFails() throws Exception {
        File tempFile = File.createTempFile("pending-video", ".mp4");
        tempFile.deleteOnExit();

        PendingMessage videoPending = buildPending(71L, 44L, PendingMessageDispatcher.STATUS_QUEUED);
        videoPending.setMediaType("VIDEO");
        videoPending.setLocalFilePath(tempFile.getAbsolutePath());

        when(pendingMessageRepository.getByConversationKey(44L)).thenReturn(List.of(videoPending));
        when(pendingMessageRepository.findByLocalId(eq(71L))).thenReturn(videoPending);

        dispatcher.dispatchConversationNow(44L);

        ArgumentCaptor<VideoPreparationService.Callback> callbackCaptor = ArgumentCaptor.forClass(VideoPreparationService.Callback.class);
        verify(videoPreparationService).prepareVideo(eq(tempFile), callbackCaptor.capture());
        dispatcher.handleVideoPrepareFailureNow(71L, "compress failed");

        assertEquals(PendingMessageDispatcher.STATUS_FAILED, videoPending.getStatus());
        assertEquals("压缩失败: compress failed", videoPending.getErrorMessage());
        assertEquals(1, videoPending.getAttemptCount());
    }

    @Test
    public void dispatchConversationNow_reuploadsProcessedVideoWhenRemoteUrlMissing() throws Exception {
        File processedFile = File.createTempFile("processed-video", ".mp4");
        processedFile.deleteOnExit();

        PendingMessage videoPending = buildPending(81L, 33L, PendingMessageDispatcher.STATUS_FAILED);
        videoPending.setMediaType("VIDEO");
        videoPending.setLocalFilePath("/tmp/original-video.mp4");
        videoPending.setProcessedFilePath(processedFile.getAbsolutePath());
        videoPending.setRemoteUrl(null);

        when(pendingMessageRepository.getByConversationKey(33L)).thenReturn(List.of(videoPending));
        when(pendingMessageRepository.findByLocalId(eq(81L))).thenReturn(videoPending);

        dispatcher.dispatchConversationNow(33L);

        verify(fileRepository).upload(eq(processedFile), any(FileRepository.FileCallback.class));
        verify(messageService, never()).send(any());
    }

    @Test
    public void dispatchConversationNow_marksMissingLocalFileAsFailed() {
        PendingMessage imagePending = buildPending(91L, 22L, PendingMessageDispatcher.STATUS_QUEUED);
        imagePending.setMediaType("IMAGE");
        imagePending.setLocalFilePath("/tmp/not-exists.jpg");

        when(pendingMessageRepository.getByConversationKey(22L)).thenReturn(List.of(imagePending));
        when(pendingMessageRepository.findByLocalId(eq(91L))).thenReturn(imagePending);

        dispatcher.dispatchConversationNow(22L);

        assertEquals(PendingMessageDispatcher.STATUS_FAILED, imagePending.getStatus());
        assertEquals("本地文件不存在", imagePending.getErrorMessage());
        assertEquals(1, imagePending.getAttemptCount());
        verify(fileRepository, never()).upload(any(File.class), any(FileRepository.FileCallback.class));
    }

    @Test
    public void dispatchNow_marksSendFailureAsServerRejected() {
        PendingMessage textPending = buildPending(111L, 18L, PendingMessageDispatcher.STATUS_FAILED);
        when(pendingMessageRepository.findByLocalId(eq(111L))).thenReturn(textPending);

        dispatcher.dispatchNow(111L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
        verify(mockSendCall).enqueue(callbackCaptor.capture());

        retrofit2.Response<com.flashnote.java.data.model.ApiResponse<com.flashnote.java.data.model.Message>> response =
                retrofit2.Response.success(new com.flashnote.java.data.model.ApiResponse<>(1, "forbidden", null));
        callbackCaptor.getValue().onResponse(mockSendCall, response);

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        assertEquals(PendingMessageDispatcher.STATUS_FAILED, textPending.getStatus());
        assertEquals("服务器拒绝: forbidden", textPending.getErrorMessage());
        assertEquals(1, textPending.getAttemptCount());
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
