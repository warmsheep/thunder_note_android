package com.flashnote.java.security;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GestureUnlockResumeTrackerTest {

    @Test
    public void consumeResumeStateReturnsElapsedBackgroundDurationByDefault() {
        GestureUnlockResumeTracker tracker = new GestureUnlockResumeTracker();

        tracker.markBackgrounded(1_000L);
        GestureUnlockResumeTracker.ResumeState state = tracker.consumeResumeState(6_500L);

        assertFalse(state.shouldSkipUnlockCheck());
        assertEquals(5_500L, state.getBackgroundDurationMs());
    }

    @Test
    public void consumeResumeStateSkipsUnlockOnceAfterExternalFlow() {
        GestureUnlockResumeTracker tracker = new GestureUnlockResumeTracker();

        tracker.markBackgrounded(2_000L);
        tracker.suppressNextUnlockCheck();

        GestureUnlockResumeTracker.ResumeState skippedState = tracker.consumeResumeState(9_000L);
        assertTrue(skippedState.shouldSkipUnlockCheck());
        assertEquals(-1L, skippedState.getBackgroundDurationMs());

        GestureUnlockResumeTracker.ResumeState nextState = tracker.consumeResumeState(12_000L);
        assertFalse(nextState.shouldSkipUnlockCheck());
        assertEquals(-1L, nextState.getBackgroundDurationMs());
    }
}
