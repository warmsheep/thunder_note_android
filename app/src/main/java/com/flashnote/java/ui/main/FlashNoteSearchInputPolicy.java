package com.flashnote.java.ui.main;

import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;

final class FlashNoteSearchInputPolicy {
    static final int ACTION_DOWN = 0;
    static final int KEYCODE_ENTER = 66;

    private FlashNoteSearchInputPolicy() {
    }

    static boolean shouldSubmitSearch(int actionId, @Nullable KeyEvent event) {
        if (event == null) {
            return shouldSubmitSearch(actionId, false, -1, -1);
        }
        return shouldSubmitSearch(actionId, true, event.getAction(), event.getKeyCode());
    }

    static boolean shouldSubmitSearch(int actionId, boolean hasEvent, int eventAction, int keyCode) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
            return !hasEvent || eventAction == ACTION_DOWN;
        }
        if (actionId == EditorInfo.IME_NULL && !hasEvent) {
            return true;
        }
        return hasEvent && eventAction == ACTION_DOWN && keyCode == KEYCODE_ENTER;
    }

    static boolean shouldShowSearchResultsAdapter(@Nullable String currentQuery) {
        return currentQuery != null && !currentQuery.trim().isEmpty();
    }
}
