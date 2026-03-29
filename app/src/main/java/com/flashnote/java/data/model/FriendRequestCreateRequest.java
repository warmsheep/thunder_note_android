package com.flashnote.java.data.model;

public class FriendRequestCreateRequest {
    private Long targetUserId;

    public FriendRequestCreateRequest() {
    }

    public FriendRequestCreateRequest(Long targetUserId) {
        this.targetUserId = targetUserId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(Long targetUserId) {
        this.targetUserId = targetUserId;
    }
}
