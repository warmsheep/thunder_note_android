package com.flashnote.java.ui.main;

import org.junit.Test;

import android.view.inputmethod.EditorInfo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlashNoteSearchAdapterPolicyTest {

    @Test
    public void returnsTrueWhenSearchIsVisibleButRecyclerStillShowsNormalList() {
        assertTrue(FlashNoteSearchAdapterPolicy.shouldAttachSearchAdapter(true, false));
    }

    @Test
    public void returnsFalseWhenRecyclerAlreadyShowsSearchResults() {
        assertFalse(FlashNoteSearchAdapterPolicy.shouldAttachSearchAdapter(true, true));
    }

    @Test
    public void returnsFalseWhenSearchUiIsClosed() {
        assertFalse(FlashNoteSearchAdapterPolicy.shouldAttachSearchAdapter(false, false));
    }

    @Test
    public void ensuresSearchAdapterWhenSearchModeIsActiveAndAdapterMissing() {
        assertTrue(FlashNoteSearchAdapterPolicy.shouldEnsureSearchAdapter(true, false));
    }

    @Test
    public void doesNotEnsureSearchAdapterWhenSearchModeIsInactive() {
        assertFalse(FlashNoteSearchAdapterPolicy.shouldEnsureSearchAdapter(false, false));
    }

    @Test
    public void doesNotEnsureSearchAdapterWhenAlreadyAttachedInSearchMode() {
        assertFalse(FlashNoteSearchAdapterPolicy.shouldEnsureSearchAdapter(true, true));
    }

    @Test
    public void showsSearchContainerWhenSearchModeIsActiveEvenIfInputIsNotRestoredYet() {
        assertTrue(FlashNoteSearchAdapterPolicy.shouldShowSearchContainer(true, false));
    }

    @Test
    public void hidesSearchContainerWhenSearchModeIsInactiveAndInputIsEmpty() {
        assertFalse(FlashNoteSearchAdapterPolicy.shouldShowSearchContainer(false, false));
    }

    @Test
    public void showsNormalListWhenSearchModeIsInactive() {
        assertTrue(FlashNoteSearchAdapterPolicy.shouldShowNormalList(false));
    }

    @Test
    public void hidesNormalListRuleWhenSearchModeIsActive() {
        assertFalse(FlashNoteSearchAdapterPolicy.shouldShowNormalList(true));
    }

    @Test
    public void submitSearchForImeSearchWithoutKeyEvent() {
        assertTrue(FlashNoteSearchInputPolicy.shouldSubmitSearch(EditorInfo.IME_ACTION_SEARCH, null));
    }

    @Test
    public void submitSearchForImeNullWithoutKeyEvent() {
        assertTrue(FlashNoteSearchInputPolicy.shouldSubmitSearch(EditorInfo.IME_NULL, null));
    }

    @Test
    public void submitSearchForHardwareEnterKeyDown() {
        assertTrue(FlashNoteSearchInputPolicy.shouldSubmitSearch(
                EditorInfo.IME_NULL,
                true,
                FlashNoteSearchInputPolicy.ACTION_DOWN,
                FlashNoteSearchInputPolicy.KEYCODE_ENTER
        ));
    }

    @Test
    public void doesNotSubmitSearchForHardwareEnterKeyUp() {
        assertFalse(FlashNoteSearchInputPolicy.shouldSubmitSearch(
                EditorInfo.IME_NULL,
                true,
                1,
                FlashNoteSearchInputPolicy.KEYCODE_ENTER
        ));
    }

    @Test
    public void showsSearchResultsAdapterWhenQueryHasText() {
        assertTrue(FlashNoteSearchInputPolicy.shouldShowSearchResultsAdapter("abc"));
    }

    @Test
    public void hidesSearchResultsAdapterWhenQueryIsBlank() {
        assertFalse(FlashNoteSearchInputPolicy.shouldShowSearchResultsAdapter("   "));
    }

    @Test
    public void rendersSearchPaneWhenContainerIsOpenEvenIfQueryIsBlank() {
        assertTrue(FlashNoteSearchAdapterPolicy.shouldRenderSearchPane(true, "   "));
    }

    @Test
    public void rendersSearchPaneWhenQueryHasTextEvenIfContainerStateLags() {
        assertTrue(FlashNoteSearchAdapterPolicy.shouldRenderSearchPane(false, "keyword"));
    }

    @Test
    public void rendersNormalPaneWhenSearchContainerIsClosedAndQueryIsEmpty() {
        assertFalse(FlashNoteSearchAdapterPolicy.shouldRenderSearchPane(false, "   "));
    }
}
