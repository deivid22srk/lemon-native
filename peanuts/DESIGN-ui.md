# Lemon Native — UI redesign brief ("Lemon", 2026-11-21)

Owner direction for the Lemon Native UI/UX rounds (the fork's new identity on top of the Peanuts lineage).
This file is the design contract the rounds implement against; the palette numbers are validated by script
(`peanuts/scripts/check-palette.py`, WCAG 2.1 contrast, run by CI in peanuts.yml), so do not hand-edit a hex
without re-checking its annotated pair.

## 1. Identity

The launcher drops the Signal blue for a gaming-premium citrus identity — dark-first, high contrast, little
noise:

- **Surfaces: deep olive-charcoal** — flat, told apart by fill and hairline edge (the Slate structure kept,
  re-hued toward the lemon's leafy warmth).
- **Primary accent: the lemon yellow** — one saturated yellow carries every primary action, focus ring,
  selected state and progress. In the light theme the accent darkens to a text-safe olive-lemon (`#6B5C00`);
  the yellow itself never carries text on a light surface.
- **Support hue: leaf green** — the hero gradient's far end and the secondary chips (`bn_secondary`), with
  the badge circle in deep leaf (`bn_badge`, both modes) behind the icon's yellow lemon.
- **Brand moments**: the lemon glyph and the empty state only (`bn_banana`). Never a button fill.
- **Semantics**: green = success, warm lemon = caution, red = danger, each with a tonal container.
- Dark is the reference design and the first run's default (`AppearanceSection.applyDefault`); the light
  theme is the same system re-mapped; "Follow system" hands the choice back to Android
  (Settings › Appearance, `UiModeManager.setApplicationNightMode`).

## 2. Palette (validated)

Text needs 4.5:1 (AA normal), meaningful shapes 3:1; hairlines and tonal containers are informational.
Ratios below were computed by `peanuts/scripts/check-palette.py` (WCAG 2.1 relative luminance), which CI
runs on every push — a palette change that breaks a pair breaks the build.

### Dark (`values-night/colors.xml`, the reference)

| Token | Hex | Role and checked pairs |
|---|---|---|
| `bn_bg` | `#0C0D09` | screen (text 17.6:1, text_2 8.5:1) |
| `bn_card` | `#14160F` | cards, dialogs, sheets (text 16.5:1, text_2 7.9:1) |
| `bn_card_stroke` | `#3A3E2B` | hairline edges, dividers, progress track |
| `bn_surface3` | `#262A1A` | deep tonal surface: pressed tiles, slots (informational) |
| `bn_tint` | `#1C1F14` | tonal chips, icon tiles (text 15.1:1, text_2 7.3:1, accent 14.6:1) |
| `bn_accent` | `#FFE74A` | primary accent: fills, focus ring, links, quiet buttons (15.6:1 on bg, 14.6:1 on card, 10.2:1 on accent_tint, 8.8:1 vs stroke) |
| `bn_on_accent` | `#1A1700` | label on accent fill (14.4:1) |
| `bn_accent_tint` | `#3A3300` | tonal accent container: selected segments, chips (accent on it 10.2:1) |
| `bn_secondary` | `#A8E04A` | leaf green support: hero gradient end, secondary chips (12.5:1 on bg, 11.7:1 on card) |
| `bn_accent_warm` | `#FF9F43` | citric warm accent: update-available chips, warm highlights (8.9:1 on card) |
| `bn_banana` | `#FFE74A` | brand accent, brand moments only (the header badge's glyph, the empty state): never a button fill |
| `bn_banana_deep` | `#E8D11E` | pressed brand accent |
| `bn_on_banana` | `#1A1700` | text on the brand accent (14.4:1 on banana, 11.6:1 on banana_deep) |
| `bn_text` | `#F3F4EA` | body text (16.5:1 on card, 17.6:1 on bg, 15.1:1 on tint) |
| `bn_text_2` | `#A9AD98` | secondary text (7.9:1 on card, 8.5:1 on bg, 7.3:1 on tint) |
| `bn_leaf` / `bn_ok` | `#5BE38A` | success text (11.1:1 on card, 8.6:1 on ok_tint) |
| `bn_badge` | `#3F6A00` | header badge circle, both modes (the icon's yellow lemon reads 5.1:1 on it) |
| `bn_on_leaf` | `#0B2410` | text on leaf green (10.1:1) |
| `bn_danger` | `#FF6B6B` | error text (6.6:1 on card, 5.8:1 on danger_tint, 4.8:1 on warn_tint) |
| `bn_danger_tint` | `#3B1616` | danger container |
| `bn_warn` | `#FFC169` | warning text (11.4:1 on card, 8.4:1 on warn_tint) |
| `bn_warn_tint` | `#3D2C12` | warning container |
| `bn_ok_tint` | `#173112` | success container |
| `bn_control_ripple` | `#33F3F4EA` | theme widget ripple (20% text) |

### Light (`values/colors.xml`)

| Token | Hex | Role and checked pairs |
|---|---|---|
| `bn_bg` | `#FBFBF3` | screen (text 16.5:1, text_2 6.1:1) |
| `bn_card` | `#FFFFFF` | cards, dialogs (text 17.2:1, text_2 6.4:1, accent 6.7:1) |
| `bn_card_stroke` | `#CFD1B8` | hairlines, dividers, progress track |
| `bn_surface3` | `#E9EAD6` | deep tonal surface (informational) |
| `bn_tint` | `#F3F3E6` | tonal chips, tiles (text 15.4:1, text_2 5.7:1) |
| `bn_accent` | `#6B5C00` | primary accent (6.4:1 on bg, 6.7:1 on card, 5.7:1 on accent_tint, 4.3:1 vs stroke) |
| `bn_on_accent` | `#FFFFFF` | label on accent fill (6.7:1) |
| `bn_accent_tint` | `#FFFFEF7A` | tonal accent container (accent on it 5.7:1) |
| `bn_secondary` | `#3F6A00` | leaf support (6.2:1 on bg, 6.4:1 on card) |
| `bn_accent_warm` | `#B35C00` | citric warm accent (4.7:1 on card) |
| `bn_banana` | `#C7A500` | brand accent, brand moments only — never a button fill, never text on a light card |
| `bn_banana_deep` | `#B39200` | pressed brand accent |
| `bn_on_banana` | `#201A00` | text on the brand accent (7.3:1 on banana, 5.8:1 on banana_deep) |
| `bn_text` | `#1B1C14` | body text (17.2:1 on card, 16.5:1 on bg) |
| `bn_text_2` | `#5E614F` | secondary text (6.4:1 on card, 6.1:1 on bg) |
| `bn_leaf` / `bn_ok` | `#1B7F43` | success text (5.0:1 on card, 4.6:1 on ok_tint) |
| `bn_badge` | `#3F6A00` | header badge circle (the icon's yellow lemon reads 5.1:1 on it) |
| `bn_on_leaf` | `#FFFFFF` | text on leaf green (5.0:1) |
| `bn_danger` | `#B3261E` | error text (6.5:1 on card, 5.2:1 on danger_tint, 5.4:1 on warn_tint) |
| `bn_danger_tint` | `#FAE0DD` | danger container |
| `bn_warn` | `#8F5A00` | warning text (5.8:1 on card, 4.8:1 on warn_tint) |
| `bn_warn_tint` | `#F7E9C8` | warning container |
| `bn_ok_tint` | `#EDF7EF` | success container |
| `bn_control_ripple` | `#331B1C14` | theme widget ripple (20% ink) |

The overlay set (`bn_overlay_*`) is unchanged in its role: it renders over the running game in both modes,
lives in `values/colors.xml` only (CI fails if `values-night` redefines any), and is re-hued to the same
lemon palette (`bn_overlay_accent` = the lemon yellow, 14.7:1 on the overlay card).

## 3. Type, shape, space, motion

- **Type** (`dev.peanuts.ui.theme.LemonType`, loaded once per process by `NxApp`): Manrope (OFL, in
  `res/font`) carries every weight the builders ask for — 400 body, 500 medium, 600 semibold, 700 heading,
  800 title/display; `Ui.face`/`PUi.face` route every text through it. JetBrains Mono (OFL, `res/font`)
  carries numeric readouts, versions and hashes only (the build card's percent). The scale, the spacing grid
  (4/8 dp), the 48 dp touch floor and the component measures stay in `Tokens`; the radius tiers are
  chips/inputs 12 (`RADIUS_ROW`), cards 20 (`RADIUS_LG`), sheets and dialogs 28 (`RADIUS_SHEET`,
  `bn_dialog_bg`).
- **Gradients** exist on exactly two surfaces: the library's hero card (lemon container → leaf tone, same
  container depth) and nowhere else — CTAs keep flat accent fills.
- **Motion** runs through `Motion`'s tokens, 150–300 ms, ease-out (`PathInterpolator`), with the
  reduced-motion path honoured; nothing animates layout on every frame. Haptics go through `Haptics.Word`.

## 4. States and feedback

- Every screen answers: empty (the lemon art + one line + the action that fixes it), loading
  (indeterminate progress + what is happening), error (what failed + the action that fixes it), success
  (confirmation + next step). `PUi.emptyState` and the build-card taxonomy are the pattern.
- The build card carries the **stepper** (`dev.peanuts.ui.components.BuildStepper`): Import, Prepare,
  Companion as pills — done in the success text, running in the accent container, stopped in the danger
  container; an unknown step degrades to the card's status line, nothing is invented.
- The library leads with the **hero** (`dev.peanuts.ui.components.HeroCard`, "Continue playing"): the
  last-used Ready game at the top of Home, Play one tap away (the brief's two-tap rule). Status pills come
  from `dev.peanuts.ui.components.StatusChip` (OK/BUILD/WARN/ERROR/NEUTRAL), never a filled accent.
- The pack-`.zip` import keeps its live feedback (stage words, cancel, haptics on result, errors that name
  the fix).
- Haptics use `Haptics.Word` only; long-press keeps the system's own haptic (no double buzz).
- Animations run through `Motion`'s tokens, 150–300 ms, ease-out; nothing animates layout on every frame.

## 5. Accessibility and i18n

- `contentDescription` on every icon-only control; covers carry name + state; the stepper's pills carry
  step + state strings.
- Switch rows keep `title.setLabelFor(switchId)`.
- Touch targets ≥ 48 dp (`Tokens.TOUCH_MIN`); text in sp everywhere; no text-only color encoding
  (state = color + word/pill).
- New user-facing strings go into `res/values/strings.xml` (English) **and** `res/values-pt-rBR/strings.xml`
  (Brazilian Portuguese) — the Lemon brief's two-locale rule; the older Java literals are extracted as the
  screens that own them are touched (the known gap lives in `docs/ui-audit.md` §5 and the scorecard).

## 6. Invariants (breaking any of these fails the round)

1. `Ui.java` and `PUi.java` stay in sync: the only lines that may differ between the two files are the
   documented exceptions — the `package` line, PUi's `import dev.*` lines, the `public` modifiers (and each
   such line's unchanged counterpart in the other file), and the lines PUi or Ui mark PEANUTS or LEMON (the
   Feel hooks, and PUi-only helpers such as `joined`). The Lemon type line (`face()` → `LemonType`) is the
   same text in both files. Run
   `cd android/app/src/main/java && diff dev/nxnative/android/Ui.java dev/peanuts/ui/PUi.java`
   and confirm every differing line is one of those.
2. UI work touches only UI: no `dm/` (native), no gradle deps, no manifest logic, no import/build engine
   changes (`PackImport`, `PackZip`, `PackCheck` logic untouched).
3. `peanuts/tests/test_*.py` stay green — several assert exact UI strings/wiring.
4. New user-facing text is a string resource in en **and** pt-BR (§5); app comments, logs and docs stay
   English (the repo rule).
5. CI edits are allowed where the Lemon brief names them (the artifact rename, the palette check job); no
   build step of the game pipeline is removed or weakened. No secrets, no force push.
