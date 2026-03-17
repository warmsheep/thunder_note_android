package com.flashnote.java.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class MatchedMessageInfo {
    @SerializedName("messageId")
    private Long messageId;

    @SerializedName("snippet")
    private String snippet;

    @SerializedName("contextMessages")
    private List<Message> contextMessages;

    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
    public List<Message> getContextMessages() { return contextMessages; }
    public void setContextMessages(List<Message> contextMessages) { this.contextMessages = contextMessages; }
}
