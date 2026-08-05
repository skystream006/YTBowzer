package com.skystream.ytbowzer;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayInputStream;

/**
 * Hosts a single WebView that shows the YouTube mobile site, keeps the sign-in session
 * across app restarts and drops advertising requests.
 */
public class MainActivity extends AppCompatActivity {

    private static final String HOME_URL = "https://m.youtube.com/";

    private static final String MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Mobile Safari/537.36";

    /** Hides ad containers that are rendered inline by the page itself. */
    private static final String AD_HIDING_SCRIPT =
            "javascript:(function(){"
                    + "var id='ytbowzer-adblock';"
                    + "if(document.getElementById(id)){return;}"
                    + "var s=document.createElement('style');"
                    + "s.id=id;"
                    + "s.textContent='ytm-promoted-video-renderer,"
                    + "ytm-promoted-sparkles-web-renderer,"
                    + "ytm-companion-slot,"
                    + "ytm-player-ad-slot,"
                    + "ytm-compact-promoted-video-renderer,"
                    + "ytm-carousel-ad-renderer,"
                    + "ytm-search-ad-renderer,"
                    + "ytm-banner-promo-renderer,"
                    + "ytm-statement-banner-renderer,"
                    + "ytm-ad-slot-renderer,"
                    + "ytm-promoted-sparkles-text-search-renderer,"
                    + ".ad-showing .video-ads,"
                    + ".ytp-ad-module,"
                    + ".ytp-ad-overlay-container,"
                    + ".ytp-ad-text,"
                    + ".ytp-ad-player-overlay{display:none !important;}';"
                    + "(document.head||document.documentElement).appendChild(s);"
                    + "})()";

    /**
     * Removes ad-signaling keys (e.g. {@code playerAds}, {@code adPlacements}) from JSON
     * data before the page's own scripts can read them. Mirrors uBlock Origin's
     * {@code json-prune} scriptlet: since YouTube serves ad media from the same CDN as
     * regular video, the only reliable way to stop in-stream ads is to strip the fields
     * that tell the player where the ads are, before it schedules them.
     */
    static final String AD_JSON_PRUNE_SCRIPT =
            "javascript:(function(){"
                    + "if(window.__ytbowzerJsonPruneInstalled){return;}"
                    + "window.__ytbowzerJsonPruneInstalled=true;"
                    + "var AD_KEYS=['playerAds','adPlacements','adSlots','adBreakHeartbeatParams',"
                    + "'playerAdParams','adPlacementConfig','adBreakParams'];"
                    + "function prune(value,depth){"
                    + "if(!value||typeof value!=='object'||depth>8){return;}"
                    + "if(Array.isArray(value)){"
                    + "for(var i=0;i<value.length;i++){prune(value[i],depth+1);}"
                    + "return;"
                    + "}"
                    + "for(var i=0;i<AD_KEYS.length;i++){"
                    + "if(AD_KEYS[i] in value){delete value[AD_KEYS[i]];}"
                    + "}"
                    + "for(var key in value){"
                    + "if(Object.prototype.hasOwnProperty.call(value,key)){prune(value[key],depth+1);}"
                    + "}"
                    + "}"
                    + "var originalParse=JSON.parse;"
                    + "JSON.parse=function(){"
                    + "var result=originalParse.apply(this,arguments);"
                    + "prune(result,0);"
                    + "return result;"
                    + "};"
                    + "if(window.Response&&Response.prototype.json){"
                    + "var originalJson=Response.prototype.json;"
                    + "Response.prototype.json=function(){"
                    + "return originalJson.apply(this,arguments).then(function(result){"
                    + "prune(result,0);"
                    + "return result;"
                    + "});"
                    + "};"
                    + "}"
                    + "prune(window.ytInitialPlayerResponse,0);"
                    + "prune(window.ytInitialData,0);"
                    + "})()";

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(MOBILE_USER_AGENT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        // Persist the session cookies so the user only has to sign in once.
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new YouTubeWebViewClient());

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(HOME_URL);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void openExternally(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            // No app can handle the link; simply do nothing.
        }
    }

    private class YouTubeWebViewClient extends WebViewClient {

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (AdBlocker.isAd(request.getUrl().toString())) {
                return emptyResponse();
            }
            return super.shouldInterceptRequest(view, request);
        }

        @SuppressWarnings("deprecation")
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            if (AdBlocker.isAd(url)) {
                return emptyResponse();
            }
            return super.shouldInterceptRequest(view, url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUrl(request.getUrl().toString());
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(url);
        }

        private boolean handleUrl(String url) {
            if (SiteScope.isInAppUrl(url)) {
                return false;
            }
            openExternally(url);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            view.loadUrl(AD_JSON_PRUNE_SCRIPT);
            view.loadUrl(AD_HIDING_SCRIPT);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            view.loadUrl(AD_JSON_PRUNE_SCRIPT);
            view.loadUrl(AD_HIDING_SCRIPT);
            CookieManager.getInstance().flush();
        }

        private WebResourceResponse emptyResponse() {
            return new WebResourceResponse("text/plain", "utf-8",
                    new ByteArrayInputStream(new byte[0]));
        }
    }
}
