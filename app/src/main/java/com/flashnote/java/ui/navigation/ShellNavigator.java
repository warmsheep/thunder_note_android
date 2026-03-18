package com.flashnote.java.ui.navigation;

public interface ShellNavigator {
    void openSplash();

    void openLogin();

    void openRegister();

    void openMainShell();

    void openChat(long flashNoteId, String title);

    void openChat(long flashNoteId, String title, long scrollToMessageId);

    void openContactChat(long peerUserId, String title);

    void openQuickCaptureTextEditor();

    void openEditProfile();

    void openChangePassword();

    void openSettings();

    void openDebug();

    void logoutToLogin();
}
