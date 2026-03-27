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
        DebugLog.i("DebugLogViewer", "open debug log page");
        binding.backButton.setOnClickListener(v -> navigateBack());
        binding.clearLogButton.setOnClickListener(v -> DebugLog.clear());

        renderLogs(DebugLog.getCurrentSessionLog());

        DebugLog.getLiveData().observe(getViewLifecycleOwner(), log -> {
            if (binding != null) {
                renderLogs(log);
            }
        });
    }

    private void renderLogs(@Nullable String liveLog) {
        String previousSessionLog = DebugLog.readPreviousSessionLog();
        String currentSessionLog = DebugLog.getCurrentSessionLog();
        String effectiveCurrentLog = (currentSessionLog == null || currentSessionLog.isEmpty())
                ? (liveLog == null ? "" : liveLog)
                : currentSessionLog;

        StringBuilder combined = new StringBuilder();
        if (previousSessionLog != null && !previousSessionLog.isEmpty()) {
            combined.append("=== 历史日志 (上次运行) ===\n");
            combined.append(previousSessionLog.trim()).append("\n\n");
        }
        if (effectiveCurrentLog != null && !effectiveCurrentLog.isEmpty()) {
            combined.append("=== 当前会话日志 ===\n");
            combined.append(effectiveCurrentLog.trim());
        }
        binding.debugLogText.setText(combined.length() == 0 ? "暂无日志" : combined.toString());
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
