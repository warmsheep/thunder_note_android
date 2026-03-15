package com.flashnote.java.ui.chat;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.repository.MessageRepository;

import java.util.List;

public class ChatViewModel extends AndroidViewModel {
    private final MessageRepository repository;
    private final MutableLiveData<Long> flashNoteId = new MutableLiveData<>(0L);
    private LiveData<List<Message>> messages;

    public ChatViewModel(@NonNull Application application) {
        super(application);
        repository = ((FlashNoteApp) application).getMessageRepository();
    }

    public void bindFlashNote(long id) {
        flashNoteId.setValue(id);
        repository.bindFlashNote(id);
        messages = repository.getMessages(id);
    }

    public LiveData<List<Message>> getMessages() {
        return messages;
    }

    public void sendText(String text) {
        Long id = flashNoteId.getValue();
        if (id == null || text == null || text.trim().isEmpty()) {
            return;
        }
        repository.sendText(id, text.trim());
    }
}
