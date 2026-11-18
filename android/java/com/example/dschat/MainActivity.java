package com.example.dschat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * A thin shell that serves the bundled chat-demo web assets from a virtual
 * https origin, so that localStorage / IndexedDB work under a secure context.
 */
public class MainActivity extends Activity {

    private static final String ASSET_HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + ASSET_HOST + "/";
    private static final String ASSET_PREFIX = "web";
    private static final int REQ_FILE_CHOOSER = 1001;
    private static final int REQ_AUDIO_PERMISSION = 1002;

    private WebView web;
    private ValueCallback<Uri[]> pendingFileCallback;
    private PermissionRequest pendingAudioRequest;

    /** 页面把当前背景色与深浅色状态回传给宿主，用来同步状态栏/导航栏，避免出现白边。 */
    public class ChromeBridge {
        @JavascriptInterface
        public void setChrome(String cssColor, boolean dark) {
            final int color = parseCssColor(cssColor, dark);
            runOnUiThread(() -> applyChrome(color, dark));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        if (android.os.Build.VERSION.SDK_INT >= 19) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        web.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setUserAgentString(s.getUserAgentString() + " DeepSeekChatApp/1.0");
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            s.setSafeBrowsingEnabled(false);
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null || !ASSET_HOST.equals(uri.getHost())) {
                    return null;
                }
                String path = uri.getPath();
                if (path == null || path.isEmpty() || "/".equals(path)) {
                    path = "/index.html";
                }
                InputStream in = openAsset(path);
                if (in == null && !path.contains(".")) {
                    // SPA route fallback
                    in = openAsset("/index.html");
                }
                if (in == null) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                            null, new ByteArrayInputStream(new byte[0]));
                }
                WebResourceResponse resp = new WebResourceResponse(mimeOf(path), encodingOf(path), in);
                return resp;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri != null && !ASSET_HOST.equals(uri.getHost())) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        return true;
                    } catch (Exception ignored) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(CHROME_SYNC_JS, null);
            }
        });

        web.addJavascriptInterface(new ChromeBridge(), "ChatDemoBridge");

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                if (cm == null) {
                    return false;
                }
                Log.d("ChatDemo", cm.message() + " @ " + cm.sourceId() + ":" + cm.lineNumber());
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean wantsAudio = false;
                    for (String r : request.getResources()) {
                        if ("android.webkit.resource.AUDIO_CAPTURE".equals(r)) {
                            wantsAudio = true;
                        }
                    }
                    if (wantsAudio && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                            != PackageManager.PERMISSION_GRANTED) {
                        pendingAudioRequest = request;
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                                REQ_AUDIO_PERMISSION);
                    } else {
                        request.grant(request.getResources());
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (pendingFileCallback != null) {
                    pendingFileCallback.onReceiveValue(null);
                }
                pendingFileCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, REQ_FILE_CHOOSER);
                    return true;
                } catch (Exception e) {
                    pendingFileCallback = null;
                    return false;
                }
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(START_URL);
        }
    }

    private InputStream openAsset(String path) {
        try {
            return getAssets().open(ASSET_PREFIX + path);
        } catch (IOException e) {
            return null;
        }
    }

    private String mimeOf(String path) {
        String p = path.toLowerCase(Locale.US);
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html";
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".ico")) return "image/x-icon";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".woff")) return "font/woff";
        if (p.endsWith(".ttf")) return "font/ttf";
        if (p.endsWith(".txt") || p.endsWith(".md")) return "text/plain";
        if (p.endsWith(".wasm")) return "application/wasm";
        return "application/octet-stream";
    }

    private String encodingOf(String path) {
        String mime = mimeOf(path);
        if (mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json")
                || mime.contains("svg") || mime.contains("xml")) {
            return "utf-8";
        }
        return null;
    }

    /** 把页面背景色/深浅色同步到状态栏、导航栏与窗口背景，消除顶部的白边。 */
    private void applyChrome(int color, boolean dark) {
        try {
            getWindow().setStatusBarColor(color);
            getWindow().setNavigationBarColor(color);
            getWindow().setBackgroundDrawable(new ColorDrawable(color));

            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (dark) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                if (dark) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            decor.setSystemUiVisibility(flags);
        } catch (Throwable t) {
            Log.w("ChatDemo", "applyChrome failed", t);
        }
    }

    /** 接受 rgb(r, g, b) / rgba(...) / #rrggbb；无法解析时按深浅色给默认值。 */
    private int parseCssColor(String css, boolean dark) {
        int fallback = dark ? Color.parseColor("#191919") : Color.WHITE;
        if (css == null) {
            return fallback;
        }
        String s = css.trim();
        if (s.startsWith("#")) {
            try {
                return Color.parseColor(s);
            } catch (Throwable ignored) {
                return fallback;
            }
        }
        int open = s.indexOf('(');
        int close = s.indexOf(')');
        if (open < 0 || close <= open) {
            return fallback;
        }
        String[] parts = s.substring(open + 1, close).split(",");
        if (parts.length < 3) {
            return fallback;
        }
        try {
            int r = (int) Float.parseFloat(parts[0].trim());
            int g = (int) Float.parseFloat(parts[1].trim());
            int b = (int) Float.parseFloat(parts[2].trim());
            float a = 1f;
            if (parts.length >= 4) {
                a = Float.parseFloat(parts[3].trim());
            }
            if (a < 0.2f) {
                return fallback;
            }
            return Color.rgb(
                    Math.max(0, Math.min(255, r)),
                    Math.max(0, Math.min(255, g)),
                    Math.max(0, Math.min(255, b)));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static final String CHROME_SYNC_JS =
            "(function(){"
            + "if(window.__dsChromeSync){return;}window.__dsChromeSync=true;"
            + "var last='';"
            + "function sync(){try{"
            + "var st=getComputedStyle(document.body);"
            + "var bg=st.backgroundColor||'';"
            + "var dark=document.body.classList.contains('dark');"
            + "var key=bg+'|'+dark;"
            + "if(key===last){return;}last=key;"
            + "if(window.ChatDemoBridge&&window.ChatDemoBridge.setChrome){"
            + "window.ChatDemoBridge.setChrome(bg,dark);}"
            + "}catch(e){}}"
            + "sync();"
            + "try{new MutationObserver(sync).observe(document.body,{attributes:true,attributeFilter:['class','style']});}catch(e){}"
            + "setInterval(sync,1200);"
            + "})();";

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_AUDIO_PERMISSION && pendingAudioRequest != null) {
            PermissionRequest req = pendingAudioRequest;
            pendingAudioRequest = null;
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                req.grant(req.getResources());
            } else {
                req.deny();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_CHOOSER && pendingFileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    result = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        result[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }
            pendingFileCallback.onReceiveValue(result);
            pendingFileCallback = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
