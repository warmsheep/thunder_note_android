package com.flashnote.java.ui.media;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.flashnote.java.R;
import com.flashnote.java.databinding.ActivityImageViewerBinding;

import java.io.File;

public class ImageViewerActivity extends AppCompatActivity {
    public static final String EXTRA_MEDIA_URL = "extra_media_url";
    public static final String EXTRA_FILE_PATH = "extra_file_path";

    private ActivityImageViewerBinding binding;
    private File previewFile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityImageViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.closeBtn.setOnClickListener(v -> finish());
        binding.moreBtn.setOnClickListener(v -> onMoreClicked());

        String filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        if (!TextUtils.isEmpty(filePath)) {
            previewFile = new File(filePath);
            Glide.with(this)
                    .load(previewFile)
                    .into(binding.photoView);
            return;
        }

        String mediaUrl = getIntent().getStringExtra(EXTRA_MEDIA_URL);
        File cachedFile = MediaUrlResolver.resolveCachedFile(this, mediaUrl);
        if (cachedFile != null) {
            previewFile = cachedFile;
            Glide.with(this)
                    .load(cachedFile)
                    .into(binding.photoView);
            return;
        }
        String requestUrl = MediaUrlResolver.resolve(mediaUrl);

        Glide.with(this)
                .load(requestUrl)
                .into(binding.photoView);
    }

    private void onMoreClicked() {
        if (previewFile == null || !previewFile.exists()) {
            Toast.makeText(this, R.string.media_save_loading, Toast.LENGTH_SHORT).show();
            return;
        }
        MediaSaveHelper.showSaveMenu(this, binding.moreBtn, previewFile, previewFile.getName());
    }
}
