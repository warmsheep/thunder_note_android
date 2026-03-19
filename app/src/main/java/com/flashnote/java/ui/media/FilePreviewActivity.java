package com.flashnote.java.ui.media;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.flashnote.java.R;
import com.flashnote.java.databinding.ActivityFilePreviewBinding;
import com.flashnote.java.util.MarkdownRenderer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class FilePreviewActivity extends AppCompatActivity {
    public static final String EXTRA_FILE_PATH = "extra_file_path";
    public static final String EXTRA_FILE_NAME = "extra_file_name";

    private ActivityFilePreviewBinding binding;
    private File file;
    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor pdfFd;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFilePreviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.backBtn.setOnClickListener(v -> finish());
        binding.openExternalBtn.setOnClickListener(v -> openExternal());

        String filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        String fileName = getIntent().getStringExtra(EXTRA_FILE_NAME);
        if (TextUtils.isEmpty(filePath)) {
            showUnsupported(getString(R.string.file_preview_unsupported));
            return;
        }

        file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            showUnsupported(getString(R.string.file_preview_unsupported));
            return;
        }

        if (TextUtils.isEmpty(fileName)) {
            fileName = file.getName();
        }
        binding.fileNameText.setText(fileName);

        String ext = getFileExtension(fileName);
        if (isImage(ext)) {
            Intent intent = new Intent(this, ImageViewerActivity.class);
            intent.putExtra(ImageViewerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            startActivity(intent);
            finish();
            return;
        }

        if (isText(ext)) {
            showTextPreview(file, "md".equals(ext));
            return;
        }

        if ("pdf".equals(ext)) {
            showPdfPreview(file);
            return;
        }

        showUnsupported(getString(R.string.file_preview_unsupported));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closePdfRenderer();
    }

    private void showTextPreview(File previewFile, boolean markdown) {
        try (FileInputStream inputStream = new FileInputStream(previewFile)) {
            long fileLength = previewFile.length();
            int maxBytes = 1024 * 1024;
            int expected = (int) Math.min(fileLength, maxBytes);
            byte[] bytes = new byte[expected];
            int read = inputStream.read(bytes);
            if (read <= 0) {
                binding.textPreview.setText(getString(R.string.file_preview_empty_text));
            } else {
                String content = new String(bytes, 0, read, StandardCharsets.UTF_8);
                if (markdown) {
                    MarkdownRenderer.render(binding.textPreview, content);
                } else {
                    binding.textPreview.setText(content);
                }
            }
            binding.textPreviewContainer.setVisibility(android.view.View.VISIBLE);
            binding.pdfPreviewImage.setVisibility(android.view.View.GONE);
            binding.unsupportedText.setVisibility(android.view.View.GONE);
        } catch (IOException exception) {
            showUnsupported(getString(R.string.file_preview_read_failed));
        }
    }

    private void showPdfPreview(File previewFile) {
        closePdfRenderer();
        try {
            pdfFd = ParcelFileDescriptor.open(previewFile, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(pdfFd);
            if (pdfRenderer.getPageCount() <= 0) {
                showUnsupported(getString(R.string.file_preview_pdf_failed));
                return;
            }

            PdfRenderer.Page page = pdfRenderer.openPage(0);
            int width = Math.max(page.getWidth(), 1);
            int height = Math.max(page.getHeight(), 1);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();

            binding.pdfPreviewImage.setImageBitmap(bitmap);
            binding.textPreviewContainer.setVisibility(android.view.View.GONE);
            binding.pdfPreviewImage.setVisibility(android.view.View.VISIBLE);
            binding.unsupportedText.setVisibility(android.view.View.GONE);
        } catch (IOException exception) {
            showUnsupported(getString(R.string.file_preview_pdf_failed));
        }
    }

    private void showUnsupported(String message) {
        binding.unsupportedText.setText(message);
        binding.textPreviewContainer.setVisibility(android.view.View.GONE);
        binding.pdfPreviewImage.setVisibility(android.view.View.GONE);
        binding.unsupportedText.setVisibility(android.view.View.VISIBLE);
    }

    private void openExternal() {
        if (file == null || !file.exists()) {
            Toast.makeText(this, getString(R.string.file_preview_unsupported), Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, resolveMimeType(file.getName()));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "未找到可打开该文件的应用", Toast.LENGTH_SHORT).show();
        }
    }

    private void closePdfRenderer() {
        if (pdfRenderer != null) {
            pdfRenderer.close();
            pdfRenderer = null;
        }
        if (pdfFd != null) {
            try {
                pdfFd.close();
            } catch (IOException ignored) {
            }
            pdfFd = null;
        }
    }

    private boolean isImage(String extension) {
        return "jpg".equals(extension) || "jpeg".equals(extension) || "png".equals(extension) || "gif".equals(extension);
    }

    private boolean isText(String extension) {
        return "txt".equals(extension)
                || "json".equals(extension)
                || "xml".equals(extension)
                || "csv".equals(extension)
                || "log".equals(extension)
                || "md".equals(extension)
                || "html".equals(extension)
                || "java".equals(extension)
                || "py".equals(extension)
                || "js".equals(extension);
    }

    private String getFileExtension(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveMimeType(String fileName) {
        String ext = getFileExtension(fileName);
        switch (ext) {
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "mp4":
                return "video/mp4";
            case "m4a":
                return "audio/mp4";
            default:
                return "*/*";
        }
    }
}
