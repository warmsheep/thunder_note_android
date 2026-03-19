package com.flashnote.java.util;

import android.content.Context;
import android.text.TextUtils;
import android.widget.TextView;

import java.util.regex.Pattern;

import io.noties.markwon.Markwon;

public final class MarkdownRenderer {
    private static volatile Markwon instance;
    private static final Pattern MARKDOWN_PATTERN = Pattern.compile(
            "(?m)(^#{1,6}\\s)|(^[-*+]\\s)|(^>\\s)|(```)|(`[^`]+`)|(\\*\\*[^*]+\\*\\*)|(__[^_]+__)|(\\[[^\\]]+\\]\\([^\\)]+\\))"
    );

    private MarkdownRenderer() {
    }

    private static Markwon get(Context context) {
        if (instance == null) {
            synchronized (MarkdownRenderer.class) {
                if (instance == null) {
                    instance = Markwon.create(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public static void render(TextView textView, String markdown) {
        if (textView == null) {
            return;
        }
        if (TextUtils.isEmpty(markdown)) {
            textView.setText("");
            return;
        }
        get(textView.getContext()).setMarkdown(textView, markdown);
    }

    public static void renderIfMarkdown(TextView textView, String content) {
        if (textView == null) {
            return;
        }
        if (TextUtils.isEmpty(content)) {
            textView.setText("");
            return;
        }
        if (looksLikeMarkdown(content)) {
            render(textView, content);
            return;
        }
        textView.setText(content);
    }

    public static boolean looksLikeMarkdown(String content) {
        if (TextUtils.isEmpty(content)) {
            return false;
        }
        return MARKDOWN_PATTERN.matcher(content).find();
    }
}
