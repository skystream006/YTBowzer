package com.skystream.ssyoutube;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.view.ViewGroup;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hosts a single WebView that shows the YouTube mobile site, keeps the sign-in session
 * across app restarts and drops advertising requests.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "ssyoutube_prefs";
    private static final String KEY_THEME = "theme";
    private static final String KEY_DESKTOP_MODE = "desktop_mode";

    /** Hides ad containers that are rendered inline by the page itself. */
    static final String AD_HIDING_SCRIPT =
            "(function(){"
                    + "var id='ssyoutube-adblock';"
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
                    + "if(window.__ssyoutubeJsonPruneInstalled){return;}"
                    + "window.__ssyoutubeJsonPruneInstalled=true;"
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
     * Elements that make up (or host) the comments section. The cleanup scripts below never
     * remove or decorate anything inside them: on large screens (e.g. unfolded foldables) the
     * mobile site renders comments inside an engagement panel whose sections look just like the
     * shelves the cleanup scripts target, which previously left the comment list empty.
     */
    static final String COMMENTS_SELECTOR =
            "ytm-comment-section-renderer,ytm-comments-entry-point-header-renderer,"
                    + "ytm-comment-thread-renderer,ytm-comment-renderer,"
                    + "ytm-comment-replies-renderer,ytm-engagement-panel,"
                    + "ytm-engagement-panel-section-list-renderer,#comments,"
                    + "ytd-comments,ytd-comment-thread-renderer,ytd-comment-renderer";

    /** JavaScript helper that reports whether a node belongs to the comments section. */
    private static final String COMMENTS_HELPER_SCRIPT =
            "var COMMENTS='" + COMMENTS_SELECTOR + "';"
                    + "function inComments(el){"
                    + "if(!el||el.nodeType!==1){return false;}"
                    + "if(el.closest&&el.closest(COMMENTS)){return true;}"
                    + "return !!(el.querySelector&&el.querySelector(COMMENTS));"
                    + "}";

    /**
     * Removes shopping call-to-action buttons (e.g. "Buy now", "Shop now", "Visit site")
     * that can be added after page load.
     */
    static final String BUY_NOW_CLEANUP_SCRIPT =
            "(function(){"
                    + "if(window.__ssyoutubeBuyNowCleanupInstalled){return;}"
                    + "window.__ssyoutubeBuyNowCleanupInstalled=true;"
                    + COMMENTS_HELPER_SCRIPT
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
                    + "if(inComments(el)){continue;}"
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
                    + "if(window.__ssyoutubePlayablesCleanupInstalled){return;}"
                    + "window.__ssyoutubePlayablesCleanupInstalled=true;"
                    + COMMENTS_HELPER_SCRIPT
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
                    + "if(inComments(el)){return false;}"
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

    /** Removes the "Posts" shelf from the YouTube home page as it appears. */
    static final String POSTS_CLEANUP_SCRIPT =
            "(function(){"
                    + "if(window.__ssyoutubePostsCleanupInstalled){return;}"
                    + "window.__ssyoutubePostsCleanupInstalled=true;"
                    + COMMENTS_HELPER_SCRIPT
                    + "var selector='ytm-rich-section-renderer,ytm-shelf-renderer,"
                    + "ytm-item-section-renderer,ytm-rich-shelf-renderer,"
                    + "ytd-rich-section-renderer,ytd-shelf-renderer';"
                    + "function textOf(el){return (el.innerText||el.textContent||'')"
                    + ".replace(/\\s+/g,' ').trim().toLowerCase();}"
                    + "function asArray(list){return Array.prototype.slice.call(list);}"
                    + "function isPosts(el){"
                    + "if(inComments(el)){return false;}"
                    + "var headings=asArray(el.querySelectorAll('h1,h2,h3,h4,"
                    + "ytm-rich-section-renderer>yt-formatted-string,"
                    + ".section-title yt-formatted-string'));"
                    + "for(var i=0;i<headings.length;i++){if(textOf(headings[i])==='posts'){return true;}}"
                    + "return false;"
                    + "}"
                    + "function removePosts(root){"
                    + "var nodes=(root&&root.querySelectorAll)?asArray(root.querySelectorAll(selector)):[];"
                    + "if(root&&root.matches&&root.matches(selector)){nodes.push(root);}"
                    + "for(var i=0;i<nodes.length;i++){if(isPosts(nodes[i])){nodes[i].remove();}}"
                    + "}"
                    + "removePosts(document);"
                    + "new MutationObserver(function(mutations){"
                    + "for(var i=0;i<mutations.length;i++){"
                    + "for(var j=0;j<mutations[i].addedNodes.length;j++){"
                    + "var node=mutations[i].addedNodes[j];"
                    + "if(node.nodeType===1){removePosts(node);}"
                    + "}"
                    + "}"
                    + "}).observe(document.documentElement,{childList:true,subtree:true});"
                    + "})()";

    /** Uses YouTube's current video thumbnail as the HTML video poster while video loads. */
    static final String VIDEO_THUMBNAIL_POSTER_SCRIPT =
            "(function(){"
                    + "function bestThumbnail(thumbnails){"
                    + "if(!thumbnails||!thumbnails.length){return null;}"
                    + "var best=thumbnails[0];"
                    + "for(var i=1;i<thumbnails.length;i++){"
                    + "var candidate=thumbnails[i];"
                    + "if((candidate.width||0)>=(best.width||0)){best=candidate;}"
                    + "}"
                    + "return best&&best.url;"
                    + "}"
                    + "function currentVideoId(){"
                    + "var match=/[?&]v=([^&#]+)/.exec(window.location.search||'');"
                    + "if(match){return decodeURIComponent(match[1]);}"
                    + "match=/^\\/shorts\\/([^/?#]+)/.exec(window.location.pathname||'');"
                    + "return match?decodeURIComponent(match[1]):null;"
                    + "}"
                    + "function currentThumbnail(){"
                    + "var videoId=currentVideoId();"
                    + "if(!videoId){return null;}"
                    + "var response=window.ytInitialPlayerResponse||{};"
                    + "var details=response.videoDetails||{};"
                    + "if(details.videoId===videoId){"
                    + "var thumbnail=details.thumbnail&&details.thumbnail.thumbnails;"
                    + "var url=bestThumbnail(thumbnail);"
                    + "if(url){return url;}"
                    + "}"
                    + "return 'https://i.ytimg.com/vi/'+encodeURIComponent(videoId)+'/hqdefault.jpg';"
                    + "}"
                    + "function asArray(list){return Array.prototype.slice.call(list);}"
                    + "function clearVideoPosters(){"
                    + "var videos=asArray(document.querySelectorAll('video'));"
                    + "for(var i=0;i<videos.length;i++){videos[i].removeAttribute('poster');}"
                    + "}"
                    + "function syncVideoPosters(root){"
                    + "var videoId=currentVideoId();"
                    + "if(videoId!==window.__ssyoutubeVideoThumbnailId){"
                    + "window.__ssyoutubeVideoThumbnailId=videoId;"
                    + "clearVideoPosters();"
                    + "}"
                    + "var thumbnail=currentThumbnail();"
                    + "if(!thumbnail){return;}"
                    + "var videos=(root&&root.querySelectorAll)?asArray(root.querySelectorAll('video')):[];"
                    + "if(root&&root.tagName&&root.tagName.toLowerCase()==='video'){videos.push(root);}"
                    + "for(var i=0;i<videos.length;i++){"
                    + "videos[i].setAttribute('poster',thumbnail);"
                    + "}"
                    + "}"
                    + "syncVideoPosters(document);"
                    + "if(window.__ssyoutubeVideoThumbnailPosterInstalled){return;}"
                    + "window.__ssyoutubeVideoThumbnailPosterInstalled=true;"
                    + "document.addEventListener('yt-navigate-start',function(){"
                    + "window.__ssyoutubeVideoThumbnailId=null;"
                    + "clearVideoPosters();"
                    + "},true);"
                    + "document.addEventListener('yt-navigate-finish',function(){"
                    + "syncVideoPosters(document);"
                    + "},true);"
                    + "window.addEventListener('popstate',function(){"
                    + "syncVideoPosters(document);"
                    + "},true);"
                    + "new MutationObserver(function(mutations){"
                    + "for(var i=0;i<mutations.length;i++){"
                    + "for(var j=0;j<mutations[i].addedNodes.length;j++){"
                    + "var node=mutations[i].addedNodes[j];"
                    + "if(node.nodeType===1){syncVideoPosters(node);}"
                    + "}"
                    + "}"
                    + "}).observe(document.documentElement,{childList:true,subtree:true});"
                    + "setInterval(function(){syncVideoPosters(document);},1000);"
                    + "})()";

    /** Adds each channel's subscriber count beside its avatar on video cards. */
    static final String SUBSCRIBER_COUNT_SCRIPT =
            "(function(){"
                    + "var badgeClass='ssyoutube-subscriber-count';"
                    + "var avatarSelector='ytm-channel-thumbnail-with-link-renderer,"
                    + "ytm-channel-thumbnail-supported-renderer,ytm-channel-thumbnail-renderer,ytm-avatar';"
                    + "var cache=window.__ssyoutubeSubscriberCounts||(window.__ssyoutubeSubscriberCounts={});"
                    + COMMENTS_HELPER_SCRIPT
                    // Channel pages are full HTML documents, so the lookups are queued: a long
                    // comment list would otherwise fire hundreds of parallel requests, which
                    // starves the page's own continuation requests and leaves it half rendered.
                    + "var MAX_ACTIVE_LOOKUPS=3;"
                    + "var queue=window.__ssyoutubeSubscriberQueue||"
                    + "(window.__ssyoutubeSubscriberQueue={pending:[],active:0});"
                    + "function pump(){"
                    + "while(queue.active<MAX_ACTIVE_LOOKUPS&&queue.pending.length){"
                    + "queue.active++;"
                    + "queue.pending.shift()();"
                    + "}"
                    + "}"
                    + "function enqueue(job){queue.pending.push(job);pump();}"
                    + "function lookupDone(){queue.active--;pump();}"
                    + "function channelUrl(avatar){"
                    + "var link=avatar.querySelector('a[href]')||avatar.closest('a[href]');"
                    + "if(!link){return null;}"
                    + "var url;"
                    + "try{url=new URL(link.href,location.origin);}catch(e){return null;}"
                    + "if(url.origin!==location.origin||!/^\\/@[\\w.-]+$|^\\/channel\\/UC[\\w-]+$|"
                    + "^\\/c\\/[\\w.-]+$|^\\/user\\/[\\w.-]+$/.test(url.pathname)){"
                    + "return null;"
                    + "}"
                    + "return url.origin+url.pathname;"
                    + "}"
                    + "function subscriberCount(html){"
                    + "var start=html.indexOf('\"subscriberCountText\"');"
                    + "if(start===-1){return null;}"
                    + "var match=/\"simpleText\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"/"
                    + ".exec(html.slice(start,start+2000));"
                    + "if(!match){return null;}"
                    + "try{return JSON.parse('\"'+match[1]+'\"');}catch(e){return null;}"
                    + "}"
                    + "function addBadge(avatar,count){"
                    + "var badge=avatar.nextElementSibling;"
                    + "if(!badge||!badge.classList.contains(badgeClass)){badge=null;}"
                    + "if(!badge){"
                    + "badge=document.createElement('span');"
                    + "badge.className=badgeClass;"
                    + "badge.style.cssText='display:inline-block;margin-left:6px;vertical-align:middle;"
                    + "font-size:12px;line-height:1.2;white-space:nowrap;';"
                    + "avatar.insertAdjacentElement('afterend',badge);"
                    + "}"
                    + "badge.textContent=count;"
                    + "}"
                    + "function sync(root){"
                    + "var avatars=(root&&root.querySelectorAll)"
                    + "?Array.prototype.slice.call(root.querySelectorAll(avatarSelector)):[];"
                    + "if(root&&root.matches&&root.matches(avatarSelector)){"
                    + "avatars.push(root);"
                    + "}"
                    + "for(var i=0;i<avatars.length;i++){(function(avatar){"
                    + "if(inComments(avatar)){return;}"
                    + "var url=channelUrl(avatar);"
                    + "if(!url){return;}"
                    + "if(Object.prototype.hasOwnProperty.call(cache,url)){"
                    + "if(cache[url]){addBadge(avatar,cache[url]);}"
                    + "return;"
                    + "}"
                    + "cache[url]=null;"
                    + "enqueue(function(){"
                    + "try{"
                    + "fetch(url,{credentials:'same-origin'}).then(function(response){return response.text();})"
                    + ".then(function(html){"
                    + "var count=subscriberCount(html);"
                    + "cache[url]=count;"
                    + "if(count){addBadge(avatar,count);}"
                    + "}).catch(function(){}).then(lookupDone);"
                    + "}catch(e){lookupDone();}"
                    + "});"
                    + "})(avatars[i]);}"
                    + "}"
                    + "sync(document);"
                    + "if(window.__ssyoutubeSubscriberCountInstalled){return;}"
                    + "window.__ssyoutubeSubscriberCountInstalled=true;"
                    + "new MutationObserver(function(mutations){"
                    + "for(var i=0;i<mutations.length;i++){"
                    + "for(var j=0;j<mutations[i].addedNodes.length;j++){"
                    + "var node=mutations[i].addedNodes[j];"
                    + "if(node.nodeType===1){sync(node);}"
                    + "}"
                    + "}"
                    + "}).observe(document.documentElement,{childList:true,subtree:true});"
                    + "})()";

    /**
     * Shared touch tracking used by the swipe gestures below. It is installed once per page and
     * exposes {@code window.__ssyoutubeGestures}.
     *
     * <p>Touches are followed through {@code touchmove} and the gesture is also completed on
     * {@code touchcancel}: while the page scrolls (or the WebView takes the gesture over, which is
     * common on large/foldable screens) the browser cancels the touch sequence instead of ending
     * it, which previously dropped the swipe entirely. The player element is resolved from the
     * event's composed path so touches that start inside the player's shadow DOM are recognised
     * too, and the distance threshold scales with the viewport so the same flick works on both
     * the folded and unfolded screen.
     */
    private static final String GESTURE_SUPPORT_SCRIPT =
            "(function(){"
                    + "if(window.__ssyoutubeGestures){return;}"
                    + "var PLAYER_SELECTOR='#movie_player,.html5-video-player,ytd-player,ytm-player,"
                    + "ytm-video-player,.player-container,#player-container-id,#player';"
                    + "var handlers=[];"
                    + "var tracking=false,startX=0,startY=0,lastX=0,lastY=0,activePlayer=null;"
                    + "function threshold(){"
                    + "return Math.max(32,Math.min(120,Math.round((window.innerHeight||800)*0.06)));"
                    + "}"
                    + "function eventPath(e){"
                    + "if(e.composedPath){try{return e.composedPath();}catch(err){}}"
                    + "var path=[],node=e.target;"
                    + "while(node){path.push(node);node=node.parentNode||node.host;}"
                    + "return path;"
                    + "}"
                    + "function playerElement(e){"
                    + "var path=eventPath(e);"
                    + "for(var i=0;i<path.length;i++){"
                    + "var node=path[i];"
                    + "if(!node||node.nodeType!==1){continue;}"
                    + "if(node.matches&&node.matches(PLAYER_SELECTOR)){return node;}"
                    + "var found=node.closest&&node.closest(PLAYER_SELECTOR);"
                    + "if(found){return found;}"
                    + "}"
                    + "return null;"
                    + "}"
                    + "function isFullscreen(){"
                    + "return !!(document.fullscreenElement||document.webkitFullscreenElement||"
                    + "document.querySelector('.ytp-fullscreen'));"
                    + "}"
                    + "function isPlaying(player){"
                    + "var video=player&&player.querySelector&&player.querySelector('video');"
                    + "if(!video){video=document.querySelector('video');}"
                    + "return !!video&&!video.paused&&!video.ended;"
                    + "}"
                    + "function reset(){tracking=false;activePlayer=null;}"
                    + "function finish(){"
                    + "if(!tracking){return;}"
                    + "var player=activePlayer;"
                    + "var dx=lastX-startX;"
                    + "var dy=lastY-startY;"
                    + "reset();"
                    + "if(Math.abs(dy)<threshold()||Math.abs(dx)>Math.abs(dy)){return;}"
                    + "for(var i=0;i<handlers.length;i++){"
                    + "try{handlers[i]({player:player,dx:dx,dy:dy});}catch(err){}"
                    + "}"
                    + "}"
                    + "document.addEventListener('touchstart',function(e){"
                    + "if(e.touches.length!==1){reset();return;}"
                    + "var player=playerElement(e);"
                    + "if(!player){reset();return;}"
                    + "tracking=true;"
                    + "activePlayer=player;"
                    + "startX=lastX=e.touches[0].clientX;"
                    + "startY=lastY=e.touches[0].clientY;"
                    + "},{passive:true,capture:true});"
                    + "document.addEventListener('touchmove',function(e){"
                    + "if(!tracking||e.touches.length!==1){return;}"
                    + "lastX=e.touches[0].clientX;"
                    + "lastY=e.touches[0].clientY;"
                    + "},{passive:true,capture:true});"
                    + "function complete(e){"
                    + "var touch=e.changedTouches&&e.changedTouches[0];"
                    + "if(touch){lastX=touch.clientX;lastY=touch.clientY;}"
                    + "finish();"
                    + "}"
                    + "document.addEventListener('touchend',complete,{passive:true,capture:true});"
                    + "document.addEventListener('touchcancel',complete,{passive:true,capture:true});"
                    + "window.__ssyoutubeGestures={"
                    + "onVerticalSwipe:function(handler){handlers.push(handler);},"
                    + "isFullscreen:isFullscreen,"
                    + "isPlaying:isPlaying"
                    + "};"
                    + "})()";

    /**
     * Lets the user swipe up on a playing video to enter fullscreen and swipe down while
     * fullscreen to exit it, mirroring the native YouTube app's gesture behavior. The mobile
     * site does not use the desktop player's {@code .ytp-fullscreen-button}, so the mobile
     * control is looked up as well and the Fullscreen API is used as a last resort.
     */
    static final String FULLSCREEN_GESTURE_SCRIPT =
            GESTURE_SUPPORT_SCRIPT + ";"
                    + "(function(){"
                    + "if(window.__ssyoutubeFullscreenGestureInstalled||!window.__ssyoutubeGestures){return;}"
                    + "window.__ssyoutubeFullscreenGestureInstalled=true;"
                    + "var gestures=window.__ssyoutubeGestures;"
                    + "var BUTTON_SELECTOR='.ytp-fullscreen-button,button.fullscreen-icon,"
                    + ".fullscreen-icon,[aria-label=\"Full screen\"],[aria-label=\"Exit full screen\"]';"
                    + "function fullscreenButton(player){"
                    + "return (player&&player.querySelector&&player.querySelector(BUTTON_SELECTOR))||"
                    + "document.querySelector(BUTTON_SELECTOR);"
                    + "}"
                    + "function toggleFullscreen(player){"
                    + "var button=fullscreenButton(player);"
                    + "if(button){button.click();return;}"
                    + "if(gestures.isFullscreen()){"
                    + "var exit=document.exitFullscreen||document.webkitExitFullscreen;"
                    + "if(exit){try{exit.call(document);}catch(e){}}"
                    + "return;"
                    + "}"
                    + "var request=player&&(player.requestFullscreen||player.webkitRequestFullscreen);"
                    + "if(request){try{request.call(player);}catch(e){}}"
                    + "}"
                    + "gestures.onVerticalSwipe(function(swipe){"
                    + "var dy=swipe.dy;"
                    + "var fullscreen=gestures.isFullscreen();"
                    + "if(dy<0&&!fullscreen&&gestures.isPlaying(swipe.player)){"
                    + "toggleFullscreen(swipe.player);"
                    + "}else if(dy>0&&fullscreen){"
                    + "toggleFullscreen(swipe.player);"
                    + "}"
                    + "});"
                    + "})()";

    /**
     * Lets the user swipe down on a playing video (while not fullscreen) to shrink it into a
     * small picture-in-picture window, revealing the last visited results page (search/home)
     * underneath, mirroring the native YouTube app's miniplayer gesture.
     */
    static final String MINIPLAYER_GESTURE_SCRIPT =
            GESTURE_SUPPORT_SCRIPT + ";"
                    + "(function(){"
                    + "if(window.__ssyoutubeMiniplayerGestureInstalled||!window.__ssyoutubeGestures){return;}"
                    + "window.__ssyoutubeMiniplayerGestureInstalled=true;"
                    + "var gestures=window.__ssyoutubeGestures;"
                    + "function isWatchPage(){"
                    + "return (window.location.pathname||'')==='/watch';"
                    + "}"
                    + "window.__ssyoutubeResultsUrl=window.__ssyoutubeResultsUrl||"
                    + "(isWatchPage()?null:location.href);"
                    + "function trackResultsUrl(){"
                    + "if(!isWatchPage()){window.__ssyoutubeResultsUrl=location.href;}"
                    + "}"
                    + "document.addEventListener('yt-navigate-finish',trackResultsUrl,true);"
                    + "window.addEventListener('popstate',trackResultsUrl,true);"
                    + "gestures.onVerticalSwipe(function(swipe){"
                    + "if(swipe.dy<=0||!isWatchPage()){return;}"
                    + "if(!window.ssYouTubeNative||!window.ssYouTubeNative.minimize){return;}"
                    + "if(gestures.isFullscreen()||!gestures.isPlaying(swipe.player)){return;}"
                    + "window.ssYouTubeNative.minimize(window.__ssyoutubeResultsUrl||'',true);"
                    + "});"
                    + "})()";

    /**
     * Strips the watch page down to just the video while it is shown in the miniplayer: the
     * player is pinned over the whole (small) viewport, the video is scaled to fit inside it
     * without cropping, and the surrounding page chrome plus the player's own controls and
     * end-screen overlays are hidden. The class is re-applied on a short interval because
     * YouTube re-creates the player element on navigation.
     */
    static final String MINIPLAYER_VIEW_SCRIPT =
            "(function(){"
                    + "var PLAYER_SELECTOR='#movie_player,.html5-video-player,ytd-player,ytm-player,"
                    + "ytm-video-player,.player-container,#player-container-id,#player';"
                    + "var STYLE_ID='ssyoutube-miniplayer-style';"
                    + "var CSS='html.ssyoutube-miniplayer,html.ssyoutube-miniplayer body{"
                    + "margin:0!important;padding:0!important;overflow:hidden!important;"
                    + "background:#000!important;}"
                    + "html.ssyoutube-miniplayer .ssyoutube-miniplayer-player{"
                    + "position:fixed!important;top:0!important;left:0!important;right:0!important;"
                    + "bottom:0!important;width:100vw!important;height:100vh!important;"
                    + "max-width:100vw!important;max-height:100vh!important;margin:0!important;"
                    + "padding:0!important;background:#000!important;z-index:2147483647!important;}"
                    + "html.ssyoutube-miniplayer .ssyoutube-miniplayer-player .html5-video-container,"
                    + "html.ssyoutube-miniplayer .ssyoutube-miniplayer-player video{"
                    + "position:absolute!important;top:0!important;left:0!important;"
                    + "width:100%!important;height:100%!important;object-fit:contain!important;}"
                    + "html.ssyoutube-miniplayer .ssyoutube-miniplayer-player .ytp-chrome-top,"
                    + "html.ssyoutube-miniplayer .ssyoutube-miniplayer-player .ytp-chrome-bottom,"
                    + "html.ssyoutube-miniplayer .ssyoutube-miniplayer-player .ytp-gradient-top,"
                    + "html.ssyoutube-miniplayer .ssyoutube-miniplayer-player .ytp-gradient-bottom,"
                    + "html.ssyoutube-miniplayer .ssyoutube-miniplayer-player .ytp-ce-element,"
                    + "html.ssyoutube-miniplayer .ssyoutube-miniplayer-player .ytp-endscreen-content,"
                    + "html.ssyoutube-miniplayer .ssyoutube-miniplayer-player .ytp-pause-overlay,"
                    + "html.ssyoutube-miniplayer .ssyoutube-miniplayer-player .ytp-watermark,"
                    + "html.ssyoutube-miniplayer .ssyoutube-miniplayer-player .player-controls-background,"
                    + "html.ssyoutube-miniplayer .ssyoutube-miniplayer-player .player-controls-content,"
                    + "html.ssyoutube-miniplayer .ssyoutube-miniplayer-player ytm-custom-control,"
                    + "html.ssyoutube-miniplayer .ssyoutube-miniplayer-player .ytp-cued-thumbnail-overlay"
                    + "{display:none!important;}';"
                    + "function ensureStyle(){"
                    + "if(document.getElementById(STYLE_ID)){return;}"
                    + "var head=document.head||document.documentElement;"
                    + "if(!head){return;}"
                    + "var style=document.createElement('style');"
                    + "style.id=STYLE_ID;"
                    + "style.textContent=CSS;"
                    + "head.appendChild(style);"
                    + "}"
                    + "function apply(){"
                    + "if(!window.__ssyoutubeMiniplayerViewActive){return;}"
                    + "ensureStyle();"
                    + "document.documentElement.classList.add('ssyoutube-miniplayer');"
                    + "var players=document.querySelectorAll(PLAYER_SELECTOR);"
                    + "var player=players.length?players[0]:null;"
                    + "var marked=document.querySelectorAll('.ssyoutube-miniplayer-player');"
                    + "for(var i=0;i<marked.length;i++){"
                    + "if(marked[i]!==player){marked[i].classList.remove('ssyoutube-miniplayer-player');}"
                    + "}"
                    + "if(player){player.classList.add('ssyoutube-miniplayer-player');}"
                    + "}"
                    + "window.__ssyoutubeMiniplayerViewActive=true;"
                    + "window.__ssyoutubeMiniplayerViewApply=apply;"
                    + "apply();"
                    + "if(!window.__ssyoutubeMiniplayerViewTimer){"
                    + "window.__ssyoutubeMiniplayerViewTimer=setInterval(apply,500);"
                    + "}"
                    + "})()";

    /** Undoes {@link #MINIPLAYER_VIEW_SCRIPT} when the video returns to the full-size view. */
    static final String MINIPLAYER_VIEW_RESET_SCRIPT =
            "(function(){"
                    + "window.__ssyoutubeMiniplayerViewActive=false;"
                    + "if(window.__ssyoutubeMiniplayerViewTimer){"
                    + "clearInterval(window.__ssyoutubeMiniplayerViewTimer);"
                    + "window.__ssyoutubeMiniplayerViewTimer=null;"
                    + "}"
                    + "var style=document.getElementById('ssyoutube-miniplayer-style');"
                    + "if(style&&style.parentNode){style.parentNode.removeChild(style);}"
                    + "document.documentElement.classList.remove('ssyoutube-miniplayer');"
                    + "var marked=document.querySelectorAll('.ssyoutube-miniplayer-player');"
                    + "for(var i=0;i<marked.length;i++){"
                    + "marked[i].classList.remove('ssyoutube-miniplayer-player');"
                    + "}"
                    + "})()";

    /** Restarts a video that was playing before its WebView was moved into the miniplayer. */
    static final String MINIPLAYER_PLAYBACK_RESUME_SCRIPT =
            "(function(){"
                    + "var attempts=0;"
                    + "function resume(){"
                    + "var video=document.querySelector('video');"
                    + "if(video&&video.paused&&!video.ended){"
                    + "var playback=video.play();"
                    + "if(playback&&playback.catch){playback.catch(function(){});}"
                    + "}"
                    + "attempts++;"
                    + "if(attempts<5){setTimeout(resume,250);}"
                    + "}"
                    + "resume();"
                    + "})()";

    /**
     * Extends how far ahead of the viewport lazy-loaded content is fetched, so roughly the
     * next two screens of search/feed results are preloaded before the user scrolls to them.
     */
    static final String RESULTS_PRELOAD_SCRIPT =
            "(function(){"
                    + "if(window.__ssyoutubeResultsPreloadInstalled){return;}"
                    + "window.__ssyoutubeResultsPreloadInstalled=true;"
                    + "var PRELOAD_SCREENS=2;"
                    + "var NativeIntersectionObserver=window.IntersectionObserver;"
                    + "if(!NativeIntersectionObserver){return;}"
                    + "function expandBottomMargin(rootMargin){"
                    + "var extra=Math.round((window.innerHeight||800)*PRELOAD_SCREENS);"
                    + "var parts=(rootMargin||'0px').trim().split(/\\s+/);"
                    + "if(parts.length===1){parts=[parts[0],parts[0],parts[0],parts[0]];}"
                    + "else if(parts.length===2){parts=[parts[0],parts[1],parts[0],parts[1]];}"
                    + "else if(parts.length===3){parts=[parts[0],parts[1],parts[2],parts[1]];}"
                    + "var bottom=parseFloat(parts[2])||0;"
                    + "var unit=/[a-z%]+$/i.exec(parts[2]||'0px');"
                    + "unit=unit?unit[0]:'px';"
                    + "if(unit!=='px'){return parts.join(' ');}"
                    + "parts[2]=(bottom+extra)+'px';"
                    + "return parts.join(' ');"
                    + "}"
                    + "function PatchedIntersectionObserver(callback,options){"
                    + "options=options||{};"
                    + "var patched={};"
                    + "for(var key in options){"
                    + "if(Object.prototype.hasOwnProperty.call(options,key)){patched[key]=options[key];}"
                    + "}"
                    + "patched.rootMargin=expandBottomMargin(options.rootMargin);"
                    + "return Reflect.construct(NativeIntersectionObserver,[callback,patched],"
                    + "new.target||PatchedIntersectionObserver);"
                    + "}"
                    + "PatchedIntersectionObserver.prototype=Object.create("
                    + "NativeIntersectionObserver.prototype,"
                    + "{constructor:{value:PatchedIntersectionObserver,writable:true,configurable:true}});"
                    + "window.IntersectionObserver=PatchedIntersectionObserver;"
                    + "})()";

    /**
     * Same-origin path the WebView requests for the bundled app logo. Requests to it never
     * reach the network, they are answered from the app resources by
     * {@link YouTubeWebViewClient#shouldInterceptRequest}.
     */
    static final String APP_LOGO_PATH = "/ssyoutube_app_logo.png";

    /** Swaps the YouTube wordmark on the page for the bundled app logo. */
    static final String APP_LOGO_SCRIPT =
            "(function(){"
                    + "var CLASS='ssyoutube-app-logo';"
                    + "var CONTAINERS='ytm-mobile-topbar-renderer .topbar-logo,"
                    + "ytm-topbar-logo-renderer,ytm-youtube-logo,.mobile-topbar-header-logo,"
                    + "ytm-logo,ytd-topbar-logo-renderer,ytd-logo,a#logo,#logo-icon,"
                    + "yt-icon#logo-icon,yt-icon.logo-icon,.ytp-watermark,.ytm-watermark,"
                    + ".branding-img-container';"
                    + "var IMAGES='img[src*=\"yt_logo\"],img[src*=\"youtube_logo\"],"
                    + "img[src*=\"ytl_logo\"],img[src*=\"watermark\"],img.branding-img';"
                    + "var LOGO_SRC=location.origin+'" + APP_LOGO_PATH + "';"
                    + "function asArray(list){return Array.prototype.slice.call(list);}"
                    + "function styleImage(image,height){"
                    + "image.alt='YouTube';"
                    + "image.src=LOGO_SRC;"
                    + "image.style.height=height+'px';"
                    + "image.style.width='auto';"
                    + "image.style.display='inline-block';"
                    + "image.style.objectFit='contain';"
                    + "}"
                    + "function createLogoImage(height){"
                    + "var image=document.createElement('img');"
                    + "image.className=CLASS;"
                    + "styleImage(image,height);"
                    + "return image;"
                    + "}"
                    + "function replaceImage(image){"
                    + "if(!image.parentElement){return;}"
                    + "if(image.classList&&image.classList.contains(CLASS)"
                    + "&&image.src===LOGO_SRC){"
                    + "styleImage(image,image.offsetHeight||24);"
                    + "return;"
                    + "}"
                    + "image.parentElement.replaceChild("
                    + "createLogoImage(image.offsetHeight||24),image);"
                    + "}"
                    + "function positionOverlay(node,overlay){"
                    + "var rect=node.getBoundingClientRect();"
                    + "var parentRect=node.parentElement.getBoundingClientRect();"
                    + "overlay.style.position='absolute';"
                    + "overlay.style.top=(rect.top-parentRect.top)+'px';"
                    + "overlay.style.left=(rect.left-parentRect.left)+'px';"
                    + "overlay.style.width=rect.width+'px';"
                    + "overlay.style.height=rect.height+'px';"
                    + "overlay.style.pointerEvents='none';"
                    + "overlay.style.margin='0';"
                    + "}"
                    // Mobile masthead custom elements (ytm-youtube-logo, ytm-topbar-logo-renderer)
                    // attach an open shadow root and render their visible logo entirely inside
                    // it, unlike desktop's ytd-* equivalents which use plain light DOM.
                    // Appending a replacement image as a light-DOM child of such a host (the
                    // desktop-style approach below) never becomes visible because these hosts
                    // define no <slot> to project it into. Instead the host itself is made
                    // transparent (but left in place and clickable, so the home link keeps
                    // working) and a positioned overlay image is inserted as a light-DOM
                    // sibling, sized and placed to match the host's on-screen box.
                    + "function replaceShadowHost(node){"
                    + "var parent=node.parentElement;"
                    + "if(!parent){return;}"
                    + "var rect=node.getBoundingClientRect();"
                    // A zero-size box means the host has not been laid out yet (e.g. right
                    // after insertion); skip for now rather than creating a malformed overlay.
                    // The periodic applyLogos() re-run (interval/mutation/navigation hooks
                    // below) retries this shortly after, once layout has settled.
                    + "if(!rect.width||!rect.height){return;}"
                    + "if(getComputedStyle(parent).position==='static'){"
                    + "parent.style.position='relative';"
                    + "}"
                    + "node.style.opacity='0';"
                    + "var overlay=node.__ssyoutubeOverlay;"
                    + "if(!overlay||!overlay.isConnected){"
                    + "overlay=createLogoImage(rect.height||24);"
                    + "node.__ssyoutubeOverlay=overlay;"
                    + "parent.appendChild(overlay);"
                    + "}else{"
                    + "styleImage(overlay,rect.height||24);"
                    + "}"
                    + "positionOverlay(node,overlay);"
                    + "}"
                    + "function replaceContainer(node){"
                    + "if(!node.querySelector){return;}"
                    + "if(node.parentElement&&node.parentElement.closest"
                    + "&&node.parentElement.closest(CONTAINERS)){return;}"
                    + "if(node.shadowRoot){replaceShadowHost(node);return;}"
                    + "var height=node.offsetHeight||24;"
                    + "var children=asArray(node.children);"
                    + "for(var i=children.length-1;i>=0;i--){"
                    + "if(children[i].classList&&children[i].classList.contains(CLASS)){continue;}"
                    + "node.removeChild(children[i]);"
                    + "}"
                    + "var existing=node.querySelector('img.'+CLASS);"
                    + "if(existing){"
                    + "styleImage(existing,height);"
                    + "return;"
                    + "}"
                    + "node.appendChild(createLogoImage(height));"
                    + "}"
                    + "function watch(target){"
                    + "new MutationObserver(function(mutations){"
                    + "for(var i=0;i<mutations.length;i++){"
                    + "for(var j=0;j<mutations[i].addedNodes.length;j++){"
                    + "var node=mutations[i].addedNodes[j];"
                    + "if(node.nodeType===1){applyLogos(node);}"
                    + "}"
                    + "}"
                    + "}).observe(target,{childList:true,subtree:true});"
                    + "}"
                    + "function observeShadowRoots(root){"
                    + "if(!root||!root.querySelectorAll){return;}"
                    + "var all=asArray(root.querySelectorAll('*'));"
                    + "for(var i=0;i<all.length;i++){"
                    + "var shadow=all[i].shadowRoot;"
                    + "if(shadow&&!shadow.__ssyoutubeLogoObserved){"
                    + "shadow.__ssyoutubeLogoObserved=true;"
                    + "applyLogos(shadow);"
                    + "watch(shadow);"
                    + "}"
                    + "}"
                    + "}"
                    // Mobile's masthead components (ytm-youtube-logo, ytm-topbar-logo-renderer)
                    // render their markup inside an open shadow root, unlike the desktop
                    // ytd-* equivalents which use plain light DOM. Regular querySelectorAll
                    // calls cannot see across that boundary, so shadow roots are located and
                    // searched (and watched for further mutations) explicitly.
                    + "function applyLogos(root){"
                    + "if(!root){return;}"
                    + "var containers=root.querySelectorAll?asArray(root.querySelectorAll(CONTAINERS)):[];"
                    + "if(root.matches&&root.matches(CONTAINERS)){containers.push(root);}"
                    + "for(var i=0;i<containers.length;i++){replaceContainer(containers[i]);}"
                    + "var images=root.querySelectorAll?asArray(root.querySelectorAll(IMAGES)):[];"
                    + "if(root.matches&&root.matches(IMAGES)){images.push(root);}"
                    + "for(var j=0;j<images.length;j++){replaceImage(images[j]);}"
                    + "observeShadowRoots(root);"
                    + "}"
                    + "applyLogos(document);"
                    + "if(window.__ssyoutubeAppLogoInstalled){return;}"
                    + "window.__ssyoutubeAppLogoInstalled=true;"
                    + "document.addEventListener('yt-navigate-finish',function(){"
                    + "applyLogos(document);"
                    + "},true);"
                    + "watch(document.documentElement);"
                    + "setInterval(function(){applyLogos(document);},1000);"
                    + "})()";

    /** Height the bundled logo is downscaled to before it is handed to the WebView. */
    private static final int APP_LOGO_HEIGHT_PX = 96;

    /**
     * Delays (in milliseconds) at which {@link #APP_LOGO_SCRIPT} is re-injected after
     * {@code onPageFinished} fires. The mobile masthead's custom elements can attach their
     * shadow DOM and lay themselves out well after the WebView considers the page "finished",
     * so a single injection right at that point can run before the logo container exists or
     * has a size, and the swap is skipped until the script's own polling catches up. Re-running
     * the injection natively at a few short delays closes that gap without waiting on the
     * in-page interval.
     */
    private static final long[] APP_LOGO_REINJECT_DELAYS_MS = {300L, 1000L, 2500L, 5000L};

    private static final String JS_INTERFACE_NAME = "ssYouTubeNative";

    private final Map<Integer, byte[]> appLogoCache = new HashMap<>();

    private final Handler logoInjectionHandler = new Handler(Looper.getMainLooper());

    private WebView webView;
    private WebView miniplayerWebView;
    private View miniplayerContainer;
    private ViewGroup rootContainer;
    private ImageButton settingsButton;
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
        rootContainer = findViewById(R.id.root_container);
        settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPreferences();
            }
        });

        configureWebView(webView);

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
        if (miniplayerWebView != null) {
            miniplayerWebView.onPause();
        }
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        if (miniplayerWebView != null) {
            miniplayerWebView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        logoInjectionHandler.removeCallbacksAndMessages(null);
        if (miniplayerWebView != null) {
            miniplayerWebView.destroy();
            miniplayerWebView = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && fullscreenView != null) {
            hideFullscreenView();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && miniplayerWebView != null) {
            expandMiniplayer();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && goBack()) {
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
        updateSettingsButton(webView.getUrl());
        if (fullscreenViewCallback != null) {
            fullscreenViewCallback.onCustomViewHidden();
            fullscreenViewCallback = null;
        }
    }

    /** Applies the shared WebView configuration used by both the primary and miniplayer views. */
    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();
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
            view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }

        // Persist the session cookies so the user only has to sign in once.
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(view, true);
        }

        view.addJavascriptInterface(new PipBridge(view), JS_INTERFACE_NAME);
        view.setWebViewClient(new YouTubeWebViewClient());
        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View customView, CustomViewCallback callback) {
                showFullscreenView(customView, callback);
            }

            @Override
            public void onHideCustomView() {
                hideFullscreenView();
            }
        });
    }

    /**
     * JavaScript bridge that lets the injected {@link #MINIPLAYER_GESTURE_SCRIPT} tell native
     * code when the user has swiped down on a playing video. Bound per-WebView so a call from a
     * WebView that isn't the currently active/primary one (e.g. a stale or backgrounded view) is
     * ignored instead of silently acting on the wrong view.
     */
    private final class PipBridge {
        private final WebView source;

        PipBridge(WebView source) {
            this.source = source;
        }

        @android.webkit.JavascriptInterface
        public void minimize(final String resultsUrl, final boolean resumePlayback) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (source != webView) {
                        return;
                    }
                    enterMiniplayer(resultsUrl, resumePlayback);
                }
            });
        }
    }

    /**
     * Shrinks the currently playing video into a small picture-in-picture window and shows
     * the cached results page (the page the user was on before opening the video) underneath.
     */
    private void enterMiniplayer(String resultsUrl, boolean resumePlayback) {
        if (miniplayerWebView != null || fullscreenView != null) {
            return;
        }
        if (resultsUrl == null || resultsUrl.isEmpty()) {
            return;
        }

        WebView videoView = webView;
        rootContainer.removeView(videoView);

        WebView resultsView = new WebView(this);
        configureWebView(resultsView);
        rootContainer.addView(resultsView, 0, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        resultsView.loadUrl(resultsUrl);
        webView = resultsView;

        FrameLayout container = new FrameLayout(this);
        container.setBackgroundResource(R.drawable.bg_miniplayer);
        container.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        container.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                expandMiniplayer();
            }
        });

        int closeSize = getResources().getDimensionPixelSize(R.dimen.miniplayer_close_size);
        ImageButton closeButton = new ImageButton(this);
        closeButton.setImageResource(R.drawable.ic_close);
        closeButton.setBackgroundResource(R.drawable.bg_settings_button);
        closeButton.setContentDescription(getString(R.string.close_miniplayer));
        closeButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(closeSize, closeSize);
        closeParams.gravity = Gravity.TOP | Gravity.END;
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeMiniplayer();
            }
        });
        container.addView(closeButton, closeParams);

        int width = getResources().getDimensionPixelSize(R.dimen.miniplayer_width);
        int height = getResources().getDimensionPixelSize(R.dimen.miniplayer_height);
        int margin = getResources().getDimensionPixelSize(R.dimen.miniplayer_margin);
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(width, height);
        containerParams.gravity = Gravity.BOTTOM | Gravity.END;
        containerParams.rightMargin = margin;
        containerParams.bottomMargin = margin;
        rootContainer.addView(container, containerParams);

        miniplayerWebView = videoView;
        miniplayerContainer = container;
        videoView.evaluateJavascript(MINIPLAYER_VIEW_SCRIPT, null);
        if (resumePlayback) {
            videoView.evaluateJavascript(MINIPLAYER_PLAYBACK_RESUME_SCRIPT, null);
        }
        settingsButton.bringToFront();
        updateSettingsButton(resultsView.getUrl());
    }

    /** Re-applies the video-only miniplayer styling if {@code view} is the miniplayer WebView. */
    private void reapplyMiniplayerView(WebView view) {
        if (miniplayerWebView != null && view == miniplayerWebView) {
            view.evaluateJavascript(MINIPLAYER_VIEW_SCRIPT, null);
        }
    }

    /** Restores the miniplayer video to fullscreen, discarding the temporary results page. */
    private void expandMiniplayer() {
        if (miniplayerWebView == null) {
            return;
        }
        WebView videoView = miniplayerWebView;
        View container = miniplayerContainer;
        miniplayerWebView = null;
        miniplayerContainer = null;

        videoView.evaluateJavascript(MINIPLAYER_VIEW_RESET_SCRIPT, null);
        ((ViewGroup) videoView.getParent()).removeView(videoView);
        rootContainer.removeView(container);

        WebView resultsView = webView;
        rootContainer.removeView(resultsView);
        resultsView.destroy();

        webView = videoView;
        rootContainer.addView(videoView, 0, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        settingsButton.bringToFront();
        updateSettingsButton(videoView.getUrl());
    }

    /** Dismisses the miniplayer video entirely, keeping the results page as the primary view. */
    private void closeMiniplayer() {
        if (miniplayerWebView == null) {
            return;
        }
        WebView videoView = miniplayerWebView;
        View container = miniplayerContainer;
        miniplayerWebView = null;
        miniplayerContainer = null;

        rootContainer.removeView(container);
        videoView.stopLoading();
        videoView.destroy();
        settingsButton.bringToFront();
        updateSettingsButton(webView.getUrl());
    }

    private boolean goBack() {
        return navigate(backSteps());
    }

    private boolean goForward() {
        return navigate(forwardSteps());
    }

    private int backSteps() {
        WebBackForwardList history = webView.copyBackForwardList();
        return NavigationHistory.backSteps(historyUrls(history), history.getCurrentIndex());
    }

    private int forwardSteps() {
        WebBackForwardList history = webView.copyBackForwardList();
        return NavigationHistory.forwardSteps(historyUrls(history), history.getCurrentIndex());
    }

    private boolean navigate(int steps) {
        if (steps == 0 || !webView.canGoBackOrForward(steps)) {
            return false;
        }
        webView.goBackOrForward(steps);
        return true;
    }

    private List<String> historyUrls(WebBackForwardList history) {
        List<String> urls = new ArrayList<>(history.getSize());
        for (int i = 0; i < history.getSize(); i++) {
            urls.add(history.getItemAtIndex(i).getUrl());
        }
        return urls;
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

    private void openPreferencePanel(AlertDialog dialog) {
        settingsButton.setImageResource(R.drawable.ic_close);
        settingsButton.setContentDescription(getString(R.string.close));
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }

    private void closePreferencePanel() {
        settingsButton.animate().translationY(0f).setDuration(
                getResources().getInteger(android.R.integer.config_shortAnimTime))
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        settingsButton.setImageResource(R.drawable.ic_settings);
                        settingsButton.setContentDescription(getString(R.string.settings));
                    }
                });
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPreferences();
            }
        });
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
                webView.loadUrl(Preferences.siteModeUrl(webView.getUrl(), wantsDesktop));
            }
        });

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.preferences)
                .setView(content)
                .create();

        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setBackgroundDrawableResource(R.drawable.bg_preference_panel);
            dialogWindow.setGravity(Gravity.BOTTOM);
            dialogWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            dialogWindow.setWindowAnimations(R.style.PreferencePanelAnimation);
        }

        ImageButton backButton = content.findViewById(R.id.back_button);
        ImageButton forwardButton = content.findViewById(R.id.forward_button);
        backButton.setEnabled(backSteps() != 0);
        forwardButton.setEnabled(forwardSteps() != 0);
        backButton.setAlpha(backButton.isEnabled() ? 1f : 0.4f);
        forwardButton.setAlpha(forwardButton.isEnabled() ? 1f : 0.4f);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (goBack()) {
                    dialog.dismiss();
                }
            }
        });
        forwardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (goForward()) {
                    dialog.dismiss();
                }
            }
        });
        content.findViewById(R.id.refresh_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.reload();
                dialog.dismiss();
            }
        });
        content.findViewById(R.id.home_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.loadUrl(Preferences.homeUrl(desktopMode));
                dialog.dismiss();
            }
        });

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                closePreferencePanel();
            }
        });

        openPreferencePanel(dialog);
        dialog.show();

        content.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        int panelHeight = content.getHeight();
                        if (panelHeight <= 0) {
                            return;
                        }
                        content.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        settingsButton.animate().translationY(-panelHeight).setDuration(
                                getResources().getInteger(android.R.integer.config_shortAnimTime));
                    }
                });
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

    /**
     * Decodes the bundled logo once per theme, downscaled to roughly the size the page
     * renders it at, and keeps the encoded bytes around for later requests.
     *
     * @param resource the drawable holding the logo for the active theme
     * @return the encoded PNG bytes of the downscaled logo
     */
    private synchronized byte[] appLogoBytes(int resource) {
        byte[] cached = appLogoCache.get(resource);
        if (cached != null) {
            return cached;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), resource, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (bounds.outHeight / (options.inSampleSize * 2) >= APP_LOGO_HEIGHT_PX) {
            options.inSampleSize *= 2;
        }
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resource, options);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        if (bitmap != null) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, encoded);
            bitmap.recycle();
        }
        byte[] bytes = encoded.toByteArray();
        appLogoCache.put(resource, bytes);
        return bytes;
    }

    /**
     * @param url a request URL seen by the WebView
     * @return true when the request is the WebView asking for the bundled app logo
     */
    static boolean isAppLogoRequest(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        int pathStart = lower.indexOf('/', lower.indexOf("://") + 3);
        if (pathStart < 0) {
            return false;
        }
        int pathEnd = lower.length();
        for (int i = pathStart; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == '?' || c == '#') {
                pathEnd = i;
                break;
            }
        }
        return lower.substring(pathStart, pathEnd).equals(APP_LOGO_PATH);
    }

    private class YouTubeWebViewClient extends WebViewClient {

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (isAppLogoRequest(url)) {
                return appLogoResponse();
            }
            if (AdBlocker.isAd(url)) {
                return emptyResponse();
            }
            return super.shouldInterceptRequest(view, request);
        }

        @SuppressWarnings("deprecation")
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            if (isAppLogoRequest(url)) {
                return appLogoResponse();
            }
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
            logoInjectionHandler.removeCallbacksAndMessages(null);
            updateSettingsButton(url);
            view.evaluateJavascript(AD_JSON_PRUNE_SCRIPT, null);
            view.evaluateJavascript(AD_HIDING_SCRIPT, null);
            view.evaluateJavascript(BUY_NOW_CLEANUP_SCRIPT, null);
            view.evaluateJavascript(PLAYABLES_CLEANUP_SCRIPT, null);
            view.evaluateJavascript(POSTS_CLEANUP_SCRIPT, null);
            view.evaluateJavascript(VIDEO_THUMBNAIL_POSTER_SCRIPT, null);
            view.evaluateJavascript(SUBSCRIBER_COUNT_SCRIPT, null);
            view.evaluateJavascript(FULLSCREEN_GESTURE_SCRIPT, null);
            view.evaluateJavascript(MINIPLAYER_GESTURE_SCRIPT, null);
            view.evaluateJavascript(RESULTS_PRELOAD_SCRIPT, null);
            view.evaluateJavascript(APP_LOGO_SCRIPT, null);
            reapplyMiniplayerView(view);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            updateSettingsButton(url);
            view.evaluateJavascript(AD_JSON_PRUNE_SCRIPT, null);
            view.evaluateJavascript(AD_HIDING_SCRIPT, null);
            view.evaluateJavascript(BUY_NOW_CLEANUP_SCRIPT, null);
            view.evaluateJavascript(PLAYABLES_CLEANUP_SCRIPT, null);
            view.evaluateJavascript(POSTS_CLEANUP_SCRIPT, null);
            view.evaluateJavascript(VIDEO_THUMBNAIL_POSTER_SCRIPT, null);
            view.evaluateJavascript(SUBSCRIBER_COUNT_SCRIPT, null);
            view.evaluateJavascript(FULLSCREEN_GESTURE_SCRIPT, null);
            view.evaluateJavascript(MINIPLAYER_GESTURE_SCRIPT, null);
            view.evaluateJavascript(RESULTS_PRELOAD_SCRIPT, null);
            view.evaluateJavascript(APP_LOGO_SCRIPT, null);
            reapplyMiniplayerView(view);
            CookieManager.getInstance().flush();
            scheduleAppLogoReinjection(view);
        }

        /**
         * Re-runs {@link MainActivity#APP_LOGO_SCRIPT} at a few short delays after the page
         * finishes loading. See {@link MainActivity#APP_LOGO_REINJECT_DELAYS_MS} for why a
         * single injection at {@code onPageFinished} is not always enough on the mobile site.
         */
        private void scheduleAppLogoReinjection(WebView view) {
            WeakReference<WebView> viewRef = new WeakReference<>(view);
            for (long delayMs : APP_LOGO_REINJECT_DELAYS_MS) {
                logoInjectionHandler.postDelayed(() -> {
                    WebView target = viewRef.get();
                    if (target != null && target.isAttachedToWindow()) {
                        target.evaluateJavascript(APP_LOGO_SCRIPT, null);
                    }
                }, delayMs);
            }
        }

        private WebResourceResponse appLogoResponse() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "no-cache");
            headers.put("Access-Control-Allow-Origin", "*");
            return new WebResourceResponse("image/png", null, 200, "OK", headers,
                    new ByteArrayInputStream(appLogoBytes(appLogoResource())));
        }

        private int appLogoResource() {
            int nightMode = getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            return nightMode == Configuration.UI_MODE_NIGHT_YES
                    ? R.drawable.app_logo_dark : R.drawable.app_logo_light;
        }

        private WebResourceResponse emptyResponse() {
            return new WebResourceResponse("text/plain", "utf-8",
                    new ByteArrayInputStream(new byte[0]));
        }
    }
}
