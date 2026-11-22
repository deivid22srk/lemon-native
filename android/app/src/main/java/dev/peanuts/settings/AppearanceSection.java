package dev.peanuts.settings;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import dev.nxnative.android.R;
import dev.peanuts.PeanutsPrefs;
import dev.peanuts.ui.Haptics;
import dev.peanuts.ui.PUi;

/**
 * Settings › Appearance (LEMON, docs/design-system.md section 2): the app's dark default and its two
 * alternatives. Dark is the reference look of the design contract; the light theme is the same system
 * re-mapped, not an afterthought; and "Follow system" hands the choice back to Android. The preference is
 * applied through {@link UiModeManager#setApplicationNightMode} (Android 12's per-app night mode, the
 * non-AndroidX equivalent of a default night mode): the system persists it, applies it to every activity
 * of the app (recreating them through the uiMode configuration change, which no manifest line suppresses)
 * and reaches the :game process's surfaces the same way.
 *
 * The launcher's first run starts dark — Lemon's identity — unless the player picks otherwise here
 * (applyDefault, an NxApp hook; the one-shot flag lives in the app's own prefs, PeanutsPrefs).
 */
public final class AppearanceSection {
    private AppearanceSection() {}

    private static final String DARK = "dark", LIGHT = "light", SYSTEM = "system";

    /** NxApp.onCreate (LEMON hook), every process: the first run starts dark. */
    public static void applyDefault(Context c) {
        if (PeanutsPrefs.nightDefaultDone(c)) return;
        PeanutsPrefs.nightDefaultDone(c, true);
        apply(c, PeanutsPrefs.nightMode(c));
    }

    /** Settings › Appearance, right after Settings › Library (MainActivity's buildSettings, the LEMON hook). */
    public static void add(Activity a, LinearLayout content) {
        PUi.add(content, PUi.section(a, a.getString(R.string.lemon_appearance)), PUi.SECTION_GAP);
        LinearLayout card = PUi.card(a);
        int p = PUi.dp(a, 16);
        card.setPadding(p, p, p, p);
        card.addView(PUi.small(a, a.getString(R.string.lemon_appearance_note)));
        LinearLayout row = PUi.row(a);
        row.setPadding(0, PUi.dp(a, 10), 0, 0);
        String cur = PeanutsPrefs.nightMode(a);
        row.addView(option(a, a.getString(R.string.lemon_appearance_dark), DARK, cur));
        row.addView(option(a, a.getString(R.string.lemon_appearance_light), LIGHT, cur));
        row.addView(option(a, a.getString(R.string.lemon_appearance_system), SYSTEM, cur));
        card.addView(row);
        PUi.add(content, card, 12);
    }

    /** One choice: the active one in the tonal accent (the selection container, never a filled accent). */
    private static View option(Activity a, String label, String value, String current) {
        Button b = PUi.button(a, label, value.equals(current) ? PUi.Kind.TONAL_ACCENT : PUi.Kind.TONAL, v -> {
            Haptics.play(v, Haptics.Word.CLICK);
            PeanutsPrefs.nightMode(a, value);
            apply(a, value);
        });
        LinearLayout.LayoutParams lp = PUi.wrap();
        if (!DARK.equals(value)) lp.leftMargin = PUi.dp(a, 8);
        return b;
    }

    /** The choice applied (LEMON: public for the setup's Appearance step, dev.peanuts.ui.SetupActivity — the same one door Settings uses). */
    public static void apply(Context c, String value) {
        UiModeManager m = c.getSystemService(UiModeManager.class);
        if (m == null) return;
        m.setApplicationNightMode(LIGHT.equals(value) ? UiModeManager.MODE_NIGHT_NO
                : SYSTEM.equals(value) ? UiModeManager.MODE_NIGHT_AUTO
                : UiModeManager.MODE_NIGHT_YES);
    }
}
