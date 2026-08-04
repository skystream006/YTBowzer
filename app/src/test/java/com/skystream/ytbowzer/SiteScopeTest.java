package com.skystream.ytbowzer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SiteScopeTest {

    @Test
    public void keepsYouTubeAndSignInInApp() {
        assertTrue(SiteScope.isInAppUrl("https://m.youtube.com/watch?v=abc"));
        assertTrue(SiteScope.isInAppUrl("https://youtu.be/abc"));
        assertTrue(SiteScope.isInAppUrl("https://accounts.google.com/ServiceLogin"));
        assertTrue(SiteScope.isInAppUrl("https://i.ytimg.com/vi/abc/hq.jpg"));
    }

    @Test
    public void sendsOtherSitesToExternalBrowser() {
        assertFalse(SiteScope.isInAppUrl("https://example.com/"));
        assertFalse(SiteScope.isInAppUrl("https://youtube.com.evil.example/"));
        assertFalse(SiteScope.isInAppUrl("http://m.youtube.com/"));
        assertFalse(SiteScope.isInAppUrl("intent://foo#Intent;end"));
        assertFalse(SiteScope.isInAppUrl(null));
    }
}
