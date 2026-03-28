package com.flashnote.java.ui.settings;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.data.remote.ServerConfigStore;
import com.flashnote.java.databinding.FragmentServerSettingsBinding;
import com.flashnote.java.ui.FragmentUiSafe;
import com.flashnote.java.ui.navigation.ShellNavigator;

public class ServerSettingsFragment extends Fragment {
    private FragmentServerSettingsBinding binding;
    private ServerConfigStore serverConfigStore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentServerSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        serverConfigStore = FlashNoteApp.getInstance().getServerConfigStore();

        binding.backButton.setOnClickListener(v -> FragmentUiSafe.navigateBack(this));
        binding.saveButton.setOnClickListener(v -> saveServerConfig());
        binding.officialOption.setOnClickListener(v -> selectOfficialMode());
        binding.selfHostedOption.setOnClickListener(v -> selectSelfHostedMode());
        binding.serverAddressInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearError();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        bindCurrentConfig();
    }

    private void bindCurrentConfig() {
        if (serverConfigStore.isOfficialMode()) {
            binding.serverModeGroup.check(R.id.officialOption);
            binding.serverAddressInput.setText("");
            binding.serverAddressInput.setEnabled(false);
            binding.serverAddressHint.setText(getString(R.string.server_settings_official_hint));
        } else {
            binding.serverModeGroup.check(R.id.selfHostedOption);
            binding.serverAddressInput.setEnabled(true);
            binding.serverAddressInput.setText(serverConfigStore.getBaseUrl());
            binding.serverAddressHint.setText(getString(R.string.server_settings_custom_hint));
        }
    }

    private void selectOfficialMode() {
        binding.serverAddressInput.setEnabled(false);
        binding.serverAddressHint.setText(getString(R.string.server_settings_official_hint));
        clearError();
    }

    private void selectSelfHostedMode() {
        binding.serverAddressInput.setEnabled(true);
        binding.serverAddressHint.setText(getString(R.string.server_settings_custom_hint));
        clearError();
    }

    private void saveServerConfig() {
        boolean useOfficial = binding.officialOption.isChecked();
        try {
            if (useOfficial) {
                serverConfigStore.useOfficialServer();
            } else {
                serverConfigStore.useSelfHostedServer(binding.serverAddressInput.getText() == null
                        ? ""
                        : binding.serverAddressInput.getText().toString());
            }
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
            return;
        }

        FlashNoteApp.getInstance().switchServerEnvironment();
        if (isAdded() && getContext() != null) {
            Toast.makeText(getContext(), getString(R.string.server_settings_saved_restart_login), Toast.LENGTH_SHORT).show();
        }
        if (isAdded() && getActivity() instanceof ShellNavigator navigator) {
            navigator.logoutToLogin();
            return;
        }
        FragmentUiSafe.navigateBack(this);
    }

    private void showError(@Nullable String message) {
        if (binding == null) {
            return;
        }
        binding.errorText.setVisibility(View.VISIBLE);
        binding.errorText.setText(message == null ? getString(R.string.server_settings_invalid_address) : message);
    }

    private void clearError() {
        if (binding == null) {
            return;
        }
        binding.errorText.setVisibility(View.GONE);
        binding.errorText.setText("");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
