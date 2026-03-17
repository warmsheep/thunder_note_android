package com.flashnote.java.data.model;

import com.google.gson.annotations.SerializedName;

public class MessageListRequest {
    @SerializedName("flashNoteId")
    private final Long flashNoteId;
    
    @SerializedName("page")
    private Integer page;
    
    @SerializedName("limit")
    private Integer limit;

    public MessageListRequest(Long flashNoteId) {
        this.flashNoteId = flashNoteId;
    }

    public MessageListRequest(Long flashNoteId, Integer page, Integer limit) {
        this.flashNoteId = flashNoteId;
        this.page = page;
        this.limit = limit;
    }

    public Long getFlashNoteId() {
        return flashNoteId;
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
