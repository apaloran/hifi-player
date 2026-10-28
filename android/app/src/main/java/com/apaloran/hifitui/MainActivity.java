package com.apaloran.hifitui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private WebView webView;
    private PowerManager.WakeLock wakeLock;

    @SuppressLint({"SetJavaScriptEnabled", "WakelockTimeout"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor("#030604"));
            window.setNavigationBarColor(Color.parseColor("#030604"));
        }

        // Acquire partial CPU wakeLock so lossless MSE streaming & track transitions continue with screen off
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HiFiTUI::AudioStreamingWakeLock");
            wakeLock.acquire();
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#030604"));
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false); // Allow gapless auto-advance in background
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 14; HiFi-TUI) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        );

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient());

        // Intercept *.tidal.com / *.audio.tidal.com / *.manifest.tidal.com requests natively
        // to inject Origin: https://listen.tidal.com, Referer: https://listen.tidal.com/,
        // and Access-Control-Allow-Origin: * so MSE 24-bit HI_RES_LOSSLESS & 16-bit LOSSLESS
        // DASH .mp4 segments stream directly from Tidal CDN with zero CORS failures.
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null) return super.shouldInterceptRequest(view, request);

                String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
                boolean isTidalCdn = host.endsWith(".tidal.com") || host.equals("tidal.com");

                if (!isTidalCdn) {
                    return super.shouldInterceptRequest(view, request);
                }

                String method = request.getMethod() != null ? request.getMethod().toUpperCase() : "GET";

                // Handle CORS preflight OPTIONS immediately
                if ("OPTIONS".equals(method)) {
                    Map<String, String> preflightHeaders = buildPermissiveCorsHeaders();
                    return new WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        200,
                        "OK",
                        preflightHeaders,
                        new ByteArrayInputStream(new byte[0])
                    );
                }

                // Proxy GET / HEAD requests to *.tidal.com with spoofed Origin & Referer headers
                if ("GET".equals(method) || "HEAD".equals(method)) {
                    return proxyTidalCdnRequest(uri.toString(), method, request.getRequestHeaders());
                }

                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null) return false;
                String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
                if ("file".equals(scheme) || "data".equals(scheme) || "blob".equals(scheme)) {
                    return false;
                }
                // External links open in system browser
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                } catch (Exception ignored) {}
                return true;
            }
        });

        // Native Android DownloadManager integration for direct FLAC / M4A track downloads
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                if (url == null || url.startsWith("blob:")) {
                    Toast.makeText(MainActivity.this, "Preparing track download...", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                    req.addRequestHeader("Origin", "https://listen.tidal.com");
                    req.addRequestHeader("Referer", "https://listen.tidal.com/");
                    req.setMimeType(mimeType);
                    String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    req.setTitle(fileName);
                    req.setDescription("Downloading lossless audio via h.shy.cx");
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName);
                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm != null) {
                        dm.enqueue(req);
                        Toast.makeText(MainActivity.this, "Downloading to Music/" + fileName, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ignored) {}
                }
            }
        });

        // Load the bundled hifi-tui application
        webView.loadUrl("file:///android_asset/index.html");
    }

    private Map<String, String> buildPermissiveCorsHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, HEAD, POST, DELETE, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "*");
        headers.put("Access-Control-Expose-Headers", "Content-Length, Content-Range, Content-Type, Accept-Ranges, ETag");
        return headers;
    }

    private WebResourceResponse proxyTidalCdnRequest(String targetUrl, String method, Map<String, String> incomingHeaders) {
        try {
            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);

            // Forward Range and Accept headers for byte-range seeking and fMP4 segments
            if (incomingHeaders != null) {
                for (Map.Entry<String, String> entry : incomingHeaders.entrySet()) {
                    String k = entry.getKey();
                    if (k == null) continue;
                    String lower = k.toLowerCase();
                    if ("range".equals(lower) || "accept".equals(lower) || "user-agent".equals(lower)) {
                        conn.setRequestProperty(k, entry.getValue());
                    }
                }
            }

            // Inject official Tidal web player Origin & Referer headers at the native socket layer
            conn.setRequestProperty("Origin", "https://listen.tidal.com");
            conn.setRequestProperty("Referer", "https://listen.tidal.com/");

            int statusCode = conn.getResponseCode();
            String reasonPhrase = conn.getResponseMessage();
            if (reasonPhrase == null || reasonPhrase.isEmpty()) {
                reasonPhrase = (statusCode == 206) ? "Partial Content" : "OK";
            }

            String contentType = conn.getContentType();
            String mimeType = "audio/mp4";
            String encoding = "binary";
            if (contentType != null && !contentType.isEmpty()) {
                String[] parts = contentType.split(";");
                mimeType = parts[0].trim();
            }

            Map<String, String> responseHeaders = buildPermissiveCorsHeaders();
            for (Map.Entry<String, List<String>> header : conn.getHeaderFields().entrySet()) {
                String key = header.getKey();
                List<String> vals = header.getValue();
                if (key != null && vals != null && !vals.isEmpty()) {
                    String lowerKey = key.toLowerCase();
                    if (!lowerKey.startsWith("access-control-")) {
                        responseHeaders.put(key, vals.get(0));
                    }
                }
            }

            InputStream stream = (statusCode >= 400) ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) {
                stream = new ByteArrayInputStream(new byte[0]);
            }

            return new WebResourceResponse(mimeType, encoding, statusCode, reasonPhrase, responseHeaders, stream);
        } catch (Exception e) {
            Map<String, String> errHeaders = buildPermissiveCorsHeaders();
            return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                502,
                "Bad Gateway",
                errHeaders,
                new ByteArrayInputStream(("CDN proxy error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8))
            );
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Deliberately do NOT call webView.onPause() or webView.pauseTimers()
        // so background FLAC/MSE audio playback continues when the screen is off.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null) {
            // Close active modals/drawers or navigate back to Home inside the TUI first
            webView.evaluateJavascript(
                "(function() {" +
                "  const detail = document.getElementById('view-detail');" +
                "  if (detail && !detail.classList.contains('hidden')) { switchView('home'); return true; }" +
                "  const drawer = document.getElementById('sideDrawer');" +
                "  if (drawer && !drawer.classList.contains('hidden')) { document.getElementById('closeDrawerBtn').click(); return true; }" +
                "  return false;" +
                "})()",
                value -> {
                    if (!"true".equals(value)) {
                        moveTaskToBack(true);
                    }
                }
            );
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}