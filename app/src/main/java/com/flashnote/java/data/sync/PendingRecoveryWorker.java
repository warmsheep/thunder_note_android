package com.flashnote.java.data.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.flashnote.java.FlashNoteApp;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PendingRecoveryWorker extends Worker {
    public static final String UNIQUE_WORK_NAME = "pending-recovery-sync";

    interface RecoveryDependencies {
        boolean isTokenValid();
        void retryAllPendingMessages();
        void pullAndRefreshLocal(com.flashnote.java.data.repository.SyncRepository.SyncCallback callback);
    }

    private RecoveryDependencies testDependencies;

    public PendingRecoveryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    void setTestDependencies(@NonNull RecoveryDependencies dependencies) {
        this.testDependencies = dependencies;
    }

    @NonNull
    @Override
    public Result doWork() {
        RecoveryDependencies dependencies = resolveDependencies();
        if (dependencies == null || !dependencies.isTokenValid()) {
            return Result.success();
        }
        dependencies.retryAllPendingMessages();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        dependencies.pullAndRefreshLocal(new com.flashnote.java.data.repository.SyncRepository.SyncCallback() {
            @Override
            public void onSuccess(java.util.Map<String, Object> data) {
                success.set(true);
                latch.countDown();
            }

            @Override
            public void onError(String message, int code) {
                success.set(false);
                latch.countDown();
            }
        });
        try {
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            if (!completed) {
                return Result.retry();
            }
            return success.get() ? Result.success() : Result.retry();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }
    }

    private RecoveryDependencies resolveDependencies() {
        if (testDependencies != null) {
            return testDependencies;
        }
        FlashNoteApp app = FlashNoteApp.getInstance();
        if (app == null || app.getTokenManager() == null || app.getMessageRepository() == null || app.getSyncRepository() == null) {
            return null;
        }
        return new RecoveryDependencies() {
            @Override
            public boolean isTokenValid() {
                return app.getTokenManager().isTokenValid();
            }

            @Override
            public void retryAllPendingMessages() {
                app.getMessageRepository().retryAllPendingMessages();
            }

            @Override
            public void pullAndRefreshLocal(com.flashnote.java.data.repository.SyncRepository.SyncCallback callback) {
                app.getSyncRepository().pullAndRefreshLocal(callback);
            }
        };
    }

    public static void enqueue(@NonNull Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PendingRecoveryWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }
}
