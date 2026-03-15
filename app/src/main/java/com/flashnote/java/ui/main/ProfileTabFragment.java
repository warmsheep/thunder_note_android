package com.flashnote.java.ui.main;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.data.model.FavoriteItem;
import com.flashnote.java.databinding.FragmentProfileTabBinding;
import com.flashnote.java.data.model.Collection;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.data.repository.SyncRepository;
import com.flashnote.java.ui.navigation.ShellNavigator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileTabFragment extends Fragment {
    private FragmentProfileTabBinding binding;
    private SyncRepository syncRepository;
    private FileRepository fileRepository;
    private ActivityResultLauncher<String> filePickerLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileTabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FlashNoteApp app = FlashNoteApp.getInstance();
        syncRepository = app.getSyncRepository();
        fileRepository = app.getFileRepository();

        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleFilePicked);

        binding.bootstrapButton.setOnClickListener(v -> syncRepository.bootstrap(syncCallback("bootstrap")));
        binding.pullButton.setOnClickListener(v -> syncRepository.pull(syncCallback("pull")));
        binding.pushButton.setOnClickListener(v -> pushCurrentData());
        binding.uploadButton.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
        binding.logoutButton.setOnClickListener(v -> logout());
    }

    private SyncRepository.SyncCallback syncCallback(String action) {
        return new SyncRepository.SyncCallback() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                if (!isAdded()) {
                    return;
                }
                FlashNoteApp app = FlashNoteApp.getInstance();
                app.getFlashNoteRepository().refresh();
                app.getCollectionRepository().refresh();
                app.getFavoriteRepository().refresh();
                requireActivity().runOnUiThread(() -> binding.statusText.setText(action + " 成功：" + data));
            }

            @Override
            public void onError(String message, int code) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> binding.statusText.setText(action + " 失败：" + message));
            }
        };
    }

    private void pushCurrentData() {
        FlashNoteApp app = FlashNoteApp.getInstance();
        List<FlashNote> notes = app.getFlashNoteRepository().getNotes().getValue();
        List<Collection> collections = app.getCollectionRepository().getCollections().getValue();
        List<FavoriteItem> favorites = app.getFavoriteRepository().getFavorites().getValue();

        Map<String, Object> payload = new HashMap<>();
        payload.put("notes", notes == null ? List.of() : notes);
        payload.put("collections", collections == null ? List.of() : collections);
        payload.put("messages", List.of());
        payload.put("favorites", favorites == null ? List.of() : favorites);
        syncRepository.push(payload, syncCallback("push"));
    }

    private void handleFilePicked(@Nullable Uri uri) {
        if (uri == null || !isAdded()) {
            return;
        }

        try {
            File tempFile = new File(requireContext().getCacheDir(), "picked-upload.bin");
            try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                if (inputStream == null) {
                    throw new IllegalStateException("Cannot open selected file");
                }
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            fileRepository.upload(tempFile, new FileRepository.FileCallback() {
                @Override
                public void onSuccess(String objectName) {
                    fileRepository.download(objectName, new FileRepository.FileCallback() {
                        @Override
                        public void onSuccess(String value) {
                            if (!isAdded()) {
                                return;
                            }
                            requireActivity().runOnUiThread(() -> binding.statusText.setText("文件上传并回读成功：" + value));
                        }

                        @Override
                        public void onError(String message, int code) {
                            if (!isAdded()) {
                                return;
                            }
                            requireActivity().runOnUiThread(() -> binding.statusText.setText("文件下载失败：" + message));
                        }
                    });
                }

                @Override
                public void onError(String message, int code) {
                    if (!isAdded()) {
                        return;
                    }
                    requireActivity().runOnUiThread(() -> binding.statusText.setText("文件上传失败：" + message));
                }
            });
        } catch (Exception exception) {
            Toast.makeText(requireContext(), exception.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void logout() {
        FlashNoteApp.getInstance().getAuthRepository().logout();
        if (requireActivity() instanceof ShellNavigator navigator) {
            navigator.logoutToLogin();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
