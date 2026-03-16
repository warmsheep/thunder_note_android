package com.flashnote.java.data.model;

public class FlashNoteSearchRequest {
    private String query;

    public FlashNoteSearchRequest(String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
