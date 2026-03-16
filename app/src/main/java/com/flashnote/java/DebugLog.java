package com.flashnote.java;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DebugLog {
    private static final int MAX_ENTRIES = 200;
    private static final MutableLiveData<String> logLiveData = new MutableLiveData<>("");
    private static final StringBuilder buffer = new StringBuilder();
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private static int entryCount = 0;

    private DebugLog() {}

    public static void init() {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            appendEntry("CRASH [" + thread.getName() + "]", throwableToString(throwable));
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

    public static void clear() {
        synchronized (buffer) {
            buffer.setLength(0);
            entryCount = 0;
        }
        logLiveData.postValue("");
    }

    private static void appendEntry(String level, String message) {
        String timestamp = TIME_FMT.format(new Date());
        String entry = timestamp + " " + level + "\n" + message + "\n\n";
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
        logLiveData.postValue(buffer.toString());
    }

    private static String throwableToString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
