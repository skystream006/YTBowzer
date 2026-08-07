package com.skystream.ssyoutube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SiteScopeTest {

    @Test
    public void keepsYouTubeAndSignInInApp() {
        assertTrue(SiteScope.isInAppUrl("https://m.youtube.com/watch?v=abc"));
        assertTrue(SiteScope.isInAppUrl("https://youtu.be/abc"));
        assertTrue(SiteScope.isInAppUrl("https://accounts.google.com/ServiceLogin"));
        assertTrue(SiteScope.isInAppUrl("https://i.ytimg.com/vi/abc/hq.jpg"));
        assertTrue(SiteScope.isInAppUrl("http://m.youtube.com/watch?v=abc"));
    }

    @Test
    public void sendsOtherSitesToExternalBrowser() {
        assertFalse(SiteScope.isInAppUrl("https://example.com/"));
        assertFalse(SiteScope.isInAppUrl("https://youtube.com.evil.example/"));
        assertFalse(SiteScope.isInAppUrl("intent://foo#Intent;end"));
        assertFalse(SiteScope.isInAppUrl(null));
    }

    @Test
    public void normalizesHttpYouTubeLinksToHttps() {
        assertEquals("https://m.youtube.com/watch?v=abc",
                SiteScope.normalizeInAppUrl("http://m.youtube.com/watch?v=abc"));
        assertEquals("https://youtu.be/abc",
                SiteScope.normalizeInAppUrl("https://youtu.be/abc"));
        assertNull(SiteScope.normalizeInAppUrl("http://example.com/"));
    }
}
