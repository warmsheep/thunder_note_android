package com.flashnote.java.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.databinding.FragmentChangeLoginPasswordBinding;
import com.flashnote.java.data.repository.AuthRepository;
import com.flashnote.java.ui.FragmentUiSafe;

public class ChangeLoginPasswordFragment extends Fragment {
    private static final int MIN_PASSWORD_LENGTH = 6;

    private FragmentChangeLoginPasswordBinding binding;
    private AuthRepository authRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentChangeLoginPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        authRepository = FlashNoteApp.getInstance().getAuthRepository();
        binding.backButton.setOnClickListener(v -> FragmentUiSafe.navigateBack(this));
        binding.saveButton.setOnClickListener(v -> savePassword());
    }

    private void savePassword() {
        if (!isAdded() || getActivity() == null) {
            return;
        }

        String currentPassword = binding.currentPasswordInput.getText().toString();
        String newPassword = binding.newPasswordInput.getText().toString();
        String confirmPassword = binding.confirmPasswordInput.getText().toString();

        if (currentPassword.isEmpty()) {
            showError("请输入当前密码");
            return;
        }

        if (newPassword.isEmpty()) {
            showError("请输入新密码");
            return;
        }

        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            showError("新密码长度至少为" + MIN_PASSWORD_LENGTH + "位");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            showError("两次输入的新密码不一致");
            return;
        }

        hideError();
        showLoading(true);

        authRepository.changePassword(currentPassword, newPassword, new AuthRepository.PasswordCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    showLoading(false);
                    android.content.Context context = getContext();
                    if (context != null) {
                        Toast.makeText(context, "密码修改成功", Toast.LENGTH_SHORT).show();
                    }
                    FragmentUiSafe.navigateBack(ChangeLoginPasswordFragment.this);
                });
            }

            @Override
            public void onError(String message, int code) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    showLoading(false);
                    showError(message);
                });
            }
        });
    }

    private void showError(String message) {
        if (!isAdded() || binding == null) {
            return;
        }
        binding.errorText.setText(message);
        binding.errorText.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        if (!isAdded() || binding == null) {
            return;
        }
        binding.errorText.setVisibility(View.GONE);
    }

    private void showLoading(boolean show) {
        if (!isAdded() || binding == null) {
            return;
        }
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.saveButton.setEnabled(!show);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
