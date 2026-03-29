package com.flashnote.java.data.model;

public class AvatarUpdateRequest {
    private String avatar;

    public AvatarUpdateRequest() {
    }

    public AvatarUpdateRequest(String avatar) {
        this.avatar = avatar;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }
}
