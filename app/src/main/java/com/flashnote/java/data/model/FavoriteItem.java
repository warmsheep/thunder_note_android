package com.flashnote.java.data.model;

import com.google.gson.annotations.SerializedName;

public class FavoriteItem {
    @SerializedName("id")
    private Long id;

    @SerializedName("messageId")
    private Long messageId;

    @SerializedName("flashNoteId")
    private Long flashNoteId;

    @SerializedName("flashNoteTitle")
    private String flashNoteTitle;

    @SerializedName("role")
    private String role;

    @SerializedName("content")
    private String content;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getFlashNoteId() {
        return flashNoteId;
    }

    public void setFlashNoteId(Long flashNoteId) {
        this.flashNoteId = flashNoteId;
    }

    public String getFlashNoteTitle() {
        return flashNoteTitle;
    }

    public void setFlashNoteTitle(String flashNoteTitle) {
        this.flashNoteTitle = flashNoteTitle;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
