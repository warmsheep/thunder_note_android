package com.flashnote.java.ui.media;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertTrue;

public class EpubDocumentTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void prepare_buildsReadableHtmlFromMinimalEpub() throws Exception {
        File epub = temporaryFolder.newFile("book.epub");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(epub))) {
            add(zip, "META-INF/container.xml", "<?xml version=\"1.0\"?><container><rootfiles><rootfile full-path=\"OPS/package.opf\"/></rootfiles></container>");
            add(zip, "OPS/package.opf", "<?xml version=\"1.0\"?><package><manifest><item id=\"c1\" href=\"chapter.xhtml\" media-type=\"application/xhtml+xml\"/></manifest><spine><itemref idref=\"c1\"/></spine></package>");
            add(zip, "OPS/chapter.xhtml", "<html><body><h1>第一章</h1><p>你好 EPUB</p></body></html>");
        }

        EpubDocument.PreparedDocument document = EpubDocument.prepare(epub, temporaryFolder.newFolder("cache"));

        String html = new String(java.nio.file.Files.readAllBytes(document.htmlFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(html.contains("第一章"));
        assertTrue(html.contains("你好 EPUB"));
    }

    @Test(expected = EpubDocument.EpubException.class)
    public void prepare_rejectsZipSlipEntry() throws Exception {
        File epub = temporaryFolder.newFile("evil.epub");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(epub))) {
            add(zip, "../evil.txt", "evil");
        }

        EpubDocument.prepare(epub, temporaryFolder.newFolder("cache"));
    }

    @Test(expected = EpubDocument.EpubException.class)
    public void safeFile_rejectsPathTraversal() throws Exception {
        EpubDocument.safeFile(temporaryFolder.newFolder("root"), "OPS/../../evil.xhtml");
    }

    private static void add(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
