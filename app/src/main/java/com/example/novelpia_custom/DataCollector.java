package com.example.novelpia_custom;

import android.util.Log;
import android.webkit.WebView;

/**
 * 노벨피아 localStorage 데이터를 서버로 전송
 * Android NativeBridge를 통해 네이티브 HTTP POST 사용
 * - 전송 실패 시 SharedPreferences에 자동 큐잉
 * - 네트워크 복구 시 큐 일괄 전송
 */
public class DataCollector {
    private static final String TAG = "DataCollector";
    private static final long MIN_INTERVAL_MS = 3000; // 최소 전송 간격 (중복 방지)

    /**
     * 페이지 로드 후 localStorage 데이터 읽기 + setItem 후킹
     * 모든 POST는 Android.sendData()를 통해 네이티브에서 처리
     */
    public static void collect(WebView wv) {
        // 1) localStorage.setItem 후킹 — 변경될 때마다 Android.sendData() 호출
        String hookJS =
            "(function() {" +
            "  if (window.__dataCollected) return;" +
            "  window.__dataCollected = true;" +
            "  var _orig = localStorage.setItem;" +
            "  localStorage.setItem = function(k,v) {" +
            "    _orig.call(localStorage, k, v);" +
            "    if (k === 'userLastNovelData' || k === 'page_mark') {" +
            "      var payload = JSON.stringify({" +
            "        userLastNovelData: localStorage.getItem('userLastNovelData')," +
            "        page_mark: localStorage.getItem('page_mark')," +
            "        timestamp: new Date().toISOString()," +
            "        url: window.location.href," +
            "        changedKey: k" +
            "      });" +
            "      if (window.Android) Android.sendData(payload);" +
            "    }" +
            "  };" +
            "})();";
        wv.evaluateJavascript(hookJS, null);

        // 2) 3초 후 기존 데이터 읽기 (첫 수집)
        wv.postDelayed(() -> {
            String readJS =
                "(function() {" +
                "  var ud = localStorage.getItem('userLastNovelData');" +
                "  var pm = localStorage.getItem('page_mark');" +
                "  if ((ud && ud !== 'null') || (pm && pm !== 'null')) {" +
                "    var payload = JSON.stringify({" +
                "      userLastNovelData: ud," +
                "      page_mark: pm," +
                "      timestamp: new Date().toISOString()," +
                "      url: window.location.href," +
                "      reason: 'timer'" +
                "    });" +
                "    if (window.Android) Android.sendData(payload);" +
                "  }" +
                "})();";
            wv.evaluateJavascript(readJS, null);
        }, 3000);
    }
}