package com.flashnote.java.data.model;

import com.google.gson.annotations.SerializedName;

import java.time.LocalDateTime;

public class FlashNote {
    @SerializedName("id")
    private Long id;
    
    @SerializedName("userId")
    private Long userId;
    
    @SerializedName("title")
    private String title;

    @SerializedName("icon")
    private String icon;
    
    @SerializedName("content")
    private String content;

    @SerializedName("latestMessage")
    private String latestMessage;
    
    @SerializedName("tags")
    private String tags;
    
    @SerializedName("deleted")
    private Boolean deleted;

    @SerializedName("pinned")
    private Boolean pinned;

    @SerializedName("hidden")
    private Boolean hidden;

    @SerializedName("inbox")
    private Boolean inbox;
    
    @SerializedName("createdAt")
    private LocalDateTime createdAt;
    
    @SerializedName("updatedAt")
    private LocalDateTime updatedAt;

    public FlashNote() {}

    public FlashNote(Long id, String title, String content) {
        this.id = id;
        this.title = title;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTags() {
        return tags;
    }

    public String getLatestMessage() {
        return latestMessage;
    }

    public void setLatestMessage(String latestMessage) {
        this.latestMessage = latestMessage;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Boolean getPinned() {
        return pinned;
    }

    public void setPinned(Boolean pinned) {
        this.pinned = pinned;
    }

    public Boolean getHidden() {
        return hidden;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    public Boolean getInbox() {
        return inbox;
    }

    public void setInbox(Boolean inbox) {
        this.inbox = inbox;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
