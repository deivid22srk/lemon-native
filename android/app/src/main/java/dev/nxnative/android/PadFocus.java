package dev.nxnative.android;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.EditText;
import android.widget.ListView;

import java.util.ArrayList;

/**
 * A controller in the launcher, in the style of the in-game panel (SidePanel): the D-pad moves the View focus (Android's own focus search, so the tile grid moves in
 * two dimensions), A presses what has the focus (Android's confirm), B goes back one level (a dialog cancels, a page returns to the list: MainActivity.dispatchKeyEvent,
 * the dialogs through {@link Builder}), L1 / R1 switch a game page's tabs. The focus is drawn as a frame in the palette's two tones (not touch mode, where nothing has the
 * focus): {@link #watch} for a window, the list of a choice dialog through {@link Builder}. Touch is unchanged.
 * Public (like R and Tokens) so dev.peanuts.ui.PUi, the verbatim copy of Ui, can build the same dialogs.
 */
public final class PadFocus {
    private PadFocus() {}

    /** The faint accent wash under the ring's fill (an alpha, like Ui.page's scrim): the tone comes from bn_accent */
    private static final int FILL_ALPHA = 0x26;

    /** The focus frame: a two-tone ring (the accent outside, the on-banana brown inside) over a faint accent fill, rounded. Two tones so it shows on anything,
     *  the yellow primary buttons and the dark and cream pages alike: the accent is the deep gold on cream but the banana itself in dark mode, where only the
     *  inner brown still separates the ring from a yellow button. Both tones are palette resources (Ui.style's focus edge is the same accent), so the ring reads
     *  the same on every screen. The same look in a row, a tile, a tab, a button and a dialog's list */
    static Drawable frame(Context c) {
        int ring = Ui.dp(c, 3);   // the outer stroke's width, and the inset it leaves around the inner one
        GradientDrawable outer = new GradientDrawable();
        outer.setColor((c.getColor(R.color.bn_accent) & 0x00FFFFFF) | (FILL_ALPHA << 24));
        outer.setStroke(ring, c.getColor(R.color.bn_accent));
        outer.setCornerRadius(Ui.dp(c, Tokens.RADIUS_MD));
        GradientDrawable inner = new GradientDrawable();
        inner.setStroke(Ui.dp(c, Tokens.FOCUS_EDGE_DP), c.getColor(R.color.bn_on_banana));
        inner.setCornerRadius(Ui.dp(c, Tokens.RADIUS_ROW));
        android.graphics.drawable.LayerDrawable d = new android.graphics.drawable.LayerDrawable(new Drawable[] {outer, inner});
        d.setLayerInset(1, ring, ring, ring, ring);
        return d;
    }

    /** Whether a key is the controller's back button */
    static boolean isBack(int keyCode) { return keyCode == KeyEvent.KEYCODE_BUTTON_B; }

    /** The step of a tab switch for a shoulder button: -1 (L1), +1 (R1), else 0 */
    static int tabStep(int keyCode) { return keyCode == KeyEvent.KEYCODE_BUTTON_L1 ? -1 : keyCode == KeyEvent.KEYCODE_BUTTON_R1 ? 1 : 0; }

    /** Puts the frame on whatever has the focus in this window, and takes it off the one that lost it. A view that draws its own focus edge already (the
     *  StateListDrawable of Ui.style and Ui.touchable: a button, an action, a card, a row, a tab) is left alone, so its edge and this ring never show together */
    static void watch(Window w) {
        final View[] lit = new View[1];
        final Drawable[] old = new Drawable[1];
        w.getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((was, now) -> {
            if (lit[0] != null) { lit[0].setForeground(old[0]); lit[0] = null; old[0] = null; }
            if (now == null || now.isInTouchMode() || now instanceof EditText || now instanceof ListView) return;
            if (now.getBackground() instanceof android.graphics.drawable.StateListDrawable) return;   // its own focused edge shows
            lit[0] = now;
            old[0] = now.getForeground();
            now.setForeground(frame(now.getContext()));
        });
    }

    /** Every focusable view under root, in focus order */
    static ArrayList<View> focusables(View root) { return root.getFocusables(View.FOCUS_FORWARD); }

    /** The view a page opens its focus on: the selected one (a tab), else the first */
    static View first(ArrayList<View> l) {
        for (View v : l) if (v.isSelected()) return v;
        return l.isEmpty() ? null : l.get(0);
    }

    /** An AlertDialog builder whose dialogs work with a controller: B cancels, the focus frame shows, a choice list shows its selection */
    public static final class Builder extends AlertDialog.Builder {
        public Builder(Context c) { super(c); }
        public Builder(Context c, int theme) { super(c, theme); }

        // PEANUTS begin: a confirm button that does something clicks (dev.peanuts.ui.Feel; an OK without a listener stays as quiet as Cancel)
        @Override public Builder setPositiveButton(CharSequence text, android.content.DialogInterface.OnClickListener l) { super.setPositiveButton(text, dev.peanuts.ui.Feel.confirm(l)); return this; }
        // PEANUTS end

        @Override public AlertDialog create() {
            AlertDialog d = super.create();
            dev.peanuts.ui.Feel.dialog(d);   // PEANUTS: a choice picked in the dialog's list clicks
            d.setOnKeyListener((di, k, e) -> {
                if (!isBack(k)) return false;
                if (e.getAction() == KeyEvent.ACTION_UP) di.cancel();
                return true;
            });
            return d;
        }

        @Override public AlertDialog show() {
            AlertDialog d = super.show();
            Window w = d.getWindow();
            if (w != null) watch(w);
            ListView l = d.getListView();
            if (l != null) l.setSelector(frame(d.getContext()));
            View f = d.getCurrentFocus();
            if (f != null && !f.isInTouchMode() && w != null) f.requestFocus();   // the frame shows on what the dialog focused first
            return d;
        }
    }
}
