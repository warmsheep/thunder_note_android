package com.flashnote.java.data.model;

import com.google.gson.annotations.SerializedName;

public class MessageListRequest {
    @SerializedName("flashNoteId")
    private final Long flashNoteId;

    @SerializedName("peerUserId")
    private final Long peerUserId;
    
    @SerializedName("page")
    private Integer page;
    
    @SerializedName("limit")
    private Integer limit;

    public MessageListRequest(Long flashNoteId) {
        this.flashNoteId = flashNoteId;
        this.peerUserId = null;
    }

    public MessageListRequest(Long flashNoteId, Long peerUserId) {
        this.flashNoteId = flashNoteId;
        this.peerUserId = peerUserId;
    }

    public MessageListRequest(Long flashNoteId, Integer page, Integer limit) {
        this.flashNoteId = flashNoteId;
        this.peerUserId = null;
        this.page = page;
        this.limit = limit;
    }

    public MessageListRequest(Long flashNoteId, Long peerUserId, Integer page, Integer limit) {
        this.flashNoteId = flashNoteId;
        this.peerUserId = peerUserId;
        this.page = page;
        this.limit = limit;
    }

    public Long getFlashNoteId() {
        return flashNoteId;
    }

    public Long getPeerUserId() {
        return peerUserId;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
