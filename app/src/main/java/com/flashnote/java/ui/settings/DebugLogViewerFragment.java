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

        String persistedLog = DebugLog.readPersistedLog();
        String persistedHeader = (persistedLog == null || persistedLog.isEmpty())
                ? ""
                : "=== 历史日志 (上次运行) ===\n" + persistedLog + "\n=== 当前会话日志 ===\n";

        String currentSessionLog = DebugLog.getCurrentSessionLog();
        String initialCombinedLog = persistedHeader + (currentSessionLog == null ? "" : currentSessionLog);
        binding.debugLogText.setText(initialCombinedLog.isEmpty() ? "暂无日志" : initialCombinedLog);

        DebugLog.getLiveData().observe(getViewLifecycleOwner(), log -> {
            if (binding != null) {
                String liveSessionLog = (log == null || log.isEmpty()) ? "" : log;
                String snapshotSessionLog = DebugLog.getCurrentSessionLog();
                String effectiveSessionLog = (snapshotSessionLog == null || snapshotSessionLog.isEmpty())
                        ? liveSessionLog
                        : snapshotSessionLog;
                String combinedLog = persistedHeader + effectiveSessionLog;
                binding.debugLogText.setText(combinedLog.isEmpty() ? "暂无日志" : combinedLog);
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
