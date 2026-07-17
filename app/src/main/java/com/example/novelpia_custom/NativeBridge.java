package com.example.novelpia_custom;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Android 네이티브 ↔ JS 브릿지
 * - 오프라인 큐: 전송 실패 시 데이터를 SharedPreferences에 저장
 * - 네트워크 복구 시 큐 일괄 전송
 * - 모든 HTTP 요청은 별도 스레드에서 실행 (UI 블로킹 방지)
 */
public class NativeBridge {
    private static final String TAG = "NativeBridge";
    private static final String PREF_QUEUE = "novelpia_offline_queue";
    private static final String KEY_QUEUE = "pending_queue";
    private static final String SERVER_URL = "http://100.102.179.114:8765/collect";
    private static final int CONNECT_TIMEOUT = 4000;
    private static final int READ_TIMEOUT = 6000;

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public NativeBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * JS → Android: 데이터 전송 (fetch 성공/실패 모두 이쪽으로)
     * fetch를 native OkHttp로 대체하여 완전한 제어 확보
     */
    @JavascriptInterface
    public void sendData(String json) {
        executor.execute(() -> {
            if (tryPost(json)) {
                Log.d(TAG, "전송 성공");
                flushQueue(); // 기존 대기열도 함께 전송
            } else {
                Log.w(TAG, "전송 실패 → 큐에 저장");
                enqueue(json);
            }
        });
    }

    /** 서버로 POST */
    private boolean tryPost(String json) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(SERVER_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception e) {
            Log.w(TAG, "POST 실패: " + e.getMessage());
            return false;
        }
    }

    /** 대기열 저장 (SharedPreferences) */
    private void enqueue(String json) {
        SharedPreferences sp = context.getSharedPreferences(PREF_QUEUE, Context.MODE_PRIVATE);
        String existing = sp.getString(KEY_QUEUE, "[]");
        try {
            JSONArray arr = new JSONArray(existing);
            // body 전체를 JSON 객체로 감싸서 저장
            arr.put(new JSONObject(json));
            sp.edit().putString(KEY_QUEUE, arr.toString()).apply();
            Log.d(TAG, "큐 저장 완료 (총 " + arr.length() + "개 대기)");
        } catch (Exception e) {
            Log.e(TAG, "큐 저장 실패", e);
        }
    }

    /** 대기열 전송 후 비우기 (서버 연결 성공 시 호출) */
    private void flushQueue() {
        SharedPreferences sp = context.getSharedPreferences(PREF_QUEUE, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_QUEUE, "[]");
        if (raw.equals("[]") || raw.equals("")) return;

        try {
            JSONArray arr = new JSONArray(raw);
            int len = arr.length();

            // 큐 데이터를 순차 전송
            for (int i = 0; i < len; i++) {
                JSONObject item = arr.getJSONObject(i);
                if (!tryPost(item.toString())) {
                    Log.w(TAG, "큐 전송 중단 (" + i + "/" + len + " 실패)");
                    return; // 실패 시 남은 건 그대로 둠
                }
            }

            // 전부 성공 → 큐 초기화
            sp.edit().putString(KEY_QUEUE, "[]").apply();
            Log.d(TAG, "큐 " + len + "개 전송 완료 ✅");
        } catch (Exception e) {
            Log.e(TAG, "큐 플러시 실패", e);
        }
    }

    /** 큐 대기 개수 확인 (JS 디버깅용) */
    @JavascriptInterface
    public String getQueueSize() {
        SharedPreferences sp = context.getSharedPreferences(PREF_QUEUE, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_QUEUE, "[]");
        try {
            return String.valueOf(new JSONArray(raw).length());
        } catch (Exception e) {
            return "0";
        }
    }

    /**
     * JS → Android: API 응답 데이터 전송 (XHR/fetch 후킹)
     * page_mark 외에도 mybook 페이지의 API 응답에서 더 많은 작품 정보 추출
     */
    @JavascriptInterface
    public void sendApiData(String json) {
        executor.execute(() -> {
            // API 데이터는 별도 marker와 함께 전송
            String wrapped = "{\"source\":\"api_hook\",\"data\":" + json + "}";
            if (tryPost(wrapped)) {
                Log.d(TAG, "API 데이터 전송 성공");
            } else {
                Log.w(TAG, "API 데이터 전송 실패 → 큐에 저장");
                enqueue(wrapped);
            }
        });
    }
}