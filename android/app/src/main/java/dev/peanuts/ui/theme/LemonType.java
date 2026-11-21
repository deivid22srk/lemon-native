package dev.peanuts.ui.theme;

import android.content.Context;
import android.graphics.Typeface;

import dev.nxnative.android.R;

/**
 * Lemon type (docs/design-system.md section 3): Manrope (OFL, shipped in res/font) carries every weight the
 * design system's builders ask for — face(): 400 body, 500 medium, 600 semibold, 700 heading, 800 title and
 * display — and JetBrains Mono (OFL, res/font) carries numeric readouts, versions and hashes (mono(): the
 * build card's percent, a companion's version value). The fonts ship with the app, so no network and no
 * system font family is involved, and the type is identical on every device.
 *
 * init() runs once per process from NxApp.onCreate (the LEMON hook) and caches the five instances; face()
 * falls back to the system sans serif before that and on any failure, so a screen is never blank or
 * unstyled (the same guarantee Ui.face already gave).
 */
public final class LemonType {
    private LemonType() {}

    private static Typeface[] faces;   // by weight bucket: regular, medium, semibold, bold, extrabold
    private static Typeface monoFace;

    /** NxApp.onCreate (LEMON hook), every process: the launcher, :game, :pack, :readiness, :selftest. */
    public static void init(Context c) {
        try {
            android.content.res.Resources r = c.getResources();
            faces = new Typeface[]{
                    r.getFont(R.font.manrope_regular),
                    r.getFont(R.font.manrope_medium),
                    r.getFont(R.font.manrope_semibold),
                    r.getFont(R.font.manrope_bold),
                    r.getFont(R.font.manrope_extrabold)};
            monoFace = r.getFont(R.font.jbmono_medium);   // LEMON: the medium instance, so numeric readouts need no synthetic bold
        } catch (RuntimeException e) {
            faces = null;   // monoFace stays null too: mono() then falls back to the system monospace
        }
    }

    /** Ui.face / PUi.face: the weight the design system asks for, as the nearest bundled Manrope instance. */
    public static Typeface face(int weight) {
        if (faces == null) return Typeface.create(Typeface.SANS_SERIF, weight, false);
        int i = weight <= 450 ? 0 : weight <= 550 ? 1 : weight <= 650 ? 2 : weight <= 750 ? 3 : 4;
        return faces[i];
    }

    /** Numeric readouts, versions and hashes (Library's build percent, CompanionVersions): JetBrains Mono. */
    public static Typeface mono(Context c) {
        if (monoFace == null) {
            try { monoFace = c.getResources().getFont(R.font.jbmono_medium); }
            catch (RuntimeException e) { return Typeface.MONOSPACE; }
        }
        return monoFace;
    }
}
