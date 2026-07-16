package com.example.novelpia_custom;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * APK 다운로드 + 설치 인텐트 실행
 * - 다운로드 진행률을 Notification으로 표시
 * - Android 7+ FileProvider 대응
 * - 호출부에서 백그라운드 스레드 실행 필요
 */
public class UpdateInstaller {
    private static final String TAG = "UpdateInstaller";
    private static final String CHANNEL_ID = "update_download";
    private static final int NOTIFY_ID = 1001;
    private static final String FILE_PROVIDER_AUTHORITY = ".fileprovider";

    public interface DownloadCallback {
        void onProgress(int percent);
        void onComplete(boolean success, String message);
    }

    /**
     * APK 다운로드 + 설치
     * @param downloadUrl GitHub Release APK URL
     * @param context Application context
     * @param callback 진행률/완료 콜백 (UI 스레드 아님 주의)
     */
    public static void downloadAndInstall(String downloadUrl, Context context,
                                          DownloadCallback callback) {
        File apkFile = null;
        try {
            // 캐시 디렉토리에 APK 저장
            File cacheDir = context.getCacheDir();
            if (!cacheDir.exists()) cacheDir.mkdirs();
            apkFile = new File(cacheDir, "novelpia-update.apk");

            // 이전 다운로드 파일 정리
            if (apkFile.exists()) apkFile.delete();

            // HTTP 연결
            HttpURLConnection conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String msg = "다운로드 실패 (HTTP " + responseCode + ")";
                Log.e(TAG, msg);
                if (callback != null) callback.onComplete(false, msg);
                return;
            }

            int totalSize = conn.getContentLength();
            Log.d(TAG, "APK 다운로드 시작: " + totalSize + " bytes");

            // 스트림 → 파일 저장
            try (InputStream is = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(apkFile)) {

                byte[] buffer = new byte[8192];
                int read;
                int downloaded = 0;
                int lastPercent = -1;

                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                    downloaded += read;

                    if (totalSize > 0) {
                        int percent = (int) ((long) downloaded * 100 / totalSize);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            if (callback != null) callback.onProgress(percent);
                            Log.d(TAG, "다운로드: " + percent + "%");
                        }
                    }
                }
            }
            conn.disconnect();

            Log.d(TAG, "APK 다운로드 완료: " + apkFile.getAbsolutePath());
            if (callback != null) callback.onProgress(100);

            // 설치 실행
            installApk(context, apkFile);
            if (callback != null) callback.onComplete(true, "다운로드 완료");

        } catch (Exception e) {
            Log.e(TAG, "다운로드/설치 실패: " + e.getMessage());
            if (apkFile != null && apkFile.exists()) apkFile.delete();
            if (callback != null) callback.onComplete(false, "설치 실패: " + e.getMessage());
        }
    }

    /**
     * FileProvider URI로 설치 인텐트 실행
     */
    private static void installApk(Context context, File apkFile) {
        Uri apkUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + FILE_PROVIDER_AUTHORITY,
                apkFile
        );

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
    }
}