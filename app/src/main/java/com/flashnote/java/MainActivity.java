package com.flashnote.java;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.flashnote.java.databinding.ActivityMainBinding;
import com.flashnote.java.ui.auth.LoginFragment;
import com.flashnote.java.ui.auth.RegisterFragment;
import com.flashnote.java.ui.auth.SplashFragment;
import com.flashnote.java.ui.chat.ChatFragment;
import com.flashnote.java.ui.chat.CardEditorFragment;
import com.flashnote.java.ui.main.EditProfileFragment;
import com.flashnote.java.ui.main.MainShellFragment;
import com.flashnote.java.ui.main.QuickCaptureTextFragment;
import com.flashnote.java.ui.navigation.ShellNavigator;
import com.flashnote.java.ui.settings.ChangePasswordFragment;
import com.flashnote.java.ui.settings.SettingsFragment;

public class MainActivity extends AppCompatActivity implements ShellNavigator {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState == null) {
            openSplash();
        }
    }

    @Override
    public void openSplash() {
        replaceRootFragment(new SplashFragment(), false, true);
    }

    @Override
    public void openLogin() {
        replaceRootFragment(new LoginFragment(), false, true);
    }

    @Override
    public void openRegister() {
        replaceRootFragment(new RegisterFragment(), true, false);
    }

    @Override
    public void openMainShell() {
        replaceRootFragment(new MainShellFragment(), false, true);
    }

    @Override
    public void openChat(long flashNoteId, String title) {
        replaceRootFragment(ChatFragment.newInstance(flashNoteId, title), true, false);
    }

    @Override
    public void openChat(long flashNoteId, String title, long scrollToMessageId) {
        replaceRootFragment(ChatFragment.newInstance(flashNoteId, title, scrollToMessageId), true, false);
    }

    @Override
    public void openContactChat(long peerUserId, String title) {
        replaceRootFragment(ChatFragment.newContactInstance(peerUserId, title), true, false);
    }

    @Override
    public void openQuickCaptureTextEditor() {
        replaceRootFragment(new QuickCaptureTextFragment(), true, false);
    }

    @Override
    public void openCardEditor(long flashNoteId, long peerUserId, String title) {
        replaceRootFragment(CardEditorFragment.newInstance(flashNoteId, peerUserId, title), true, false);
    }

    @Override
    public void openEditProfile() {
        replaceRootFragment(new EditProfileFragment(), true, false);
    }

    @Override
    public void openChangePassword() {
        replaceRootFragment(new ChangePasswordFragment(), true, false);
    }

    @Override
    public void openSettings() {
        replaceRootFragment(new SettingsFragment(), true, false);
    }

    @Override
    public void logoutToLogin() {
        openLogin();
    }

    private void replaceRootFragment(Fragment fragment, boolean addToBackStack, boolean clearBackStack) {
        if (clearBackStack) {
            clearBackStack();
        }

        if (isFinishing() || isDestroyed()) {
            return;
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        androidx.fragment.app.FragmentTransaction transaction = fragmentManager.beginTransaction()
                .replace(binding.rootFragmentContainer.getId(), fragment, fragment.getClass().getSimpleName());

        if (addToBackStack) {
            transaction.addToBackStack(fragment.getClass().getSimpleName());
        }

        if (fragmentManager.isStateSaved()) {
            transaction.commitAllowingStateLoss();
        } else {
            transaction.commit();
        }
    }

    private void clearBackStack() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            if (fragmentManager.isStateSaved()) {
                fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            } else {
                fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            }
        }
    }
}
