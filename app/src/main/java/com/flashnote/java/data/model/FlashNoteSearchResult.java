package com.flashnote.java.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class FlashNoteSearchResult {
    @SerializedName("flashNote")
    private FlashNote flashNote;

    @SerializedName("matchedMessages")
    private List<MatchedMessageInfo> matchedMessages;

    @SerializedName("noteMatched")
    private boolean noteMatched;

    public FlashNote getFlashNote() { return flashNote; }
    public void setFlashNote(FlashNote flashNote) { this.flashNote = flashNote; }
    public List<MatchedMessageInfo> getMatchedMessages() { return matchedMessages; }
    public void setMatchedMessages(List<MatchedMessageInfo> matchedMessages) { this.matchedMessages = matchedMessages; }
    public boolean isNoteMatched() { return noteMatched; }
    public void setNoteMatched(boolean noteMatched) { this.noteMatched = noteMatched; }
}
