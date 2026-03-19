package com.flashnote.java.data.model;

import java.util.List;

public class MessageMergeRequest {
    private String title;
    private List<Long> messageIds;
    private Long flashNoteId;
    private Long receiverId;

    public MessageMergeRequest() {}

    public MessageMergeRequest(String title, List<Long> messageIds) {
        this.title = title;
        this.messageIds = messageIds;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Long> getMessageIds() {
        return messageIds;
    }

    public void setMessageIds(List<Long> messageIds) {
        this.messageIds = messageIds;
    }

    public Long getFlashNoteId() {
        return flashNoteId;
    }

    public void setFlashNoteId(Long flashNoteId) {
        this.flashNoteId = flashNoteId;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }
}
