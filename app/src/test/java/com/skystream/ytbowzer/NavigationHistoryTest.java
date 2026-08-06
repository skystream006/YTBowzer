package com.skystream.ytbowzer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NavigationHistoryTest {

    @Test
    public void noHistoryMeansNoNavigation() {
        List<String> urls = Collections.singletonList("https://m.youtube.com/");
        assertEquals(0, NavigationHistory.backSteps(urls, 0));
        assertEquals(0, NavigationHistory.forwardSteps(urls, 0));
    }

    @Test
    public void backAndForwardMoveOneEntry() {
        List<String> urls = Arrays.asList(
                "https://m.youtube.com/",
                "https://m.youtube.com/watch?v=1",
                "https://m.youtube.com/watch?v=2");
        assertEquals(-1, NavigationHistory.backSteps(urls, 2));
        assertEquals(1, NavigationHistory.forwardSteps(urls, 1));
    }

    @Test
    public void duplicateEntriesAreSkipped() {
        List<String> urls = Arrays.asList(
                "https://m.youtube.com/",
                "https://m.youtube.com/watch?v=1",
                "https://m.youtube.com/watch?v=1",
                "https://m.youtube.com/watch?v=1");
        assertEquals(-3, NavigationHistory.backSteps(urls, 3));
        assertEquals(2, NavigationHistory.forwardSteps(urls, 1));
    }

    @Test
    public void trailingSlashAndFragmentAreIgnored() {
        List<String> urls = Arrays.asList(
                "https://m.youtube.com/",
                "https://m.youtube.com",
                "https://m.youtube.com/#fragment");
        assertEquals(-2, NavigationHistory.backSteps(urls, 2));
        assertEquals(2, NavigationHistory.forwardSteps(urls, 0));
    }

    @Test
    public void invalidIndexesAreIgnored() {
        List<String> urls = Arrays.asList("a", "b");
        assertEquals(0, NavigationHistory.backSteps(urls, 5));
        assertEquals(0, NavigationHistory.forwardSteps(urls, 5));
        assertEquals(0, NavigationHistory.backSteps(null, 1));
        assertEquals(0, NavigationHistory.forwardSteps(null, 0));
    }

    @Test
    public void nullUrlsAreTreatedAsEmpty() {
        List<String> urls = Arrays.asList(null, null, "https://m.youtube.com/");
        assertEquals(-1, NavigationHistory.backSteps(urls, 2));
        assertEquals(2, NavigationHistory.forwardSteps(urls, 0));
    }
}
