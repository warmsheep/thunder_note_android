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
    
    @SerializedName("mediaType")
    private String mediaType;
    
    @SerializedName("mediaUrl")
    private String mediaUrl;
    
    @SerializedName("mediaDuration")
    private Integer mediaDuration;
    
    @SerializedName("thumbnailUrl")
    private String thumbnailUrl;
    
    @SerializedName("fileName")
    private String fileName;
    
    @SerializedName("fileSize")
    private Long fileSize;
    
    @SerializedName("payload")
    private CardPayload payload;
    
    private transient boolean uploading = false;
    private transient Long localSortTimestamp;

    public Message() {}

    public Message(Long id, Long flashNoteId, String role, String content) {
        this.id = id;
        this.flashNoteId = flashNoteId;
        this.role = role;
        this.content = content;
    }

    public CardPayload getPayload() {
        return payload;
    }

    public void setPayload(CardPayload payload) {
        this.payload = payload;
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
    
    public Integer getMediaDuration() {
        return mediaDuration;
    }
    
    public void setMediaDuration(Integer mediaDuration) {
        this.mediaDuration = mediaDuration;
    }
    
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }
    
    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
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
    
    public boolean isUploading() {
        return uploading;
    }
    
    public void setUploading(boolean uploading) {
        this.uploading = uploading;
    }

    public Long getLocalSortTimestamp() {
        return localSortTimestamp;
    }

    public void setLocalSortTimestamp(Long localSortTimestamp) {
        this.localSortTimestamp = localSortTimestamp;
    }
}
