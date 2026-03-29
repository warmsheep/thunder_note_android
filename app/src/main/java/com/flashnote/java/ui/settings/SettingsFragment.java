package com.flashnote.java.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.flashnote.java.R;
import com.flashnote.java.databinding.FragmentSettingsBinding;
import com.flashnote.java.ui.FragmentUiSafe;

public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        binding.backButton.setOnClickListener(v -> FragmentUiSafe.navigateBack(this));
        
        binding.aboutItem.setOnClickListener(v -> openAbout());
        binding.websiteItem.setOnClickListener(v -> openWebsite());
        binding.debugLogItem.setOnClickListener(v -> openDebugLogViewer());
    }

    private void openAbout() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        getActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.rootFragmentContainer, new AboutFragment())
                .addToBackStack(null)
                .commit();
    }

    private void openWebsite() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/warmsheep/thunder_note_android"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法打开浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    private void openDebugLogViewer() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        getActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.rootFragmentContainer, new DebugLogViewerFragment())
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
