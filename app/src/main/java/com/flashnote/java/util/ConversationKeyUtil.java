package com.flashnote.java.util;

import androidx.annotation.Nullable;

public final class ConversationKeyUtil {

    public static final long COLLECTION_BOX_NOTE_ID = -1L;
    private static final long CONTACT_KEY_BASE = -1_000_000_000L;

    private ConversationKeyUtil() {
    }

    public static long forFlashNote(long flashNoteId) {
        return flashNoteId;
    }

    public static long forContact(long peerUserId) {
        return CONTACT_KEY_BASE - Math.abs(peerUserId);
    }

    @Nullable
    public static Long resolve(@Nullable Long flashNoteId, @Nullable Long peerUserId) {
        if (peerUserId != null && peerUserId > 0L) {
            return forContact(peerUserId);
        }
        if (flashNoteId != null && flashNoteId != 0L) {
            return forFlashNote(flashNoteId);
        }
        return null;
    }
}
