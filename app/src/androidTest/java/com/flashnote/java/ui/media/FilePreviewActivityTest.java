package com.flashnote.java.ui.media;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.flashnote.java.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class FilePreviewActivityTest {
    @Test
    public void preparesEpubOnDevice() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File epub = new File(context.getFilesDir(), "instrumented-prepare-epub.epub");
        createEpub(epub);

        EpubDocument.prepare(epub, new File(context.getCacheDir(), "tn_epub_test"));
    }

    @Test
    public void opensEpubPreviewFromAppPrivateFile() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File epub = new File(context.getFilesDir(), "instrumented-epub.epub");
        createEpub(epub);

        Intent intent = new Intent(context, FilePreviewActivity.class);
        intent.putExtra(FilePreviewActivity.EXTRA_FILE_PATH, epub.getAbsolutePath());
        intent.putExtra(FilePreviewActivity.EXTRA_FILE_NAME, epub.getName());
        intent.putExtra(FilePreviewActivity.EXTRA_DISPLAY_NAME, epub.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try (ActivityScenario<FilePreviewActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.epubWebView).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.unsupportedText).getVisibility());
            });
        }
    }

    private static void createEpub(File file) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            add(zip, "META-INF/container.xml", "<?xml version=\"1.0\"?><!DOCTYPE container SYSTEM \"http://example.invalid/container.dtd\"><container><rootfiles><rootfile full-path=\"OPS/package%20file.opf#opf\"/></rootfiles></container>");
            add(zip, "OPS/package file.opf", "<?xml version=\"1.0\"?><package><manifest><item id=\"c1\" href=\"Text/chapter%201.xhtml#start\" media-type=\"application/xhtml+xml\"/></manifest><spine><itemref idref=\"c1\"/></spine></package>");
            add(zip, "OPS/Text/chapter 1.xhtml", "<html><body><h1>调试 EPUB</h1><p>模拟器预览内容</p></body></html>");
        }
    }

    private static void add(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
