package com.flashnote.java.ui.media;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

final class EpubDocument {
    private EpubDocument() {
    }

    static PreparedDocument prepare(File epubFile, File cacheParent) throws Exception {
        if (epubFile == null || !epubFile.exists() || !epubFile.isFile()) {
            throw new EpubException("文件不是有效的 EPUB 压缩包");
        }
        File root = new File(cacheParent, simpleHash(epubFile.getAbsolutePath() + ":" + epubFile.length() + ":" + epubFile.lastModified()));
        deleteRecursively(root);
        if (!root.mkdirs() && !root.isDirectory()) {
            throw new IOException("无法创建 EPUB 缓存目录");
        }

        try (ZipFile zipFile = new ZipFile(epubFile)) {
            extract(zipFile, root);
        } catch (IOException exception) {
            throw new EpubException("文件不是有效的 EPUB 压缩包", exception);
        }

        File container = safeFile(root, "META-INF/container.xml");
        if (!container.exists()) {
            throw new EpubException("EPUB 缺少 META-INF/container.xml");
        }
        Document containerXml = parseXml(container);
        NodeList rootFiles = containerXml.getElementsByTagNameNS("*", "rootfile");
        if (rootFiles.getLength() == 0) {
            rootFiles = containerXml.getElementsByTagName("rootfile");
        }
        if (rootFiles.getLength() == 0) {
            throw new EpubException("EPUB 缺少 OPF 包描述文件");
        }
        Element rootFile = (Element) rootFiles.item(0);
        String opfPath = rootFile.getAttribute("full-path");
        if (opfPath == null || opfPath.trim().isEmpty()) {
            throw new EpubException("EPUB 缺少 OPF 包描述文件");
        }

        File opfFile = safeFile(root, stripFragmentAndQuery(opfPath));
        if (!opfFile.exists()) {
            throw new EpubException("EPUB 缺少 OPF 包描述文件");
        }
        File opfBase = opfFile.getParentFile();
        Document opfXml = parseXml(opfFile);
        Map<String, ManifestItem> manifest = manifestItems(opfXml);
        List<String> spine = spineRefs(opfXml);
        if (spine.isEmpty()) {
            throw new EpubException("EPUB 缺少正文目录");
        }

        List<ManifestItem> htmlItems = new ArrayList<>();
        for (String idRef : spine) {
            ManifestItem item = manifest.get(idRef);
            if (item == null) {
                continue;
            }
            String mediaType = lower(item.mediaType);
            String href = lower(stripFragmentAndQuery(item.href));
            if (mediaType.contains("html") || href.endsWith(".html") || href.endsWith(".xhtml") || href.endsWith(".htm")) {
                htmlItems.add(item);
            }
        }
        if (htmlItems.isEmpty()) {
            throw new EpubException("EPUB 未找到可显示的正文内容");
        }

        String html = buildReaderHtml(htmlItems, opfBase, root);
        File htmlFile = safeFile(root, "tn_epub_reader.html");
        try (FileOutputStream outputStream = new FileOutputStream(htmlFile)) {
            outputStream.write(html.getBytes(StandardCharsets.UTF_8));
        }
        return new PreparedDocument(htmlFile, root);
    }

    private static void extract(ZipFile zipFile, File root) throws IOException, EpubException {
        for (ZipEntry entry : java.util.Collections.list(zipFile.entries())) {
            File destination = safeFile(root, entry.getName());
            if (entry.isDirectory()) {
                if (!destination.mkdirs() && !destination.isDirectory()) {
                    throw new IOException("无法创建 EPUB 目录");
                }
                continue;
            }
            File parent = destination.getParentFile();
            if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
                throw new IOException("无法创建 EPUB 目录");
            }
            try (InputStream inputStream = zipFile.getInputStream(entry);
                 FileOutputStream outputStream = new FileOutputStream(destination)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
            }
        }
    }

    static File safeFile(File root, String relativePath) throws IOException, EpubException {
        if (relativePath == null || relativePath.indexOf('\0') >= 0) {
            throw new EpubException("EPUB 内包含不安全路径，已阻止打开");
        }
        String normalized = decodePath(stripFragmentAndQuery(relativePath).replace('\\', '/'));
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/") || lower.contains("://") || normalized.equals("..") || normalized.startsWith("../") || normalized.contains("/../") || normalized.endsWith("/..")) {
            throw new EpubException("EPUB 内包含不安全路径，已阻止打开");
        }
        File rootCanonical = root.getCanonicalFile();
        File destination = new File(rootCanonical, normalized).getCanonicalFile();
        String rootPath = rootCanonical.getPath();
        String destinationPath = destination.getPath();
        if (!destinationPath.equals(rootPath) && !destinationPath.startsWith(rootPath + File.separator)) {
            throw new EpubException("EPUB 内包含不安全路径，已阻止打开");
        }
        return destination;
    }

    private static File resolvedFile(File root, File base, String relativePath) throws IOException, EpubException {
        if (relativePath == null || relativePath.indexOf('\0') >= 0) {
            throw new EpubException("EPUB 内包含不安全路径，已阻止打开");
        }
        String normalized = decodePath(stripFragmentAndQuery(relativePath).replace('\\', '/'));
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/") || lower.contains("://")) {
            throw new EpubException("EPUB 内包含不安全路径，已阻止打开");
        }
        File rootCanonical = root.getCanonicalFile();
        File destination = new File(base.getCanonicalFile(), normalized).getCanonicalFile();
        String rootPath = rootCanonical.getPath();
        String destinationPath = destination.getPath();
        if (!destinationPath.equals(rootPath) && !destinationPath.startsWith(rootPath + File.separator)) {
            throw new EpubException("EPUB 内包含不安全路径，已阻止打开");
        }
        return destination;
    }

    private static Document parseXml(File file) throws Exception {
        try {
            return parseXml(file, true);
        } catch (Exception firstException) {
            try {
                return parseXmlWithoutDoctype(file);
            } catch (Exception ignored) {
                throw firstException;
            }
        }
    }

    private static Document parseXml(File file, boolean disallowDoctype) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        setFeatureIfSupported(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        if (disallowDoctype) {
            setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        }
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeatureIfSupported(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        setAttributeIfSupported(factory, "http://javax.xml.XMLConstants/property/accessExternalDTD", "");
        setAttributeIfSupported(factory, "http://javax.xml.XMLConstants/property/accessExternalSchema", "");
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return builder.parse(inputStream);
        }
    }

    private static Document parseXmlWithoutDoctype(File file) throws Exception {
        String xml = stripDoctype(readUtf8(file));
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        setFeatureIfSupported(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeatureIfSupported(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        setAttributeIfSupported(factory, "http://javax.xml.XMLConstants/property/accessExternalDTD", "");
        setAttributeIfSupported(factory, "http://javax.xml.XMLConstants/property/accessExternalSchema", "");
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static void setFeatureIfSupported(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (RuntimeException ignored) {
        } catch (Exception ignored) {
        }
    }

    private static void setAttributeIfSupported(DocumentBuilderFactory factory, String name, String value) {
        try {
            factory.setAttribute(name, value);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static Map<String, ManifestItem> manifestItems(Document opfXml) {
        Map<String, ManifestItem> result = new HashMap<>();
        NodeList nodes = opfXml.getElementsByTagNameNS("*", "item");
        if (nodes.getLength() == 0) {
            nodes = opfXml.getElementsByTagName("item");
        }
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String id = element.getAttribute("id");
            String href = element.getAttribute("href");
            if (id == null || id.isEmpty() || href == null || href.isEmpty()) {
                continue;
            }
            result.put(id, new ManifestItem(href, element.getAttribute("media-type")));
        }
        return result;
    }

    private static List<String> spineRefs(Document opfXml) {
        List<String> result = new ArrayList<>();
        NodeList nodes = opfXml.getElementsByTagNameNS("*", "itemref");
        if (nodes.getLength() == 0) {
            nodes = opfXml.getElementsByTagName("itemref");
        }
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String idRef = element.getAttribute("idref");
            if (idRef != null && !idRef.isEmpty()) {
                result.add(idRef);
            }
        }
        return result;
    }

    private static String buildReaderHtml(List<ManifestItem> htmlItems, File opfBase, File root) throws Exception {
        List<String> sections = new ArrayList<>();
        for (ManifestItem item : htmlItems) {
            File itemFile = resolvedFile(root, opfBase, stripFragmentAndQuery(item.href));
            if (!itemFile.exists()) {
                continue;
            }
            String raw = readUtf8(itemFile);
            String body = firstMatch(raw, Pattern.compile("(?is)<body[^>]*>(.*?)</body>"));
            if (body == null) {
                body = raw;
            }
            sections.add("<section>" + rewriteReferences(body, itemFile.getParentFile(), root) + "</section>");
        }
        if (sections.isEmpty()) {
            throw new EpubException("EPUB 未找到可显示的正文内容");
        }
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=yes\"><style>body{margin:0;padding:20px;line-height:1.65;color:#1f2937;background:#ffffff;font-family:sans-serif;}img,svg,video{max-width:100%;height:auto;}section{margin:0 auto 28px auto;max-width:760px;}a{color:#2563eb;}pre{white-space:pre-wrap;overflow-wrap:anywhere;}</style></head><body>" + String.join("\n", sections) + "</body></html>";
    }

    private static String rewriteReferences(String html, File base, File root) {
        Pattern pattern = Pattern.compile("\\b(src|href)\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            String lower = lower(value);
            if (value.startsWith("#") || lower.startsWith("data:")) {
                matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            try {
                File resolved = resolvedFile(root, base, stripFragmentAndQuery(value));
                matcher.appendReplacement(output, Matcher.quoteReplacement(key + "=\"" + escapeHtml(resolved.toURI().toString()) + "\""));
            } catch (Exception ignored) {
                matcher.appendReplacement(output, "");
            }
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String firstMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String stripDoctype(String xml) {
        return xml.replaceFirst("(?is)<!DOCTYPE\\s+[^>]*(\\[[\\s\\S]*?\\]\\s*)?>", "");
    }

    private static String stripFragmentAndQuery(String path) {
        if (path == null) {
            return null;
        }
        int fragmentIndex = path.indexOf('#');
        int queryIndex = path.indexOf('?');
        int end = path.length();
        if (fragmentIndex >= 0) {
            end = Math.min(end, fragmentIndex);
        }
        if (queryIndex >= 0) {
            end = Math.min(end, queryIndex);
        }
        return path.substring(0, end);
    }

    private static String decodePath(String path) throws EpubException {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (ch != '%') {
                result.append(ch);
                continue;
            }
            if (i + 2 >= path.length()) {
                throw new EpubException("EPUB 内包含不安全路径，已阻止打开");
            }
            int high = Character.digit(path.charAt(i + 1), 16);
            int low = Character.digit(path.charAt(i + 2), 16);
            if (high < 0 || low < 0) {
                throw new EpubException("EPUB 内包含不安全路径，已阻止打开");
            }
            result.append((char) ((high << 4) + low));
            i += 2;
        }
        return result.toString();
    }

    private static String readUtf8(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String simpleHash(String input) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < input.length(); i++) {
            hash ^= input.charAt(i);
            hash *= 0x100000001b3L;
        }
        return Long.toUnsignedString(hash, 16);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    static final class PreparedDocument {
        final File htmlFile;
        final File readAccessRoot;

        PreparedDocument(File htmlFile, File readAccessRoot) {
            this.htmlFile = htmlFile;
            this.readAccessRoot = readAccessRoot;
        }
    }

    static final class EpubException extends Exception {
        EpubException(String message) {
            super(message);
        }

        EpubException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class ManifestItem {
        final String href;
        final String mediaType;

        ManifestItem(String href, String mediaType) {
            this.href = href;
            this.mediaType = mediaType;
        }
    }
}
