package com.flashnote.java.ui.main;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;

import androidx.lifecycle.MutableLiveData;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.data.model.Collection;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.repository.CollectionRepository;
import com.flashnote.java.data.repository.FlashNoteRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FlashNoteViewModelTest {

    @Mock private FlashNoteApp application;
    @Mock private FlashNoteRepository flashNoteRepository;
    @Mock private CollectionRepository collectionRepository;

    private final MutableLiveData<List<FlashNote>> notes = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Collection>> collections = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private FlashNoteViewModel viewModel;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(application.getFlashNoteRepository()).thenReturn(flashNoteRepository);
        when(application.getCollectionRepository()).thenReturn(collectionRepository);
        when(flashNoteRepository.getNotes()).thenReturn(notes);
        when(collectionRepository.getCollections()).thenReturn(collections);
        when(flashNoteRepository.isLoading()).thenReturn(loading);
        when(flashNoteRepository.getErrorMessage()).thenReturn(error);
        viewModel = new FlashNoteViewModel((Application) application);
    }

    @Test
    public void refreshIfNeededSkipsWhileRefreshAlreadyInFlight() {
        loading.setValue(true);

        viewModel.refreshIfNeeded();

        verify(flashNoteRepository, never()).refresh();
        verify(collectionRepository, never()).refresh();
    }

    @Test
    public void refreshIfNeededRefreshesOnceWhenEmptyAndIdle() {
        loading.setValue(false);

        viewModel.refreshIfNeeded();

        verify(flashNoteRepository, times(1)).refresh();
        verify(collectionRepository, times(1)).refresh();
    }
}
