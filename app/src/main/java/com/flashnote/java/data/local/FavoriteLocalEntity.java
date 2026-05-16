package com.flashnote.java.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorites_local")
public class FavoriteLocalEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private Long id;

    @ColumnInfo(name = "message_id")
    private Long messageId;

    @ColumnInfo(name = "user_id")
    private Long userId;

    @ColumnInfo(name = "flash_note_id")
    private Long flashNoteId;

    @ColumnInfo(name = "flash_note_title")
    private String flashNoteTitle;

    @ColumnInfo(name = "role")
    private String role;

    @ColumnInfo(name = "content")
    private String content;

    @ColumnInfo(name = "flash_note_icon")
    private String flashNoteIcon;

    @ColumnInfo(name = "media_type")
    private String mediaType;

    @ColumnInfo(name = "media_url")
    private String mediaUrl;

    @ColumnInfo(name = "file_name")
    private String fileName;

    @ColumnInfo(name = "file_size")
    private Long fileSize;

    @ColumnInfo(name = "media_duration")
    private Integer mediaDuration;

    @ColumnInfo(name = "payload_json")
    private String payloadJson;

    @ColumnInfo(name = "favorited_at")
    private String favoritedAt;

    @ColumnInfo(name = "message_created_at")
    private String messageCreatedAt;

    @NonNull
    public Long getId() {
        return id;
    }

    public void setId(@NonNull Long id) {
        this.id = id;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getFavoritedAt() {
        return favoritedAt;
    }

    public void setFavoritedAt(String favoritedAt) {
        this.favoritedAt = favoritedAt;
    }

    public String getMessageCreatedAt() {
        return messageCreatedAt;
    }

    public void setMessageCreatedAt(String messageCreatedAt) {
        this.messageCreatedAt = messageCreatedAt;
    }
}
