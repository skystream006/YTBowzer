package com.skystream.ytbowzer;

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
}
