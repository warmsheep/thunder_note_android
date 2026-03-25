package com.flashnote.java.data.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;

import androidx.lifecycle.LiveData;

import com.flashnote.java.TokenManager;
import com.flashnote.java.data.local.FlashNoteLocalDao;
import com.flashnote.java.data.local.FlashNoteLocalEntity;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.remote.FlashNoteService;

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
public class FlashNoteRepositoryImplTest {

    @Mock
    private FlashNoteService flashNoteService;

    @Mock
    private Call<ApiResponse<List<FlashNote>>> mockListCall;

    @Mock
    private Call<ApiResponse<FlashNote>> mockCreateCall;

    @Mock
    private Call<ApiResponse<FlashNote>> mockUpdateCall;

    @Mock
    private Call<ApiResponse<Void>> mockDeleteCall;

    @Mock
    private FlashNoteLocalDao flashNoteLocalDao;

    @Mock
    private TokenManager tokenManager;

    private FlashNoteRepositoryImpl repository;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(flashNoteService.list()).thenReturn(mockListCall);
        when(flashNoteService.create(any(FlashNote.class))).thenReturn(mockCreateCall);
        when(flashNoteService.update(any(Long.class), any(FlashNote.class))).thenReturn(mockUpdateCall);
        when(flashNoteService.delete(any(Long.class))).thenReturn(mockDeleteCall);
        when(tokenManager.getUserId()).thenReturn(101L);
        when(flashNoteLocalDao.observeAllByUserId(101L)).thenReturn(new androidx.lifecycle.MutableLiveData<>(new ArrayList<>()));

        repository = new FlashNoteRepositoryImpl(flashNoteService, flashNoteLocalDao, tokenManager);
    }

    @Test
    public void getList_callsApi() {
        repository.refresh();

        verify(flashNoteService).list();
    }

    @Test
    public void create_callsApi() {
        repository.createNote("Test", "💡", "工作");

        ArgumentCaptor<FlashNote> captor = forClass(FlashNote.class);
        verify(flashNoteService).create(captor.capture());
        FlashNote payload = captor.getValue();
        org.junit.Assert.assertEquals("Test", payload.getTitle());
        org.junit.Assert.assertEquals("💡", payload.getIcon());
        org.junit.Assert.assertEquals("工作", payload.getTags());
    }

    @Test
    public void update_callsApi() {
        Long noteId = 1L;

        repository.updateNote(noteId, "Updated", "Content", "📚", "学习");

        ArgumentCaptor<FlashNote> captor = forClass(FlashNote.class);
        verify(flashNoteService).update(eq(noteId), captor.capture());
        FlashNote payload = captor.getValue();
        org.junit.Assert.assertEquals("Updated", payload.getTitle());
        org.junit.Assert.assertEquals("📚", payload.getIcon());
        org.junit.Assert.assertEquals("学习", payload.getTags());
    }

    @Test
    public void delete_callsApi() {
        Long noteId = 1L;

        repository.deleteNote(noteId);

        verify(flashNoteService).delete(eq(noteId));
    }

    @Test
    public void getNotes_observesLocalDaoInsteadOfRepositoryMemoryState() {
        LiveData<List<FlashNote>> notes = repository.getNotes();

        org.junit.Assert.assertNotNull(notes);
        verify(flashNoteLocalDao).observeAllByUserId(101L);
    }

    @Test
    public void refresh_persistsRemoteNotesIntoLocalDao() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<List<FlashNote>>>> callbackCaptor = forClass(Callback.class);

        FlashNote note = new FlashNote();
        note.setId(7L);
        note.setTitle("本地真源");
        note.setPinned(true);
        note.setUpdatedAt(LocalDateTime.parse("2026-03-25T12:00:00"));

        repository.refresh();

        verify(mockListCall).enqueue(callbackCaptor.capture());
        callbackCaptor.getValue().onResponse(
                mockListCall,
                Response.success(new ApiResponse<>(0, "ok", List.of(note)))
        );

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(flashNoteLocalDao).clearAllByUserId(101L);
        verify(flashNoteLocalDao, atLeastOnce()).upsertAll(any());
    }

    @Test
    public void create_success_persistsCreatedNoteToLocalDao() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<FlashNote>>> callbackCaptor = forClass(Callback.class);
        FlashNote created = new FlashNote();
        created.setId(9L);
        created.setTitle("新闪记");

        repository.createNote("Test", "💡", "工作");

        verify(mockCreateCall).enqueue(callbackCaptor.capture());
        callbackCaptor.getValue().onResponse(
                mockCreateCall,
                Response.success(new ApiResponse<>(0, "ok", created))
        );

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(flashNoteLocalDao).upsert(any(FlashNoteLocalEntity.class));
    }

    @Test
    public void delete_success_removesNoteFromLocalDao() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<Void>>> callbackCaptor = forClass(Callback.class);

        repository.deleteNote(11L);

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

        verify(flashNoteLocalDao).deleteById(11L);
        verify(flashNoteLocalDao, never()).upsert(any(FlashNoteLocalEntity.class));
    }

    @Test
    public void refresh_onlyClearsCurrentUserLocalNotes() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<List<FlashNote>>>> callbackCaptor = forClass(Callback.class);

        repository.refresh();

        verify(mockListCall).enqueue(callbackCaptor.capture());
        callbackCaptor.getValue().onResponse(
                mockListCall,
                Response.success(new ApiResponse<>(0, "ok", List.of()))
        );

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        verify(flashNoteLocalDao, times(1)).clearAllByUserId(101L);
    }
}
