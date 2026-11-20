package dev.peanuts.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;

import dev.nxnative.android.R;
import dev.peanuts.Bridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * PEANUTS: the overlay's top-right block as a label / value table (Overlay.tick hands its readings here). Built from spans only, because the upstream path draws a
 * CharSequence into a bitmap and redraws when Overlay.key (text and colours) changes: one monospace face, labels padded to a fixed width, values padded to the widest,
 * a larger fps number, and the frame-time histogram as a ReplacementSpan over a placeholder whose chars encode the bars (so a new frame changes the key).
 * The style ("table" or "classic") and the preset are in the launcher's preferences file, the one Prefs uses.
 */
public final class Hud {
    private Hud() {}

    /** The launcher's SharedPreferences file (Prefs.of); a test checks it against Prefs.java */
    static final String PREFS = "launcher";
    static final String STYLE = "pn_overlay_style", PRESET = "pn_overlay_preset", CORNER = "pn_overlay_corner", SCALE = "pn_overlay_scale";
    /** The corner keys, in the host's order (vka_ovl.h: VKO_TOP_LEFT, VKO_TOP_RIGHT, VKO_BOTTOM_LEFT, VKO_BOTTOM_RIGHT) */
    static final String[] CORNERS = {"tl", "tr", "bl", "br"};
    private static int sentCorner = -1;

    /** The throttle's traffic light: the dot's levels (upstream Overlay.DOT's own trio, and HistogramSpan's bars) in the overlay's own tokens — bn_overlay_ok, bn_overlay_warn, bn_overlay_danger — stable over the game in both modes */
    private static int[] levels(Context c) { return new int[] {c.getColor(R.color.bn_overlay_ok), c.getColor(R.color.bn_overlay_warn), c.getColor(R.color.bn_overlay_danger)}; }
    /** LABEL: the widest label ("GAME") and the column every label is padded to; MIN_VALUE: the narrowest value column */
    private static final int BARS = 48, WINDOW = 512, LABEL = 4, MIN_VALUE = 10;
    private static final float GIB = 1024f * 1024f * 1024f;

    private static volatile String driver;
    private static final float[] frames = new float[WINDOW];

    @SuppressWarnings("deprecation")
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_MULTI_PROCESS); }

    private static boolean item(Context c, String key) { return prefs(c).getBoolean("overlay_" + key, true); }

    /** Whether an item of the table that Overlay.tick has no switch of its own for is on (the early return) */
    public static boolean extra(Context c) { return item(c, "graph") || item(c, "driver"); }

    /** The GPU driver's name for the DRV row (null: the system driver) */
    public static void driver(String label) {
        String s = FrameStats.shortDriver(label);
        driver = s.isEmpty() ? "System" : s;
    }

    /** One row: label, value and colours; {@code hist} is the histogram's placeholder (no value) */
    private static final class Row {
        String label = "", value = "", hist;
        int color, dot = -2;   // color: the value's ink, 0 until set (build falls back to the block's text colour); dot: -2 none, -1 grey, 0..2 level
        boolean big;
        Row(String label, String value) { this.label = label; this.value = value; }
    }

    /** The block: {@code classic} when the style is "classic", otherwise the table (null when no row) */
    public static CharSequence table(Context c, CharSequence classic, float[] rate, boolean fresh, int level, float cap, float batTemp, int batLevel, boolean charging,
                                     long rss, long memTotal, long memAvail, boolean memLow) {
        SharedPreferences p = prefs(c);
        sendCorner(p);
        int scale = p.getInt(SCALE, 100);
        int ink = c.getColor(R.color.bn_overlay_text), grey = c.getColor(R.color.bn_overlay_text_2);   // the overlay's text colours (bn_overlay_*): stable over the game in both modes
        int[] LEVEL = levels(c);   // the traffic light's three tokens, resolved once per table
        if ("classic".equals(p.getString(STYLE, "table"))) { graphOff(); return scaled(classic, scale); }
        List<Row> rows = new ArrayList<>();
        boolean fps = item(c, "fps"), dot = item(c, "throttle");
        // the frame-time window: the histogram, 1%L and FT's colour all take their target from its median
        int n = item(c, "graph") || (fps && fresh) ? readFrames() : 0;
        float target = n >= 10 ? FrameStats.median(frames, n) : Float.NaN;
        if (fps || dot) {
            Row r = new Row(fps ? "FPS" : "THR", "");
            if (fps) {
                r.value = !fresh ? "--" : rate[3] == 1 || rate[3] == 2 ? String.format(Locale.ROOT, "%.0f→%.0f", rate[0], rate[1]) : String.format(Locale.ROOT, "%.0f", rate[0]);
                r.big = true;
            }
            if (dot) r.dot = level >= 0 && level <= 2 ? level : -1;
            rows.add(r);
        }
        if (item(c, "graph")) graph(rows, n, target, LEVEL);
        if (fps && fresh) {
            Row r = new Row("FT", String.format(Locale.ROOT, "%.1f ms", rate[2]));
            if (!Float.isNaN(target) && !Float.isNaN(rate[2])) r.color = warn(c, FrameStats.level(rate[2], target));
            rows.add(r);
        }
        if (item(c, "gpu") && fresh && rate[5] >= 0) rows.add(new Row("GPU", String.format(Locale.ROOT, "%.1f ms", rate[5])));
        if (item(c, "cpu") && !Float.isNaN(cap)) {
            Row r = new Row("CPU", String.format(Locale.ROOT, "%.0f%%", cap * 100f));
            r.color = cap < 0.65f ? LEVEL[2] : cap < 0.85f ? LEVEL[1] : ink;
            rows.add(r);
        }
        if (item(c, "soc")) {
            int t = SocTemp.celsius();
            if (t != SocZones.NONE) {
                Row r = new Row("SOC", t + "°");
                r.color = t >= SocTemp.HOT ? LEVEL[2] : t >= 70 ? LEVEL[1] : ink;
                rows.add(r);
            }
        }
        if (item(c, "battery") && batLevel >= 0) {
            // the temperature drives the colour of the whole value
            Row r = new Row("BAT", (Float.isNaN(batTemp) ? "" : String.format(Locale.ROOT, "%.0f° ", batTemp)) + batLevel + "%" + (charging ? "⚡︎" : ""));
            r.color = warn(c, FrameStats.batteryLevel(batTemp));
            rows.add(r);
        }
        if (item(c, "memory")) {
            // GAME: the app's own resident memory; RAM: the phone's used / total (red " low" when it runs short)
            if (rss >= 0) rows.add(new Row("GAME", String.format(Locale.ROOT, "%.1fG", rss / GIB)));
            if (memTotal > 0) {
                Row r = new Row("RAM", String.format(Locale.ROOT, "%.1f/%.1fG", (memTotal - memAvail) / GIB, memTotal / GIB));
                if (memLow) r.value += " low";
                r.color = warn(c, FrameStats.ramLevel(memTotal - memAvail, memTotal, memLow));
                rows.add(r);
            }
        }
        String drv = driver;
        if (item(c, "driver") && drv != null) rows.add(new Row("DRV", drv));
        boolean hasGraph = false;
        for (Row r : rows) hasGraph |= r.hist != null;
        if (!hasGraph) graphOff();   // no bars in this block: the host paints none
        if (rows.isEmpty()) return null;
        return scaled(build(rows, ink, grey, LEVEL), scale);
    }

    /** The whole block at scale percent of its size (a span over all of it, so the histogram's paint is scaled too); null stays null */
    private static CharSequence scaled(CharSequence t, int scale) {
        if (t == null || scale == 100 || scale <= 0) return t;
        SpannableStringBuilder s = new SpannableStringBuilder(t);
        s.setSpan(new RelativeSizeSpan(scale / 100f), 0, s.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        return s;
    }

    /** Tells the host which corner the block is drawn in, when it changed (the choice is read here, so the first table of a game sends it too) */
    private static void sendCorner(SharedPreferences p) {
        int c = Arrays.asList(CORNERS).indexOf(p.getString(CORNER, "tr"));
        if (c < 0) c = 1;
        if (c == sentCorner) return;
        try { Bridge.overlayCorner(c); sentCorner = c; } catch (UnsatisfiedLinkError e) { /* no host */ }
    }

    private static void graphOff() {
        try { Bridge.graphRect(0, 0, 0, 0); } catch (UnsatisfiedLinkError e) { /* no host */ }
    }

    /** Copies the frame-time window into frames and returns its count (0 without a host) */
    private static int readFrames() {
        try { return Math.min(Bridge.frameTimes(frames), WINDOW); } catch (UnsatisfiedLinkError e) { return 0; }
    }

    /** A value's colour from its status: 0 the overlay's text (normal), 1 the warning amber, 2 the danger red (the overlay's tokens) */
    private static int warn(Context c, int level) {
        if (level <= 0) return c.getColor(R.color.bn_overlay_text);
        return levels(c)[level];
    }

    private static void graph(List<Row> rows, int n, float target, int[] LEVEL) {
        if (n < 10) return;
        int shown = Math.min(BARS, n);
        Row h = new Row("", "");
        h.hist = FrameStats.placeholder(frames, n - shown, shown, target);
        rows.add(h);
        float low = FrameStats.onePercentLowFps(frames, n);
        if (!Float.isNaN(low)) {
            Row r = new Row("1%L", String.format(Locale.ROOT, "%.0f", low));
            r.color = LEVEL[FrameStats.level(1000f / low, target)];
            rows.add(r);
        }
    }

    private static CharSequence build(List<Row> rows, int ink, int grey, int[] LEVEL) {
        int w = MIN_VALUE;
        for (Row r : rows) w = Math.max(w, valueLength(r));
        SpannableStringBuilder s = new SpannableStringBuilder();
        for (Row r : rows) {
            if (s.length() > 0) s.append('\n');
            int ls = s.length();
            s.append(pad(r.label, LABEL)).append(' ');
            int label = FrameStats.labelColor(r.label);
            if (label == FrameStats.LABEL_DEFAULT) label = grey;   // the plain label's grey is the overlay's secondary text (bn_overlay_text_2), like the throttle dot's
            if (!r.label.isEmpty()) s.setSpan(new ForegroundColorSpan(label), ls, ls + r.label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (r.hist != null) {
                int hs = s.length();
                s.append(r.hist);
                s.setSpan(new HistogramSpan(w, LEVEL), hs, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }
            int len = valueLength(r);
            // the fps number is 1.5 times as wide: half a char less padding for each of its chars keeps the right edge near the others
            int gap = Math.max(0, w - len - (r.big ? (int) Math.round(r.value.length() * 0.5) : 0));
            for (int i = 0; i < gap; i++) s.append(' ');
            if (r.dot > -2) {
                int d = s.length();
                s.append('●');
                s.setSpan(new ForegroundColorSpan(r.dot >= 0 ? LEVEL[r.dot] : grey), d, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (!r.value.isEmpty()) s.append(' ');
            }
            int vs = s.length();
            s.append(r.value);
            if (s.length() > vs) {
                s.setSpan(new ForegroundColorSpan(r.color != 0 ? r.color : ink), vs, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);   // 0: the row never set its colour — the block's text
                if (r.big) s.setSpan(new RelativeSizeSpan(1.5f), vs, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        s.setSpan(new TypefaceSpan("monospace"), 0, s.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        return s;
    }

    private static int valueLength(Row r) {
        if (r.hist != null) return 0;
        return (r.dot > -2 ? (r.value.isEmpty() ? 1 : 2) : 0) + r.value.length();
    }

    private static String pad(String t, int n) {
        StringBuilder b = new StringBuilder(t);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }
}
