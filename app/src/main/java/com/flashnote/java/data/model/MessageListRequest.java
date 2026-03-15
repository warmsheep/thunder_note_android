package com.flashnote.java.data.model;

public class MessageListRequest {
    private final Long flashNoteId;

    public MessageListRequest(Long flashNoteId) {
        this.flashNoteId = flashNoteId;
    }

    public Long getFlashNoteId() {
        return flashNoteId;
    }
}
