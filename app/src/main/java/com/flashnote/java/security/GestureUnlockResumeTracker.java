package com.flashnote.java.security;

public class GestureUnlockResumeTracker {
    private long backgroundAtElapsed = -1L;
    private int pendingSkipUnlockChecks;

    public void markBackgrounded(long elapsedRealtime) {
        backgroundAtElapsed = elapsedRealtime;
    }

    public void suppressNextUnlockCheck() {
        pendingSkipUnlockChecks++;
    }

    public ResumeState consumeResumeState(long elapsedRealtime) {
        if (pendingSkipUnlockChecks > 0) {
            pendingSkipUnlockChecks--;
            backgroundAtElapsed = -1L;
            return new ResumeState(true, -1L);
        }

        long backgroundDurationMs = backgroundAtElapsed < 0L
                ? -1L
                : elapsedRealtime - backgroundAtElapsed;
        backgroundAtElapsed = -1L;
        return new ResumeState(false, backgroundDurationMs);
    }

    public static final class ResumeState {
        private final boolean skipUnlockCheck;
        private final long backgroundDurationMs;

        ResumeState(boolean skipUnlockCheck, long backgroundDurationMs) {
            this.skipUnlockCheck = skipUnlockCheck;
            this.backgroundDurationMs = backgroundDurationMs;
        }

        public boolean shouldSkipUnlockCheck() {
            return skipUnlockCheck;
        }

        public long getBackgroundDurationMs() {
            return backgroundDurationMs;
        }
    }
}
