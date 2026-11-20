package dev.nxnative.android;

/**
 * The launcher's design tokens (Ui.java builds every view from these, so screens stay the same): the 4/8 dp spacing grid, the corner radii, the type scale and
 * the smallest touch target. Colors are not here on purpose: they live in res/values/colors.xml (day) and values-night (dark), because resources switch with
 * the system's mode and constants cannot. The look is flat on purpose: surfaces are told apart by their fill and hairline edge, so there are no shadow tokens
 * (dialogs get their elevation from the theme).
 */
final class Tokens {
    private Tokens() {}

    // Spacing on the 4/8 grid: gaps between cards, inside cards, around sections (SPACE_1 = 4 dp up to SPACE_8 = 32 dp)
    static final int SPACE_1 = 4, SPACE_2 = 8, SPACE_3 = 12, SPACE_4 = 16,
            SPACE_5 = 20, SPACE_6 = 24, SPACE_7 = 28, SPACE_8 = 32;

    // Corner radii: small controls (rows, tiles), buttons and icon buttons, cards; ROW and INNER are the two in-between tiers the system draws
    // (a row's touch ripple, a card inside a card and a game tile). RADIUS_PILL (999) stays a literal in Ui: a fully rounded pill, not a tier
    static final int RADIUS_SM = 8, RADIUS_ROW = 12, RADIUS_MD = 14, RADIUS_INNER = 16, RADIUS_LG = 20;

    // Type scale (sp): the five sizes every text is set in (section labels and tags, secondary text, body and buttons, headings, page titles)
    static final float TYPE_CAPTION = 12, TYPE_SMALL = 13, TYPE_BODY = 15, TYPE_HEADING = 18, TYPE_TITLE = 24;

    // Touch targets (dp): the smallest tappable area of a control (buttons and icon buttons size themselves to it, so a finger never misses and a
    // screen reader's cursor has something to land on)
    static final int TOUCH_MIN = 48;

    // The status-bar scrim's alpha: the page's background color held at this alpha over the clock (Ui.page), so scrolled cards never show through
    static final int SCRIM_ALPHA = 0xEE;
}
