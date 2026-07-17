package com.example.novelpia_custom;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayDeque;
import java.util.Deque;

public class MainActivity extends AppCompatActivity {
    // 웹뷰
    private WebView wvViewer;
    private WebView wvSearch;
    private WebView wvMain;
    private WebView wvBook;
    private WebView wvNovel;
    private String wvUserAgent = null;
    private DiskCache diskCache;
    private ImageButton btnGo;
    private BottomNavigationView bottomNav;
    private volatile boolean isBookLongPress = false;
    private int navDepth = 0;
    // 페이지 이동용
    private final Deque<Byte> backoffstack = new ArrayDeque<>();
    private static final byte MAIN_INDEX = 0b0001;
    private static final byte SEARCH_INDEX = 0b0010;
    private static final byte VIEWER_INDEX = 0b0011;
    private static final byte BOOK_INDEX = 0b0100;
    private static final byte NOVEL_INDEX = 0b0101;
    private byte current = MAIN_INDEX;
    // 현재 참조중인 페이지 링크 저장용
    private String searchString = START_URL + SEARCH_SUF;
    private String viewerString = "";
    private String novelString = "";
    // url 식별용
    private static final String START_URL  = "https://novelpia.com/";
    private static final String SEARCH_SUF = "search";
    private static final String RANKING_SUF = "ranking";
    private static final String VIEWER_SUF = "viewer";
    private static final String BOOK_SUF = "mybook";
    private static final String NOVEL_SUF = "novel/"; // novelpia 중복
    // restore keys
    private static final String KEY_CURRENT = "key_current";
    private static final String KEY_STACK = "key_stack";
    private static final String KEY_MAIN_STATE = "key_wv_main";
    private static final String KEY_SEARCH_STATE = "key_wv_search";
    private static final String KEY_VIEWER_STATE = "key_wv_viewer";
    private static final String KEY_BOOK_STATE = "key_wv_book";
    private static final String KEY_NOVEL_STATE = "key_wv_novel";
    private static final String PREF_NAME = "novelpia_pref";
    private static final String KEY_LAST_READ_NOVEL_URL = "last_read_novel_url";
    // Toast 핸들러
    private final Handler toastHandler = new Handler(Looper.getMainLooper());
    // 메인코드 =====================================================================================
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);

        diskCache = new DiskCache(getCacheDir());

        setContentView(R.layout.activity_main);

        wvMain = findViewById(R.id.wvMain);
        wvViewer = findViewById(R.id.wvReader);
        wvSearch = findViewById(R.id.wvSearch);
        wvBook = findViewById(R.id.wvBook);
        wvNovel = findViewById(R.id.wvNovel);
        btnGo = findViewById(R.id.btnGo);
        bottomNav = findViewById(R.id.bottomNav);

        setupWebView(wvMain);
        setupWebView(wvViewer);
        setupWebView(wvSearch);
        setupWebView(wvBook);
        setupWebView(wvNovel);

        // 팝업 허용
        WebSettings s = wvMain.getSettings();
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);

        // 뒤로가기 콜백 등록
                getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        handleBackPressed();
                    }
                });

                // 하단 내비게이션
                bottomNav.setOnItemSelectedListener(item -> {
                    if (navDepth > 0) return true;
                    navDepth++;
                    int id = item.getItemId();
                    if (id == R.id.nav_main) {
                        openMain(START_URL);
                    } else if (id == R.id.nav_ranking) {
                        openSearch(START_URL + RANKING_SUF);
                    } else if (id == R.id.nav_search) {
                        openSearch(START_URL + SEARCH_SUF);
                    } else if (id == R.id.nav_book) {
                        if (isBookLongPress) {
                            isBookLongPress = false;
                        } else {
                            openBook(START_URL + "mybook/last_view");
                        }
                    } else if (id == R.id.nav_settings) {
                        showSettingsDialog();
                    }
                    navDepth--;
                    return true;
                });

                // 서재 롱클릭 → 선호작 (onTouch로 long press 감지)
                bottomNav.post(() -> {
                    try {
                        if (!(bottomNav.getChildAt(0) instanceof android.view.ViewGroup)) return;
                        android.view.ViewGroup vg = (android.view.ViewGroup) bottomNav.getChildAt(0);
                        for (int i = 0; i < vg.getChildCount(); i++) {
                            View child = vg.getChildAt(i);
                            if (child != null && child.getId() == R.id.nav_book) {
                                child.setOnLongClickListener(v -> {
                                    isBookLongPress = true;
                                    openBook(START_URL + "mybook/like");
                                    handleToast("선호작");
                                    return true;
                                });
                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                });

                // 새로고침 버튼
                ImageButton btnRefresh = findViewById(R.id.btnRefresh);
                if (btnRefresh != null) {
                    btnRefresh.setOnClickListener(v -> {
                        WebView wv = classify(current);
                        if (wv != null) wv.reload();
                    });
                }

                // 현재 링크 복사 기능
        WebView[] webViews = {wvViewer, wvNovel};
        for(WebView wv : webViews) {
            wv.setOnLongClickListener(w -> {
                if (wv.getVisibility() != View.VISIBLE) return false;

                String url = wv.getUrl();
                if(url == null || url.isEmpty()) return false;

                ClipboardManager cm1 = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm1.setPrimaryClip(ClipData.newPlainText("url", cutUrl(url)));

                handleToast("링크 복사됨");
                return true;
            });
        }

        // 초기 로드
        btnGo.setOnClickListener(v -> handleGoDialog());
        boolean restored = false;
        if(savedInstanceState != null) {
            restored = restoreAll(savedInstanceState);
        }
        if(!restored) {
            wvMain.loadUrl(START_URL);
            wvBook.loadUrl(START_URL + "mybook/last_view");
            wvSearch.loadUrl(START_URL + SEARCH_SUF);
            swapView(BOOK_INDEX, false); // NOTE: 앱 시작 시 자동으로 최근기록 열람
        }

        // 백그라운드에서 업데이트 확인
        checkForUpdate();
    }

    // ─── 자동 업데이트 ────────────────────────────────────
    /** 백그라운드 스레드에서 GitHub 최신 버전 확인 */
    private void checkForUpdate() {
        new Thread(() -> {
            final UpdateChecker.UpdateInfo info = UpdateChecker.check(BuildConfig.VERSION_NAME);
            if (info.hasUpdate) {
                runOnUiThread(() -> showUpdateDialog(info));
            }
        }).start();
    }

    /** 업데이트 다이얼로그 표시 */
    private void showUpdateDialog(final UpdateChecker.UpdateInfo info) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("📲 업데이트 가능")
                .setMessage(String.format(
                        "새 버전 %s이(가) 있습니다.\n\n현재 버전: %s\nAPK 크기: %s",
                        info.latestVersion,
                        BuildConfig.VERSION_NAME,
                        info.apkSize > 0 ? String.format("%.1fMB", info.apkSize / 1024f / 1024f) : "?"
                ))
                .setPositiveButton("지금 업데이트", (DialogInterface dialog, int which) -> {
                    downloadAndInstall(info);
                })
                .setNegativeButton("나중에", null)
                .show();
    }

    private void showSettingsDialog() {
        String[] items = {"🔄 현재 페이지 새로고침", "🗑️ 캐시 초기화", "📲 업데이트 확인", "ℹ️ 앱 정보"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("⚙️ 설정")
                .setItems(items, (DialogInterface dialog, int which) -> {
                    if (which == 0) {
                        WebView wv = classify(current);
                        if (wv != null) wv.reload();
                    } else if (which == 1) {
                        diskCache.clear();
                        handleToast("캐시 초기화 완료");
                    } else if (which == 2) {
                        checkForUpdate();
                    } else if (which == 3) {
                        showAboutDialog();
                    }
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void showAboutDialog() {
        String msg = "📚 노벨피아 커스텀\n\n"
                + "버전: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                + "\n"
                + "GitHub: github.com/choyeun/novelpia_custom\n"
                + "\n"
                + "기능:\n"
                + "• 읽은 기록 자동 수집\n"
                + "• 자동 업데이트\n"
                + "• 이미지 캐싱 (데이터 절약)\n"
                + "• 볼륨키 페이지 이동";
        new MaterialAlertDialogBuilder(this)
                .setTitle("ℹ️ 정보")
                .setMessage(msg)
                .setPositiveButton("닫기", null)
                .show();
    }

    /** APK 다운로드 + 설치 (백그라운드 스레드) */
    private void downloadAndInstall(final UpdateChecker.UpdateInfo info) {
        // 진행률 다이얼로그
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("📲 업데이트 다운로드");
        pd.setMessage("APK 다운로드 중...");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setCancelable(false);
        pd.show();

        new Thread(() -> {
            UpdateInstaller.downloadAndInstall(info.downloadUrl, getApplicationContext(),
                    new UpdateInstaller.DownloadCallback() {
                        @Override
                        public void onProgress(int percent) {
                            runOnUiThread(() -> {
                                pd.setProgress(percent);
                                pd.setMessage("APK 다운로드 중... " + percent + "%");
                            });
                        }

                        @Override
                        public void onComplete(boolean success, String message) {
                            runOnUiThread(() -> {
                                pd.dismiss();
                                String msg = success ? "설치를 시작합니다." : "업데이트 실패: " + message;
                                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                            });
                        }
                    });
        }).start();
    }

    // 웹뷰 초기화
    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(WebView wv) {
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // 구글 로그인 문제 수정
        if (wvUserAgent == null) {
            wvUserAgent = s.getUserAgentString().replace("; wv", "");
        }
        s.setUserAgentString(wvUserAgent);
        // NativeBridge 등록 (오프라인 큐 + 작품 정보 수집)
        wv.addJavascriptInterface(new NativeBridge(this), "Android");
        // 링크 이동 블락
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // NOTE: 노벨피아는 로그인 요구를 파라미터로 넘기는데, 아래와 같은 로직을 사용하면
                //       로그인 요구 창이 열래는 대신 단순히 메인 창으로 돌아감
                // NOTE: 파라미터 분리 로직을 '?sid='로 명시하면 해결되지만 어차피 큰 차이는 없어서 냅둠
                String url = cutUrl(request.getUrl().toString());
                byte target = classify(url);
                Log.d("stack", backoffstack.size() + "**" + toRead(current) + "->" + toRead(target));
                // 뷰어 웹뷰로 이동하는 경우
                if (target == VIEWER_INDEX) setLastUrl(url);
                // 동일 웹뷰 내에서 이동한 경우
                if (target == current) {
                    String current_url = view.getUrl();
                    if (target != VIEWER_INDEX && current_url != null && !url.equals(cutUrl(current_url))) {
                        backoffstack.push(current);
                    }
                    return false;
                }

                handleUrl(url);
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString();
                String ext = CachingWebViewClient.getExtension(reqUrl).toLowerCase();

                // 이미지/폰트만 캐싱
                if (!CachingWebViewClient.isCacheableExtension(ext)) {
                    return null;
                }

                // 캐시 hit
                DiskCache.CacheEntry cached = diskCache.get(reqUrl);
                if (cached != null) {
                    Log.d("WebViewCache", "✅ 캐시 hit: " + CachingWebViewClient.truncateUrl(reqUrl, 60));
                    return new WebResourceResponse(
                            cached.contentType,
                            "",  // HttpURLConnection이 이미 압축 해제
                            new java.io.ByteArrayInputStream(cached.data)
                    );
                }

                // 캐시 miss → 직접 받아서 저장
                Log.d("WebViewCache", "⬇ 캐시 miss: " + CachingWebViewClient.truncateUrl(reqUrl, 60));
                try {
                    java.net.HttpURLConnection conn =
                            (java.net.HttpURLConnection) new java.net.URL(reqUrl).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(15000);
                    conn.setInstanceFollowRedirects(true);

                    if (conn.getResponseCode() != 200) {
                        conn.disconnect();
                        return null;
                    }

                    String contentType = conn.getContentType();
                    if (contentType == null) contentType = CachingWebViewClient.mimeFromExtension(ext);

                    byte[] data = CachingWebViewClient.readAllBytes(conn.getInputStream());
                    conn.disconnect();

                    diskCache.put(reqUrl, data, contentType, "");
                    return new WebResourceResponse(contentType, "", new java.io.ByteArrayInputStream(data));

                } catch (Exception e) {
                    Log.w("WebViewCache", "캐시 miss 처리 실패: " + e.getMessage());
                    return null;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 모든 WebView에서 localStorage 데이터 수집
                DataCollector.collect(view);
                // mybook 페이지면 HTML에서 novel_no 전체 추출
                if (url != null && url.contains("/mybook")) {
                    DataCollector.collectMybookNovels(view);
                }
                // 노벨피아 하단바 숨기기 (앱 내비와 중복)
                view.evaluateJavascript(
                    "(function(){" +
                    "var e=document.querySelector('.bt-nv-wrapper');" +
                    "if(e){e.style.display='none';}" +
                    "var f=document.querySelector('footer');" +
                    "if(f){f.style.display='none';}" +
                    "})();", null);
            }
        });
        // 얼럿창 처리
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("알림")
                        .setMessage(message)
                        .setPositiveButton("OK", (dialog, which) -> result.confirm())
                        .setOnCancelListener(dialog -> result.cancel())
                        .show();
                return true;
            }
            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("확인")
                        .setMessage(message)
                        .setPositiveButton("OK", (dialog, which) -> result.confirm())
                        .setNegativeButton("Cancel", (dialog, which) -> result.cancel())
                        .setOnCancelListener(dialog -> result.cancel())
                        .show();
                return true;
            }
        });
    }
    // 라우팅 로직 ==================================================================================
    private void swapView(byte index, boolean isbackoff) { //0b0000
        Log.d("stack", "open: "+toRead(index));
        wvMain.setVisibility(View.GONE);
        wvSearch.setVisibility(View.GONE);
        wvViewer.setVisibility(View.GONE);
        wvBook.setVisibility(View.GONE);
        wvNovel.setVisibility(View.GONE);
        // viewer 웹뷰에서 바로가기 버튼 숨기기
        if(index != VIEWER_INDEX) btnGo.setVisibility(View.VISIBLE);
        else btnGo.setVisibility(View.GONE);

        classify(index).setVisibility(View.VISIBLE);
        // viewer 웹뷰가 아니거나 되돌리기 작업이 아닌 경우 스택에 삽입
        if((current != VIEWER_INDEX) && (!isbackoff)) backoffstack.push(current);
        current = index;
    }
    private void openBook(String url) {
        wvBook.loadUrl(url);
        swapView(BOOK_INDEX, false);
        syncNav(R.id.nav_book);
    }
    private void openMain(String url) {
        wvMain.loadUrl(url);
        swapView(MAIN_INDEX, false);
        if(url.equals(START_URL)) backoffstack.clear();
        syncNav(R.id.nav_main);
    }
    private void openViewer(String url) {
        if (!viewerString.equals(url)) wvViewer.loadUrl(url);
        swapView(VIEWER_INDEX, false);
        viewerString = url;
    }
    private void openSearch(String url) {
        if (!searchString.equals(url)) wvSearch.loadUrl(url);
        swapView(SEARCH_INDEX, false);
        searchString = url;
        if (url.contains(RANKING_SUF)) {
            syncNav(R.id.nav_ranking);
        } else {
            syncNav(R.id.nav_search);
        }
    }
    private void openNovel(String url) {
        if(!novelString.equals(url)) wvNovel.loadUrl(url);

        swapView(NOVEL_INDEX, false);
        novelString = url;
        // 소설 상세는 하단 내비 선택 해제 (메인 유지)
    }
    // 핸들러 ======================================================================================
    private void handleUrl(String url) {
        // novel에서 넘어가는 경우 해당 웹뷰의 로딩 화면 제거
        if(current == NOVEL_INDEX) wvNovel.loadUrl(novelString);

        if (url.contains(SEARCH_SUF)) openSearch(url);
        else if (url.contains(VIEWER_SUF)) openViewer(url);
        else if (url.contains(BOOK_SUF)) openBook(url);
        else if (url.contains(NOVEL_SUF)) openNovel(url);
        else openMain(url);
    }
    public void handleBackPressed() {
        // 종료
        if(backoffstack.isEmpty()) {
            finish();
            return;
        }

        byte backoff = backoffstack.pop();
        WebView wv = classify(current);

        if (current == backoff || current == MAIN_INDEX) {
            if (wv.canGoBack()) wv.goBack();
        }
        if (current != backoff) {
            swapView(backoff, true);
        }
    }
    private void handleGoDialog() {
        String clip = getLatestClipboard();
        final String clipUrl = clip != null ? getNovelpiaUrl(clip) : null;

        EditText et = new EditText(this);
        et.setHint("링크를 입력하세요");
        if(clipUrl != null) et.setHint(clipUrl);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        new AlertDialog.Builder(this)
                .setTitle("주소로 이동")
                .setView(et)
                .setPositiveButton("이동", (d, w) -> {
                    String raw = et.getText().toString();
                    String url = getNovelpiaUrl(raw);
                    if(url != null) handleUrl(url);
                    else if (clipUrl != null) handleUrl(clipUrl);
                })
                .setNeutralButton("마지막 읽은 소설로 이동", (d, w) -> {
                    String lastUrl = getLastUrl();
                    if (lastUrl != null) handleUrl(lastUrl);
                    else handleToast("마지막 읽은 소설이 없습니다.");
                })
                .show();
    }
    // utils ======================================================================================
    private String getNovelpiaUrl(String raw) {
        String[] tokens = raw.split("\\s+");

        for (String token : tokens) if (token.contains("novelpia.com"))
            return cutUrl(token);
        return null;
    }
    private String cutUrl(String url) {
        return url.split("\\?")[0];
    }
    private void handleToast(String msg) {
        Toast myToast = Toast.makeText(this.getApplicationContext(),msg, Toast.LENGTH_SHORT);
        myToast.show();
        toastHandler.postDelayed(myToast::cancel, 500);
    }

    /** 하단 내비 선택 (재귀 방지) */
    private void syncNav(int itemId) {
        navDepth++;
        bottomNav.setSelectedItemId(itemId);
        navDepth--;
    }
    private String toRead(byte index) {
        if(index == SEARCH_INDEX) return "search";
        if(index == VIEWER_INDEX) return "viewer";
        if(index == BOOK_INDEX) return "book";
        if(index == NOVEL_INDEX) return "novel";
        if(index == MAIN_INDEX) return "main";
        throw new AssertionError();
    }
    private byte classify(String url) {
        if (url.contains(SEARCH_SUF)) return SEARCH_INDEX;
        if (url.contains(VIEWER_SUF)) return VIEWER_INDEX;
        if (url.contains(BOOK_SUF)) return BOOK_INDEX;
        if (url.contains(NOVEL_SUF)) return NOVEL_INDEX;
        return MAIN_INDEX;
    }
    private WebView classify(byte index) {
        if(index == SEARCH_INDEX) return wvSearch;
        if(index == VIEWER_INDEX) return wvViewer;
        if(index == BOOK_INDEX) return wvBook;
        if(index == NOVEL_INDEX) return wvNovel;
        if(index == MAIN_INDEX) return wvMain;
        throw new AssertionError();
    }
    private String getLatestClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        if(cm == null) return null;
        if(!cm.hasPrimaryClip()) return null;

        ClipData clip = cm.getPrimaryClip();
        if(clip == null || clip.getItemCount() == 0) return null;

        ClipData.Item item = clip.getItemAt(0);
        CharSequence text = item.getText();
        if(text == null) return null;

        return text.toString();
    }
    // 볼륨 up down 키 제어
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();

            if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (current != VIEWER_INDEX) {
                    return super.dispatchKeyEvent(event);
                }
                WebView wv = classify(current);

                if (code == KeyEvent.KEYCODE_VOLUME_UP) {
                    // 위 + 왼쪽
                    sendArrowKeyViaJs(wv, "ArrowUp");
                    sendArrowKeyViaJs(wv, "ArrowUp");
                    sendArrowKeyViaJs(wv, "ArrowLeft");
                } else {
                    // 아래 + 오른쪽
                    sendArrowKeyViaJs(wv, "ArrowDown");
                    sendArrowKeyViaJs(wv, "ArrowDown");
                    sendArrowKeyViaJs(wv, "ArrowRight");
                }

                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    private void sendArrowKeyViaJs(WebView wv, String key) {
        // "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight"
        String js =
                "(function(){"
                        + "  var opt = {key:'" + key + "', code:'" + key + "', bubbles:true, cancelable:true};"
                        + "  document.dispatchEvent(new KeyboardEvent('keydown', opt));"
                        + "  document.dispatchEvent(new KeyboardEvent('keyup', opt));"
                        + "  window.dispatchEvent(new KeyboardEvent('keydown', opt));"
                        + "  window.dispatchEvent(new KeyboardEvent('keyup', opt));"
                        + "})();";
        wv.evaluateJavascript(js, null);
    }
    // 마지막 읽은 소설 url
    private void setLastUrl(String url) {
        Log.d("cache", url);
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        sp.edit().putString(KEY_LAST_READ_NOVEL_URL, url).apply();
    }
    private String getLastUrl() {
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        return sp.getString(KEY_LAST_READ_NOVEL_URL, null);
    }
    // restore =====================================================================================
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putByte(KEY_CURRENT, current);

        // backoffstack 저장
        int size = backoffstack.size();
        byte[] arr = new byte[size];
        int i = 0;
        for (Byte b : backoffstack) {
            arr[i++] = (byte) (b & 0xFF);
        }
        outState.putByteArray(KEY_STACK, arr);

        // WebView 상태 저장
        Bundle bMain = new Bundle();
        Bundle bSearch = new Bundle();
        Bundle bViewer = new Bundle();
        Bundle bBook = new Bundle();
        Bundle bNovel = new Bundle();

        wvMain.saveState(bMain);
        wvSearch.saveState(bSearch);
        wvViewer.saveState(bViewer);
        wvBook.saveState(bBook);
        wvNovel.saveState(bNovel);

        outState.putBundle(KEY_MAIN_STATE, bMain);
        outState.putBundle(KEY_SEARCH_STATE, bSearch);
        outState.putBundle(KEY_VIEWER_STATE, bViewer);
        outState.putBundle(KEY_BOOK_STATE, bBook);
        outState.putBundle(KEY_NOVEL_STATE, bNovel);
    }
    private boolean restoreAll(Bundle state) {
        try {
            // backoffstack 구성
            byte[] arr = state.getByteArray(KEY_STACK);
            backoffstack.clear();
            if (arr != null) {
                for (int i = arr.length - 1; i >= 0; i--) {
                    backoffstack.push((byte) (arr[i] & 0xFF));
                }
            }

            Bundle bMain = state.getBundle(KEY_MAIN_STATE);
            Bundle bSearch = state.getBundle(KEY_SEARCH_STATE);
            Bundle bViewer = state.getBundle(KEY_VIEWER_STATE);
            Bundle bBook = state.getBundle(KEY_BOOK_STATE);
            Bundle bNovel = state.getBundle(KEY_NOVEL_STATE);

            if (bMain != null) wvMain.restoreState(bMain);
            if (bSearch != null) wvSearch.restoreState(bSearch);
            if (bViewer != null) wvViewer.restoreState(bViewer);
            if (bBook != null) wvBook.restoreState(bBook);
            if (bNovel != null) wvNovel.restoreState(bNovel);

            // 현재 화면 띄우기
            current = state.getByte(KEY_CURRENT, MAIN_INDEX);
            swapView(current, true);

            return true;
        } catch (Exception e) {
            return false;
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        wvMain.onPause();
        wvSearch.onPause();
        wvViewer.onPause();
        wvBook.onPause();
        wvNovel.onPause();
    }
    @Override
    protected void onResume() {
        super.onResume();
        wvMain.onResume();
        wvSearch.onResume();
        wvViewer.onResume();
        wvBook.onResume();
        wvNovel.onResume();
    }
}