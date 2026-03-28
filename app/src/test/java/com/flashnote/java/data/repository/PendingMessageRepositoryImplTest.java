package com.flashnote.java.data.repository;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.flashnote.java.data.local.PendingMessageDao;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class PendingMessageRepositoryImplTest {

    @Mock
    private PendingMessageDao pendingMessageDao;

    private PendingMessageRepositoryImpl repository;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        repository = new PendingMessageRepositoryImpl(pendingMessageDao);
    }

    @Test
    public void observePendingSyncCount_delegatesToDaoWithSyncStatuses() {
        MutableLiveData<Integer> liveData = new MutableLiveData<>(3);
        when(pendingMessageDao.observeCountByStatuses(org.mockito.ArgumentMatchers.anyList())).thenReturn(liveData);

        LiveData<Integer> result = repository.observePendingSyncCount();

        assertSame(liveData, result);
        verify(pendingMessageDao).observeCountByStatuses(List.of(
                PendingMessageDispatcher.STATUS_QUEUED,
                PendingMessageDispatcher.STATUS_PROCESSING,
                PendingMessageDispatcher.STATUS_UPLOADING,
                PendingMessageDispatcher.STATUS_UPLOADED,
                PendingMessageDispatcher.STATUS_SENDING,
                PendingMessageDispatcher.STATUS_FAILED
        ));
    }

    @Test
    public void getPendingSyncCountNow_delegatesToDaoWithSyncStatuses() {
        when(pendingMessageDao.countByStatuses(org.mockito.ArgumentMatchers.anyList())).thenReturn(5);

        int result = repository.getPendingSyncCountNow();

        assertEquals(5, result);
        verify(pendingMessageDao).countByStatuses(List.of(
                PendingMessageDispatcher.STATUS_QUEUED,
                PendingMessageDispatcher.STATUS_PROCESSING,
                PendingMessageDispatcher.STATUS_UPLOADING,
                PendingMessageDispatcher.STATUS_UPLOADED,
                PendingMessageDispatcher.STATUS_SENDING,
                PendingMessageDispatcher.STATUS_FAILED
        ));
    }

    @Test
    public void observePendingSyncMessages_delegatesToDaoWithSyncStatuses() {
        MutableLiveData<java.util.List<com.flashnote.java.data.model.PendingMessage>> liveData = new MutableLiveData<>(List.of());
        when(pendingMessageDao.observeByStatuses(org.mockito.ArgumentMatchers.anyList())).thenReturn(liveData);

        LiveData<java.util.List<com.flashnote.java.data.model.PendingMessage>> result = repository.observePendingSyncMessages();

        assertSame(liveData, result);
        verify(pendingMessageDao).observeByStatuses(List.of(
                PendingMessageDispatcher.STATUS_QUEUED,
                PendingMessageDispatcher.STATUS_PROCESSING,
                PendingMessageDispatcher.STATUS_UPLOADING,
                PendingMessageDispatcher.STATUS_UPLOADED,
                PendingMessageDispatcher.STATUS_SENDING,
                PendingMessageDispatcher.STATUS_FAILED
        ));
    }
}
