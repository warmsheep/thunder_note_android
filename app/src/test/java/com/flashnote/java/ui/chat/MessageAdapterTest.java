package com.flashnote.java.ui.chat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessageAdapterTest {
    @Test
    public void isInlineFilePreviewExtension_includesEpub() {
        assertTrue(MessageAdapter.isInlineFilePreviewExtension("epub"));
        assertTrue(MessageAdapter.isInlineFilePreviewExtension("pdf"));
        assertTrue(MessageAdapter.isInlineFilePreviewExtension("txt"));
        assertFalse(MessageAdapter.isInlineFilePreviewExtension("zip"));
    }
}
