package com.flashnote.java.ui.media;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.flashnote.java.TokenManager;
import com.flashnote.java.databinding.ActivityImageViewerBinding;

public class ImageViewerActivity extends AppCompatActivity {
    public static final String EXTRA_MEDIA_URL = "extra_media_url";

    private ActivityImageViewerBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityImageViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.closeBtn.setOnClickListener(v -> finish());

        String mediaUrl = getIntent().getStringExtra(EXTRA_MEDIA_URL);
        String requestUrl = MediaUrlResolver.resolve(mediaUrl);
        String token = new TokenManager(this).getAccessToken();

        GlideUrl glideUrl = new GlideUrl(requestUrl, new LazyHeaders.Builder()
                .addHeader("Authorization", token == null ? "" : "Bearer " + token)
                .build());

        Glide.with(this)
                .load(glideUrl)
                .into(binding.photoView);
    }
}
