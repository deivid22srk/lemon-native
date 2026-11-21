package dev.peanuts.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.nxnative.android.BananaBackgroundView;   // the launcher's drifting lemon grid behind the page (public, another package: the import is needed)
import dev.nxnative.android.R;
import dev.nxnative.android.Tokens;
import dev.peanuts.PeanutsPrefs;
import dev.peanuts.ui.components.StatusChip;

/**
 * The dedicated first-run setup (LEMON, the owner's onboarding brief): one screen, shown once, before Home —
 * the launcher's own first screen today is the library's first-run card (Library.setupCard), which guides
 * well but asks four things on one card. This screen walks the same four things in short steps, one thing
 * each, with a step pill row, Voltar / Continuar / Pular, and a final summary:
 *
 * <ul>
 * <li>Welcome — what the app is, what you will need, and the privacy line (nothing leaves the device).</li>
 * <li>File access — All files access, what it is for, the state after a round trip to Android's page, and
 * the honest way out (a .nup works without it; the app asks again when a build needs it).</li>
 * <li>Game folder — the one folder the library shows on Home (the library's own picker, LibrarySettings).</li>
 * <li>Keys and firmware — the two build prerequisites, with their real status, before the first Build.</li>
 * <li>Done — the checklist of where the setup landed, plus the Appearance choice (the design contract's
 * dark default, or light, or follow the system), then Get started.</li>
 * </ul>
 *
 * The screen is an activity of its own (Theme.Banana, like DriverActivity and CatalogActivity) so the
 * launcher's Home never has to host a full-page state of its own: MainActivity calls {@link #launch} once
 * in onCreate, and the pickers' results come back here to {@link LibrarySettings#result} — the same door
 * Settings uses. Finishing (Get started, or Skip setup) marks the setup done (PeanutsPrefs.setupDone) and
 * returns to Home, which keeps showing its first-run card while a game folder is still missing. Backing out
 * with the system Back on the first step leaves the setup unfinished: it is offered again on the next
 * launch, exactly like the first-run card it stands beside.
 *
 * Every part is the launcher's own system: PUi for the views (Tokens' grid, the palette resources, Manrope
 * through LemonType), Motion's enter for the step change (reduced motion honoured inside Motion),
 * Haptics through the buttons' Feel wrappers, StatusChip for the step pills. Texts are resources in en and
 * pt-BR only; comments and logs stay English (the repo rule).
 */
public final class SetupActivity extends Activity {
    private static final int STEP_WELCOME = 0, STEP_ACCESS = 1, STEP_FOLDER = 2, STEP_KEYS = 3, STEP_READY = 4;
    private static final int STEP_COUNT = 5;

    /** MainActivity.onActivityResult's code for this screen (upstream uses 1–8, the library's pickers 60–72; 9 is free) */
    public static final int REQ_SETUP = 9;

    private LinearLayout content;   // the page's column (PUi.page): header, pills, the step's body
    private LinearLayout stepBody;   // the step's card, rebuilt at every step change (the enter animation's target)
    private Button btnBack, btnContinue;   // the bar under the page (built once; labels and visibility follow the step)
    private BananaBackgroundView bananas;   // the drifting lemons behind the page, run between onResume and onPause like Home's
    private int step;
    private boolean builtOnce;   // the first onResume comes right after onCreate's build: nothing to refresh
    private boolean resultHandled;   // onActivityResult already rebuilt the page: onResume need not build it again (one build per picker return)

    /** MainActivity.onCreate (LEMON hook): the dedicated setup, once, until it is done. An intent that opens a game or a .nup takes over instead — the same
     *  payloads fromIntent consumes (MainActivity: a game or a folder to add, a frontend's game, an on-device dump); the rest (no_pause, a relaunched task) is a plain start. */
    public static void launch(Activity a, Intent intent) {
        if (PeanutsPrefs.setupDone(a)) return;
        if (intent != null && (intent.getData() != null || intent.hasExtra("game") || intent.hasExtra("add_game")
                || intent.hasExtra("frontend_game") || intent.hasExtra("ondevice_dump"))) return;
        a.startActivityForResult(new Intent(a, SetupActivity.class), REQ_SETUP);
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        step = saved != null ? saved.getInt("step", STEP_WELCOME) : STEP_WELCOME;
        if (step < STEP_WELCOME || step >= STEP_COUNT) step = STEP_WELCOME;

        FrameLayout frame = new FrameLayout(this);
        bananas = new BananaBackgroundView(this);
        frame.addView(bananas, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout pageFrame = new FrameLayout(this);
        root.addView(pageFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content = PUi.page(this, pageFrame, 640, true);

        View line = new View(this);
        line.setBackgroundColor(PUi.col(this, R.color.bn_card_stroke));
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, PUi.dp(this, Tokens.HAIRLINE_DP)));
        LinearLayout bar = PUi.row(this);
        bar.setBackgroundColor(PUi.col(this, R.color.bn_bg));
        btnBack = PUi.button(this, getString(R.string.lemon_setup_back), PUi.Kind.TONAL, v -> setStep(step - 1));
        bar.addView(btnBack, PUi.weight());
        View gap = new View(this);
        bar.addView(gap, new LinearLayout.LayoutParams(PUi.dp(this, Tokens.SPACE_3), 1));
        btnContinue = PUi.button(this, getString(R.string.lemon_setup_continue), PUi.Kind.PRIMARY, v -> {
            if (step == STEP_READY) complete(); else setStep(step + 1);
        });
        bar.addView(btnContinue, PUi.weight());
        bar.setOnApplyWindowInsetsListener((v, in) -> {   // as ReleasePage's bar: the page keeps a plain bottom, the bar takes the navigation inset
            android.graphics.Insets i = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            int w = getResources().getDisplayMetrics().widthPixels;
            int gutter = PUi.dp(this, getResources().getConfiguration().screenWidthDp < 360 ? Tokens.SPACE_4 : Tokens.SPACE_5);
            int side = Math.max(gutter, (w - i.left - i.right - PUi.dp(this, 640)) / 2);
            v.setPadding(side + i.left, PUi.dp(this, Tokens.SPACE_3), side + i.right, i.bottom + PUi.dp(this, Tokens.SPACE_3));
            return in;
        });
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(frame);
        build();
        Motion.enterPage(content, 0);   // the page enters once (Motion's tokens; reduced motion honoured inside)
    }

    /** The screen is in front: a firmware read landing (or the pickers' values changing) draws it again. Registered while in front, cleared while covered, so nothing redraws a covered or finishing wizard. */
    @Override protected void onStart() {
        super.onStart();
        LibrarySettings.rowsChangedHook = this::build;
    }

    @Override protected void onStop() {
        LibrarySettings.rowsChangedHook = null;
        super.onStop();
    }

    @Override protected void onDestroy() {
        LibrarySettings.rowsChangedHook = null;
        super.onDestroy();
    }

    /** A fold, a rotation, a window resize: the same step again (the activity handles its size changes itself, as the launcher's manifest does) */
    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        build();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("step", step);
    }

    /** The row values and the access state may have changed outside (a picker, Android's file access page, a firmware read landing): the step is drawn again.
     *  resume() also reads a firmware that waited for All files access (LibrarySettings.resume — coming back from Android's access page is enough), and the
     *  hook makes the page draw again with it. */
    @Override protected void onResume() {
        super.onResume();
        LibrarySettings.resume(this);
        if (builtOnce && !resultHandled) build();
        resultHandled = false;
        builtOnce = true;
        bananas.setRunning(true);
    }

    /** The lemons stop while the screen is covered, as Home's do (BananaBackgroundView animates only between setRunning true and false); the hook goes too —
     *  onPause precedes Home's onResume in a finish, so nothing redraws a wizard that is going away (onStart registers again when it comes back). */
    @Override protected void onPause() {
        LibrarySettings.rowsChangedHook = null;
        bananas.setRunning(false);
        super.onPause();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (LibrarySettings.result(this, request, result, data)) { resultHandled = true; build(); }   // the library's pickers (keys, folders, firmware) — the same door Settings uses
    }

    /** Back: a step back; on the first step the setup is left unfinished (it is offered again on the next launch) */
    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (step > STEP_WELCOME) setStep(step - 1);
        else super.onBackPressed();
    }

    /** The page built again: header (with Skip while there is still something to skip), the step pills, the step's body, the bar's labels */
    private void build() {
        content.removeAllViews();
        TextView skip = PUi.button(this, getString(R.string.lemon_setup_skip), PUi.Kind.QUIET, v -> complete());
        if (step == STEP_READY) skip.setVisibility(View.GONE);
        content.addView(PUi.header(this, PUi.badge(this, 44), getString(R.string.app_name), getString(R.string.lemon_setup_subtitle), skip));
        buildPills();
        stepBody = PUi.card(this);
        PUi.add(content, stepBody, Tokens.SPACE_4);
        switch (step) {
            case STEP_ACCESS: accessStep(); break;
            case STEP_FOLDER: folderStep(); break;
            case STEP_KEYS: keysStep(); break;
            case STEP_READY: readyStep(); break;
            default: welcomeStep(); break;
        }
        btnBack.setVisibility(step == STEP_WELCOME ? View.GONE : View.VISIBLE);
        btnContinue.setText(getString(step == STEP_READY ? R.string.lemon_setup_finish : R.string.lemon_setup_continue));
    }

    /** The step pills (the build card's stepper shape, StatusChip): the ones behind in the success tone, the current in the accent container, the ones ahead neutral */
    private void buildPills() {
        LinearLayout row = PUi.row(this);
        int[] labels = {R.string.lemon_setup_step_welcome, R.string.lemon_setup_step_access, R.string.lemon_setup_step_folder,
                R.string.lemon_setup_step_keys, R.string.lemon_setup_step_ready};
        for (int i = 0; i < STEP_COUNT; i++) {
            StatusChip.Tone tone = i < step ? StatusChip.Tone.OK : i == step ? StatusChip.Tone.BUILD : StatusChip.Tone.NEUTRAL;
            TextView chip = StatusChip.chip(this, getString(labels[i]), tone);
            chip.setContentDescription(getString(R.string.lemon_setup_step_a11y, i + 1, STEP_COUNT, getString(labels[i]),
                    getString(i < step ? R.string.lemon_setup_state_done : i == step ? R.string.lemon_setup_state_current : R.string.lemon_setup_state_waiting)));
            LinearLayout.LayoutParams lp = StatusChip.lp(this);
            if (i > 0) lp.leftMargin = PUi.dp(this, Tokens.SPACE_2);
            row.addView(chip, lp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        hs.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);   // the pills say themselves (contentDescription): the scroller is decoration
        View current = row.getChildAt(Math.min(step, row.getChildCount() - 1));
        current.post(() -> {   // a handheld's width holds three or four pills: the current one is scrolled into view; the chip's own post runs after its layout (a recreated activity lands scrolled too), and the views are captured — a rebuild between posting and running scrolls nothing
            hs.smoothScrollTo(Math.max(0, (int) current.getX() - PUi.dp(this, Tokens.SPACE_2)), 0);
        });
        PUi.add(content, hs, Tokens.SPACE_2, PUi.fill());
    }

    // ---- The steps ----

    /** Welcome: what the app is, the three files it needs, and the privacy line */
    private void welcomeStep() {
        TextView h = PUi.title(this, getString(R.string.lemon_setup_welcome_title));
        h.setAccessibilityHeading(true);
        PUi.add(stepBody, h, 0, PUi.wrap());
        PUi.add(stepBody, PUi.body(this, getString(R.string.lemon_setup_welcome_body)), Tokens.SPACE_2);
        LinearLayout needs = PUi.inner(this, R.color.bn_tint);
        PUi.add(needs, PUi.small(this, getString(R.string.lemon_setup_welcome_needs)), 0);
        PUi.add(needs, new PUi.Setting(this, "🔑", getString(R.string.lemon_setup_need_keys), null).view, Tokens.SPACE_2);
        PUi.add(needs, new PUi.Setting(this, "🎮", getString(R.string.lemon_setup_need_games), null).view, 0);
        PUi.add(needs, new PUi.Setting(this, "🔤", getString(R.string.lemon_setup_need_firmware), null).view, 0);
        PUi.add(stepBody, needs, Tokens.SPACE_3);
        PUi.add(stepBody, PUi.small(this, getString(R.string.lemon_setup_privacy)), Tokens.SPACE_3);
    }

    /** File access: what it is for, the state right now, Android's page, and the honest way out */
    private void accessStep() {
        TextView h = PUi.heading(this, getString(R.string.lemon_setup_access_title));
        h.setAccessibilityHeading(true);
        PUi.add(stepBody, h, 0, PUi.wrap());
        PUi.add(stepBody, PUi.body(this, getString(R.string.lemon_setup_access_body)), Tokens.SPACE_2);
        boolean allowed = Environment.isExternalStorageManager();
        TextView state = PUi.status(this, getString(allowed ? R.string.lemon_setup_access_allowed : R.string.lemon_setup_access_not_allowed),
                allowed ? R.color.bn_ok : R.color.bn_warn);   // Ui.status: a colored dot and the word, live region on, no color-only state
        PUi.add(stepBody, state, Tokens.SPACE_3);
        PUi.add(stepBody, PUi.button(this, getString(R.string.lemon_setup_access_allow), PUi.Kind.PRIMARY, v -> openAccessSettings()), Tokens.SPACE_3, PUi.wrap());
        if (!allowed) PUi.add(stepBody, PUi.small(this, getString(R.string.lemon_setup_access_note)), Tokens.SPACE_2);
    }

    /** Game folder: the one folder the library shows on Home (the library's own picker and result handling) */
    private void folderStep() {
        TextView h = PUi.heading(this, getString(R.string.lemon_setup_folder_title));
        h.setAccessibilityHeading(true);
        PUi.add(stepBody, h, 0, PUi.wrap());
        PUi.add(stepBody, PUi.body(this, getString(R.string.lemon_setup_folder_body)), Tokens.SPACE_2);
        String dir = PeanutsPrefs.gameDir(this);
        PUi.add(stepBody, new PUi.Setting(this, "📁", getString(R.string.lemon_setup_folder_row),
                Haptics.on(Haptics.Word.TICK, v -> LibrarySettings.pickFolder(this, LibrarySettings.REQ_GAME, getString(R.string.lemon_setup_folder_pick))))
                .value(dir != null ? dir : getString(R.string.lemon_setup_folder_not_set)).chevron(this).view, Tokens.SPACE_3);
    }

    /** Keys and firmware: the two build prerequisites, with the wizard's own resource-backed values (the Settings rows' English literals stay theirs) */
    private void keysStep() {
        TextView h = PUi.heading(this, getString(R.string.lemon_setup_keys_title));
        h.setAccessibilityHeading(true);
        PUi.add(stepBody, h, 0, PUi.wrap());
        PUi.add(stepBody, PUi.body(this, getString(R.string.lemon_setup_keys_body)), Tokens.SPACE_2);
        PUi.add(stepBody, new PUi.Setting(this, "🔑", getString(R.string.lemon_setup_keys_row), Haptics.on(Haptics.Word.TICK, v -> LibrarySettings.pickKeys(this)))
                .value(PeanutsPrefs.hasKeys(this)
                        ? getString(R.string.lemon_setup_keys_added, PeanutsPrefs.keysFile(this).length())
                        : getString(R.string.lemon_setup_value_not_yet)).chevron(this).view, Tokens.SPACE_3);
        PUi.add(stepBody, new PUi.Setting(this, "🔤", getString(R.string.lemon_setup_firmware_row), Haptics.on(Haptics.Word.TICK, v -> LibrarySettings.pickFirmwareSource(this)))
                .value(firmwareValue()).chevron(this).view, 0);
    }

    /** The Firmware row's value in the wizard's words: added, reading, failed (what to do next; the technical reason stays in the library's own state, for the
     *  log), waiting for All files access (the File access step grants it and the read starts), or not yet. */
    private CharSequence firmwareValue() {
        if (PeanutsPrefs.hasFonts(this)) return getString(R.string.lemon_setup_value_added);
        int kind = LibrarySettings.fontsKind;
        return getString(kind == LibrarySettings.FONTS_RUNNING ? R.string.lemon_setup_firmware_reading
                : kind == LibrarySettings.FONTS_FAILED ? R.string.lemon_setup_firmware_failed
                : kind == LibrarySettings.FONTS_WAITING ? R.string.lemon_setup_firmware_waiting
                : kind == LibrarySettings.FONTS_NO_IMPORTER ? R.string.lemon_setup_firmware_no_importer
                : R.string.lemon_setup_value_not_yet);
    }

    /** Done: the checklist of where the setup landed, and the Appearance choice (the design contract's dark default) */
    private void readyStep() {
        TextView h = PUi.heading(this, getString(R.string.lemon_setup_ready_title));
        h.setAccessibilityHeading(true);
        PUi.add(stepBody, h, 0, PUi.wrap());
        PUi.add(stepBody, PUi.body(this, getString(R.string.lemon_setup_ready_body)), Tokens.SPACE_2);
        boolean access = Environment.isExternalStorageManager();
        String dir = PeanutsPrefs.gameDir(this);
        check(stepBody, getString(R.string.lemon_setup_check_access), getString(access ? R.string.lemon_setup_access_allowed : R.string.lemon_setup_access_not_allowed), access);
        check(stepBody, getString(R.string.lemon_setup_check_folder), dir != null ? dir : getString(R.string.lemon_setup_folder_not_set), dir != null);
        boolean keys = PeanutsPrefs.hasKeys(this);
        check(stepBody, getString(R.string.lemon_setup_check_keys), getString(keys ? R.string.lemon_setup_value_added : R.string.lemon_setup_value_not_yet), keys);
        boolean fonts = PeanutsPrefs.hasFonts(this);
        check(stepBody, getString(R.string.lemon_setup_check_firmware), firmwareValue(), fonts);
        // Appearance is its own card, labelled as the settings pages label theirs (the checklist card stays short enough to read in one look)
        PUi.add(content, PUi.section(this, getString(R.string.lemon_appearance)), Tokens.SPACE_6);
        LinearLayout look = PUi.card(this);
        PUi.add(content, look, Tokens.SPACE_3);
        PUi.add(look, PUi.small(this, getString(R.string.lemon_appearance_note)), 0);
        String cur = PeanutsPrefs.nightMode(this);
        int sel = "light".equals(cur) ? 1 : "system".equals(cur) ? 2 : 0;
        String[] labels = {getString(R.string.lemon_appearance_dark), getString(R.string.lemon_appearance_light), getString(R.string.lemon_appearance_system)};
        PUi.add(look, new PUi.Segmented(this, labels, sel, i -> {
            String value = i == 1 ? "light" : i == 2 ? "system" : "dark";
            PeanutsPrefs.nightMode(this, value);
            dev.peanuts.settings.AppearanceSection.apply(this, value);   // UiModeManager's per-app night mode; the theme change remakes this activity
        }), Tokens.SPACE_3, PUi.fill());
    }

    /** One checklist row: a StatusChip (Done in the success tone, Later neutral — never color alone: the word is there), the item's name and its value line */
    private void check(LinearLayout parent, String name, CharSequence value, boolean done) {
        LinearLayout row = PUi.row(this);
        StatusChip.Tone tone = done ? StatusChip.Tone.OK : StatusChip.Tone.NEUTRAL;
        TextView chip = StatusChip.chip(this, getString(done ? R.string.lemon_setup_check_done : R.string.lemon_setup_check_later), tone);
        chip.setContentDescription(getString(R.string.lemon_setup_check_a11y, name, chip.getText()));
        row.addView(chip, StatusChip.lp(this));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(PUi.dp(this, Tokens.SPACE_2), 0, 0, 0);
        TextView t = PUi.text(this, name, Tokens.TYPE_BODY, R.color.bn_text, 600);
        col.addView(t);
        TextView v = PUi.small(this, value);
        v.setMaxLines(3);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(v);
        row.addView(col, PUi.weight());
        PUi.add(parent, row, Tokens.SPACE_3, PUi.fill());
    }

    /** A step back or forward: the page is built again and the step's card enters in the step's direction — forward rises from below, back settles from above (Motion's enter, discreet; the header and the pills stay put) */
    private void setStep(int to) {
        if (to < STEP_WELCOME || to >= STEP_COUNT || to == step) return;
        boolean back = to < step;
        step = to;
        build();
        Motion.enterPage(content, content.indexOfChild(stepBody), back);   // the whole step enters — the Ready step's Appearance card and section included (the index cuts the header and the pills off)
    }

    /** Get started / Skip setup: the setup is done (PeanutsPrefs.setupDone), Home takes over and keeps its first-run card while a game folder is missing */
    private void complete() {
        PeanutsPrefs.setupDone(this, true);
        Feel.done(this);   // Haptics.Word.DONE through Feel, the launcher's one haptics door
        setResult(RESULT_OK);
        finish();
    }

    /** As upstream's MainActivity.openAccessSettings: the app's own All files access page, else the list */
    private void openAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, android.net.Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
            catch (RuntimeException ignored) { }
        }
    }
}
