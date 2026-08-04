package com.skystream.ytbowzer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Defines which URLs stay inside the app. YouTube plus the Google sign-in domains are
 * allowed so that signing in works; everything else is handed to an external browser.
 * Kept free of Android dependencies so it can be unit tested on the JVM.
 */
public final class SiteScope {

    private static final List<String> ALLOWED_HOSTS = Arrays.asList(
            "youtube.com",
            "youtu.be",
            "ytimg.com",
            "ggpht.com",
            "googlevideo.com",
            "google.com",
            "gstatic.com",
            "googleusercontent.com",
            "googleapis.com"
    );

    private SiteScope() {
    }

    /**
     * @param url an absolute request URL
     * @return true when the URL should be loaded by the in-app WebView
     */
    public static boolean isInAppUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        if (!lower.startsWith("https://")) {
            return false;
        }
        String host = hostOf(lower);
        if (host == null) {
            return false;
        }
        for (String allowed : ALLOWED_HOSTS) {
            if (host.equals(allowed) || host.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    private static String hostOf(String lowerUrl) {
        int start = lowerUrl.indexOf("://") + 3;
        int end = lowerUrl.length();
        for (int i = start; i < lowerUrl.length(); i++) {
            char c = lowerUrl.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String authority = lowerUrl.substring(start, end);
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }
        int colon = authority.indexOf(':');
        if (colon >= 0) {
            authority = authority.substring(0, colon);
        }
        return authority.isEmpty() ? null : authority;
    }
}
