package com.flashnote.java.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.databinding.FragmentGestureLockVerifyBinding;
import com.flashnote.java.security.GestureLockManager;
import com.flashnote.java.ui.FragmentUiSafe;
import com.flashnote.java.ui.navigation.ShellNavigator;

public class GestureLockVerifyFragment extends Fragment {
    public interface VerifyInteractionListener {
        void onVerifyPatternSubmitted(@NonNull String pattern);
    }

    private FragmentGestureLockVerifyBinding binding;
    private GestureLockManager gestureLockManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGestureLockVerifyBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        gestureLockManager = FlashNoteApp.getInstance().getGestureLockManager();

        binding.backButton.setOnClickListener(v -> FragmentUiSafe.navigateBack(this));
        binding.resetButton.setOnClickListener(v -> resetPattern());
        binding.switchToSetupButton.setOnClickListener(v -> openGestureSetup());
        binding.patternView.setOnPatternListener(new GestureLockPatternView.OnPatternListener() {
            @Override
            public void onPatternStart() {
                if (binding == null) {
                    return;
                }
                binding.statusText.setVisibility(View.GONE);
                binding.patternView.setDisplayMode(GestureLockPatternView.DisplayMode.NORMAL);
            }

            @Override
            public void onPatternProgress(@NonNull String pattern) {
            }

            @Override
            public void onPatternComplete(@NonNull String pattern) {
                submitVerifyPattern(pattern);
            }

            @Override
            public void onPatternCleared() {
            }
        });
    }

    public void showVerifyError(@NonNull String message) {
        if (binding == null || getContext() == null) {
            return;
        }
        binding.statusText.setText(message);
        binding.statusText.setTextColor(ContextCompat.getColor(getContext(), R.color.danger));
        binding.statusText.setVisibility(View.VISIBLE);
        binding.patternView.setDisplayMode(GestureLockPatternView.DisplayMode.ERROR);
        binding.patternView.postDelayed(() -> {
            if (binding == null) {
                return;
            }
            binding.patternView.clearPattern();
        }, 550L);
    }

    public void showVerifySuccess() {
        if (binding == null || getContext() == null) {
            return;
        }
        binding.statusText.setText(R.string.gesture_lock_verify_success);
        binding.statusText.setTextColor(ContextCompat.getColor(getContext(), R.color.primary));
        binding.statusText.setVisibility(View.VISIBLE);
        binding.patternView.setDisplayMode(GestureLockPatternView.DisplayMode.CORRECT);
        binding.patternView.postDelayed(() -> {
            if (binding == null) {
                return;
            }
            binding.patternView.clearPattern();
            openGestureSetup();
        }, 550L);
    }

    private void submitVerifyPattern(@NonNull String pattern) {
        if (!isAdded() || getContext() == null) {
            return;
        }
        if (gestureLockManager != null && gestureLockManager.verifyGesture(pattern)) {
            showVerifySuccess();
            return;
        }
        showVerifyError(getString(R.string.gesture_unlock_status_failure));
    }

    private void resetPattern() {
        if (binding == null) {
            return;
        }
        binding.patternView.clearPattern();
        binding.patternView.setDisplayMode(GestureLockPatternView.DisplayMode.NORMAL);
        binding.statusText.setVisibility(View.GONE);
    }

    private void openGestureSetup() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        if (getActivity() instanceof ShellNavigator navigator) {
            navigator.openGestureLockSetup();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
