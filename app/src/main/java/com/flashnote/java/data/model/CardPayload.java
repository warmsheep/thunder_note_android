package com.flashnote.java.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class CardPayload {
    @SerializedName("cardType")
    private String cardType;

    @SerializedName("title")
    private String title;

    @SerializedName("summary")
    private String summary;

    @SerializedName("items")
    private List<CardItem> items;

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<CardItem> getItems() {
        return items;
    }

    public void setItems(List<CardItem> items) {
        this.items = items;
    }
}
