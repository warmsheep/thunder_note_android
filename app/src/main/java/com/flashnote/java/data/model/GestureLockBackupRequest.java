package com.flashnote.java.data.model;

public class GestureLockBackupRequest {
    private String ciphertext;
    private String nonce;
    private String kdfParams;
    private String version;

    public GestureLockBackupRequest() {
    }

    public GestureLockBackupRequest(String ciphertext, String nonce, String kdfParams, String version) {
        this.ciphertext = ciphertext;
        this.nonce = nonce;
        this.kdfParams = kdfParams;
        this.version = version;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(String ciphertext) {
        this.ciphertext = ciphertext;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getKdfParams() {
        return kdfParams;
    }

    public void setKdfParams(String kdfParams) {
        this.kdfParams = kdfParams;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
