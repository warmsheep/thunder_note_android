package com.flashnote.java.data.model;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pending_messages")
public class PendingMessage {

    @PrimaryKey(autoGenerate = true)
    private long localId;

    @ColumnInfo(name = "conversation_key")
    private long conversationKey;

    @ColumnInfo(name = "flash_note_id")
    @Nullable
    private Long flashNoteId;

    @ColumnInfo(name = "peer_user_id")
    @Nullable
    private Long peerUserId;

    @ColumnInfo(name = "client_request_id")
    @Nullable
    private String clientRequestId;

    @ColumnInfo(name = "media_type")
    @Nullable
    private String mediaType;

    @ColumnInfo(name = "content")
    @Nullable
    private String content;

    @ColumnInfo(name = "local_file_path")
    @Nullable
    private String localFilePath;

    @ColumnInfo(name = "remote_url")
    @Nullable
    private String remoteUrl;

    @ColumnInfo(name = "status")
    @Nullable
    private String status;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "error_message")
    @Nullable
    private String errorMessage;

    @ColumnInfo(name = "attempt_count")
    private int attemptCount;

    @ColumnInfo(name = "server_message_id")
    @Nullable
    private Long serverMessageId;

    public long getLocalId() {
        return localId;
    }

    public void setLocalId(long localId) {
        this.localId = localId;
    }

    public long getConversationKey() {
        return conversationKey;
    }

    public void setConversationKey(long conversationKey) {
        this.conversationKey = conversationKey;
    }

    @Nullable
    public Long getFlashNoteId() {
        return flashNoteId;
    }

    public void setFlashNoteId(@Nullable Long flashNoteId) {
        this.flashNoteId = flashNoteId;
    }

    @Nullable
    public Long getPeerUserId() {
        return peerUserId;
    }

    public void setPeerUserId(@Nullable Long peerUserId) {
        this.peerUserId = peerUserId;
    }

    @Nullable
    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(@Nullable String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    @Nullable
    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(@Nullable String mediaType) {
        this.mediaType = mediaType;
    }

    @Nullable
    public String getContent() {
        return content;
    }

    public void setContent(@Nullable String content) {
        this.content = content;
    }

    @Nullable
    public String getLocalFilePath() {
        return localFilePath;
    }

    public void setLocalFilePath(@Nullable String localFilePath) {
        this.localFilePath = localFilePath;
    }

    @Nullable
    public String getRemoteUrl() {
        return remoteUrl;
    }

    public void setRemoteUrl(@Nullable String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    public void setStatus(@Nullable String status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(@Nullable String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    @Nullable
    public Long getServerMessageId() {
        return serverMessageId;
    }

    public void setServerMessageId(@Nullable Long serverMessageId) {
        this.serverMessageId = serverMessageId;
    }
}
