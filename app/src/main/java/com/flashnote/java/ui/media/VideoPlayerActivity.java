package com.flashnote.java.ui.media;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import com.flashnote.java.TokenManager;
import com.flashnote.java.databinding.ActivityVideoPlayerBinding;

import java.util.HashMap;
import java.util.Map;

public class VideoPlayerActivity extends AppCompatActivity {
    public static final String EXTRA_MEDIA_URL = "extra_media_url";

    private ActivityVideoPlayerBinding binding;
    private ExoPlayer player;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVideoPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.closeBtn.setOnClickListener(v -> finish());
        initializePlayer();
    }

    private void initializePlayer() {
        String mediaUrl = getIntent().getStringExtra(EXTRA_MEDIA_URL);
        String requestUrl = MediaUrlResolver.resolve(mediaUrl);
        String token = new TokenManager(this).getAccessToken();

        Map<String, String> headers = new HashMap<>();
        if (token != null && !token.trim().isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        }

        DataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(headers);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                .build();

        binding.playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(requestUrl));
        player.prepare();
        player.play();
    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayer();
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
