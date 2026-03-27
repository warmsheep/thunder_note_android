package com.flashnote.java.data.model;

import com.google.gson.annotations.SerializedName;

public class SyncPullRequest {
    @SerializedName("lastMessageCreatedAt")
    private String lastMessageCreatedAt;

    public String getLastMessageCreatedAt() {
        return lastMessageCreatedAt;
    }

    public void setLastMessageCreatedAt(String lastMessageCreatedAt) {
        this.lastMessageCreatedAt = lastMessageCreatedAt;
    }
}
