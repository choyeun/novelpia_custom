package com.example.novelpia_custom;

import android.util.Log;
import android.webkit.WebView;

/**
 * 노벨피아 데이터 수집기
 * - localStorage 데이터 수집
 * - XHR/fetch API 후킹
 * - mybook HTML 파싱 (페이지네이션 순회)
 */
public class DataCollector {
    private static final String TAG = "DataCollector";

    public static void collect(WebView wv) {
        // 1) localStorage.setItem 후킹 + XHR/fetch API 후킹
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
            "  var _origOpen = XMLHttpRequest.prototype.open;" +
            "  XMLHttpRequest.prototype.open = function(m, u) { this._url = u; return _origOpen.apply(this, arguments); };" +
            "  var _origSend = XMLHttpRequest.prototype.send;" +
            "  XMLHttpRequest.prototype.send = function(b) {" +
            "    this.addEventListener('load', function() {" +
            "      var u = this._url || '';" +
            "      if (u.indexOf('/proc/') >= 0 || u.indexOf('/api/') >= 0) {" +
            "        var r = this.responseText;" +
            "        if (r && r.length > 100 && r.length < 500000) {" +
            "          var p = JSON.stringify({url: u, body: r.substring(0, 10000)});" +
            "          if (window.Android) Android.sendApiData(p);" +
            "        }" +
            "      }" +
            "    });" +
            "    return _origSend.apply(this, arguments);" +
            "  };" +
            "})();";
        wv.evaluateJavascript(hookJS, null);

        // 2) 5초 후 기존 데이터 읽기 (페이지 완전 로딩 대기)
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
        }, 5000);
    }

    /** mybook/last_view 전체 페이지 순회하며 novel_no 수집 */
    public static void collectMybookNovels(WebView wv) {
        String js =
            "(function(){if(window.__mybookCollected)return;" +
            "window.__mybookCollected=true;" +
            "var all={};" +
            "document.querySelectorAll('a[href*=\"/novel/\"]').forEach(function(a){" +
            "var m=a.getAttribute('href').match(/\\/novel\\/(\\d+)/);" +
            "if(m)all[m[1]]=a.textContent.trim()});" +
            "var pages=[];" +
            "document.querySelectorAll('.page-link[href*=\"/date/\"]').forEach(function(e){" +
            "var p=parseInt(e.getAttribute('href').match(/\\/date\\/(\\d+)/)[1]);" +
            "if(!isNaN(p))pages.push(p)});" +
            "var max=Math.max.apply(null,pages);" +
            "(function np(p){if(p>max){" +
            "var r=Object.keys(all).map(function(n){return{novel_no:n,title:all[n]}});" +
            "var payload=JSON.stringify({source:'mybook_html',novels:r,count:r.length," +
            "timestamp:new Date().toISOString(),url:window.location.href});" +
            "if(window.Android)Android.sendApiData(payload);return;}" +
            "var x=new XMLHttpRequest();" +
            "x.open('GET','/mybook/last_view/0/date/'+p,true);" +
            "x.onload=function(){" +
            "var d=new DOMParser().parseFromString(x.responseText,'text/html');" +
            "d.querySelectorAll('a[href*=\"/novel/\"]').forEach(function(a){" +
            "var m=a.getAttribute('href').match(/\\/novel\\/(\\d+)/);" +
            "if(m)all[m[1]]=a.textContent.trim()});" +
            "np(p+1)};x.send()})(2)})();";
        wv.evaluateJavascript(js, null);
    }
}