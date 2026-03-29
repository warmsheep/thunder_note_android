package com.flashnote.java.data.model;

public class FriendRequestActionRequest {
    private Long requestId;

    public FriendRequestActionRequest() {
    }

    public FriendRequestActionRequest(Long requestId) {
        this.requestId = requestId;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }
}
