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

    public LiveData<String> getErrorMessage() {
        return repository.getErrorMessage();
    }

    public void sendText(String text, Runnable onSuccess) {
        Long id = flashNoteId.getValue();
        if (id == null || text == null || text.trim().isEmpty()) {
            return;
        }
        repository.sendText(id, text.trim(), onSuccess);
    }

    public void sendTextToFlashNote(long targetFlashNoteId, String text, Runnable onSuccess) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        repository.sendText(targetFlashNoteId, text.trim(), onSuccess);
    }

    public void deleteMessage(long messageId, Runnable onSuccess) {
        Long id = flashNoteId.getValue();
        if (id == null || messageId <= 0) {
            return;
        }
        repository.deleteMessage(id, messageId, onSuccess);
    }

    public void sendMedia(Message message, Runnable onSuccess) {
        Long id = flashNoteId.getValue();
        if (id == null || message == null) {
            return;
        }
        repository.sendMessage(id, message, onSuccess);
    }

    public void loadMore() {
        Long id = flashNoteId.getValue();
        if (id != null) {
            repository.loadMoreMessages(id);
        }
    }

    public LiveData<Boolean> getHasMore() {
        return repository.getHasMore();
    }
}
