<!-- PEANUTS: Hogwarts Legacy, first profile; provisional memory/bring-up -->
# Hogwarts Legacy (`0100F7E00C70E000`)

Status: **bring-up / not tested upstream**. SDK 16.2.3, Unreal Engine 4, NVN, Wwise.

- Base game v0 only; 9 Unreal paks (version 11) with shader code archives.
- Shader tables generated from the dump with `shader_split.py` and the `ue_shaders`
  decoded by `unoodle` (69,002 Oodle streams): 66,578 shaders.
- Memory: `mem_reserve 0x1128000000`, `mem_alias 0x1008000000`,
  `mem_stack 0x10000000`, `app_pool 0xCD500000`. The first guess
  (`0x1028000000` reserve) left no heap and died in `SetHeapSize`.
- Known limits: 8 GB devices are below the engine's needs; the profile is a
  guess until a device boot is captured.

## Found on this game

- Android, Vulkan (Motorola Edge 30 Fusion, Adreno 660, Turnip T30, 0.1.5-dev,
  2026-11-20): on a first run — the pipeline cache empty and no prewarm seed —
  the character creator's model drew a flat bright green coat with a green
  bloom and black head and hands, and the Professor Fig cutscene (after the
  dragon attack) drew dark silhouettes with a blown-out white region; the UI
  and the 2D portraits were right, and no `[RED]` line (the run's log,
  `0100F7E00C70E000-20261120-071515.log`). This is the kept-result look of a
  draw skipped while its pipeline was made in the background
  ([A draw skipped for its pipeline left a result the game kept
  (Android)](../../../docs/reference/rendering.md#a-draw-skipped-for-its-pipeline-left-a-result-the-game-kept-android),
  the entry's Open item: a target the game keeps — a reflection probe, a
  texture drawn once — holds what its skipped draw never wrote, and undefined
  Adreno targets show as saturated green, black or blown white through bloom).
  The profile sets `pso_sync_all on` until a seed is recorded; a second and
  later run has the pipeline cache and shows whether any kept result remains
  with the key off.
- Diagnostics for the next capture: `DM_VK_PSO_SKIPLOG=n` names the first n
  targets a skipped draw was going into (serial, size, format, NVN target);
  the pipelines statistics line counts the draws not skipped by kind, the
  third being the profile's sync-all rule.
