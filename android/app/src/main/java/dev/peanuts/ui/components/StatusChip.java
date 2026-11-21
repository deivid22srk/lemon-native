package dev.peanuts.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.nxnative.android.R;
import dev.nxnative.android.Tokens;
import dev.peanuts.ui.PUi;
import dev.peanuts.ui.theme.LemonType;

/**
 * The Lemon status chip (docs/design-system.md section 4): a small pill naming a state — Ready, Build
 * needed, an update that does not fit, a file that went missing — in the palette's semantic container and
 * text colors. A chip is informational: it never fills with the accent (selection is the only filled state
 * in the design contract) and never carries a click (something that acts is a row or a button). The pill
 * radius is Tokens.RADIUS_ROW (12 dp, the chip tier), the text the small size at 600, and the container is
 * the tone's tint resource, so the chip reads in both themes and passes check-palette.py's pairs.
 */
public final class StatusChip {
    private StatusChip() {}

    /** The semantic tones: success, work in progress, caution, error, and the neutral one (informational). */
    public enum Tone {
        OK(R.color.bn_ok_tint, R.color.bn_ok),
        BUILD(R.color.bn_accent_tint, R.color.bn_accent),
        WARN(R.color.bn_warn_tint, R.color.bn_warn),
        ERROR(R.color.bn_danger_tint, R.color.bn_danger),
        NEUTRAL(R.color.bn_tint, R.color.bn_text_2);

        final int fill, text;

        Tone(int fill, int text) { this.fill = fill; this.text = text; }
    }

    /** A chip view (the caller sizes it: wrap content, 8 dp margins where it sits beside siblings). */
    public static TextView chip(Context c, CharSequence s, Tone tone) {
        TextView t = PUi.text(c, s, Tokens.TYPE_SMALL, tone.text, 600);
        t.setBackground(PUi.round(PUi.col(c, tone.fill), PUi.dp(c, Tokens.RADIUS_ROW), 0, 0));
        int px = PUi.dp(c, 10), py = PUi.dp(c, 4);
        t.setPadding(px, py, px, py);
        t.setTextColor(PUi.col(c, tone.text));
        return t;
    }

    /** The numeric variant (a percent, a version): JetBrains Mono, so digits align with the readouts elsewhere. */
    public static TextView chipNumeric(Context c, CharSequence s, Tone tone) {
        TextView t = chip(c, s, tone);
        t.setTypeface(LemonType.mono(c));   // JetBrains Mono Medium: a real weight, no synthetic bold
        return t;
    }

    /** Wrap-content layout params with the chip's standard side gap (8 dp on the 4/8 grid). */
    public static LinearLayout.LayoutParams lp(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = PUi.dp(c, 8);
        return lp;
    }
}
