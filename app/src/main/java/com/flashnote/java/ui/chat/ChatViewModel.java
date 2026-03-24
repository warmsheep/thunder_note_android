package com.flashnote.java.ui.chat;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.repository.MessageRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatViewModel extends AndroidViewModel {
    private final MessageRepository repository;
    private final MutableLiveData<Long> flashNoteId = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> peerUserId = new MutableLiveData<>(0L);
    private LiveData<List<Message>> messages;
    private final Map<Long, String> draftByFlashNoteId = new HashMap<>();

    public ChatViewModel(@NonNull Application application) {
        super(application);
        repository = ((FlashNoteApp) application).getMessageRepository();
    }

    public void bindFlashNote(long id) {
        Long currentFlashNoteId = flashNoteId.getValue();
        Long currentPeerUserId = peerUserId.getValue();
        if (currentFlashNoteId != null && currentFlashNoteId == id
                && (currentPeerUserId == null || currentPeerUserId == 0L)
                && messages != null) {
            return;
        }
        flashNoteId.setValue(id);
        peerUserId.setValue(0L);
        repository.clearError();
        repository.bindFlashNote(id);
        messages = repository.getMessages(id);
    }

    public void bindContact(long peerId) {
        Long currentPeerUserId = peerUserId.getValue();
        Long currentFlashNoteId = flashNoteId.getValue();
        if (currentPeerUserId != null && currentPeerUserId == peerId
                && (currentFlashNoteId == null || currentFlashNoteId == 0L)
                && messages != null) {
            return;
        }
        peerUserId.setValue(peerId);
        flashNoteId.setValue(0L);
        repository.clearError();
        repository.bindContact(peerId);
        messages = repository.getContactMessages(peerId);
    }

    public LiveData<List<Message>> getMessages() {
        return messages;
    }

    public LiveData<String> getErrorMessage() {
        return repository.getErrorMessage();
    }

    public void clearError() {
        repository.clearError();
    }

    public void sendText(String text, Runnable onSuccess) {
        Long id = flashNoteId.getValue();
        if (id == null || text == null || text.trim().isEmpty()) {
            return;
        }
        repository.sendText(id, text.trim(), onSuccess);
    }

    public void sendTextToCurrentConversation(String text, Runnable onSuccess) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        Long peerId = peerUserId.getValue();
        if (peerId != null && peerId > 0L) {
            repository.sendTextToContact(peerId, text.trim(), onSuccess);
            return;
        }
        Long noteId = flashNoteId.getValue();
        if (noteId != null && noteId != 0L) {
            repository.sendText(noteId, text.trim(), onSuccess);
        }
    }

    public void sendTextToFlashNote(long targetFlashNoteId, String text, Runnable onSuccess) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        repository.sendText(targetFlashNoteId, text.trim(), onSuccess);
    }

    public void sendMessageToFlashNote(long targetFlashNoteId, Message message, Runnable onSuccess) {
        if (targetFlashNoteId == 0L || message == null) {
            return;
        }
        repository.sendMessage(targetFlashNoteId, message, onSuccess);
    }

    public void sendMessageToFlashNote(long targetFlashNoteId, Message message, MessageRepository.SendCallback callback) {
        if (targetFlashNoteId == 0L || message == null) {
            return;
        }
        repository.sendMessage(targetFlashNoteId, message, callback);
    }

    public void sendMessageToContact(long targetPeerUserId, Message message, Runnable onSuccess) {
        if (targetPeerUserId == 0L || message == null) {
            return;
        }
        repository.sendMessageToContact(targetPeerUserId, message, onSuccess);
    }

    public void sendMessageToContact(long targetPeerUserId, Message message, MessageRepository.SendCallback callback) {
        if (targetPeerUserId == 0L || message == null) {
            return;
        }
        repository.sendMessageToContact(targetPeerUserId, message, callback);
    }

    public void deleteMessage(long messageId, Runnable onSuccess) {
        if (messageId <= 0) {
            return;
        }
        Long peerId = peerUserId.getValue();
        if (peerId != null && peerId > 0L) {
            repository.deleteContactMessage(peerId, messageId, onSuccess);
            return;
        }
        Long id = flashNoteId.getValue();
        if (id != null && id != 0L) {
            repository.deleteMessage(id, messageId, onSuccess);
        }
    }

    public void mergeMessages(java.util.List<Long> messageIds, String title, com.flashnote.java.data.repository.MessageRepository.MergeCallback callback) {
        if (messageIds == null || messageIds.isEmpty()) {
            if (callback != null) callback.onError("No messages selected");
            return;
        }
        Long peerId = peerUserId.getValue();
        if (peerId != null && peerId > 0L) {
            repository.mergeContactMessages(peerId, messageIds, title, callback);
            return;
        }
        Long id = flashNoteId.getValue();
        if (id != null && id != 0L) {
            repository.mergeMessages(id, messageIds, title, callback);
        }
    }

    public void sendMedia(Message message, Runnable onSuccess) {
        if (message == null) {
            return;
        }
        Long targetPeerId = message.getReceiverId();
        if (targetPeerId != null && targetPeerId > 0L) {
            repository.sendMessageToContact(targetPeerId, message, onSuccess);
            return;
        }
        Long targetFlashNoteId = message.getFlashNoteId();
        if (targetFlashNoteId != null && targetFlashNoteId != 0L) {
            repository.sendMessage(targetFlashNoteId, message, onSuccess);
        }
    }

    public void sendMedia(Message message, MessageRepository.SendCallback callback) {
        if (message == null) {
            return;
        }
        Long targetPeerId = message.getReceiverId();
        if (targetPeerId != null && targetPeerId > 0L) {
            repository.sendMessageToContact(targetPeerId, message, callback);
            return;
        }
        Long targetFlashNoteId = message.getFlashNoteId();
        if (targetFlashNoteId != null && targetFlashNoteId != 0L) {
            repository.sendMessage(targetFlashNoteId, message, callback);
        }
    }

    public void loadMore() {
        Long peerId = peerUserId.getValue();
        if (peerId != null && peerId > 0L) {
            repository.loadMoreContactMessages(peerId);
            return;
        }
        Long id = flashNoteId.getValue();
        if (id != null && id != 0L) {
            repository.loadMoreMessages(id);
        }
    }

    public LiveData<Boolean> getHasMore() {
        return repository.getHasMore();
    }

    public void addLocalMessage(Message message) {
        Long peerId = peerUserId.getValue();
        if (peerId != null && peerId > 0L) {
            repository.addLocalContactMessage(peerId, message);
            return;
        }
        repository.addLocalMessage(message);
    }

    public void removeLocalMessage(Message message) {
        if (message == null) {
            return;
        }
        repository.removeLocalMessage(message);
    }

    public void saveDraft(String text) {
        Long key = getCurrentConversationKey();
        if (key == null) {
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            draftByFlashNoteId.remove(key);
            return;
        }
        draftByFlashNoteId.put(key, text);
    }

    public String getDraft(long id) {
        String draft = draftByFlashNoteId.get(id);
        return draft == null ? "" : draft;
    }

    public String getCurrentDraft() {
        Long key = getCurrentConversationKey();
        if (key == null) {
            return "";
        }
        String draft = draftByFlashNoteId.get(key);
        return draft == null ? "" : draft;
    }

    public void clearDraft() {
        Long key = getCurrentConversationKey();
        if (key != null) {
            draftByFlashNoteId.remove(key);
        }
    }

    private Long getCurrentConversationKey() {
        Long peerId = peerUserId.getValue();
        if (peerId != null && peerId > 0L) {
            return -1_000_000_000L - Math.abs(peerId);
        }
        Long noteId = flashNoteId.getValue();
        if (noteId != null && noteId != 0L) {
            return noteId;
        }
        return null;
    }
}
