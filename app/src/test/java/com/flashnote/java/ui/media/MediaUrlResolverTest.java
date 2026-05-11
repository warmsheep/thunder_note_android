package com.flashnote.java.ui.media;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * D1-W28-16 跨端兼容：Web 端上传头像 / 媒体时存的是绝对 URL（含 host），
 * Android 端必须把 host 改写为本机配置的 baseUrl，否则会访问 localhost / 错网段。
 * 本测试覆盖 {@link MediaUrlResolver#resolveWithBase(String, String)} 的纯函数语义。
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class MediaUrlResolverTest {

    private static final String BASE = "http://192.168.0.223:8080/";

    @Test
    public void extractObjectName_handlesDownloadUrl() {
        String input = "http://localhost:8080/api/files/download?objectName=u123/avatar-1.jpg";
        assertEquals("u123/avatar-1.jpg", MediaUrlResolver.extractObjectName(input));
    }

    @Test
    public void extractObjectName_passThroughRawObjectName() {
        assertEquals("u123/avatar.jpg", MediaUrlResolver.extractObjectName("u123/avatar.jpg"));
    }

    @Test
    public void extractObjectName_returnsEmptyForExternalUrl() {
        assertEquals("", MediaUrlResolver.extractObjectName("https://img.example.com/a.jpg"));
    }

    @Test
    public void resolveWithBase_rewritesCrossHostDownloadUrlToLocalBase() {
        // Web 端在 localhost 上传 → Android APP 必须改写为本机 baseUrl
        String input = "http://localhost:8080/api/files/download?objectName=u1/avatar.jpg";
        String resolved = MediaUrlResolver.resolveWithBase(input, BASE);
        assertEquals(BASE + "api/files/download?objectName=u1%2Favatar.jpg", resolved);
    }

    @Test
    public void resolveWithBase_rewritesAnyHostNotJustLocalhost() {
        // 哪怕原 URL host 是某局域网 IP，只要含 objectName=，都按本机 baseUrl 重写
        String input = "http://192.168.3.7:8080/api/files/download?objectName=u1/avatar.jpg";
        String resolved = MediaUrlResolver.resolveWithBase(input, BASE);
        assertEquals(BASE + "api/files/download?objectName=u1%2Favatar.jpg", resolved);
    }

    @Test
    public void resolveWithBase_buildsDownloadUrlFromRawObjectName() {
        // 纯 objectName 直接拼当前 baseUrl
        String resolved = MediaUrlResolver.resolveWithBase("u1/avatar.jpg", BASE);
        assertEquals(BASE + "api/files/download?objectName=u1%2Favatar.jpg", resolved);
    }

    @Test
    public void resolveWithBase_returnsExternalUrlAsIs() {
        // 不是闪记内部下载链接 → 保留原样（外链头像）
        String external = "https://img.example.com/a.jpg";
        assertEquals(external, MediaUrlResolver.resolveWithBase(external, BASE));
    }

    @Test
    public void resolveWithBase_appendsTrailingSlashIfMissing() {
        String resolved = MediaUrlResolver.resolveWithBase("u1/a.jpg", "http://api.local:9000");
        assertEquals("http://api.local:9000/api/files/download?objectName=u1%2Fa.jpg", resolved);
    }

    @Test
    public void resolveWithBase_returnsEmptyForEmptyInput() {
        assertEquals("", MediaUrlResolver.resolveWithBase("", BASE));
    }

    @Test
    public void resolveWithBase_returnsRawWhenBaseMissing() {
        // 无 baseUrl 时不至于崩溃，保留原值
        String input = "http://x.com/api/files/download?objectName=a";
        assertEquals(input, MediaUrlResolver.resolveWithBase(input, ""));
    }
}
