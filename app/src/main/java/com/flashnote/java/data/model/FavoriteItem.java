package com.flashnote.java.data.model;

import com.google.gson.annotations.SerializedName;

import java.time.LocalDateTime;

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

    @SerializedName("createdAt")
    private LocalDateTime createdAt;

    @SerializedName("flashNoteCreatedAt")
    private LocalDateTime flashNoteCreatedAt;

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getFlashNoteCreatedAt() {
        return flashNoteCreatedAt;
    }

    public void setFlashNoteCreatedAt(LocalDateTime flashNoteCreatedAt) {
        this.flashNoteCreatedAt = flashNoteCreatedAt;
    }
}
