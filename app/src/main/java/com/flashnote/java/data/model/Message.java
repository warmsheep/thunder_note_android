package com.flashnote.java.data.model;

import com.google.gson.annotations.SerializedName;

import java.time.LocalDateTime;

public class Message {
    @SerializedName("id")
    private Long id;
    
    @SerializedName("senderId")
    private Long senderId;
    
    @SerializedName("receiverId")
    private Long receiverId;
    
    @SerializedName("content")
    private String content;
    
    @SerializedName("readStatus")
    private Boolean readStatus;
    
    @SerializedName("flashNoteId")
    private Long flashNoteId;
    
    @SerializedName("role")
    private String role;
    
    @SerializedName("createdAt")
    private LocalDateTime createdAt;

    public Message() {}

    public Message(Long id, Long flashNoteId, String role, String content) {
        this.id = id;
        this.flashNoteId = flashNoteId;
        this.role = role;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getReadStatus() {
        return readStatus;
    }

    public void setReadStatus(Boolean readStatus) {
        this.readStatus = readStatus;
    }

    public Long getFlashNoteId() {
        return flashNoteId;
    }

    public void setFlashNoteId(Long flashNoteId) {
        this.flashNoteId = flashNoteId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
