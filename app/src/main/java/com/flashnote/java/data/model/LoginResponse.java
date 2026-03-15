package com.flashnote.java.data.model;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    @SerializedName(value = "accessToken", alternate = {"token"})
    private String token;
    
    @SerializedName("refreshToken")
    private String refreshToken;
    
    @SerializedName("tokenType")
    private String tokenType;
    
    @SerializedName("expiresIn")
    private long expiresIn;
    
    @SerializedName("user")
    private User user;

    public LoginResponse() {}

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
