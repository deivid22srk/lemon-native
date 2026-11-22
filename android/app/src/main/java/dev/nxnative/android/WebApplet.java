package dev.nxnative.android;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The offline HTML page applet (nn::web::ShowOfflineHtmlPage, the host's nxhost_web_page_callback through nxlaunch.c): the page a game ships as its
 * HtmlDocument content (a launcher menu, a manual), shown in a WebView over the game. The packer copied that content to <pack>/htdocs/ (pack_android.py
 * place_htdocs); its RomFS root is html-document/, so a document "menu.htdocs/index.html" is <pack>/htdocs/html-document/menu.htdocs/index.html.
 *
 * <p>The page is served over a private https origin (shouldInterceptRequest) instead of file://, so its XMLHttpRequest reads of pages/*.html and its fonts
 * and images work under the WebView's file rules. It talks to the applet with {@code window.nx} (playSystemSe, footer): the Switch's nn::web JS extension is
 * answered by an injected no-op stub, else the page's guards ({@code window.nx && window.nx.footer}) simply skip those sounds and footer labels. Leaving the
 * page means navigating to {@code http://localhost/...} (Assassin's Creed III Remastered's menu picks a game with
 * {@code http://localhost/selectgame-1|2}): the navigation is intercepted and becomes the applet's result, the exit reason 3 the launcher reads with
 * nn::web::OfflineHtmlPageReturnValue (its last URL is the one the launcher parses).
 *
 * <p>{@link #ask} runs on a host thread and blocks until then, as the keyboard applet's does; the game's screen ({@link #screen}) shows the WebView, and a
 * screen created again shows it again. When the screen is closed for good the host gets a cancel. Touch works through the WebView; the gamepad is still the
 * game's (the launcher routes it once the page's own key handling is wired).
 */
final class WebApplet {
    private WebApplet() {}

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final String HOST = "nxnative.local";
    private static final String ORIGIN = "https://" + HOST + "/";

    private static final class Request {
        final String doc;
        String url;             // the last URL the page navigated to (null = cancelled)
        int reason;             // the applet's exit reason
        boolean done;
        WebView view;
        Request(String doc) { this.doc = doc; }
    }

    private static GameActivity screen;
    private static File pack;
    private static File gameDir;
    private static Request pending;

    private static String utf8(byte[] b) { return b == null ? "" : new String(b, StandardCharsets.UTF_8); }

    /** Called by nxlaunch.c on a thread of its own: shows the page and returns "<exit reason>\t<last URL>" as UTF-8, or null if the applet is unavailable */
    static byte[] ask(byte[] doc) {
        Request r = new Request(utf8(doc));
        synchronized (WebApplet.class) { pending = r; }
        UI.post(WebApplet::show);
        synchronized (r) {
            while (!r.done) {
                try { r.wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
        synchronized (WebApplet.class) { if (pending == r) pending = null; }
        if (r.url == null) return null;
        return (r.reason + "\t" + r.url).getBytes(StandardCharsets.UTF_8);
    }

    private static void finish(Request r, String url, int reason) {   // UI thread
        if (r.view != null) {
            if (r.view.getParent() instanceof FrameLayout) ((FrameLayout) r.view.getParent()).removeView(r.view);
            r.view.destroy();
            r.view = null;
        }
        synchronized (WebApplet.class) { if (pending == r) pending = null; }
        synchronized (r) { r.url = url; r.reason = reason; r.done = true; r.notifyAll(); }
    }

    /** The game's screen is up (onCreate): a page the game is waiting on shows on it */
    static void screen(GameActivity a, File packDir, File game) {
        screen = a; pack = packDir; gameDir = game == null ? null : game.getParentFile();
        show();
    }

    /** The game's screen goes (onDestroy). Closed for good (the game stopped): the host gets a cancel */
    static void gone(GameActivity a) {
        if (screen != a) return;
        screen = null;
        Request r;
        synchronized (WebApplet.class) { r = pending; }
        if (r != null && a.isFinishing()) {
            Native.note("[RED] web applet: the game's screen closed while its page was up: the game gets a cancel");
            finish(r, null, 0);
        }
    }

    private static void show() {   // UI thread
        Request r;
        synchronized (WebApplet.class) { r = pending; }
        GameActivity a = screen;
        if (r == null || r.done || r.view != null || a == null || a.isFinishing() || a.isDestroyed()) return;
        FrameLayout root = a.rootView();
        if (root == null) return;
        final File base = documentRoot(pack, gameDir);
        if (base == null) {
            Native.note("[RED] web applet: no htdocs in the pack or beside the game (Prepare the game again, or put the HtmlDocument page in <game folder>/htdocs/)");
            finish(r, null, 0);
            return;
        }

        WebView w = new WebView(a);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        // The page's CSS sets its rem unit only at exactly 1280px or 1920px (size.css); the phone's CSS viewport is neither, so the layout would be ~1.5x too
        // large and the taps would land off the controls. Force the console's 1280x720 viewport (fixViewport below), which the WebView scales to the screen.
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        w.setBackgroundColor(Color.BLACK);
        w.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url != null && url.startsWith("http://localhost")) { finish(r, url, 3); return true; }   // the applet's result, as on the console
                return false;
            }
            @Override public void onPageStarted(WebView v, String url, Bitmap favicon) { v.evaluateJavascript(NX_STUB, null); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                if (req == null || req.getUrl() == null || !HOST.equals(req.getUrl().getHost())) return null;
                String path = req.getUrl().getPath();
                if (path == null) return null;
                if (path.startsWith("/")) path = path.substring(1);
                if (path.contains("..")) return null;
                File f = new File(base, path);
                try {
                    if (!f.isFile()) return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                    byte[] data = read(f);
                    String m = mime(f.getName());
                    if (m.startsWith("text/html")) data = fixViewport(data);
                    return new WebResourceResponse(m, "utf-8", new ByteArrayInputStream(data));
                } catch (IOException e) {
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
            }
        });
        String doc = r.doc.isEmpty() ? "menu.htdocs/index.html" : r.doc;
        if (doc.startsWith("/")) doc = doc.substring(1);
        String query = "";
        int q = doc.indexOf('?');
        if (q >= 0) { query = doc.substring(q); doc = doc.substring(0, q); }
        w.loadUrl(ORIGIN + doc + query);
        w.setFocusableInTouchMode(true);
        root.addView(w, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        w.requestFocus();
        r.view = w;
    }

    // The document root: the pack's htdocs/ (the packer's copy of the HtmlDocument content) or the one beside the game folder, nested (html-document/) or flat
    private static File documentRoot(File pack, File gameDir) {
        File[] bases = {
            pack == null ? null : new File(pack, "htdocs/html-document"),
            pack == null ? null : new File(pack, "htdocs"),
            gameDir == null ? null : new File(gameDir, "htdocs/html-document"),
            gameDir == null ? null : new File(gameDir, "htdocs"),
        };
        for (File f : bases) if (f != null && f.isDirectory()) return f;
        return null;
    }

    private static byte[] read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            long n = f.length();
            byte[] b = new byte[(int) Math.min(n, Integer.MAX_VALUE)];
            int off = 0;
            while (off < b.length) { int k = in.read(b, off, b.length - off); if (k < 0) break; off += k; }
            if (off == b.length) return b;
            byte[] t = new byte[off]; System.arraycopy(b, 0, t, 0, off); return t;
        }
    }

    private static String mime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html";
        if (n.endsWith(".js")) return "application/javascript";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".ttf")) return "font/ttf";
        if (n.endsWith(".otf")) return "font/otf";
        if (n.endsWith(".woff")) return "font/woff";
        if (n.endsWith(".woff2")) return "font/woff2";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".wav")) return "audio/wav";
        if (n.endsWith(".mp4")) return "video/mp4";
        return "application/octet-stream";
    }

    // window.nx, the nn::web JS extension the page guards every use of. No-ops: the system sounds and the footer labels simply do nothing here.
    // The page's CSS defines its rem unit only under @media (width: 1280px) or (width: 1920px) (size.css). The WebView has no meta of its own, and the game's
    // says width=device-width, so on a phone the layout runs at the browser default (16px a rem) and everything is a third too large. Rewrite it to the console's
    // 1280x720, which useWideViewPort + loadWithOverviewMode then scale to the screen.
    private static final java.util.regex.Pattern VIEWPORT_META = java.util.regex.Pattern.compile("(?i)<meta[^>]*name=[\"']viewport[\"'][^>]*>");
    private static byte[] fixViewport(byte[] data) {
        String html = new String(data, StandardCharsets.UTF_8);
        if (html.indexOf("viewport") < 0) return data;
        String out = VIEWPORT_META.matcher(html).replaceFirst("<meta name=\"viewport\" content=\"width=1280, height=720, user-scalable=no\">");
        return out.equals(html) ? data : out.getBytes(StandardCharsets.UTF_8);
    }

    /** The applet is up: GameActivity sends it a controller's buttons as the page's own key names instead of the game's */
    static boolean up() { synchronized (WebApplet.class) { return pending != null; } }
    /** A controller button as the page's key name (handleKeyEvent), on the UI thread. Unknown key codes return null */
    static String pageKey(int keycode) {
        switch (keycode) {
            case android.view.KeyEvent.KEYCODE_BUTTON_A: case android.view.KeyEvent.KEYCODE_DPAD_CENTER: case android.view.KeyEvent.KEYCODE_ENTER: return "A";
            case android.view.KeyEvent.KEYCODE_BUTTON_B: case android.view.KeyEvent.KEYCODE_BACK: return "B";
            case android.view.KeyEvent.KEYCODE_BUTTON_X: return "X";
            case android.view.KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case android.view.KeyEvent.KEYCODE_BUTTON_L1: return "L";
            case android.view.KeyEvent.KEYCODE_BUTTON_R1: return "R";
            case android.view.KeyEvent.KEYCODE_BUTTON_L2: return "ZL";
            case android.view.KeyEvent.KEYCODE_BUTTON_R2: return "ZR";
            case android.view.KeyEvent.KEYCODE_BUTTON_START: return "ADD";
            case android.view.KeyEvent.KEYCODE_BUTTON_SELECT: return "MINUS";
            case android.view.KeyEvent.KEYCODE_DPAD_UP: return "UP";
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN: return "DOWN";
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT: return "LEFT";
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT: return "RIGHT";
            default: return null;
        }
    }
    static void key(String pageKey) {
        Request r;
        synchronized (WebApplet.class) { r = pending; }
        if (r == null || r.view == null) return;
        r.view.evaluateJavascript("(function(){try{if(window.controller&&controller.handleKeyEvent)controller.handleKeyEvent('" + pageKey + "');}catch(e){}})();", null);
    }

    private static final String NX_STUB =
            "(function(){if(window.nx)return;function noop(){return true;}" +
            "window.nx={playSystemSe:noop,playSystemBgm:noop," +
            "footer:{assign:noop,unsetAssign:noop,show:noop,hid:noop,setLabel:noop,setTitle:noop,setArrow:noop}};})();";
}
