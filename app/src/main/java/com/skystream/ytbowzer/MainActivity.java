package com.skystream.ytbowzer;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.ByteArrayInputStream;

/**
 * Hosts a single WebView that shows the YouTube mobile site, keeps the sign-in session
 * across app restarts and drops advertising requests.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "ytbowzer_prefs";
    private static final String KEY_THEME = "theme";
    private static final String KEY_DESKTOP_MODE = "desktop_mode";

    /** Hides ad containers that are rendered inline by the page itself. */
    static final String AD_HIDING_SCRIPT =
            "(function(){"
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
                    + "ytm-product-card-renderer,"
                    + "ytm-shopping-offer-renderer,"
                    + "ytm-merch-shelf-renderer,"
                    + "ytd-product-card-renderer,"
                    + "ytd-shopping-offer-renderer,"
                    + "ytd-merch-shelf-renderer,"
                    + ".ytp-shopping-overlay,"
                    + ".ytp-suggested-action,"
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
            "(function(){"
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

    /**
     * Removes shopping call-to-action buttons (e.g. "Buy now", "Shop now", "Visit site")
     * that can be added after page load.
     */
    static final String BUY_NOW_CLEANUP_SCRIPT =
            "(function(){"
                    + "if(window.__ytbowzerBuyNowCleanupInstalled){return;}"
                    + "window.__ytbowzerBuyNowCleanupInstalled=true;"
                    + "var selector='a,button,[role=\"button\"],[aria-label],[title]';"
                    + "function textOf(el){return ((el.innerText||el.textContent||'')+' '+"
                    + "(el.getAttribute('aria-label')||'')+' '+(el.getAttribute('title')||''))"
                    + ".replace(/\\s+/g,' ').trim().toLowerCase();}"
                    + "function asArray(list){return Array.prototype.slice.call(list);}"
                    + "function removeBuyNow(root){"
                    + "var nodes=(root&&root.querySelectorAll)?asArray(root.querySelectorAll(selector)):[];"
                    + "if(root&&root.matches&&root.matches(selector)){nodes.push(root);}"
                    + "for(var i=0;i<nodes.length;i++){"
                    + "var el=nodes[i];"
                    + "if(/\\bbuy\\s+(it\\s+)?now\\b|\\bshop\\s+now\\b|\\bvisit\\s+site\\b/"
                    + ".test(textOf(el))){"
                    + "var target=el.closest('ytm-product-card-renderer,ytm-shopping-offer-renderer,"
                    + "ytm-promoted-sparkles-web-renderer,ytm-promoted-video-renderer,"
                    + "ytd-product-card-renderer,ytd-shopping-offer-renderer')||el;"
                    + "target.remove();"
                    + "}"
                    + "}"
                    + "}"
                    + "removeBuyNow(document);"
                    + "new MutationObserver(function(mutations){"
                    + "for(var i=0;i<mutations.length;i++){"
                    + "for(var j=0;j<mutations[i].addedNodes.length;j++){"
                    + "var node=mutations[i].addedNodes[j];"
                    + "if(node.nodeType===1){removeBuyNow(node);}"
                    + "}"
                    + "}"
                    + "for(var k=0;k<mutations.length;k++){"
                    + "var target=mutations[k].target;"
                    + "if(target&&target.nodeType===1){removeBuyNow(target);}"
                    + "}"
                    + "}).observe(document.documentElement,"
                    + "{childList:true,subtree:true,characterData:true,attributes:true,"
                    + "attributeFilter:['aria-label','title']});"
                    + "setInterval(function(){removeBuyNow(document);},2000);"
                    + "})()";

    /** Removes the "Playables" shelves and navigation entries from YouTube pages. */
    static final String PLAYABLES_CLEANUP_SCRIPT =
            "(function(){"
                    + "if(window.__ytbowzerPlayablesCleanupInstalled){return;}"
                    + "window.__ytbowzerPlayablesCleanupInstalled=true;"
                    + "var selector='ytm-rich-section-renderer,ytm-shelf-renderer,"
                    + "ytm-item-section-renderer,ytm-rich-shelf-renderer,"
                    + "ytd-rich-section-renderer,ytd-shelf-renderer,"
                    + "ytm-pivot-bar-item-renderer,ytd-guide-entry-renderer,"
                    + "ytd-mini-guide-entry-renderer,a[href*=\"playables\"]';"
                    + "function textOf(el){return ((el.innerText||el.textContent||'')+' '+"
                    + "(el.getAttribute&&el.getAttribute('aria-label')||'')+' '+"
                    + "(el.getAttribute&&el.getAttribute('title')||''))"
                    + ".replace(/\\s+/g,' ').trim().toLowerCase();}"
                    + "function asArray(list){return Array.prototype.slice.call(list);}"
                    + "function isPlayables(el){"
                    + "var href=el.getAttribute&&el.getAttribute('href')||'';"
                    + "if(href.indexOf('playables')!==-1){return true;}"
                    + "return /\\bplayables?\\b/.test(textOf(el));"
                    + "}"
                    + "function removePlayables(root){"
                    + "var nodes=(root&&root.querySelectorAll)?asArray(root.querySelectorAll(selector)):[];"
                    + "if(root&&root.matches&&root.matches(selector)){nodes.push(root);}"
                    + "for(var i=0;i<nodes.length;i++){"
                    + "var el=nodes[i];"
                    + "if(!isPlayables(el)){continue;}"
                    + "var target=el.closest('ytm-rich-section-renderer,ytm-shelf-renderer,"
                    + "ytm-item-section-renderer,ytm-rich-shelf-renderer,"
                    + "ytd-rich-section-renderer,ytd-shelf-renderer,"
                    + "ytm-pivot-bar-item-renderer,ytd-guide-entry-renderer,"
                    + "ytd-mini-guide-entry-renderer')||el;"
                    + "target.remove();"
                    + "}"
                    + "}"
                    + "removePlayables(document);"
                    + "new MutationObserver(function(mutations){"
                    + "for(var i=0;i<mutations.length;i++){"
                    + "for(var j=0;j<mutations[i].addedNodes.length;j++){"
                    + "var node=mutations[i].addedNodes[j];"
                    + "if(node.nodeType===1){removePlayables(node);}"
                    + "}"
                    + "}"
                    + "}).observe(document.documentElement,{childList:true,subtree:true});"
                    + "})()";

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ViewGroup rootContainer;
    private View settingsButton;
    private SharedPreferences prefs;
    private boolean desktopMode;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenViewCallback;
    private int originalSystemUiVisibility;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        desktopMode = prefs.getBoolean(KEY_DESKTOP_MODE, false);
        applyTheme(prefs.getInt(KEY_THEME, Preferences.THEME_SYSTEM));

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        rootContainer = findViewById(R.id.root_container);
        settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPreferences();
            }
        });

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                webView.reload();
            }
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(Preferences.userAgent(desktopMode));
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportMultipleWindows(false);
        settings.setSupportZoom(desktopMode);
        settings.setBuiltInZoomControls(desktopMode);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }

        // Only allow the swipe gesture to trigger a refresh when the page is scrolled
        // to the top; otherwise it would conflict with scrolling through the feed.
        webView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                updateSwipeRefreshEnabled();
            }
        });

        // Persist the session cookies so the user only has to sign in once.
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new YouTubeWebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                showFullscreenView(view, callback);
            }

            @Override
            public void onHideCustomView() {
                hideFullscreenView();
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(startUrl(getIntent()));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        webView.loadUrl(startUrl(intent));
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
        if (keyCode == KeyEvent.KEYCODE_BACK && fullscreenView != null) {
            hideFullscreenView();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showFullscreenView(View view, WebChromeClient.CustomViewCallback callback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden();
            return;
        }
        fullscreenView = view;
        fullscreenViewCallback = callback;
        originalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        rootContainer.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        swipeRefreshLayout.setEnabled(false);
        webView.setVisibility(View.GONE);
        settingsButton.setVisibility(View.GONE);
    }

    private void hideFullscreenView() {
        if (fullscreenView == null) {
            return;
        }
        ((ViewGroup) fullscreenView.getParent()).removeView(fullscreenView);
        fullscreenView = null;
        getWindow().getDecorView().setSystemUiVisibility(originalSystemUiVisibility);
        webView.setVisibility(View.VISIBLE);
        updateSwipeRefreshEnabled();
        updateSettingsButton(webView.getUrl());
        if (fullscreenViewCallback != null) {
            fullscreenViewCallback.onCustomViewHidden();
            fullscreenViewCallback = null;
        }
    }

    private void updateSwipeRefreshEnabled() {
        swipeRefreshLayout.setEnabled(fullscreenView == null && webView.getScrollY() == 0);
    }

    private void applyTheme(int theme) {
        int mode;
        if (theme == Preferences.THEME_LIGHT) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (theme == Preferences.THEME_DARK) {
            mode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private void updateSettingsButton(String url) {
        settingsButton.setVisibility(Preferences.isHomePage(url) ? View.VISIBLE : View.GONE);
    }

    private void showPreferences() {
        View content = getLayoutInflater().inflate(R.layout.dialog_preferences, null);
        TextView versionView = content.findViewById(R.id.app_version);
        versionView.setText(getString(R.string.app_version_format, BuildConfig.VERSION_NAME));
        RadioGroup themeGroup = content.findViewById(R.id.theme_group);
        RadioGroup siteModeGroup = content.findViewById(R.id.site_mode_group);

        int theme = prefs.getInt(KEY_THEME, Preferences.THEME_SYSTEM);
        if (theme == Preferences.THEME_LIGHT) {
            themeGroup.check(R.id.theme_light);
        } else if (theme == Preferences.THEME_DARK) {
            themeGroup.check(R.id.theme_dark);
        } else {
            themeGroup.check(R.id.theme_system);
        }
        siteModeGroup.check(desktopMode ? R.id.site_mode_desktop : R.id.site_mode_mobile);

        themeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int selected = Preferences.THEME_SYSTEM;
                if (checkedId == R.id.theme_light) {
                    selected = Preferences.THEME_LIGHT;
                } else if (checkedId == R.id.theme_dark) {
                    selected = Preferences.THEME_DARK;
                }
                if (selected == prefs.getInt(KEY_THEME, Preferences.THEME_SYSTEM)) {
                    return;
                }
                prefs.edit().putInt(KEY_THEME, selected).apply();
                applyTheme(selected);
            }
        });

        siteModeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                boolean wantsDesktop = checkedId == R.id.site_mode_desktop;
                if (wantsDesktop == desktopMode) {
                    return;
                }
                desktopMode = wantsDesktop;
                prefs.edit().putBoolean(KEY_DESKTOP_MODE, wantsDesktop).apply();
                WebSettings webSettings = webView.getSettings();
                webSettings.setUserAgentString(Preferences.userAgent(wantsDesktop));
                webSettings.setSupportZoom(wantsDesktop);
                webSettings.setBuiltInZoomControls(wantsDesktop);
                webSettings.setDisplayZoomControls(false);
                webView.clearHistory();
                webView.loadUrl(Preferences.homeUrl(wantsDesktop));
            }
        });

        new AlertDialog.Builder(this)
                .setTitle(R.string.preferences)
                .setView(content)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private String startUrl(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())
                && intent.getDataString() != null) {
            String inAppUrl = SiteScope.normalizeInAppUrl(intent.getDataString());
            if (inAppUrl != null) {
                return inAppUrl;
            }
        }
        return Preferences.homeUrl(desktopMode);
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
            return handleUrl(view, request.getUrl().toString());
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(view, url);
        }

        private boolean handleUrl(WebView view, String url) {
            String inAppUrl = SiteScope.normalizeInAppUrl(url);
            if (inAppUrl == null) {
                return true;
            }
            if (!inAppUrl.equals(url)) {
                view.loadUrl(inAppUrl);
                return true;
            }
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            updateSettingsButton(url);
            view.evaluateJavascript(AD_JSON_PRUNE_SCRIPT, null);
            view.evaluateJavascript(AD_HIDING_SCRIPT, null);
            view.evaluateJavascript(BUY_NOW_CLEANUP_SCRIPT, null);
            view.evaluateJavascript(PLAYABLES_CLEANUP_SCRIPT, null);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            updateSettingsButton(url);
            view.evaluateJavascript(AD_JSON_PRUNE_SCRIPT, null);
            view.evaluateJavascript(AD_HIDING_SCRIPT, null);
            view.evaluateJavascript(BUY_NOW_CLEANUP_SCRIPT, null);
            view.evaluateJavascript(PLAYABLES_CLEANUP_SCRIPT, null);
            CookieManager.getInstance().flush();
            swipeRefreshLayout.setRefreshing(false);
        }

        private WebResourceResponse emptyResponse() {
            return new WebResourceResponse("text/plain", "utf-8",
                    new ByteArrayInputStream(new byte[0]));
        }
    }
}
