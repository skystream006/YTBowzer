package com.skystream.ytbowzer;

import java.util.List;

/**
 * Browser-like back/forward navigation over a WebView history list.
 *
 * <p>Single page apps such as YouTube push several history entries for the same URL, so a plain
 * {@code goBack()} often appears to do nothing. These helpers compute how many steps to move so
 * that a press always lands on a different page, the way a browser's back/forward buttons behave.
 */
public final class NavigationHistory {

    private NavigationHistory() {
    }

    /**
     * @return a negative number of steps to move back, or 0 when there is nothing to go back to.
     */
    public static int backSteps(List<String> urls, int currentIndex) {
        if (urls == null || currentIndex <= 0 || currentIndex >= urls.size()) {
            return 0;
        }
        String current = urls.get(currentIndex);
        int index = currentIndex - 1;
        while (index > 0 && sameEntry(urls.get(index), current)) {
            index--;
        }
        if (sameEntry(urls.get(index), current)) {
            return 0;
        }
        return index - currentIndex;
    }

    /**
     * @return a positive number of steps to move forward, or 0 when there is nothing to go forward
     *         to.
     */
    public static int forwardSteps(List<String> urls, int currentIndex) {
        if (urls == null || currentIndex < 0 || currentIndex >= urls.size() - 1) {
            return 0;
        }
        String current = urls.get(currentIndex);
        int last = urls.size() - 1;
        int index = currentIndex + 1;
        while (index < last && sameEntry(urls.get(index), current)) {
            index++;
        }
        if (sameEntry(urls.get(index), current)) {
            return 0;
        }
        return index - currentIndex;
    }

    private static boolean sameEntry(String first, String second) {
        return normalize(first).equals(normalize(second));
    }

    private static String normalize(String url) {
        if (url == null) {
            return "";
        }
        String normalized = url.trim();
        int fragment = normalized.indexOf('#');
        if (fragment >= 0) {
            normalized = normalized.substring(0, fragment);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
