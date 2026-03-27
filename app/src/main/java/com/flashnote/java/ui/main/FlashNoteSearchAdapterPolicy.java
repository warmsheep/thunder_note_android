package com.flashnote.java.ui.main;

final class FlashNoteSearchAdapterPolicy {

    private FlashNoteSearchAdapterPolicy() {
    }

    static boolean shouldAttachSearchAdapter(boolean searchVisible, boolean alreadyShowingSearchAdapter) {
        return searchVisible && !alreadyShowingSearchAdapter;
    }

    static boolean shouldEnsureSearchAdapter(boolean searchModeActive, boolean alreadyShowingSearchAdapter) {
        return searchModeActive && !alreadyShowingSearchAdapter;
    }

    static boolean shouldShowSearchContainer(boolean searchModeActive, boolean searchInputHasText) {
        return searchModeActive || searchInputHasText;
    }

    static boolean shouldShowNormalList(boolean searchModeActive) {
        return !searchModeActive;
    }

    static boolean shouldRenderSearchPane(boolean searchContainerVisible, String currentQuery) {
        return searchContainerVisible || FlashNoteSearchInputPolicy.shouldShowSearchResultsAdapter(currentQuery);
    }
}
