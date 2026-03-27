package com.flashnote.java;

import android.content.Context;
import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DebugLog {
    private static final int MAX_ENTRIES = 200;
    private static final MutableLiveData<String> logLiveData = new MutableLiveData<>("");
    private static final StringBuilder buffer = new StringBuilder();
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private static final ExecutorService FILE_WRITER = Executors.newSingleThreadExecutor();
    private static final Map<String, Long> toastTimestamps = new HashMap<>();
    private static int entryCount = 0;
    private static volatile File logFile;

    private DebugLog() {}

    public static void init(Context context) {
        if (context != null) {
            logFile = new File(context.getFilesDir(), "debug_log.txt");
        }
        installCrashHandler();
    }

    public static void init() {
        installCrashHandler();
    }

    private static void installCrashHandler() {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String crashEntry = buildEntry("CRASH [" + thread.getName() + "]", throwableToString(throwable));
            appendEntryInternal(crashEntry);
            appendToFileSync(crashEntry);
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    public static void e(String tag, String message, Throwable throwable) {
        String full = message + "\n" + throwableToString(throwable);
        appendEntry("ERROR [" + tag + "]", full);
    }

    public static void w(String tag, String message) {
        appendEntry("WARN [" + tag + "]", message);
    }

    public static void i(String tag, String message) {
        appendEntry("INFO [" + tag + "]", message);
    }

    public static LiveData<String> getLiveData() {
        return logLiveData;
    }

    public static String getCurrentSessionLog() {
        synchronized (buffer) {
            return buffer.toString();
        }
    }

    public static boolean isLikelyNetworkIssue(String message) {
        if (TextUtils.isEmpty(message)) {
            return false;
        }
        String normalized = message.trim().toLowerCase(Locale.US);
        return normalized.contains("network error")
                || normalized.contains("failed to connect")
                || normalized.contains("unable to resolve host")
                || normalized.contains("timeout")
                || normalized.contains("timed out")
                || normalized.contains("connection reset")
                || normalized.contains("connection refused")
                || normalized.contains("software caused connection abort")
                || normalized.contains("failed to fetch profile")
                || normalized.contains("获取资料失败");
    }

    public static boolean shouldShowToast(String key, long windowMs) {
        if (TextUtils.isEmpty(key)) {
            return true;
        }
        long now = System.currentTimeMillis();
        synchronized (toastTimestamps) {
            Long lastShown = toastTimestamps.get(key);
            if (lastShown != null && now - lastShown < windowMs) {
                return false;
            }
            toastTimestamps.put(key, now);
            return true;
        }
    }

    public static void logHandledError(String tag, String message) {
        if (!TextUtils.isEmpty(message)) {
            w(tag, message);
        }
    }

    public static void clear() {
        synchronized (buffer) {
            buffer.setLength(0);
            entryCount = 0;
        }
        synchronized (toastTimestamps) {
            toastTimestamps.clear();
        }
        clearPersistedLog();
        publish("");
    }

    private static void appendEntry(String level, String message) {
        String entry = buildEntry(level, message);
        appendEntryInternal(entry);
        if (BuildConfig.DEBUG) {
            appendToFileSync(entry);
        } else {
            appendToFileAsync(entry);
        }
    }

    private static String buildEntry(String level, String message) {
        String timestamp = TIME_FMT.format(new Date());
        return timestamp + " " + level + "\n" + message + "\n\n";
    }

    private static void appendEntryInternal(String entry) {
        synchronized (buffer) {
            if (entryCount >= MAX_ENTRIES) {
                int firstNewline = buffer.indexOf("\n\n");
                if (firstNewline > 0) {
                    buffer.delete(0, firstNewline + 2);
                    entryCount--;
                }
            }
            buffer.append(entry);
            entryCount++;
        }
        publish(buffer.toString());
    }

    private static void appendToFileAsync(String entry) {
        File localFile = logFile;
        if (localFile == null) {
            return;
        }
        FILE_WRITER.execute(() -> appendToFile(localFile, entry));
    }

    private static void appendToFileSync(String entry) {
        File localFile = logFile;
        if (localFile == null) {
            return;
        }
        appendToFile(localFile, entry);
    }

    private static void appendToFile(File targetFile, String content) {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return;
        }
        try (FileWriter writer = new FileWriter(targetFile, true)) {
            writer.write(content);
        } catch (IOException ignored) {
        }
    }

    public static String readPersistedLog() {
        File localFile = logFile;
        if (localFile == null || !localFile.exists() || !localFile.isFile()) {
            return "";
        }
        try (FileInputStream inputStream = new FileInputStream(localFile)) {
            byte[] data = new byte[(int) localFile.length()];
            int read = inputStream.read(data);
            if (read <= 0) {
                return "";
            }
            return new String(data, 0, read);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static void clearPersistedLog() {
        File localFile = logFile;
        if (localFile != null && localFile.exists() && !localFile.delete()) {
            appendEntry("WARN [DebugLog]", "Failed to delete persisted debug log file");
        }
    }

    private static void publish(String value) {
        try {
            logLiveData.postValue(value);
        } catch (RuntimeException ignored) {
        }
    }

    private static String throwableToString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
