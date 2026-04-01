package com.flashnote.java.ui.media;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.databinding.ActivityVideoPlayerBinding;

import java.io.File;

import okhttp3.OkHttpClient;

public class VideoPlayerActivity extends AppCompatActivity {
    public static final String EXTRA_MEDIA_URL = "extra_media_url";

    private ActivityVideoPlayerBinding binding;
    private ExoPlayer player;
    private String mediaUrl;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVideoPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.closeBtn.setOnClickListener(v -> finish());
        initializePlayer();
    }

    private void initializePlayer() {
        mediaUrl = getIntent().getStringExtra(EXTRA_MEDIA_URL);
        if (TextUtils.isEmpty(mediaUrl)) {
            Toast.makeText(this, "视频地址无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        File cacheFile = MediaUrlResolver.resolveCachedFile(this, mediaUrl);
        if (cacheFile != null) {
            playLocalFile(cacheFile);
            return;
        }

        binding.playerView.setVisibility(View.GONE);
        Toast.makeText(this, "正在加载视频...", Toast.LENGTH_SHORT).show();
        FileRepository repository = FlashNoteApp.getInstance().getFileRepository();
        repository.download(mediaUrl, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(String path) {
                runOnUiThread(() -> {
                    File localFile = new File(path);
                    if (localFile.exists() && localFile.length() > 0) {
                        binding.playerView.setVisibility(View.VISIBLE);
                        playLocalFile(localFile);
                    } else {
                        playFromNetwork(mediaUrl);
                    }
                });
            }

            @Override
            public void onError(String message, int code) {
                runOnUiThread(() -> {
                    binding.playerView.setVisibility(View.VISIBLE);
                    playFromNetwork(mediaUrl);
                });
            }
        });
    }

    private void playLocalFile(File file) {
        releasePlayer();
        player = new ExoPlayer.Builder(this).build();
        binding.playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
        player.prepare();
        player.play();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void playFromNetwork(String mediaUrl) {
        releasePlayer();
        String requestUrl = MediaUrlResolver.resolve(mediaUrl);
        OkHttpClient okHttpClient = FlashNoteApp.getInstance().getApiClient().getOkHttpClient();
        DataSource.Factory dataSourceFactory = new OkHttpDataSource.Factory(okHttpClient);

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
