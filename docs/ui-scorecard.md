# Lemon Native — UI scorecard

Every round's Critic report (nota, travas, falhas) lands here, newest last. The loop and the rubric are the
owner's brief (sections 6–8); the stop conditions are brief section 7.2. Build evidence: the CI runs named
per round (build-apk.yml = the APK, peanuts.yml = the fork's checks + the palette contrast job).

| Round | Commit | Nota | Critério < 9.0 | Build | Relatório |
|---|---|---|---|---|---|
| 0 (baseline) | `3647c8c` rebrand | — (no UI round: the renamed upstream, CI baseline) | — | build-apk: fixed after 44924410924's compile error (Signer brace) + 44924993648; peanuts: fixed after 44924410858's two test assertions | — |
| 1 | `ui(round-01)` | pending the Critic | pending | pending | below |

## Round 1 — ui(round-01): the Lemon design system

The Creator's declared scope for the round (the Critic judges the artifacts, not this list):

- Palette "Lemon" (dark reference + light re-map), every annotated WCAG 2.1 pair proven by
  `peanuts/scripts/check-palette.py`, which CI now runs (peanuts.yml).
- Type: bundled Manrope (5 static weights, OFL) through `Ui.face`/`PUi.face` → `LemonType`; JetBrains Mono
  for the build card's percent readout.
- Shape: the sheet/dialog tier tokenized (`RADIUS_SHEET = 28`, matching `bn_dialog_bg`).
- Components: `StatusChip`, `HeroCard` (the library's "Continue playing"), `BuildStepper` (the build
  card's Import → Prepare → Companion pills).
- Settings › Appearance: dark default on first run, light and follow-system alternatives
  (`UiModeManager.setApplicationNightMode`).
- i18n: `values/strings.xml` (en) + `values-pt-rBR/strings.xml` (pt-BR), including translations of the
  29 pre-existing resource strings; new UI text is resources-only.
- Docs: `peanuts/DESIGN-ui.md` rewritten as the Lemon contract; this scorecard; `docs/design-system.md`;
  the baseline map is `docs/ui-audit.md`.

Known gaps the Creator declares for the Critic (also in `docs/ui-audit.md` §5): the older Java-literal
strings are not yet extracted to resources; no emulator screenshots in this environment (no local Android
SDK — evidence is code, CI, the palette script and the contract tables); the icons' happy-face heritage
icon is replaced by the minimalist lemon, but the legacy `ic_launcher.xml` fallback remains (minSdk 33
never renders it).
