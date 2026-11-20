# Peanuts Native — UI redesign brief ("Slate & Signal", 2026-11-21)

Owner direction for the UI/UX overhaul rounds on `feat/import-pack-zip-long-press`. This file is the
design contract the rounds implement against; the palette numbers were validated by script (WCAG 2.1
contrast), so do not hand-edit a hex without re-checking its annotated pair.

## 1. Identity

The launcher drops the cream-and-banana playfulness for a professional, dark-first look:

- **Surfaces: "Slate"** — cool blue-black graphite, flat, told apart by fill and hairline edge.
- **Primary accent: "Signal blue"** — one saturated blue carries every primary action, focus ring,
  selected state and progress.
- **Secondary accent: "Harvest amber"** — the banana heritage, refined: reserved for brand moments
  (header badge glyph, empty state, celebration highlights). Never a competing button color.
- **Semantics**: green = success, amber = caution, red = danger, each with a tonal container.
- Dark is the reference design; the light theme is the same system re-mapped, not an afterthought.

## 2. Palette (validated)

Text needs 4.5:1 (AA normal), meaningful shapes 3:1; hairlines and tonal containers are informational.
Ratios below were computed by script (WCAG 2.1 relative luminance).

### Dark (`values-night/colors.xml`, the reference)

| Token | Hex | Role and checked pairs |
|---|---|---|
| `bn_bg` | `#0B0E14` | screen (text 16.4:1, text_2 8.1:1) |
| `bn_card` | `#141A24` | cards, dialogs, sheets (text 14.8:1, text_2 7.3:1) |
| `bn_card_stroke` | `#2B3543` | hairline edges, dividers, progress track |
| `bn_tint` | `#1B2330` | tonal chips, icon tiles (text 13.4:1, text_2 6.6:1, accent 6.2:1) |
| `bn_accent` | `#58A6F5` | primary accent: fills, focus ring, links, quiet buttons (7.5:1 on bg, 6.8:1 on card, 5.7:1 on accent_tint, 4.84:1 vs stroke) |
| `bn_on_accent` | `#06101D` | label on accent fill (7.5:1) |
| `bn_accent_tint` | `#17293F` | tonal accent container (selected states) |
| `bn_banana` | `#F2BA49` | secondary accent, brand moments only |
| `bn_banana_deep` | `#D9A02F` | pressed secondary accent |
| `bn_on_banana` | `#221502` | text on amber (10.1:1 / 7.7:1) |
| `bn_text` | `#E8EDF4` | body text |
| `bn_text_2` | `#9DA9B8` | secondary text |
| `bn_leaf` / `bn_ok` | `#67C795` | success text (8.5:1 on card, 6.9:1 on ok_tint) |
| `bn_badge` | `#1E5B3A` | header badge circle, both modes (the badge glyph — ic_banana_happy's `#FFD43B` banana — reads 5.64:1 on it) |
| `bn_on_leaf` | `#0C2317` | text on leaf (8.0:1) |
| `bn_danger` | `#FF8A76` | error text (7.6:1 on card, 6.4:1 on danger_tint, 5.7:1 on warn_tint) |
| `bn_danger_tint` | `#3D211C` | danger container |
| `bn_warn` | `#F2C341` | warning text (10.5:1 on card, 7.9:1 on warn_tint) |
| `bn_warn_tint` | `#3A2F13` | warning container |
| `bn_ok_tint` | `#16301F` | success container |
| `bn_control_ripple` | `#33E8EDF4` | theme widget ripple (20% text) |

### Light (`values/colors.xml`)

| Token | Hex | Role and checked pairs |
|---|---|---|
| `bn_bg` | `#F2F4F8` | screen (text 15.1:1, text_2 5.7:1) |
| `bn_card` | `#FFFFFF` | cards, dialogs (text 16.6:1, text_2 6.3:1, accent 6.0:1) |
| `bn_card_stroke` | `#CFD8E3` | hairlines, dividers, progress track |
| `bn_tint` | `#EBEFF5` | tonal chips, tiles (accent 5.2:1) |
| `bn_accent` | `#0A5FC7` | primary accent (5.5:1 on bg, 5.0:1 on accent_tint, 4.2:1 vs stroke) |
| `bn_on_accent` | `#FFFFFF` | label on accent fill (6.0:1) |
| `bn_accent_tint` | `#DFEBF9` | tonal accent container |
| `bn_banana` | `#F7C03A` | secondary accent, brand moments only |
| `bn_banana_deep` | `#D9A02F` | pressed secondary accent |
| `bn_on_banana` | `#33200A` | text on amber (9.3:1 / 6.7:1) |
| `bn_text` | `#171F2B` | body text |
| `bn_text_2` | `#526175` | secondary text |
| `bn_leaf` / `bn_ok` | `#0F7A40` | success text (5.4:1 on card, 4.5:1 on ok_tint) |
| `bn_badge` | `#1E5B3A` | header badge circle (the badge glyph — ic_banana_happy's `#FFD43B` banana — reads 5.64:1 on it) |
| `bn_on_leaf` | `#FFFFFF` | text on leaf (5.4:1) |
| `bn_danger` | `#B23517` | error text (6.2:1 on card, 5.0:1 on danger_tint, 5.0:1 on warn_tint) |
| `bn_danger_tint` | `#FAE3DB` | danger container |
| `bn_warn` | `#8A5200` | warning text (6.4:1 on card, 5.2:1 on warn_tint) |
| `bn_warn_tint` | `#F6E8B9` | warning container |
| `bn_ok_tint` | `#DFEFE3` | success container |
| `bn_control_ripple` | `#33171F2B` | theme widget ripple (20% ink) |

## 3. Component rules

- **One filled accent per view**: the page's main action uses `bn_accent` + `bn_on_accent`
  (`Ui.button`/`PUi.button` Kind.PRIMARY). Everything else is SOFT/QUIET. `bn_banana` never fills a
  button.
- **Focus** is always `bn_accent` (ring and focused labels). The old banana focus ring on cream
  (≈1.4:1) is gone.
- **Selected states** (segments, chips, tabs) are tonal: `bn_accent_tint` fill + `bn_accent` label,
  not filled accent.
- **Ripples** come from `bn_control_ripple` (or `Ui.touchable`), never a literal in a screen file.
- **In-game/companion overlays** render over the game in both modes, so they use the dark slate
  values; overlay-only tokens live in `values/colors.xml` with no night override and say so in their
  comment. Accent drift (`0xFFFFD54F`) is a bug: overlays use the same Signal blue.
- Progress: `bn_accent` fill on `bn_card_stroke` track (4.2:1 light, 4.84:1 dark — a UI shape needs 3:1, both clear it).
- Type floor is `Tokens.TYPE_CAPTION` (12 sp). Documented exception: `Tokens.PANEL_TAB_MIN_SP` (10 sp)
  for the resizable panel's four tabs only. Anything else below 12 sp is a defect.

## 4. States and feedback

- Every screen answers: empty (illustration/wordmark + one line + the action that fixes it),
  loading (indeterminate progress + what is happening), error (what failed + the action that fixes
  it), success (confirmation + next step). `PUi.emptyState` and the build-card taxonomy are the
  pattern.
- The pack-`.zip` import gives live feedback: an indeterminate progress surface with the current
  stage ("Reading the zip…", "Validating the pack…", "Installing…"), a cancel affordance (cancelling
  only dismisses the wait; the import itself reports its own result), haptics on result
  (`Haptics.Word.DONE` / `REJECT`), and errors that name the fix.
- Haptics use `Haptics.Word` only; long-press keeps the system's own haptic (no double buzz).
- Animations run through `Motion`'s tokens, 150–300 ms, ease-out; nothing animates layout on every
  frame.

## 5. Accessibility

- `contentDescription` on every icon-only control; covers carry name + state.
- Switch rows keep `title.setLabelFor(switchId)`.
- Touch targets ≥ 48 dp (`Tokens.TOUCH_MIN`).
- Text in sp everywhere; no text-only color encoding (state = color + word/pill).

## 6. Invariants (breaking any of these fails the round)

1. `Ui.java` and `PUi.java` stay in sync: the only lines that may differ between the two files are the documented exceptions — the `package` line, PUi's
   `import dev.*` lines, the `public` modifiers (and each such line's unchanged counterpart in the other file), and the lines PUi marks PEANUTS (the Feel
   hooks it drops or adds, and PUi-only helpers such as `joined`). Run
   `cd android/app/src/main/java && diff dev/nxnative/android/Ui.java dev/peanuts/ui/PUi.java`
   and confirm every differing line is one of those.
2. UI work touches only UI: no `dm/` (native), no gradle deps, no manifest logic, no import/build
   engine changes (`PackImport`, `PackZip`, `PackCheck` logic untouched).
3. `peanuts/tests/test_*.py` stay green — several assert exact UI strings/wiring
   (`test_reimport.py`, `test_swap_screens.py`, `test_driver_page.py`).
4. App text stays English, in string resources when new user-facing strings are added
   (the repo ships one locale; no `values-pt` exists, so none is added).
5. No secrets, no force push, no CI file edits; builds are triggered with `workflow_dispatch`.
