package com.flashnote.java.data.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.lifecycle.LiveData;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.remote.FlashNoteService;

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

    private FlashNoteRepositoryImpl repository;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(flashNoteService.list()).thenReturn(mockListCall);
        when(flashNoteService.create(any(FlashNote.class))).thenReturn(mockCreateCall);
        when(flashNoteService.update(any(Long.class), any(FlashNote.class))).thenReturn(mockUpdateCall);
        when(flashNoteService.delete(any(Long.class))).thenReturn(mockDeleteCall);

        repository = new FlashNoteRepositoryImpl(flashNoteService);
    }

    @Test
    public void getList_callsApi() {
        repository.refresh();

        verify(flashNoteService).list();
    }

    @Test
    public void create_callsApi() {
        repository.createNote("Test", "Content");

        verify(flashNoteService).create(any(FlashNote.class));
    }

    @Test
    public void update_callsApi() {
        Long noteId = 1L;

        repository.updateNote(noteId, "Updated", "Content");

        verify(flashNoteService).update(eq(noteId), any(FlashNote.class));
    }

    @Test
    public void delete_callsApi() {
        Long noteId = 1L;

        repository.deleteNote(noteId);

        verify(flashNoteService).delete(eq(noteId));
    }
}
