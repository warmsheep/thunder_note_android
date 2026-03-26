package com.flashnote.java.ui.chat;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.data.model.CardItem;
import com.flashnote.java.data.model.CardPayload;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.repository.MessageRepository;
import com.flashnote.java.databinding.FragmentCardEditorBinding;
import com.flashnote.java.databinding.ItemCardEditorFileBinding;
import com.flashnote.java.databinding.ItemCardEditorMediaBinding;
import com.flashnote.java.ui.navigation.ShellNavigator;
import com.flashnote.java.util.VideoCompressor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CardEditorFragment extends Fragment {
    private static final String ARG_FLASH_NOTE_ID = "flashNoteId";
    private static final String ARG_PEER_USER_ID = "peerUserId";
    private static final String ARG_TITLE = "title";
    private static final long COLLECTION_BOX_NOTE_ID = -1L;
    private static final int MAX_ATTACHMENTS = 9;

    private FragmentCardEditorBinding binding;
    private MessageRepository messageRepository;
    private Uri cameraPhotoUri;
    private boolean saving;
    private String currentAttachmentType;
    private final List<PendingAttachment> attachments = new ArrayList<>();
    private int pendingAttachmentJobs;

    public static CardEditorFragment newInstance(long flashNoteId, long peerUserId, @Nullable String title) {
        CardEditorFragment fragment = new CardEditorFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_FLASH_NOTE_ID, flashNoteId);
        args.putLong(ARG_PEER_USER_ID, peerUserId);
        args.putString(ARG_TITLE, title);
        fragment.setArguments(args);
        return fragment;
    }

    private final ActivityResultLauncher<Intent> mediaPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    handlePickedMedia(result.getData());
                }
            }
    );

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    handlePickedFiles(result.getData());
                }
            }
    );

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && cameraPhotoUri != null) {
                    addUriAsAttachment(cameraPhotoUri, "IMAGE");
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCardEditorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        messageRepository = FlashNoteApp.getInstance().getMessageRepository();
        String pageTitle = getArguments() == null ? "新建卡片" : getArguments().getString(ARG_TITLE, "新建卡片");
        binding.pageTitleText.setText(pageTitle);

        binding.cancelButton.setOnClickListener(v -> navigateBack());
        binding.saveButton.setOnClickListener(v -> saveCard());
        binding.addImageVideoButton.setOnClickListener(v -> openMediaPicker());
        binding.addFileButton.setOnClickListener(v -> openFilePicker());
        binding.addCameraButton.setOnClickListener(v -> openCamera());
        binding.formatBold.setOnClickListener(v -> insertToken("**", "**"));
        binding.formatItalic.setOnClickListener(v -> insertToken("*", "*"));
        binding.formatQuote.setOnClickListener(v -> insertLinePrefix("> "));
        binding.formatTodo.setOnClickListener(v -> insertLinePrefix("- [ ] "));
        updateAttachmentViews();
        syncActionState();
    }

    private void openMediaPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        mediaPickerLauncher.launch(intent);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        filePickerLauncher.launch(intent);
    }

    private void openCamera() {
        if (!isAdded()) {
            return;
        }
        try {
            File photoFile = new File(requireContext().getCacheDir(), "card_" + System.currentTimeMillis() + ".jpg");
            cameraPhotoUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    photoFile
            );
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cameraLauncher.launch(intent);
        } catch (ActivityNotFoundException exception) {
            showToast("无法打开相机");
        }
    }

    private void handlePickedMedia(@NonNull Intent data) {
        List<Uri> uris = extractUris(data);
        if (uris.isEmpty()) {
            return;
        }
        List<String> types = new ArrayList<>();
        for (Uri uri : uris) {
            String type = resolveMediaType(uri);
            if (type == null) {
                showToast("仅支持图片或视频");
                return;
            }
            if (!types.contains(type)) {
                types.add(type);
            }
        }
        if (types.size() > 1) {
            showToast("单个卡片只支持同一种图片或视频附件");
            return;
        }
        for (Uri uri : uris) {
            addUriAsAttachment(uri, types.get(0));
        }
    }

    private void handlePickedFiles(@NonNull Intent data) {
        List<Uri> uris = extractUris(data);
        if (uris.isEmpty()) {
            return;
        }
        for (Uri uri : uris) {
            addUriAsAttachment(uri, "FILE");
        }
    }

    private void addUriAsAttachment(@NonNull Uri uri, @NonNull String type) {
        if (!canAddAttachment(type)) {
            return;
        }
        String prefix = "FILE".equals(type) ? "file" : type.toLowerCase(Locale.ROOT);
        pendingAttachmentJobs++;
        syncActionState();
        copyUriToTempFile(uri, prefix, file -> {
            pendingAttachmentJobs = Math.max(0, pendingAttachmentJobs - 1);
            if (!isAdded() || binding == null) {
                syncActionState();
                return;
            }
            if (file == null) {
                showToast("文件处理失败");
                updateAttachmentViews();
                syncActionState();
                return;
            }
            PendingAttachment attachment = new PendingAttachment();
            attachment.type = type;
            attachment.file = file;
            attachment.previewUri = Uri.fromFile(file);
            attachment.displayName = resolveDisplayName(uri, file);
            attachment.fileSize = file.length();
            attachments.add(attachment);
            currentAttachmentType = type;
            updateAttachmentViews();
            syncActionState();
        });
    }

    private boolean canAddAttachment(@NonNull String type) {
        if (attachments.size() >= MAX_ATTACHMENTS) {
            showToast("最多添加 9 个附件");
            return false;
        }
        if (currentAttachmentType == null || currentAttachmentType.equals(type)) {
            return true;
        }
        showToast("同一卡片暂不支持混合不同类型附件");
        return false;
    }

    private void updateAttachmentViews() {
        if (binding == null) {
            return;
        }
        binding.mediaGrid.removeAllViews();
        binding.fileListContainer.removeAllViews();

        boolean empty = attachments.isEmpty();
        binding.emptyAttachmentText.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.mediaGrid.setVisibility(!empty && !"FILE".equals(currentAttachmentType) ? View.VISIBLE : View.GONE);
        binding.fileListContainer.setVisibility(!empty && "FILE".equals(currentAttachmentType) ? View.VISIBLE : View.GONE);
        if (pendingAttachmentJobs > 0) {
            binding.attachmentTipText.setText("正在处理 " + pendingAttachmentJobs + " 个附件，已添加 " + attachments.size() + " / 9 个附件");
        } else {
            binding.attachmentTipText.setText(empty ? "最多添加 9 个同类型附件" : "已添加 " + attachments.size() + " / 9 个附件");
        }

        for (int i = 0; i < attachments.size(); i++) {
            PendingAttachment attachment = attachments.get(i);
            int index = i;
            if ("FILE".equals(attachment.type)) {
                ItemCardEditorFileBinding itemBinding = ItemCardEditorFileBinding.inflate(getLayoutInflater(), binding.fileListContainer, false);
                itemBinding.fileNameText.setText(attachment.displayName);
                itemBinding.fileSizeText.setText(formatFileSize(attachment.fileSize));
                itemBinding.removeButton.setOnClickListener(v -> removeAttachment(index));
                binding.fileListContainer.addView(itemBinding.getRoot());
            } else {
                ItemCardEditorMediaBinding itemBinding = ItemCardEditorMediaBinding.inflate(getLayoutInflater(), binding.mediaGrid, false);
                Glide.with(this).load(attachment.previewUri).into(itemBinding.previewImage);
                itemBinding.playOverlay.setVisibility("VIDEO".equals(attachment.type) ? View.VISIBLE : View.GONE);
                itemBinding.removeButton.setOnClickListener(v -> removeAttachment(index));
                binding.mediaGrid.addView(itemBinding.getRoot());
            }
        }
    }

    private void removeAttachment(int index) {
        if (index < 0 || index >= attachments.size()) {
            return;
        }
        PendingAttachment removed = attachments.remove(index);
        if (removed.file != null && removed.file.exists()) {
            removed.file.delete();
        }
        if (attachments.isEmpty()) {
            currentAttachmentType = null;
        }
        updateAttachmentViews();
        syncActionState();
    }

    private void saveCard() {
        if (binding == null || messageRepository == null || saving) {
            return;
        }
        String title = safeText(binding.titleInput.getText());
        String content = safeText(binding.contentInput.getText());
        if (title.isEmpty()) {
            showToast("请输入卡片标题");
            return;
        }
        if (pendingAttachmentJobs > 0) {
            showToast("附件仍在处理中，请稍候");
            return;
        }
        if (attachments.isEmpty()) {
            showToast("请先添加至少一个附件");
            return;
        }

        saving = true;
        syncActionState();

        List<CardItem> items = new ArrayList<>();
        Long currentUserId = FlashNoteApp.getInstance().getTokenManager().getUserId();
        if (!content.isEmpty()) {
            CardItem textItem = new CardItem();
            textItem.setType("TEXT");
            textItem.setContent(content);
            textItem.setSenderId(currentUserId);
            textItem.setRole("user");
            items.add(textItem);
        }
        for (PendingAttachment attachment : attachments) {
            CardItem item = new CardItem();
            item.setType(attachment.type);
            item.setUrl(attachment.file == null ? null : attachment.file.getAbsolutePath());
            item.setLocalPath(attachment.file == null ? null : attachment.file.getAbsolutePath());
            item.setFileName(attachment.displayName);
            item.setFileSize(attachment.fileSize);
            item.setSenderId(currentUserId);
            item.setRole("user");
            items.add(item);
        }
        sendCompositeMessage(buildPayload(title, content, items));
        showToast("卡片已加入发送队列，可立即返回");
        navigateBack();
    }

    private CardPayload buildPayload(@NonNull String title, @NonNull String content, @NonNull List<CardItem> items) {
        CardPayload payload = new CardPayload();
        payload.setTitle(title);
        payload.setCardType(resolveCardType());
        payload.setSummary(buildSummary(content, items));
        payload.setItems(items);
        return payload;
    }

    private void sendCompositeMessage(@NonNull CardPayload payload) {
        Message message = new Message();
        message.setMediaType("COMPOSITE");
        message.setContent(payload.getTitle());
        message.setFileName(payload.getTitle());
        message.setPayload(payload);

        long flashNoteId = getArguments() == null ? 0L : getArguments().getLong(ARG_FLASH_NOTE_ID, 0L);
        long peerUserId = getArguments() == null ? 0L : getArguments().getLong(ARG_PEER_USER_ID, 0L);

        MessageRepository.SendCallback callback = new MessageRepository.SendCallback() {
            @Override
            public void onSuccess() {
                saving = false;
                runIfUiAlive(() -> {
                    if (binding != null) {
                        syncActionState();
                    }
                    if (flashNoteId == COLLECTION_BOX_NOTE_ID && peerUserId == 0L) {
                        Bundle result = new Bundle();
                        result.putString("inbox_preview", payload.getTitle());
                        getParentFragmentManager().setFragmentResult("quick_capture_saved", result);
                    }
                });
            }

            @Override
            public void onError(String messageText) {
                saving = false;
                runIfUiAlive(() -> {
                    if (binding != null) {
                        syncActionState();
                    }
                    showToast(TextUtils.isEmpty(messageText) ? "卡片发送失败" : messageText);
                });
            }
        };

        messageRepository.enqueueCompositeMessage(flashNoteId, peerUserId, message, callback);
    }

    private String resolveCardType() {
        if (currentAttachmentType == null) {
            return "COMPOSITE_CARD";
        }
        return currentAttachmentType + "_COLLECTION";
    }

    private String buildSummary(@NonNull String content, @NonNull List<CardItem> items) {
        if (!content.isEmpty()) {
            return content;
        }
        int mediaCount = 0;
        for (CardItem item : items) {
            if (item != null && item.getType() != null && !"TEXT".equalsIgnoreCase(item.getType())) {
                mediaCount++;
            }
        }
        if (mediaCount <= 0) {
            return "卡片消息";
        }
        String prefix;
        if ("IMAGE".equals(currentAttachmentType)) {
            prefix = "[图片]";
        } else if ("VIDEO".equals(currentAttachmentType)) {
            prefix = "[视频]";
        } else {
            prefix = "[文件]";
        }
        return mediaCount == 1 ? prefix : prefix + " 共" + mediaCount + "项";
    }

    private List<Uri> extractUris(@NonNull Intent data) {
        List<Uri> uris = new ArrayList<>();
        if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri();
                if (uri != null && !uris.contains(uri)) {
                    uris.add(uri);
                }
            }
        }
        return uris;
    }

    @Nullable
    private String resolveMediaType(@NonNull Uri uri) {
        if (!isAdded()) {
            return null;
        }
        ContentResolver resolver = requireContext().getContentResolver();
        String mimeType = resolver.getType(uri);
        if (mimeType == null) {
            return null;
        }
        if (mimeType.startsWith("image/")) {
            return "IMAGE";
        }
        if (mimeType.startsWith("video/")) {
            return "VIDEO";
        }
        return null;
    }

    private String resolveDisplayName(@NonNull Uri uri, @NonNull File fallbackFile) {
        String name = getOriginalFileName(uri);
        return TextUtils.isEmpty(name) ? fallbackFile.getName() : name;
    }

    @Nullable
    private String getOriginalFileName(@NonNull Uri uri) {
        if (!isAdded()) {
            return null;
        }
        android.database.Cursor cursor = null;
        try {
            cursor = requireContext().getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    return cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private void copyUriToTempFile(@NonNull Uri uri, @NonNull String prefix, @NonNull TempFileCallback callback) {
        new Thread(() -> {
            File tempFile = null;
            try {
                if (!isAdded()) {
                    callback.onReady(null);
                    return;
                }
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date());
                String extension = getFileExtension(uri);
                tempFile = new File(requireContext().getCacheDir(), prefix + "_" + timeStamp + "_" + UUID.randomUUID() + "." + extension);
                try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                     FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                    if (inputStream == null) {
                        postTempFile(callback, null);
                        return;
                    }
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, len);
                    }
                    outputStream.flush();
                }
                postTempFile(callback, tempFile);
            } catch (Exception exception) {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
                postTempFile(callback, null);
            }
        }).start();
    }

    private void postTempFile(@NonNull TempFileCallback callback, @Nullable File file) {
        runIfUiAlive(() -> callback.onReady(file));
    }

    private String getFileExtension(@NonNull Uri uri) {
        if (!isAdded()) {
            return "tmp";
        }
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        if (!TextUtils.isEmpty(extension)) {
            return extension;
        }
        String mimeType = requireContext().getContentResolver().getType(uri);
        String mapped = mimeType == null ? null : android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        return TextUtils.isEmpty(mapped) ? "tmp" : mapped;
    }

    private String formatFileSize(long size) {
        if (size <= 0L) {
            return "0 B";
        }
        String[] units = new String[]{"B", "KB", "MB", "GB"};
        double value = size;
        int unitIndex = 0;
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        return String.format(Locale.getDefault(), unitIndex == 0 ? "%.0f %s" : "%.1f %s", value, units[unitIndex]);
    }

    private void insertToken(@NonNull String prefix, @NonNull String suffix) {
        if (binding == null || binding.contentInput.getText() == null) {
            return;
        }
        Editable editable = binding.contentInput.getText();
        int start = Math.max(0, binding.contentInput.getSelectionStart());
        int end = Math.max(0, binding.contentInput.getSelectionEnd());
        if (start > end) {
            int temp = start;
            start = end;
            end = temp;
        }
        String selected = editable.subSequence(start, end).toString();
        String replacement = prefix + selected + suffix;
        editable.replace(start, end, replacement);
        int cursor = start + replacement.length();
        binding.contentInput.setSelection(Math.min(cursor, editable.length()));
    }

    private void insertLinePrefix(@NonNull String prefix) {
        if (binding == null || binding.contentInput.getText() == null) {
            return;
        }
        Editable editable = binding.contentInput.getText();
        int start = Math.max(0, binding.contentInput.getSelectionStart());
        int lineStart = start;
        while (lineStart > 0 && editable.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        editable.insert(lineStart, prefix);
        binding.contentInput.setSelection(Math.min(start + prefix.length(), editable.length()));
    }

    private String safeText(@Nullable Editable editable) {
        return editable == null ? "" : editable.toString().trim();
    }

    private void navigateBack() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        getActivity().getSupportFragmentManager().popBackStack();
    }

    private void runIfUiAlive(@NonNull Runnable action) {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        getActivity().runOnUiThread(action);
    }

    private void showToast(@NonNull String text) {
        if (!isAdded() || getContext() == null || text.isEmpty()) {
            return;
        }
        Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
    }

    private void syncActionState() {
        if (binding == null) {
            return;
        }
        boolean busy = saving || pendingAttachmentJobs > 0;
        binding.saveButton.setEnabled(!busy);
        if (saving) {
            binding.saveButton.setText("保存中...");
        } else if (pendingAttachmentJobs > 0) {
            binding.saveButton.setText("处理中...");
        } else {
            binding.saveButton.setText("保存");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private interface TempFileCallback {
        void onReady(@Nullable File file);
    }

    private static final class PendingAttachment {
        private String type;
        private File file;
        private Uri previewUri;
        private String displayName;
        private long fileSize;
    }
}
