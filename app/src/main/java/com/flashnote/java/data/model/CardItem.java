package com.flashnote.java.data.model;

import com.google.gson.annotations.SerializedName;

public class CardItem {
    @SerializedName("type")
    private String type;

    @SerializedName("content")
    private String content;

    @SerializedName("url")
    private String url;

    @SerializedName("thumbnailUrl")
    private String thumbnailUrl;

    @SerializedName("fileName")
    private String fileName;

    @SerializedName("fileSize")
    private Long fileSize;

    @SerializedName("originalMsgId")
    private Long originalMsgId;

    @SerializedName("senderId")
    private Long senderId;

    @SerializedName("role")
    private String role;

    private String localPath;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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

    public Long getOriginalMsgId() {
        return originalMsgId;
    }

    public void setOriginalMsgId(Long originalMsgId) {
        this.originalMsgId = originalMsgId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }
}
