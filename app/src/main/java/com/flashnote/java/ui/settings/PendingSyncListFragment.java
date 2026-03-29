package com.flashnote.java.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.data.model.PendingMessage;
import com.flashnote.java.data.repository.PendingMessageRepository;
import com.flashnote.java.data.repository.SyncRepository;
import com.flashnote.java.databinding.FragmentPendingSyncListBinding;
import com.flashnote.java.ui.FragmentUiSafe;

import java.util.List;

public class PendingSyncListFragment extends Fragment {
    private FragmentPendingSyncListBinding binding;
    private final PendingSyncAdapter adapter = new PendingSyncAdapter();
    private PendingMessageRepository pendingMessageRepository;
    private SyncRepository syncRepository;
    private boolean syncInProgress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPendingSyncListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FlashNoteApp app = FlashNoteApp.getInstance();
        pendingMessageRepository = app == null ? null : app.getPendingMessageRepository();
        syncRepository = app == null ? null : app.getSyncRepository();

        binding.backButton.setOnClickListener(v -> FragmentUiSafe.navigateBack(this));
        binding.syncButton.setOnClickListener(v -> triggerSync());
        binding.pendingRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.pendingRecyclerView.setAdapter(adapter);

        if (pendingMessageRepository == null) {
            renderPendingMessages(null);
            return;
        }

        pendingMessageRepository.observePendingSyncMessages().observe(getViewLifecycleOwner(), this::renderPendingMessages);
    }

    private void renderPendingMessages(@Nullable List<PendingMessage> pendingMessages) {
        if (binding == null) {
            return;
        }
        int count = pendingMessages == null ? 0 : pendingMessages.size();
        binding.countText.setText(count == 0
                ? getString(R.string.pending_sync_empty)
                : getString(R.string.pending_sync_count, count));
        binding.emptyText.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        binding.pendingRecyclerView.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        adapter.submitList(pendingMessages);
    }

    private void triggerSync() {
        if (syncRepository == null || syncInProgress) {
            return;
        }
        syncInProgress = true;
        updateSyncProgress(true);
        syncRepository.syncAll(new SyncRepository.SyncCallback() {
            @Override
            public void onSuccess(java.util.Map<String, Object> data) {
                FragmentUiSafe.runIfUiAlive(PendingSyncListFragment.this, binding, () -> {
                    syncInProgress = false;
                    updateSyncProgress(false);
                });
            }

            @Override
            public void onError(String message, int code) {
                FragmentUiSafe.runIfUiAlive(PendingSyncListFragment.this, binding, () -> {
                    syncInProgress = false;
                    updateSyncProgress(false);
                });
            }
        });
    }

    private void updateSyncProgress(boolean syncing) {
        if (binding == null) {
            return;
        }
        binding.syncIcon.setVisibility(syncing ? View.INVISIBLE : View.VISIBLE);
        binding.syncProgress.setVisibility(syncing ? View.VISIBLE : View.GONE);
        binding.syncButton.setEnabled(!syncing);
        binding.syncButton.setAlpha(syncing ? 0.75f : 1f);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
