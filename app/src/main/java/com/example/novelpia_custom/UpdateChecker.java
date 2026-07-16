package com.example.novelpia_custom;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * GitHub Releases API를 통해 최신 버전 확인
 * - 호출부에서 백그라운드 스레드 실행 필요
 * - 네트워크 오류 시 hasUpdate=false 반환 (조용한 실패)
 */
public class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static final String GITHUB_API =
            "https://api.github.com/repos/choyeun/novelpia_custom/releases/latest";

    public static class UpdateInfo {
        /** 새 버전이 있는지 */
        public final boolean hasUpdate;
        /** 최신 버전명 (예: "1.3.6") */
        public final String latestVersion;
        /** APK 다운로드 URL */
        public final String downloadUrl;
        /** APK 크기 (bytes) */
        public final long apkSize;
        /** 릴리스 노트 본문 */
        public final String releaseBody;

        public UpdateInfo(boolean hasUpdate, String latestVersion,
                          String downloadUrl, long apkSize, String releaseBody) {
            this.hasUpdate = hasUpdate;
            this.latestVersion = latestVersion;
            this.downloadUrl = downloadUrl;
            this.apkSize = apkSize;
            this.releaseBody = releaseBody;
        }

        /** 업데이트 없음 / 오류 발생 시 사용 */
        public static UpdateInfo none() {
            return new UpdateInfo(false, null, null, 0, null);
        }
    }

    /**
     * GitHub Releases API 조회 → 현재 버전과 비교
     * @param currentVersion BuildConfig.VERSION_NAME (예: "1.3.5")
     */
    public static UpdateInfo check(String currentVersion) {
        try {
            // GitHub API 호출
            HttpURLConnection conn = (HttpURLConnection) new URL(GITHUB_API).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "GitHub API 응답 " + code);
                return UpdateInfo.none();
            }

            // JSON 파싱
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            conn.disconnect();

            JSONObject json = new JSONObject(sb.toString());
            String tagName = json.optString("tag_name", "");        // "v1.3.6"
            String body = json.optString("body", "");
            String latestVer = tagName.startsWith("v") ? tagName.substring(1) : tagName;

            // APK 에셋 찾기
            String downloadUrl = null;
            long apkSize = 0;
            JSONArray assets = json.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.optString("name", "");
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", null);
                        apkSize = asset.optLong("size", 0);
                        break;
                    }
                }
            }
            // assets 배열이 없으면 tag_name으로 URL 추정
            if (downloadUrl == null) {
                downloadUrl = "https://github.com/choyeun/novelpia_custom/releases/download/"
                        + tagName + "/novelpia-custom-" + tagName + ".apk";
            }

            // 버전 비교
            boolean hasUpdate = isNewer(currentVersion, latestVer);
            Log.d(TAG, String.format("현재=%s 최신=%s 업데이트%s",
                    currentVersion, latestVer, hasUpdate ? "있음 ✅" : "없음"));

            return new UpdateInfo(hasUpdate, latestVer, downloadUrl, apkSize, body);

        } catch (Exception e) {
            Log.e(TAG, "버전 체크 실패: " + e.getMessage());
            return UpdateInfo.none();
        }
    }

    /**
     * SemVer 비교 (major.minor.patch)
     * @return latest > current 이면 true
     */
    public static boolean isNewer(String current, String latest) {
        int[] cur = parseVersion(current);
        int[] lat = parseVersion(latest);
        if (cur.length == 0 || lat.length == 0) return false;

        int len = Math.max(cur.length, lat.length);
        for (int i = 0; i < len; i++) {
            int c = i < cur.length ? cur[i] : 0;
            int l = i < lat.length ? lat[i] : 0;
            if (l > c) return true;
            if (l < c) return false;
        }
        return false; // 동일 버전
    }

    /** "1.3.5" → {1, 3, 5} */
    public static int[] parseVersion(String v) {
        if (v == null || v.isEmpty()) return new int[0];
        try {
            String[] parts = v.split("\\.");
            int[] result = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Integer.parseInt(parts[i]);
            }
            return result;
        } catch (NumberFormatException e) {
            return new int[0];
        }
    }
}