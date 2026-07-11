package com.example.novelpia_custom;

import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebView;

/**
 * 노벨피아 localStorage 데이터를 서버로 전송
 * JS fetch()로 직접 POST (Java 브릿지 불필요)
 */
public class DataCollector {
    private static final String TAG = "DataCollector";
    private static final String SERVER_URL = "http://100.102.179.114:8765/collect";
    private static long lastSent = 0;

    /**
     * 페이지 로드 후 localStorage 데이터 읽기 + setItem 후킹
     */
    public static void collect(WebView wv) {
        // 1) localStorage.setItem 후킹 — 변경될 때마다 fetch로 전송
        String hookJS =
            "(function() {" +
            "  var _orig = localStorage.setItem;" +
            "  var _url = '" + SERVER_URL + "';" +
            "  localStorage.setItem = function(k,v) {" +
            "    _orig.call(localStorage, k, v);" +
            "    if (k === 'userLastNovelData' || k === 'page_mark') {" +
            "      fetch(_url, {" +
            "        method: 'POST'," +
            "        headers: {'Content-Type': 'application/json'}," +
            "        body: JSON.stringify({" +
            "          userLastNovelData: localStorage.getItem('userLastNovelData')," +
            "          page_mark: localStorage.getItem('page_mark')," +
            "          timestamp: new Date().toISOString()," +
            "          url: window.location.href," +
            "          changedKey: k" +
            "        })" +
            "      }).catch(function(){});" +
            "    }" +
            "  };" +
            "})();";
        wv.evaluateJavascript(hookJS, null);

        // 2) 3초 후 기존 데이터 읽기
        wv.postDelayed(() -> {
            String readJS =
                "(function() {" +
                "  var ud = localStorage.getItem('userLastNovelData');" +
                "  var pm = localStorage.getItem('page_mark');" +
                "  if ((ud && ud !== 'null') || (pm && pm !== 'null')) {" +
                "    fetch('" + SERVER_URL + "', {" +
                "      method: 'POST'," +
                "      headers: {'Content-Type': 'application/json'}," +
                "      body: JSON.stringify({" +
                "        userLastNovelData: ud," +
                "        page_mark: pm," +
                "        timestamp: new Date().toISOString()," +
                "        url: window.location.href," +
                "        reason: 'timer'" +
                "      })" +
                "    }).catch(function(){});" +
                "  }" +
                "})();";
            wv.evaluateJavascript(readJS, null);
        }, 3000);
    }
}