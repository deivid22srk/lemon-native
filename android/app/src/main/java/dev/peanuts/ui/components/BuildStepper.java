package dev.peanuts.ui.components;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.nxnative.android.R;
import dev.nxnative.android.Tokens;
import dev.peanuts.ui.PUi;

/**
 * The build's stepper (docs/design-system.md section 4): a build's steps as named pills — Import, Prepare,
 * Companion — the ones behind in the success text, the current one in the accent container, a stopped step
 * in the danger container, so a run that takes minutes to over an hour (ScreensOff's words) always answers
 * "where is it" without reading a log. Library's build card holds it under the progress bar and calls
 * {@link #update} from updateBuildCard, the same place the bar and the status line are fed. A step the
 * pipeline names that the stepper does not know (upstream's on-device job has its own vocabulary) degrades
 * to the last known step plus the status line the card already carries: nothing is invented.
 */
public final class BuildStepper {
    private BuildStepper() {}

    private static final String[] STEPS = {"import", "prepare", "companion"};

    /** The stepper row (a HorizontalScrollView of pills; hidden entirely once the job is done). */
    public static View view(Activity a) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    /** The job's state into the pills: every step before the current one in the success text, the current in the accent, all done hides the row. */
    public static void update(Activity a, View stepper, String step, boolean failed) {
        if (!(stepper instanceof LinearLayout)) return;
        LinearLayout row = (LinearLayout) stepper;
        row.removeAllViews();
        if (step == null || step.equals("done")) { row.setVisibility(View.GONE); return; }
        row.setVisibility(View.VISIBLE);
        int current = -1;
        for (int i = 0; i < STEPS.length; i++) if (STEPS[i].equals(step)) { current = i; break; }
        int shown = current >= 0 ? STEPS.length : STEPS.length - 1;   // an unknown step: the known pills stand, the card's status line says what runs
        for (int i = 0; i < shown; i++) {
            boolean failedStep = failed && (current == i || (current < 0 && i == shown - 1));
            boolean done = !failedStep && (current < 0 || i < current);
            StatusChip.Tone tone = failedStep ? StatusChip.Tone.ERROR : done ? StatusChip.Tone.OK
                    : i == current ? StatusChip.Tone.BUILD : StatusChip.Tone.NEUTRAL;
            TextView chip = StatusChip.chip(a, label(a, i), tone);
            chip.setContentDescription(a.getString(R.string.lemon_step_a11y, label(a, i),
                    failedStep ? a.getString(R.string.lemon_step_state_stopped)
                            : done ? a.getString(R.string.lemon_step_state_done)
                            : i == current ? a.getString(R.string.lemon_step_state_running)
                            : a.getString(R.string.lemon_step_state_waiting)));
            LinearLayout.LayoutParams lp = StatusChip.lp(a);
            if (i > 0) lp.leftMargin = PUi.dp(a, 8);
            row.addView(chip, lp);
        }
    }

    private static CharSequence label(Activity a, int i) {
        return a.getString(i == 0 ? R.string.lemon_step_import
                : i == 1 ? R.string.lemon_step_prepare : R.string.lemon_step_companion);
    }
}
