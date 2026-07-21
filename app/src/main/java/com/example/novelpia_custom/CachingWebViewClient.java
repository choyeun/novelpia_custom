package com.example.novelpia_custom;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * WebView 리소스 캐싱 유틸 — static 헬퍼 메서드 모음
 * - getExtension: URL에서 확장자 추출
 * - isCacheableExtension: 캐싱 대상 확인 (이미지 전용, 폰트 제외)
 * - mimeFromExtension: 확장자 → MIME 타입
 * - readAllBytes: InputStream → byte[]
 * - truncateUrl: 긴 URL 로그 출력용
 *
 * MainActivity의 WebViewClient anonymous class에서 호출됨
 */
public class CachingWebViewClient {
    /** 이미지 + CSS/JS/폰트 캐싱 (폰트는 Content-Encoding 버그 방지 위해 encoding=\"\" 필수) */
    private static final Set<String> CACHEABLE_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".ico",
            ".css", ".js",
            ".woff", ".woff2", ".ttf", ".eot", ".otf"
    ));

    /** URL에서 파일 확장자 추출 (쿼리 파라미터 제외) */
    public static String getExtension(String url) {
        int qIdx = url.indexOf('?');
        String path = qIdx > 0 ? url.substring(0, qIdx) : url;
        int fIdx = path.indexOf('#');
        path = fIdx > 0 ? path.substring(0, fIdx) : path;
        int dotIdx = path.lastIndexOf('.');
        if (dotIdx < 0) return "";
        return path.substring(dotIdx);
    }

    /** 캐싱 가능한 확장자인지 확인 */
    public static boolean isCacheableExtension(String ext) {
        return CACHEABLE_EXTENSIONS.contains(ext);
    }

    /** 확장자 → MIME 타입 */
    public static String mimeFromExtension(String ext) {
        switch (ext) {
            case ".jpg":
            case ".jpeg": return "image/jpeg";
            case ".png":  return "image/png";
            case ".gif":  return "image/gif";
            case ".webp": return "image/webp";
            case ".svg":  return "image/svg+xml";
            case ".ico":  return "image/x-icon";
            case ".css":  return "text/css";
            case ".js":   return "application/javascript";
            case ".woff": return "font/woff";
            case ".woff2":return "font/woff2";
            case ".ttf":  return "font/ttf";
            case ".eot":  return "application/vnd.ms-fontobject";
            case ".otf":  return "font/otf";
            default:  return "application/octet-stream";
        }
    }

    /** InputStream → byte[] */
    public static byte[] readAllBytes(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = is.read(buf)) != -1) {
            baos.write(buf, 0, read);
        }
        is.close();
        return baos.toByteArray();
    }

    /** 긴 URL 로그용 자르기 */
    public static String truncateUrl(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}