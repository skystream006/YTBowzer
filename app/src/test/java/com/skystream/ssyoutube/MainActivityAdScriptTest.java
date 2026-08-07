package com.skystream.ssyoutube;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Validates the shape of the ad JSON-pruning script injected by {@link MainActivity},
 * mirroring uBlock Origin's {@code json-prune} scriptlet approach.
 */
public class MainActivityAdScriptTest {

    @Test
    public void prunesKnownAdSignalingKeys() {
        String script = MainActivity.AD_JSON_PRUNE_SCRIPT;
        assertTrue(script.startsWith("(function"));
        assertTrue(script.contains("'playerAds'"));
        assertTrue(script.contains("'adPlacements'"));
        assertTrue(script.contains("'adSlots'"));
        assertTrue(script.contains("'adBreakHeartbeatParams'"));
    }

    @Test
    public void patchesJsonParseAndResponseJson() {
        String script = MainActivity.AD_JSON_PRUNE_SCRIPT;
        assertTrue(script.contains("JSON.parse=function"));
        assertTrue(script.contains("Response.prototype.json=function"));
    }

    @Test
    public void prunesExistingInitialData() {
        String script = MainActivity.AD_JSON_PRUNE_SCRIPT;
        assertTrue(script.contains("prune(window.ytInitialPlayerResponse,0)"));
        assertTrue(script.contains("prune(window.ytInitialData,0)"));
    }

    @Test
    public void removesBuyNowButtonsAsynchronously() {
        String script = MainActivity.BUY_NOW_CLEANUP_SCRIPT;
        assertTrue(script.contains("BuyNowCleanupInstalled"));
        assertTrue(script.contains("buy\\s+(it\\s+)?now"));
        assertTrue(script.contains("shop\\s+now"));
        assertTrue(script.contains("visit\\s+site"));
        assertTrue(script.contains("MutationObserver"));
        assertTrue(script.contains("addedNodes"));
        assertTrue(script.contains("characterData:true"));
        assertTrue(script.contains("setInterval"));
    }

    @Test
    public void hidesShoppingRenderersWithCss() {
        String script = MainActivity.AD_HIDING_SCRIPT;
        assertTrue(script.contains("ytm-product-card-renderer"));
        assertTrue(script.contains("ytm-shopping-offer-renderer"));
        assertTrue(script.contains("ytd-merch-shelf-renderer"));
    }

    @Test
    public void removesPlayablesSections() {
        String script = MainActivity.PLAYABLES_CLEANUP_SCRIPT;
        assertTrue(script.contains("PlayablesCleanupInstalled"));
        assertTrue(script.contains("playables?"));
        assertTrue(script.contains("MutationObserver"));
        assertTrue(script.contains("addedNodes"));
    }

    @Test
    public void removesPostsSections() {
        String script = MainActivity.POSTS_CLEANUP_SCRIPT;
        assertTrue(script.contains("PostsCleanupInstalled"));
        assertTrue(script.contains("==='posts'"));
        assertTrue(script.contains("MutationObserver"));
        assertTrue(script.contains("addedNodes"));
    }

    @Test
    public void setsVideoPosterFromPlayerThumbnail() {
        String script = MainActivity.VIDEO_THUMBNAIL_POSTER_SCRIPT;
        assertTrue(script.contains("ytInitialPlayerResponse"));
        assertTrue(script.contains("videoDetails"));
        assertTrue(script.contains("thumbnail.thumbnails"));
        assertTrue(script.contains("querySelectorAll('video')"));
        assertTrue(script.contains("setAttribute('poster',thumbnail)"));
        assertTrue(script.contains("MutationObserver"));
        assertTrue(script.contains("setInterval"));
        assertTrue(script.contains("yt-navigate-start"));
        assertTrue(script.contains("yt-navigate-finish"));
        assertTrue(script.contains("removeAttribute('poster')"));
        assertTrue(script.contains("details.videoId===videoId"));
    }

    @Test
    public void injectsSubscriberCountsBesideChannelAvatars() {
        String script = MainActivity.SUBSCRIBER_COUNT_SCRIPT;
        assertTrue(script.contains("subscriberCountText"));
        assertTrue(script.contains("ssyoutube-subscriber-count"));
        assertTrue(script.contains("insertAdjacentElement('afterend',badge)"));
        assertTrue(script.contains("fetch(url,{credentials:'same-origin'})"));
        assertTrue(script.contains("MutationObserver"));
    }

    @Test
    public void togglesFullscreenOnVerticalVideoSwipes() {
        String script = MainActivity.FULLSCREEN_GESTURE_SCRIPT;
        assertTrue(script.startsWith("(function"));
        assertTrue(script.contains("FullscreenGestureInstalled"));
        assertTrue(script.contains("touchstart"));
        assertTrue(script.contains("touchend"));
        assertTrue(script.contains(".ytp-fullscreen-button"));
        assertTrue(script.contains("isFullscreen"));
        assertTrue(script.contains("isPlaying"));
        assertTrue(script.contains("dy<0&&!fullscreen"));
        assertTrue(script.contains("dy>0&&fullscreen"));
        assertTrue(script.contains("button.click()"));
    }

    @Test
    public void minimizesToPipOnDownwardVideoSwipe() {
        String script = MainActivity.MINIPLAYER_GESTURE_SCRIPT;
        assertTrue(script.startsWith("(function"));
        assertTrue(script.contains("MiniplayerGestureInstalled"));
        assertTrue(script.contains("touchstart"));
        assertTrue(script.contains("touchend"));
        assertTrue(script.contains("isWatchPage"));
        assertTrue(script.contains("pathname||'')==='/watch'"));
        assertTrue(script.contains("isFullscreen"));
        assertTrue(script.contains("isPlaying"));
        assertTrue(script.contains("yt-navigate-finish"));
        assertTrue(script.contains("__ssyoutubeResultsUrl"));
        assertTrue(script.contains("window.ssYouTubeNative.minimize"));
    }

    @Test
    public void preloadsUpcomingResultsAheadOfScroll() {
        String script = MainActivity.RESULTS_PRELOAD_SCRIPT;
        assertTrue(script.startsWith("(function"));
        assertTrue(script.contains("ResultsPreloadInstalled"));
        assertTrue(script.contains("PRELOAD_SCREENS=2"));
        assertTrue(script.contains("window.IntersectionObserver"));
        assertTrue(script.contains("rootMargin"));
        assertTrue(script.contains("expandBottomMargin"));
    }

    @Test
    public void replacesYouTubeLogosWithTheAppLogo() {
        String script = MainActivity.APP_LOGO_SCRIPT;
        assertTrue(script.startsWith("(function"));
        assertTrue(script.contains("AppLogoInstalled"));
        assertTrue(script.contains("ytm-mobile-topbar-renderer .topbar-logo"));
        assertTrue(script.contains("ytm-topbar-logo-renderer"));
        assertTrue(script.contains("ytm-youtube-logo"));
        assertTrue(script.contains("ytd-topbar-logo-renderer"));
        assertTrue(script.contains("img[src*=\"yt_logo\"]"));
        assertTrue(script.contains("img[alt*=\"YouTube\"]"));
        assertTrue(script.contains("location.origin+'" + MainActivity.APP_LOGO_PATH + "'"));
        assertTrue(script.contains("styleImage(existing,height)"));
        assertTrue(script.contains("MutationObserver"));
        assertTrue(script.contains("yt-navigate-finish"));
    }

    @Test
    public void recognisesAppLogoRequests() {
        assertTrue(MainActivity.isAppLogoRequest(
                "https://m.youtube.com" + MainActivity.APP_LOGO_PATH));
        assertTrue(MainActivity.isAppLogoRequest(
                "https://www.youtube.com" + MainActivity.APP_LOGO_PATH + "?v=1"));
        assertFalse(MainActivity.isAppLogoRequest("https://m.youtube.com/"));
        assertFalse(MainActivity.isAppLogoRequest(
                "https://m.youtube.com/other" + MainActivity.APP_LOGO_PATH));
        assertFalse(MainActivity.isAppLogoRequest(null));
    }
}
