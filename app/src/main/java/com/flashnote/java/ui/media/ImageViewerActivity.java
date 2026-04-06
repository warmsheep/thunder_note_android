package com.flashnote.java.ui.media;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.databinding.ActivityImageViewerBinding;

import java.io.File;

public class ImageViewerActivity extends AppCompatActivity {
    public static final String EXTRA_MEDIA_URL = "extra_media_url";
    public static final String EXTRA_FILE_PATH = "extra_file_path";
    public static final String EXTRA_DISPLAY_NAME = "extra_display_name";

    private ActivityImageViewerBinding binding;
    private File previewFile;
    private String previewDisplayName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityImageViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.closeBtn.setOnClickListener(v -> finish());
        binding.moreBtn.setOnClickListener(v -> onMoreClicked());

        String extraDisplayName = getIntent().getStringExtra(EXTRA_DISPLAY_NAME);
        String filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        if (!TextUtils.isEmpty(filePath)) {
            previewFile = new File(filePath);
            previewDisplayName = TextUtils.isEmpty(extraDisplayName)
                    ? previewFile.getName()
                    : extraDisplayName;
            Glide.with(this)
                    .load(previewFile)
                    .into(binding.photoView);
            return;
        }

        String mediaUrl = getIntent().getStringExtra(EXTRA_MEDIA_URL);
        File cachedFile = MediaUrlResolver.resolveCachedFile(this, mediaUrl);
        if (cachedFile != null) {
            previewFile = cachedFile;
            previewDisplayName = TextUtils.isEmpty(extraDisplayName)
                    ? cachedFile.getName()
                    : extraDisplayName;
            Glide.with(this)
                    .load(cachedFile)
                    .into(binding.photoView);
            return;
        }
        String requestUrl = MediaUrlResolver.resolve(mediaUrl);

        Glide.with(this)
                .load(requestUrl)
                .into(binding.photoView);

        previewDisplayName = TextUtils.isEmpty(extraDisplayName)
                ? resolveDisplayName(mediaUrl)
                : extraDisplayName;
        String objectName = MediaUrlResolver.extractObjectName(mediaUrl);
        if (!TextUtils.isEmpty(objectName)) {
            FileRepository fileRepository = FlashNoteApp.getInstance().getFileRepository();
            fileRepository.download(objectName, new FileRepository.FileCallback() {
                @Override
                public void onSuccess(String path) {
                    previewFile = new File(path);
                }

                @Override
                public void onError(String message, int code) {
                }
            });
        }
    }

    private void onMoreClicked() {
        if (previewFile == null || !previewFile.exists()) {
            Toast.makeText(this, R.string.media_save_loading, Toast.LENGTH_SHORT).show();
            return;
        }
        MediaSaveHelper.showSaveMenu(this, binding.moreBtn, previewFile, previewDisplayName);
    }

    private String resolveDisplayName(String mediaUrl) {
        String objectName = MediaUrlResolver.extractObjectName(mediaUrl);
        if (!TextUtils.isEmpty(objectName)) {
            int split = objectName.lastIndexOf('/');
            if (split >= 0 && split < objectName.length() - 1) {
                return objectName.substring(split + 1);
            }
            return objectName;
        }
        return "image_" + System.currentTimeMillis() + ".jpg";
    }
}
