package com.flashnote.java.ui.chat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.flashnote.java.DebugLog;
import com.flashnote.java.databinding.FragmentChatBinding;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ChatRecordingHelper {

    public interface UiCallback {
        void showToast(@NonNull String message);

        void runIfUiAlive(@NonNull Runnable action);

        void scrollToBottomAfterLayout();
    }

    private final UiCallback uiCallback;
    private final FragmentChatBinding binding;
    private final ChatViewModel chatViewModel;
    private final ActivityResultLauncher<String[]> permissionLauncher;

    private boolean isRecording = false;
    @Nullable
    private MediaRecorder mediaRecorder;
    @Nullable
    private String currentRecordingPath;

    @Nullable
    private Handler recordingTimerHandler;
    @Nullable
    private Runnable recordingTimerRunnable;
    private int recordingSeconds = 0;

    public ChatRecordingHelper(@NonNull UiCallback uiCallback,
                               @NonNull FragmentChatBinding binding,
                               @NonNull ChatViewModel chatViewModel,
                               @NonNull ActivityResultLauncher<String[]> permissionLauncher) {
        this.uiCallback = uiCallback;
        this.binding = binding;
        this.chatViewModel = chatViewModel;
        this.permissionLauncher = permissionLauncher;
    }

    public void setupMicButton() {
        binding.micButton.setOnLongClickListener(v -> {
            if (checkRecordPermission()) {
                startRecordingWithUI();
            } else {
                requestRecordPermission();
            }
            return true;
        });

        binding.micButton.setOnClickListener(v -> uiCallback.showToast("长按开始录音"));

        binding.recordCancelBtn.setOnClickListener(v -> cancelRecording());
        binding.recordSendBtn.setOnClickListener(v -> confirmSendRecording());
    }

    public void onPermissionResult(boolean granted) {
        if (!granted) {
            uiCallback.showToast("需要相关权限才能使用此功能");
        }
    }

    private boolean checkRecordPermission() {
        Context context = binding.getRoot().getContext();
        if (context == null) {
            return false;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRecordPermission() {
        permissionLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
    }

    private void startRecording() {
        try {
            Context context = binding.getRoot().getContext();
            if (context == null) {
                uiCallback.showToast("录音失败");
                return;
            }
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String storageDir = context.getCacheDir().getAbsolutePath();
            currentRecordingPath = storageDir + "/voice_" + timeStamp + ".m4a";

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(currentRecordingPath);
            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            binding.micButton.setColorFilter(0xFFFF0000);
            uiCallback.showToast("录音中...");
        } catch (IOException e) {
            DebugLog.e("ChatRecordingHelper", "Failed to start recording", e);
            uiCallback.showToast("录音失败");
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            } catch (RuntimeException e) {
                DebugLog.e("ChatRecordingHelper", "Failed to stop recording cleanly", e);
            }
        }
        isRecording = false;
        binding.micButton.clearColorFilter();
    }

    private void startRecordingWithUI() {
        startRecording();
        if (!isRecording) {
            return;
        }

        binding.recordingOverlay.setVisibility(View.VISIBLE);
        binding.toolsPanel.setVisibility(View.GONE);
        binding.messageInput.setVisibility(View.GONE);
        binding.addButton.setVisibility(View.GONE);
        binding.sendButton.setVisibility(View.GONE);
        binding.micButton.setVisibility(View.GONE);

        binding.recordWaveView.startAnimation();

        recordingSeconds = 0;
        binding.recordTimerText.setText("0:00");

        recordingTimerHandler = new Handler(Looper.getMainLooper());
        recordingTimerRunnable = new Runnable() {
            @Override
            public void run() {
                recordingSeconds++;
                binding.recordTimerText.setText(formatRecordingTime(recordingSeconds));
                if (recordingTimerHandler != null) {
                    recordingTimerHandler.postDelayed(this, 1000);
                }
            }
        };
        recordingTimerHandler.postDelayed(recordingTimerRunnable, 1000);
    }

    public void cancelRecording() {
        stopRecordingUI();
        stopRecording();
        if (currentRecordingPath != null) {
            File file = new File(currentRecordingPath);
            if (file.exists()) {
                file.delete();
            }
            currentRecordingPath = null;
        }
    }

    private void confirmSendRecording() {
        stopRecordingUI();
        if (isRecording) {
            stopRecording();
        }
        if (currentRecordingPath != null) {
            sendVoiceMessage(currentRecordingPath);
            currentRecordingPath = null;
        }
    }

    private void stopRecordingUI() {
        binding.recordingOverlay.setVisibility(View.GONE);
        binding.toolsPanel.setVisibility(View.GONE);
        binding.messageInput.setVisibility(View.VISIBLE);
        binding.addButton.setVisibility(View.VISIBLE);
        binding.sendButton.setVisibility(View.VISIBLE);
        binding.micButton.setVisibility(View.VISIBLE);
        binding.recordWaveView.stopAnimation();

        if (recordingTimerHandler != null) {
            if (recordingTimerRunnable != null) {
                recordingTimerHandler.removeCallbacks(recordingTimerRunnable);
            }
            recordingTimerHandler = null;
            recordingTimerRunnable = null;
        }
    }

    private String formatRecordingTime(int seconds) {
        int mins = seconds / 60;
        int secs = seconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", mins, secs);
    }

    private void sendVoiceMessage(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            uiCallback.showToast("文件不存在");
            return;
        }
        Integer durationSeconds = null;
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(filePath);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration != null) {
                durationSeconds = Integer.parseInt(duration) / 1000;
            }
            retriever.release();
        } catch (Exception e) {
            DebugLog.e("ChatRecordingHelper", "Failed to resolve voice duration", e);
        }

        Integer finalDurationSeconds = durationSeconds;
        chatViewModel.enqueueMedia(
                "VOICE",
                file,
                file.getName(),
                file.length(),
                finalDurationSeconds,
                () -> uiCallback.runIfUiAlive(uiCallback::scrollToBottomAfterLayout)
        );
    }

    public void release() {
        if (isRecording) {
            stopRecording();
        }
        if (recordingTimerHandler != null) {
            if (recordingTimerRunnable != null) {
                recordingTimerHandler.removeCallbacks(recordingTimerRunnable);
            }
            recordingTimerHandler = null;
            recordingTimerRunnable = null;
        }
    }
}
