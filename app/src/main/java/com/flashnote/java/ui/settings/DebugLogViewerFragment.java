package com.flashnote.java.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.flashnote.java.DebugLog;
import com.flashnote.java.databinding.FragmentDebugLogViewerBinding;

public class DebugLogViewerFragment extends Fragment {
    private FragmentDebugLogViewerBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDebugLogViewerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.backButton.setOnClickListener(v -> navigateBack());
        binding.clearLogButton.setOnClickListener(v -> DebugLog.clear());

        DebugLog.getLiveData().observe(getViewLifecycleOwner(), log -> {
            if (binding != null) {
                binding.debugLogText.setText(log == null || log.isEmpty() ? "暂无日志" : log);
            }
        });
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
