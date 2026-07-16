package com.example.novelpia_custom;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 디스크 기반 LRU 캐시 — 이미지/폰트 등 정적 리소스 저장
 * - 최대 50MB, 초과 시 가장 오래 접근 안 한 파일부터 삭제
 * - 스레드 안전 (synchronized)
 * - WebView shouldInterceptRequest에서 사용
 */
public class DiskCache {
    private static final String TAG = "DiskCache";
    private static final String CACHE_DIR = "novelpia_cache";
    private static final String INDEX_FILE = "index.json";
    private static final long MAX_SIZE = 50L * 1024 * 1024; // 50MB
    private static final long DEFAULT_TTL = 24 * 60 * 60 * 1000L; // 24시간

    private final File cacheDir;
    private final File indexFile;
    private JSONObject index; // { hash: {url, contentType, size, cachedAt, ttl, lastAccess} }

    public DiskCache(File baseCacheDir) {
        this.cacheDir = new File(baseCacheDir, CACHE_DIR);
        this.indexFile = new File(cacheDir, INDEX_FILE);
        this.index = new JSONObject();
        loadIndex();
    }

    // ─── public API ────────────────────────────────────────

    /** 캐시에서 조회. 있으면 lastAccess 갱신 */
    public synchronized CacheEntry get(String url) {
        try {
            String hash = md5(url);
            if (!index.has(hash)) return null;

            JSONObject entry = index.getJSONObject(hash);
            long cachedAt = entry.optLong("cachedAt", 0);
            long ttl = entry.optLong("ttl", DEFAULT_TTL);

            // TTL 만료 확인
            if (System.currentTimeMillis() - cachedAt > ttl) {
                remove(hash);
                return null;
            }

            // 파일 존재 확인
            File file = new File(cacheDir, hash);
            if (!file.exists()) {
                index.remove(hash);
                saveIndex();
                return null;
            }

            // lastAccess 갱신
            entry.put("lastAccess", System.currentTimeMillis());
            saveIndex();

            byte[] data = readFile(file);
            String contentType = entry.optString("contentType", "");
            String encoding = entry.optString("encoding", "");
            return new CacheEntry(data, contentType, encoding);

        } catch (Exception e) {
            Log.w(TAG, "get 실패: " + url + " — " + e.getMessage());
            return null;
        }
    }

    /** 캐시에 저장. 초과 시 LRU eviction */
    public synchronized void put(String url, byte[] data, String contentType, String encoding) {
        try {
            String hash = md5(url);
            File file = new File(cacheDir, hash);

            // 파일 저장
            if (!cacheDir.exists()) cacheDir.mkdirs();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
            }

            // 인덱스 갱신
            JSONObject entry = new JSONObject();
            entry.put("url", url);
            entry.put("contentType", contentType != null ? contentType : "");
            entry.put("encoding", encoding != null ? encoding : "");
            entry.put("size", data.length);
            entry.put("cachedAt", System.currentTimeMillis());
            entry.put("ttl", DEFAULT_TTL);
            entry.put("lastAccess", System.currentTimeMillis());
            index.put(hash, entry);

            saveIndex();
            evictIfNeeded();

        } catch (Exception e) {
            Log.w(TAG, "put 실패: " + url + " — " + e.getMessage());
        }
    }

    /** 캐시 전체 삭제 */
    public synchronized void clear() {
        if (cacheDir.exists()) {
            for (File f : cacheDir.listFiles()) {
                if (f.isFile() && !f.getName().equals(INDEX_FILE)) f.delete();
            }
        }
        index = new JSONObject();
        saveIndex();
        Log.d(TAG, "캐시 초기화 완료");
    }

    /** 현재 캐시 크기 (bytes) */
    public synchronized long getSize() {
        long total = 0;
        try {
            for (String hash : keySet()) {
                total += index.getJSONObject(hash).optLong("size", 0);
            }
        } catch (Exception ignored) {}
        return total;
    }

    /** 캐시된 파일 개수 */
    public synchronized int getCount() {
        return keySet().size();
    }

    // ─── 내부 ──────────────────────────────────────────────

    private void remove(String hash) {
        File file = new File(cacheDir, hash);
        if (file.exists()) file.delete();
        index.remove(hash);
    }

    private void evictIfNeeded() {
        long total = getSize();
        if (total <= MAX_SIZE) return;

        // lastAccess 오름차순 정렬
        List<String> keys = new ArrayList<>(keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                try {
                    long la = index.getJSONObject(a).optLong("lastAccess", 0);
                    long lb = index.getJSONObject(b).optLong("lastAccess", 0);
                    return Long.compare(la, lb);
                } catch (Exception e) {
                    return 0;
                }
            }
        });

        for (String hash : keys) {
            if (total <= MAX_SIZE) break;
            try {
                total -= index.getJSONObject(hash).optLong("size", 0);
                remove(hash);
            } catch (Exception ignored) {}
        }
        saveIndex();
        Log.d(TAG, "LRU eviction 완료, 현재 크기: " + (total / 1024 / 1024) + "MB");
    }

    private void loadIndex() {
        if (indexFile.exists()) {
            try {
                byte[] data = readFile(indexFile);
                index = new JSONObject(new String(data, "UTF-8"));
            } catch (Exception e) {
                Log.w(TAG, "인덱스 로드 실패, 초기화: " + e.getMessage());
                index = new JSONObject();
            }
        }
    }

    private void saveIndex() {
        try {
            if (!cacheDir.exists()) cacheDir.mkdirs();
            try (FileOutputStream fos = new FileOutputStream(indexFile)) {
                fos.write(index.toString(2).getBytes("UTF-8"));
            }
        } catch (Exception e) {
            Log.w(TAG, "인덱스 저장 실패: " + e.getMessage());
        }
    }

    private List<String> keySet() {
        List<String> keys = new ArrayList<>();
        java.util.Iterator<String> iter = index.keys();
        while (iter.hasNext()) keys.add(iter.next());
        return keys;
    }

    private static byte[] readFile(File f) throws Exception {
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = fis.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            return data;
        }
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    // ─── 데이터 클래스 ─────────────────────────────────────

    public static class CacheEntry {
        public final byte[] data;
        public final String contentType;
        public final String encoding;

        public CacheEntry(byte[] data, String contentType, String encoding) {
            this.data = data;
            this.contentType = contentType;
            this.encoding = encoding;
        }
    }
}