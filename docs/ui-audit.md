# Lemon Native — UI audit (baseline `c823a1c`, 2026-11-21)

The pre-redesign map every later round is checked against. Functional parity with this list is a
hard gate: a screen, state or flow that disappears limits the round's score (ui-scorecard.md).

## 1. UI stack (finding, and the redesign decision)

The launcher has **no XML layouts, no AndroidX, no Compose**: every screen is Java built
programmatically from a small design system —

- `dev/nxnative/android/Ui.java` + `Tokens.java`: the building blocks (buttons, cards, rows,
  headers, sheets) reading the palette from `res/values/colors.xml` + `values-night/colors.xml`
  ("Slate & Signal") and the measures from `Tokens` (4/8 dp grid, radii, type scale, 48 dp touch
  floor). `dev/peanuts/ui/PUi.java` is the fork's documented copy of `Ui`.
- `dev/peanuts/ui/`: the fork's UI layer — Library, GameSheet, BuildLogUi, BottomPanel/BottomScreen
  (second display), Motion, Feel (haptics), Haptics, PadFocus integration, DriverActivity,
  ScreensOff.
- `PadFocus.java` + `Motion.java`: the D-pad/gamepad focus system and the animation helpers the
  prompt's "handheld" criterion depends on.
- The companion runs inside the **`:game` process** (`GameScreen` as a `Presentation` on the second
  display, `CompanionScreen` as an activity, `BottomPanel` while the launcher is in front) and reads
  the running game over JNI — its data path is engine territory and is not redesigned.

**Decision (section 4.1 of the brief): the UI is not migrated to Compose.** The brief allows the
fallback when migration would not stay stable. Justification, in order of weight:

1. There is no Kotlin/AndroidX in the build at all; Compose means a new toolchain (Kotlin plugin,
   compose compiler, ~15 MB of new dependencies) validated only by remote CI (no local SDK here),
   against a 26 k-line launcher whose focus system, in-game overlay and second-display presentations
   are hand-built against plain `View`.
2. The redesign's substance — palette, type, shape, motion, components, flows — lives in the design
   system layer (`Ui`/`PUi`/`Tokens`/resources) by construction: every screen already reads from it,
   so re-theming the layer re-themes all screens coherently with zero structural risk to the engine.
3. New UI code stays under `dev/peanuts/ui/**` (the fork's own territory, PEANUTS.md rule 1), keeping
   the repo's upstream-diff discipline.

The equivalent of the brief's `ui/theme` / `ui/components` structure is implemented inside
`dev/peanuts/ui/` (`theme/`, `components/`), and the design contract is `peanuts/DESIGN-ui.md`
(rewritten for Lemon) plus `docs/design-system.md`.

## 2. Screens and components (every one must keep working)

| Screen / component | Class(es) | Notes |
|---|---|---|
| Launcher Home = library | `MainActivity` (+ `dev.peanuts.ui.Library`, `LibraryScanner`, `LibrarySettings`, `StartView`, `BananaBackgroundView`→lemon tiles) | header: badge, "Lemon Native", build tag, Screens-off button, FAQ + Settings actions; game cards (cover, status chips), Add-game card, empty state |
| First-run setup | setup flow into `PeanutsPrefs` (keys, firmware, game folder, optional update/DLC folders) | permissions: All-files access |
| Settings page | `MainActivity.page(true)` + upstream sections + `dev.peanuts.settings.*` (Setup, Library, Companions, Mods, About) | updater row (`Updater`), GPU driver row → `DriverActivity` |
| Game page (hold cover) | `GameSettings`, `dev.peanuts.settings.CompanionSection`, `ModsSection` | picture/speed, companion install/update/remove, saves, storage, Build again |
| Game sheet (tap unbuilt game) | `dev.peanuts.ui.GameSheet` | base/update/DLC rows, readiness facts, single **Build** CTA, Import again |
| Import dump / choose version | `dev.peanuts.importer.*`, `ImporterPort`, upstream `Ondevice`/`Edition` paths | NSP/XCI/.nup/dump folder |
| Build in progress | `dev.peanuts.build.*` (`BuildPipeline`, `Prepare`, `ShaderCapture`), `Library.Build`, `BuildLogUi`, services `BuildService`/`ModBuildService`/`PackService`/`GameHold` | progress strip + log, foreground notifications (`PackService` icon = `ic_lemon_mono` now) |
| "Screens off" | `dev.peanuts.ui.ScreensOff`, header button | blacks the panels while a build runs |
| Play a game | upstream `GameActivity` / `FrontendLaunch` | started from a Ready card |
| In-game panel + overlay | upstream `SidePanel`, `Overlay`, `dev.peanuts.overlay.*` | renders over the game, overlay-stable palette |
| Second display (companion) | `GameScreen` (Presentation), `CompanionPage`, `CompanionPresentation`, `CompanionScreen` (activity, `:game`), `BottomPanel`, `Companion`, `CompanionNotes/Versions` | data from the running game over JNI — engine path, not redesigned |
| Companion catalog | `CatalogActivity` + `Catalog`/`Installer` (Eden Duo releases, checksums) | "Lemon Companions" label |
| GPU driver page | `dev.peanuts.ui.DriverActivity`, `DriverFetch/Match/Port`, `GpuDriver` | download on the user's tap |
| Readiness check | upstream `ReadinessActivity` | verdict in the display type tier |
| Graphics self-test | upstream `SelftestActivity` | |
| Frontends / adb door | `FrontendLaunch` (aliases `${applicationId}.Launch`, `dev.peanuts.launch.LaunchActivity`, `${applicationId}.Open`), `dev.peanuts.ctl.Command` | invisible, contract files (`FRONTENDS.md`, `CONTROL.md`) |
| D-pad/gamepad focus | `PadFocus` + `Ui.focus` edge + `Feel`/`Haptics` | A confirm, B back; ≥ 48 dp targets (`Tokens.TOUCH_MIN`) |

## 3. Flows (end-to-end, none may regress)

1. First open → All-files access → Setup (keys/firmware/folders) → library empty state with lemon
   art and the Add-game CTA.
2. Add game (NSP/XCI/.nup/dump folder) → title matched by CNMT → GameSheet shows base/update/DLC and
   the companion → **Build** (single primary CTA).
3. Build → foreground service → progress on the library card + BuildLogUi; **Screens off** available
   throughout; error states carry the log path and a retry ("Import again", "Build again").
4. Ready card → tap → play (`GameActivity`); companion auto-starts on the second display when
   present and not set to Off (per-game Second screen row).
5. Hold cover → game page: settings, mods, companion, saves, storage (Build again / Rebuild).
6. Settings → Setup/Library/Companions/GPU driver/Overlay/Mods/About; updater row (release app only).

## 4. States inventory (must exist in the redesign)

Empty (library, companions, logs) · loading/progress (cards, stepper, log) · error (build failure,
import failure, driver fetch, readiness fail rows) · success (Ready chip, readiness pass) ·
disabled (no profile, update incompatible) · reduced-motion (`Motion` honours the system setting).

## 5. Known constraints the redesign inherits

- minSdk 33, `Theme.Banana` (Material, light base + `values-night`), edge-to-edge via `Ui.page`'s
  status-bar scrim; no AndroidX anywhere.
- Strings are Java literals in this codebase (there are no string resources today). New UI text
  goes into `res/values/strings.xml` (en) + `res/values-pt-rBR/strings.xml` (pt-BR); extracting the
  26 k lines of existing literals is out of scope and is recorded as a known gap in the scorecard.
- The palette is annotated with WCAG 2.1 pairs; any value change must re-run
  `peanuts/scripts/check-palette.py` (CI job).
- Engine, native, shaders, pack pipeline and the second-display data path are out of bounds.
