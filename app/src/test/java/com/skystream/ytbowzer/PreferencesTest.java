package com.skystream.ytbowzer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PreferencesTest {

    @Test
    public void mobileModeUsesMobileSite() {
        assertEquals("https://m.youtube.com/", Preferences.homeUrl(false));
        assertTrue(Preferences.userAgent(false).contains("Mobile"));
    }

    @Test
    public void desktopModeUsesDesktopSite() {
        assertEquals("https://www.youtube.com/", Preferences.homeUrl(true));
        assertFalse(Preferences.userAgent(true).contains("Mobile"));
    }

    @Test
    public void homePageIsRecognised() {
        assertTrue(Preferences.isHomePage("https://m.youtube.com/"));
        assertTrue(Preferences.isHomePage("https://www.youtube.com"));
        assertTrue(Preferences.isHomePage("https://www.youtube.com/?gl=US"));
    }

    @Test
    public void otherPagesAreNotHome() {
        assertFalse(Preferences.isHomePage("https://m.youtube.com/watch?v=abc"));
        assertFalse(Preferences.isHomePage("https://m.youtube.com/feed/subscriptions"));
        assertFalse(Preferences.isHomePage("https://accounts.google.com/"));
        assertFalse(Preferences.isHomePage(null));
    }
}
