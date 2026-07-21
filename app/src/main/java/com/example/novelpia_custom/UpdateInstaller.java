package com.example.novelpia_custom;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * APK 다운로드 + 설치 + 알림
 * - downloadApk: 백그라운드 다운로드 전용
 * - installDownloadedApk: 캐시된 APK 설치
 * - showUpdateNotification: 다운로드 완료 알림
 */
public class UpdateInstaller {
    private static final String TAG = "UpdateInstaller";
    private static final String CHANNEL_ID = "update_download";
    private static final int NOTIFY_DOWNLOADING = 1000;
    private static final int NOTIFY_READY = 1001;
    private static final String FILE_PROVIDER_AUTHORITY = ".fileprovider";
    private static final String APK_FILENAME = "novelpia-update.apk";

    /** 다운로드 진행률 콜백 */
    public interface DownloadCallback {
        void onProgress(int percent);
        void onComplete(boolean success, String message);
    }

    // ─── 알림 채널 ────────────────────────────────────

    /** 알림 채널 생성 (앱 시작 시 한 번 호출) */
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "업데이트 다운로드",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("APK 업데이트 다운로드 상태 및 설치 알림");
        channel.setShowBadge(true);
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    // ─── 다운로드 전용 ─────────────────────────────────

    /**
     * APK 다운로드 (설치 안 함)
     * @return 다운로드된 APK 파일, 실패 시 null
     */
    public static File downloadApk(String downloadUrl, Context context, DownloadCallback callback) {
        File apkFile = null;
        try {
            File cacheDir = context.getCacheDir();
            if (!cacheDir.exists()) cacheDir.mkdirs();
            apkFile = new File(cacheDir, APK_FILENAME);

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
                return null;
            }

            int totalSize = conn.getContentLength();
            Log.d(TAG, "APK 다운로드 시작: " + totalSize + " bytes");

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
                        }
                    }
                }
            }
            conn.disconnect();

            Log.d(TAG, "APK 다운로드 완료: " + apkFile.getAbsolutePath());
            if (callback != null) {
                callback.onProgress(100);
                callback.onComplete(true, "다운로드 완료");
            }
            return apkFile;

        } catch (Exception e) {
            Log.e(TAG, "다운로드 실패: " + e.getMessage());
            if (apkFile != null && apkFile.exists()) apkFile.delete();
            if (callback != null) callback.onComplete(false, "다운로드 실패: " + e.getMessage());
            return null;
        }
    }

    // ─── 설치 ─────────────────────────────────────────

    /** 캐시된 APK 파일 반환 (없으면 null) */
    public static File getDownloadedApkFile(Context context) {
        File apkFile = new File(context.getCacheDir(), APK_FILENAME);
        return apkFile.exists() ? apkFile : null;
    }

    /** 캐시된 APK 설치 인텐트 실행 */
    public static void installDownloadedApk(Context context) {
        File apkFile = getDownloadedApkFile(context);
        if (apkFile == null) {
            Log.w(TAG, "설치할 APK 파일 없음");
            return;
        }
        installApk(context, apkFile);
    }

    /** FileProvider URI로 설치 인텐트 실행 */
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

    // ─── 알림 ─────────────────────────────────────────

    /** 다운로드 완료 알림 표시 */
    public static void showUpdateNotification(Context context, String version) {
        File apkFile = getDownloadedApkFile(context);
        if (apkFile == null) return;

        // 설치 인텐트를 PendingIntent로 감싸기
        Uri apkUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + FILE_PROVIDER_AUTHORITY,
                apkFile
        );

        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("📲 업데이트 준비 완료")
                .setContentText("v" + version + " 설치를 탭하여 시작")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFY_READY, builder.build());
    }

    // ─── 레거시 (다운로드 + 즉시 설치) ─────────────────

    /**
     * APK 다운로드 + 설치 (기존 호환용)
     * @deprecated downloadApk + installDownloadedApk 사용 권장
     */
    @Deprecated
    public static void downloadAndInstall(String downloadUrl, Context context,
                                          DownloadCallback callback) {
        File apkFile = downloadApk(downloadUrl, context, callback);
        if (apkFile != null) {
            installApk(context, apkFile);
        }
    }
}