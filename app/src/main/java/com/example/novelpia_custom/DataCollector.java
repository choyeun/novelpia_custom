package com.example.novelpia_custom;

import android.util.Log;
import android.webkit.WebView;

/**
 * 노벨피아 데이터 수집기
 * - localStorage 데이터 수집
 * - XHR/fetch API 후킹
 * - mybook HTML 파싱 (페이지네이션 순회)
 * - document.cookie 수집 (LOGINKEY)
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

            // 3) document.cookie 수집 (최초 1회만)
            String cookieJS =
                "(function() {" +
                "  if (window.__cookieCollected) return;" +
                "  window.__cookieCollected = true;" +
                "  var c = document.cookie || '';" +
                "  if (c && c.indexOf('LOGINKEY') >= 0) {" +
                "    var payload = JSON.stringify({" +
                "      source: 'cookie'," +
                "      cookie: c," +
                "      timestamp: new Date().toISOString()," +
                "      url: window.location.href" +
                "    });" +
                "    if (window.Android) Android.sendData(payload);" +
                "  }" +
                "})();";
            wv.evaluateJavascript(cookieJS, null);
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

    /**
     * Filter emoticon-only comments with 0 votes (viewer comment section).
     * Installs a MutationObserver on the comment area (#comment_load) to catch
     * dynamically-loaded comments, then hides those that contain ONLY emoticon
     * images (no text) AND have zero vote count.
     */
    public static void filterZeroVoteEmoticonComments(WebView wv) {
        String js =
            "(function(){" +
            "if(window.__commentFilterDone)return;" +
            "window.__commentFilterDone=true;" +
            "function filterComment(comment){" +
            "  if(comment.__filtered)return;" +
            "  comment.__filtered=true;" +
            "  " +
            "  // vote count - try multiple selectors" +
            "  var voteEl = comment.querySelector('.c_vote_num, .comment_vote_num, .vote_num, .c_vote, [class*=vote]');" +
            "  var voteText = voteEl ? voteEl.textContent.trim() : '';" +
            "  var vote = parseInt(voteText.replace(/[^0-9]/g,'')) || 0;" +
            "  if(vote > 0) return;" +
            "  " +
            "  // comment body - try multiple selectors" +
            "  var body = comment.querySelector('.comment_body, .c_body, .comment_cont, .comment_text, [class*=body], [class*=cont]');" +
            "  if(!body) return;" +
            "  " +
            "  // emoticon-only: no text, only imgs" +
            "  var text = body.textContent.trim();" +
            "  if(text.length > 0) return;" +
            "  " +
            "  var imgs = body.querySelectorAll('img');" +
            "  if(imgs.length === 0) return;" +
            "  " +
            "  // hide it" +
            "  comment.style.display = 'none';" +
            "}" +
            "function scanComments(){" +
            "  var container = document.getElementById('comment_load');" +
            "  if(!container){" +
            "    container = document.querySelector('.comment_list, .comment_area, .comment-box, [class*=comment]');" +
            "  }" +
            "  if(!container) return;" +
            "  " +
            "  var comments = container.querySelectorAll('.comment_box, .comment-item, .commentItem, .comment, [class*=comment_]:not(#comment_load)');" +
            "  comments.forEach(filterComment);" +
            "}" +
            "// 1) scan already-loaded comments" +
            "scanComments();" +
            "// 2) MutationObserver for dynamic loading" +
            "var target = document.getElementById('comment_load') || document.querySelector('.comment_list, .comment_area, [class*=comment]');" +
            "if(target){" +
            "  var obs = new MutationObserver(function(){scanComments();});" +
            "  obs.observe(target, {childList:true, subtree:true});" +
            "}" +
            "})();";
        wv.evaluateJavascript(js, null);
    }
}