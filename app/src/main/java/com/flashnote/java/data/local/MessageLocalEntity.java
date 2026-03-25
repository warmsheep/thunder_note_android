package com.flashnote.java.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "messages_local")
public class MessageLocalEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private Long id;

    @ColumnInfo(name = "conversation_key")
    private long conversationKey;

    @ColumnInfo(name = "sender_id")
    private Long senderId;

    @ColumnInfo(name = "receiver_id")
    private Long receiverId;

    @ColumnInfo(name = "flash_note_id")
    private Long flashNoteId;

    @ColumnInfo(name = "client_request_id")
    private String clientRequestId;

    @ColumnInfo(name = "content")
    private String content;

    @ColumnInfo(name = "read_status")
    private Boolean readStatus;

    @ColumnInfo(name = "role")
    private String role;

    @ColumnInfo(name = "created_at")
    private String createdAt;

    @ColumnInfo(name = "media_type")
    private String mediaType;

    @ColumnInfo(name = "media_url")
    private String mediaUrl;

    @ColumnInfo(name = "media_duration")
    private Integer mediaDuration;

    @ColumnInfo(name = "thumbnail_url")
    private String thumbnailUrl;

    @ColumnInfo(name = "file_name")
    private String fileName;

    @ColumnInfo(name = "file_size")
    private Long fileSize;

    @NonNull
    public Long getId() {
        return id;
    }

    public void setId(@NonNull Long id) {
        this.id = id;
    }

    public long getConversationKey() {
        return conversationKey;
    }

    public void setConversationKey(long conversationKey) {
        this.conversationKey = conversationKey;
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

    public Long getFlashNoteId() {
        return flashNoteId;
    }

    public void setFlashNoteId(Long flashNoteId) {
        this.flashNoteId = flashNoteId;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
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
}
