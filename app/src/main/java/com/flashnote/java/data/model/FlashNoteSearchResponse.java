package com.flashnote.java.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class FlashNoteSearchResponse {
    @SerializedName("noteNameMatched")
    private List<FlashNoteSearchResult> noteNameMatched;

    @SerializedName("messageContentMatched")
    private List<FlashNoteSearchResult> messageContentMatched;

    public List<FlashNoteSearchResult> getNoteNameMatched() { return noteNameMatched; }
    public void setNoteNameMatched(List<FlashNoteSearchResult> noteNameMatched) { this.noteNameMatched = noteNameMatched; }
    public List<FlashNoteSearchResult> getMessageContentMatched() { return messageContentMatched; }
    public void setMessageContentMatched(List<FlashNoteSearchResult> messageContentMatched) { this.messageContentMatched = messageContentMatched; }
}
