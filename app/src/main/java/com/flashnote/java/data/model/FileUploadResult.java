package com.flashnote.java.data.model;

import com.google.gson.annotations.SerializedName;

public class FileUploadResult {
    @SerializedName("objectName")
    private String objectName;

    @SerializedName("originalFilename")
    private String originalFilename;

    public String getObjectName() { return objectName; }
    public void setObjectName(String objectName) { this.objectName = objectName; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
}
