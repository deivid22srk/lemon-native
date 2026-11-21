package dev.peanuts.ui.components;

import android.app.Activity;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.nxnative.android.R;
import dev.nxnative.android.Tokens;
import dev.peanuts.ui.Haptics;
import dev.peanuts.ui.PUi;

/**
 * The library's hero (docs/design-system.md section 4): "Continue playing" — the one Ready game the player
 * used last, at the top of Home, so the brief's efficiency rule (app open to playing in at most two taps)
 * is met: the hero's Play is one tap at the very top of the page, above the cover grid.
 *
 * The gradient surface (the lemon's primary container into the checked leaf end bn_hero_end) is the design
 * contract's only gradient. Everything drawn on it is proven by check-palette.py's hero pairs (bn_text on
 * both ends, both modes) and both actions carry their own surfaces (Kind.PRIMARY and Kind.TONAL), so no
 * label sits directly on the gradient. The card is one focus stop: it owns its focused look (the gradient
 * stays under an accent ring at the card's radius; {@link #focusBackground}), and Library wires the
 * haptics, the keyboard paths and the play/manage actions onto it, the way it wires a cover.
 */
public final class HeroCard {
    private HeroCard() {}

    /** The hero card for a Ready row with a launchable game (Library builds the actions and wires the focus). */
    public static View view(Activity a, String name, Bitmap icon, CharSequence caption, CharSequence played,
                            Runnable onPlay, Runnable onManage) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(focusBackground(a));
        int p = PUi.dp(a, 16);
        c.setPadding(p, p, p, p);

        LinearLayout top = PUi.row(a);
        TextView tile = PUi.tile(a, name, 64);
        if (icon != null) PUi.tileIcon(tile, icon);
        top.addView(tile, new LinearLayout.LayoutParams(PUi.dp(a, 64), PUi.dp(a, 64)));

        LinearLayout info = new LinearLayout(a);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(PUi.dp(a, 14), 0, PUi.dp(a, 4), 0);
        TextView n = PUi.text(a, name, Tokens.TYPE_TITLE, R.color.bn_text, 800);   // the title tier: the hero is the page's headline
        n.setLetterSpacing(-0.01f);
        n.setMaxLines(2);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(n);
        // the caption lines are body text (bn_text): bn_text_2 misses 4.5:1 on the gradient's ends in both
        // modes, and check-palette.py now proves the four hero pairs
        if (caption != null && caption.length() > 0) {
            TextView cap = PUi.small(a, caption);
            cap.setTextColor(PUi.col(a, R.color.bn_text));
            info.addView(cap);
        }
        if (played != null && played.length() > 0) {
            TextView w = PUi.small(a, played);
            w.setTextColor(PUi.col(a, R.color.bn_text));
            info.addView(w);
        }
        top.addView(info, PUi.weight());
        c.addView(top);

        LinearLayout chipRow = PUi.row(a);
        chipRow.addView(StatusChip.chip(a, a.getString(R.string.lemon_hero_caption_ready), StatusChip.Tone.OK), StatusChip.lp(a));
        LinearLayout.LayoutParams chipLp = PUi.wrap();
        chipLp.topMargin = PUi.dp(a, 10);
        c.addView(chipRow, chipLp);

        LinearLayout actions = PUi.row(a);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        playLp.topMargin = PUi.dp(a, 12);
        actions.addView(PUi.button(a, a.getString(R.string.lemon_hero_play), PUi.Kind.PRIMARY,
                v -> { Haptics.play(v, Haptics.Word.HEAVY); onPlay.run(); }), playLp);
        LinearLayout.LayoutParams mgLp = PUi.wrap();
        mgLp.topMargin = PUi.dp(a, 12);
        mgLp.leftMargin = PUi.dp(a, 8);
        // TONAL, not QUIET: the quiet label is bn_accent on nothing, and bn_accent on the gradient's leaf end
        // misses 4.5:1 in the light mode (3.63:1) — the tonal container carries its own proven surface
        actions.addView(PUi.button(a, a.getString(R.string.lemon_hero_manage), PUi.Kind.TONAL,
                v -> { Haptics.play(v, Haptics.Word.CLICK); onManage.run(); }), mgLp);
        c.addView(actions);
        return c;
    }

    /**
     * The card's background, focus included: the default state is the gradient; the focused state is the
     * same gradient under the focus ring, at the card's radius (20 dp) — the hero never loses its surface
     * when the D-pad lands on it (round 2's Grave: the generic ring drew a transparent body over it).
     */
    private static android.graphics.drawable.Drawable focusBackground(Activity a) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{PUi.col(a, R.color.bn_accent_tint), PUi.col(a, R.color.bn_hero_end)});
        g.setCornerRadius(PUi.dp(a, Tokens.RADIUS_LG));   // a card: the 20 dp tier
        android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
        ring.setCornerRadius(PUi.dp(a, Tokens.RADIUS_LG));
        ring.setStroke(PUi.dp(a, 3), PUi.col(a, R.color.bn_accent));
        android.graphics.drawable.LayerDrawable focused = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{g, ring});
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[0], g);
        return s;
    }

}
