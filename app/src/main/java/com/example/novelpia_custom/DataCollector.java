package com.example.novelpia_custom;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 노벨피아 localStorage 데이터를 수집해서 서버로 전송
 * - userLastNovelData: 최근 본 작품 정보
 * - page_mark: 읽던 페이지 북마크 목록
 */
public class DataCollector {
    private static final String TAG = "DataCollector";
    // Tailscale IP로 직접 전송
    private static final String SERVER_URL = "http://100.102.179.114:8765/collect";
    private static final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * WebView에서 localStorage 데이터를 읽어 서버로 전송
     * 페이지 로딩 완료 후 1.5초 지연 후 실행 (Vue/JS 렌더링 대기)
     */
    public static void collect(WebView wv) {
        handler.postDelayed(() -> readAndSend(wv), 1500);
    }

    private static void readAndSend(WebView wv) {
        String js =
            "(function() {" +
            "  var data = {" +
            "    userLastNovelData: localStorage.getItem('userLastNovelData')," +
            "    page_mark: localStorage.getItem('page_mark')," +
            "    timestamp: new Date().toISOString()," +
            "    url: window.location.href" +
            "  };" +
            "  return JSON.stringify(data);" +
            "})();";

        wv.evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (value == null || value.equals("null")) {
                    Log.d(TAG, "localStorage data is null");
                    return;
                }
                // evaluateJavascript returns JSON-escaped string
                // Unescape: remove surrounding quotes and unescape
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                    value = value.replace("\\\"", "\"")
                                 .replace("\\\\", "\\");
                }
                sendToServer(value);
            }
        });
    }

    /**
     * 수집된 JSON 데이터를 서버로 POST 전송
     */
    private static void sendToServer(String jsonData) {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonData.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Data sent successfully, response: " + responseCode);
            } catch (Exception e) {
                Log.e(TAG, "Failed to send data: " + e.getMessage());
            }
        }).start();
    }
}
