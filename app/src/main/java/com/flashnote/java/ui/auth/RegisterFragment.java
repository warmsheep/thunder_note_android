package com.flashnote.java.ui.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.TokenManager;
import com.flashnote.java.data.model.LoginResponse;
import com.flashnote.java.data.remote.ServerConfigStore;
import com.flashnote.java.databinding.FragmentRegisterBinding;
import com.flashnote.java.ui.FragmentUiSafe;
import com.flashnote.java.ui.navigation.ShellNavigator;

public class RegisterFragment extends Fragment {
    private FragmentRegisterBinding binding;
    private AuthViewModel authViewModel;
    private TokenManager tokenManager;
    private ServerConfigStore serverConfigStore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FlashNoteApp app = FlashNoteApp.getInstance();
        tokenManager = app.getTokenManager();
        serverConfigStore = app.getServerConfigStore();
        if (getActivity() != null) {
            authViewModel = new ViewModelProvider(getActivity()).get(AuthViewModel.class);
            authViewModel.resetLoginState();
        }

        binding.registerButton.setOnClickListener(v -> submitRegister());
        binding.goLoginText.setOnClickListener(v -> {
            ShellNavigator navigator = FragmentUiSafe.getNavigatorOrNull(this);
            if (navigator != null) {
                navigator.openLogin();
            }
        });

        if (authViewModel != null) {
            authViewModel.getAuthState().observe(getViewLifecycleOwner(), state -> {
                if (state == null || binding == null) {
                    return;
                }
                switch (state) {
                    case LOADING:
                        setLoading(true);
                        break;
                    case SUCCESS:
                        setLoading(false);
                        break;
                    case ERROR:
                    case IDLE:
                    default:
                        setLoading(false);
                        break;
                }
            });

            authViewModel.getErrorMessage().observe(getViewLifecycleOwner(), this::handleErrorFromViewModel);
            authViewModel.getLoginResponse().observe(getViewLifecycleOwner(), this::handleLoginResponse);
        }
    }

    private void submitRegister() {
        clearError();
        String username = binding.usernameInput.getText() == null
                ? "" : binding.usernameInput.getText().toString().trim();
        String email = binding.emailInput.getText() == null
                ? "" : binding.emailInput.getText().toString().trim();
        String password = binding.passwordInput.getText() == null
                ? "" : binding.passwordInput.getText().toString();

        if (TextUtils.isEmpty(username)) {
            showError(getString(R.string.error_username_required));
            return;
        }
        if (TextUtils.isEmpty(email)) {
            showError(getString(R.string.error_email_required));
            return;
        }
        if (TextUtils.isEmpty(password)) {
            showError(getString(R.string.error_password_required));
            return;
        }

        authViewModel.clearError();
        authViewModel.register(username, email, password);
    }

    private void handleErrorFromViewModel(@Nullable String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        if (message.startsWith("Network error:")) {
            showError(getString(R.string.error_network_prefix, message.replace("Network error:", "").trim()));
            return;
        }
        showError(message);
    }

    private void handleLoginResponse(@Nullable LoginResponse response) {
        if (response == null || tokenManager == null || !isAdded()) {
            return;
        }
        String accessToken = response.getToken();
        if (TextUtils.isEmpty(accessToken)) {
            return;
        }
        tokenManager.saveTokens(
                accessToken,
                response.getRefreshToken(),
                response.getExpiresIn()
        );
        FlashNoteApp.getInstance().reloadSessionScopedDependencies();
        authViewModel.consumeLoginResponse();

        ShellNavigator navigator = FragmentUiSafe.getNavigatorOrNull(this);
        if (navigator != null) {
            navigator.openMainShell();
        }
    }

    private void setLoading(boolean loading) {
        binding.registerButton.setEnabled(!loading);
        binding.registerButton.setText(loading
                ? getString(R.string.action_register_loading)
                : getString(R.string.action_register));
        binding.goLoginText.setEnabled(!loading);
    }

    private void showError(@NonNull String msg) {
        binding.errorText.setVisibility(View.VISIBLE);
        binding.errorText.setText(msg);
    }

    private void clearError() {
        binding.errorText.setVisibility(View.GONE);
        binding.errorText.setText("");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
