# YTBowzer

A minimal Android app that shows the YouTube **mobile** website in a full-screen WebView,
keeps you signed in, and blocks advertising/tracking requests.

## Features

- **YouTube mobile only** – loads `https://m.youtube.com/` with a mobile user agent.
  Links to other sites open in the system browser instead of inside the app
  (`SiteScope`), while YouTube and the Google sign-in domains stay in-app.
- **Persistent sign-in** – cookies are accepted (including third-party cookies needed by
  the Google account flow) and flushed to disk on pause, and DOM storage is enabled, so
  the session survives app restarts.
- **Preferences** – a floating settings button appears on the YouTube home page (the page
  with the bottom navigation bar). It opens a preferences dialog where the theme can be set
  to system/light/dark and the site mode to mobile or desktop (`Preferences`), mirroring a
  browser's "desktop site" toggle.
- **Ad blocking** – requests to known ad/tracking hosts and ad endpoints are intercepted
  and answered with an empty response (`AdBlocker`), and a stylesheet is injected on every
  page load to hide inline promoted/ad renderers.

## Project layout

```
app/src/main/java/com/skystream/ytbowzer/
  MainActivity.java   WebView setup, cookie persistence, request interception
  AdBlocker.java      URL-based ad/tracker blocklist (pure Java, unit tested)
  SiteScope.java      Which URLs stay inside the app (pure Java, unit tested)
  Preferences.java    Theme/site-mode values, user agents, home URLs (pure Java, unit tested)
app/src/test/java/... JUnit tests for AdBlocker and SiteScope
```

## Build and test

Requires JDK 17 and the Android SDK (compileSdk 34); minSdk is 21.

```bash
gradle assembleDebug   # build the APK
gradle test            # run the JVM unit tests
```
