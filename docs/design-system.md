# Lemon Native — design system (docs/design-system.md)

The single source the screens read from, and where each piece lives. The design contract is
[peanuts/DESIGN-ui.md](../peanuts/DESIGN-ui.md); this file maps it to the code. Palette changes must re-run
`peanuts/scripts/check-palette.py` (CI runs it on every push).

## Structure

| Brief's layer | Where it lives | Notes |
|---|---|---|
| `ui/theme` | `res/values/colors.xml` + `values-night/colors.xml` (palette), `Tokens.java` (grid, radii, type scale, touch floor), `res/font/` + `dev/peanuts/ui/theme/LemonType.java` (type) | The palette keeps the historical `bn_*` resource names (hundreds of call sites), now carrying the Lemon values; dark is the reference set in `values-night`. |
| `ui/components` | `dev/peanuts/ui/components/` — `StatusChip`, `HeroCard`, `BuildStepper`; the shared builders are `Ui.java`/`PUi.java` (buttons, cards, rows, headers, sheets, empty state) | `LemonButton` ≙ `PUi.button` (Kind.PRIMARY is the page's one filled accent); `LemonCard` ≙ `PUi.card`; `LemonTopBar` ≙ `Ui.header`. New screens must compose from these, not re-draw their own. |
| `ui/screens` | `MainActivity` (Home library + Settings), `GameSheet`, `DriverActivity`, `ReadinessActivity`, `SelftestActivity`, `CatalogActivity`, the game page (`GameSettings`), the companion surfaces (`GameScreen`, `BottomPanel`, `CompanionPage`) | The full screen/flow map is `docs/ui-audit.md`; functional parity with it is a hard gate per round. |
| `ui/navigation` | `MainActivity.page(...)` (single-task, two pages deep) + the header actions + the second display's `BottomScreen` | The handheld has no bottom bar: D-pad/gamepad focus (`PadFocus` + `Ui` focus ring) is the navigation; A confirms, B backs, Y is the row's second action, L1/R1 step the library's filters. |

## Tokens

- **Spacing**: the 4/8 dp grid (`Tokens.SPACE_1..8`); pages share one gutter and one header height.
- **Radii**: 12 chips/inputs (`RADIUS_ROW`), 14 buttons (`RADIUS_MD`), 16 inners (`RADIUS_INNER`), 20 cards
  (`RADIUS_LG`), 28 sheets/dialogs (`RADIUS_SHEET`).
- **Type**: Manrope 400/500/600/700/800 (static instances in `res/font`, OFL); JetBrains Mono for numeric
  readouts, versions and hashes. Scale in sp: 12 caption, 13 small, 15 body, 18 heading, 24 title, 26
  display; the two display-tier texts are the launch screen's game name and the Readiness verdict.
- **Color**: the `bn_*` resources (see the contract's tables); the overlay set is stable across modes.
- **Motion**: `Motion`'s tokens, 150–300 ms, ease-out; reduced-motion honoured; haptics via `Haptics.Word`.

## Components (Lemon additions)

| Component | File | Contract |
|---|---|---|
| `StatusChip` | `dev/peanuts/ui/components/StatusChip.java` | A state pill in a semantic container (OK/BUILD/WARN/ERROR/NEUTRAL); informational, never a filled accent, never clickable; `chipNumeric` for readouts. |
| `HeroCard` | `dev.peanuts.ui.components.HeroCard` | The library's "Continue playing" card: the last-used Ready game, title-tier name, cover icon, Play (the page's one filled accent) + Manage. The design's only gradient surface. Library wires focus/haptics/keys onto it. |
| `BuildStepper` | `dev.peanuts.ui/components.BuildStepper` | Import → Prepare → Companion pills; success/accent/danger by state; unknown steps degrade to the card's status line. |
| Appearance section | `dev.peanuts.settings.AppearanceSection` | Settings › Appearance: Dark (default and first run), Light, Follow system — applied per-app via `UiModeManager.setApplicationNightMode`. |

## Checks a round must pass

1. `peanuts/scripts/check-palette.py` (CI): every annotated pair holds its AA minimum.
2. `diff dev/nxnative/android/Ui.java dev/peanuts/ui/PUi.java`: only the contract's documented exceptions
   (peanuts/DESIGN-ui.md §6.1).
3. `peanuts/tests/test_*.py` green; `docs/ui-audit.md` parity — every screen, state and flow still there.
4. New strings in `values/strings.xml` and `values-pt-rBR/strings.xml`; no hardcoded UI text in new code.
