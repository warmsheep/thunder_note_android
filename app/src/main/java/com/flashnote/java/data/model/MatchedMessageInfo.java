package com.flashnote.java.data.model;

import com.google.gson.annotations.SerializedName;

public class MatchedMessageInfo {
    @SerializedName("messageId")
    private Long messageId;

    @SerializedName("snippet")
    private String snippet;

    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
}
