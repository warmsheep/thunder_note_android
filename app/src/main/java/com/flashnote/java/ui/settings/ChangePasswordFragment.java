package com.flashnote.java.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.databinding.FragmentChangePasswordBinding;
import com.flashnote.java.security.GestureLockManager;
import com.flashnote.java.ui.navigation.ShellNavigator;

public class ChangePasswordFragment extends Fragment {
    private FragmentChangePasswordBinding binding;
    private GestureLockManager gestureLockManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentChangePasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        gestureLockManager = FlashNoteApp.getInstance().getGestureLockManager();

        binding.backButton.setOnClickListener(v -> navigateBack());
        binding.changeLoginPasswordItem.setOnClickListener(v -> openChangeLoginPassword());
        binding.gestureLockItem.setOnClickListener(v -> openGestureEntry());
    }

    private void openChangeLoginPassword() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        if (getActivity() instanceof ShellNavigator navigator) {
            navigator.openChangeLoginPassword();
        }
    }

    private void openGestureEntry() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        if (getActivity() instanceof ShellNavigator navigator) {
            if (gestureLockManager != null && gestureLockManager.isGestureEnabled()) {
                navigator.openGestureLockVerify();
            } else {
                navigator.openGestureLockSetup();
            }
        }
    }

    private void navigateBack() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        getActivity().getSupportFragmentManager().popBackStack();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
