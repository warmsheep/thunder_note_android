package com.flashnote.java.data.model;

import java.util.List;
import java.util.Map;

public class SyncPushRequest {
    private List<FlashNote> notes;
    private List<Collection> collections;
    private List<Map<String, Object>> messages;
    private List<FavoriteItem> favorites;

    public SyncPushRequest() {
    }

    public SyncPushRequest(List<FlashNote> notes,
                           List<Collection> collections,
                           List<Map<String, Object>> messages,
                           List<FavoriteItem> favorites) {
        this.notes = notes;
        this.collections = collections;
        this.messages = messages;
        this.favorites = favorites;
    }

    public List<FlashNote> getNotes() {
        return notes;
    }

    public void setNotes(List<FlashNote> notes) {
        this.notes = notes;
    }

    public List<Collection> getCollections() {
        return collections;
    }

    public void setCollections(List<Collection> collections) {
        this.collections = collections;
    }

    public List<Map<String, Object>> getMessages() {
        return messages;
    }

    public void setMessages(List<Map<String, Object>> messages) {
        this.messages = messages;
    }

    public List<FavoriteItem> getFavorites() {
        return favorites;
    }

    public void setFavorites(List<FavoriteItem> favorites) {
        this.favorites = favorites;
    }
}
