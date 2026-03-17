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

    @SerializedName("flashNoteIcon")
    private String flashNoteIcon;

    @SerializedName("mediaType")
    private String mediaType;

    @SerializedName("mediaUrl")
    private String mediaUrl;

    @SerializedName("fileName")
    private String fileName;

    @SerializedName("fileSize")
    private Long fileSize;

    @SerializedName("mediaDuration")
    private Integer mediaDuration;

    @SerializedName(value = "favoritedAt", alternate = {"createdAt"})
    private LocalDateTime favoritedAt;

    @SerializedName(value = "messageCreatedAt", alternate = {"flashNoteCreatedAt"})
    private LocalDateTime messageCreatedAt;

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

    public String getFlashNoteIcon() {
        return flashNoteIcon;
    }

    public void setFlashNoteIcon(String flashNoteIcon) {
        this.flashNoteIcon = flashNoteIcon;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Integer getMediaDuration() {
        return mediaDuration;
    }

    public void setMediaDuration(Integer mediaDuration) {
        this.mediaDuration = mediaDuration;
    }

    public LocalDateTime getFavoritedAt() {
        return favoritedAt;
    }

    public void setFavoritedAt(LocalDateTime favoritedAt) {
        this.favoritedAt = favoritedAt;
    }

    public LocalDateTime getMessageCreatedAt() {
        return messageCreatedAt;
    }

    public void setMessageCreatedAt(LocalDateTime messageCreatedAt) {
        this.messageCreatedAt = messageCreatedAt;
    }
}
