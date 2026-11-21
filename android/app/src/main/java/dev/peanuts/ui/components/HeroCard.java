package dev.peanuts.ui.components;

import android.app.Activity;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.FrameLayout;
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
 * The gradient surface (the lemon's primary container into a leaf tone of the same depth) is the design
 * contract's only gradient beside the primary CTA; the text on both ends sits in the annotated container
 * range check-palette.py proves (accent_tint and the card-side mix, both at the container's depth). The
 * card carries the game's tile icon, its name in the title tier, a caption line and two actions: Play (the
 * page's one filled accent, Kind.PRIMARY) and Manage (quiet). Library wires the D-pad focus, the haptics
 * and the keyboard paths onto the returned view (track / focusRing / shoulders), the way it wires a cover.
 */
public final class HeroCard {
    private HeroCard() {}

    /** The hero card for a Ready row with a launchable game (Library builds the actions and wires the focus). */
    public static View view(Activity a, CharSequence name, Bitmap icon, CharSequence caption, CharSequence played,
                            Runnable onPlay, Runnable onManage) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{PUi.col(a, R.color.bn_accent_tint), leafDeep(a)});
        g.setCornerRadius(PUi.dp(a, Tokens.RADIUS_LG));   // a card: the 20 dp tier
        c.setBackground(g);
        int p = PUi.dp(a, 16);
        c.setPadding(p, p, p, p);

        LinearLayout top = PUi.row(a);
        TextView tile = PUi.tile(a, name, 64);
        if (icon != null) PUi.tileIcon(tile, icon);
        top.addView(tile, new FrameLayout.LayoutParams(PUi.dp(a, 64), PUi.dp(a, 64)));

        LinearLayout info = new LinearLayout(a);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(PUi.dp(a, 14), 0, PUi.dp(a, 4), 0);
        TextView n = PUi.text(a, name, Tokens.TYPE_TITLE, R.color.bn_text, 800);   // the title tier: the hero is the page's headline
        n.setLetterSpacing(-0.01f);
        n.setMaxLines(2);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(n);
        if (caption != null && caption.length() > 0) info.addView(PUi.small(a, caption));
        if (played != null && played.length() > 0) {
            TextView w = PUi.small(a, played);
            w.setTextColor(PUi.col(a, R.color.bn_text_2));
            info.addView(w);
        }
        top.addView(info, PUi.weight());
        c.addView(top);

        LinearLayout actions = PUi.row(a);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        playLp.topMargin = PUi.dp(a, 12);
        actions.addView(PUi.button(a, a.getString(R.string.lemon_hero_play), PUi.Kind.PRIMARY,
                v -> { Haptics.play(v, Haptics.Word.HEAVY); onPlay.run(); }), playLp);
        LinearLayout.LayoutParams mgLp = PUi.wrap();
        mgLp.topMargin = PUi.dp(a, 12);
        mgLp.leftMargin = PUi.dp(a, 8);
        actions.addView(PUi.button(a, a.getString(R.string.lemon_hero_manage), PUi.Kind.QUIET,
                v -> { Haptics.play(v, Haptics.Word.CLICK); onManage.run(); }), mgLp);
        c.addView(actions);
        return c;
    }

    /** The gradient's far end: the leaf support's hue held at the primary container's depth (the subtle lemon -> leaf ramp). */
    private static int leafDeep(Activity a) {
        float f = 0.35f;
        int s = PUi.col(a, R.color.bn_secondary), card = PUi.col(a, R.color.bn_card);
        return android.graphics.Color.argb(255,
                Math.round(android.graphics.Color.red(s) * f + android.graphics.Color.red(card) * (1 - f)),
                Math.round(android.graphics.Color.green(s) * f + android.graphics.Color.green(card) * (1 - f)),
                Math.round(android.graphics.Color.blue(s) * f + android.graphics.Color.blue(card) * (1 - f)));
    }
}
