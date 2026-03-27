package com.flashnote.java.data.sync;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import androidx.work.WorkerParameters;

import com.flashnote.java.data.repository.SyncRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class PendingRecoveryWorkerTest {

    @Mock private android.content.Context context;
    @Mock private WorkerParameters workerParameters;
    @Mock private PendingRecoveryWorker.RecoveryDependencies dependencies;

    private PendingRecoveryWorker worker;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        worker = new PendingRecoveryWorker(context, workerParameters);
        worker.setTestDependencies(dependencies);
    }

    @Test
    public void doWork_returnsSuccessWhenTokenInvalid() {
        org.mockito.Mockito.when(dependencies.isTokenValid()).thenReturn(false);

        androidx.work.ListenableWorker.Result result = worker.doWork();

        assertEquals(androidx.work.ListenableWorker.Result.success().toString(), result.toString());
        verify(dependencies, never()).retryAllPendingMessages();
    }

    @Test
    public void doWork_returnsSuccessWhenPullRefreshSucceeds() {
        org.mockito.Mockito.when(dependencies.isTokenValid()).thenReturn(true);
        ArgumentCaptor<SyncRepository.SyncCallback> callbackCaptor = ArgumentCaptor.forClass(SyncRepository.SyncCallback.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            SyncRepository.SyncCallback callback = invocation.getArgument(0);
            callback.onSuccess(Map.of("ok", true));
            return null;
        }).when(dependencies).pullAndRefreshLocal(callbackCaptor.capture());

        androidx.work.ListenableWorker.Result result = worker.doWork();

        assertEquals(androidx.work.ListenableWorker.Result.success().toString(), result.toString());
        verify(dependencies).retryAllPendingMessages();
        verify(dependencies).pullAndRefreshLocal(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void doWork_returnsRetryWhenPullRefreshFails() {
        org.mockito.Mockito.when(dependencies.isTokenValid()).thenReturn(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            SyncRepository.SyncCallback callback = invocation.getArgument(0);
            callback.onError("boom", 500);
            return null;
        }).when(dependencies).pullAndRefreshLocal(org.mockito.ArgumentMatchers.any());

        androidx.work.ListenableWorker.Result result = worker.doWork();

        assertEquals(androidx.work.ListenableWorker.Result.retry().toString(), result.toString());
        verify(dependencies).retryAllPendingMessages();
    }
}
