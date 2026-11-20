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
  and the 2D portraits were right, and no `[RED]` line naming a cause (the
  run's log, `0100F7E00C70E000-20261120-071515.log`, and a second run the same
  afternoon, `0100F7E00C70E000-20261120-164406.log`, the same picture on the
  same build; the skips line `[RED] vk: 7 % of draws skipped …` is there, it
  counts the draws and names no target).
  This is the kept-result look of a draw skipped while its pipeline was made in
  the background
  ([A draw skipped for its pipeline left a result the game kept
  (Android)](../../../docs/reference/rendering.md#a-draw-skipped-for-its-pipeline-left-a-result-the-game-kept-android),
  the entry's Open item: a target the game keeps — a reflection probe, a
  texture drawn once — holds what its skipped draw never wrote, and undefined
  Adreno targets show as saturated green, black or blown white through bloom;
  the second log counts 7,981 draws skipped over its last 300-frame window,
  7 %, all "(0 failed)" pipelines). The profile sets `pso_sync_all on` until a
  seed is recorded; a second and later run has the pipeline cache and shows
  whether any kept result remains with the key off.
- 2026-11-20, second log: its run learned the pair `44 37 640 360`
  (B8G8R8A8 and R8G8B8A8 textures over one 640x360 memory — UE4's BGRA8/RGBA8
  colour targets), and its 7,981 skipped draws are the class the profile's
  `pso_sync_all` closes. The audit of the operations
  [Refracting materials flat green through a shared image of two formats](../../../docs/reference/rendering.md#refracting-materials-flat-green-through-a-shared-image-of-two-formats-android)
  had left open — the DLSS stand-in's evaluation, its last stores and its
  fallback blit going by the textures' formats through scratch images — landed
  **after** that capture, so the log does not show its lines yet: its last
  `same_shape` line names no NVN formats and its skips line ends without the
  shader pairs. A run of the audited build is the one that must show them:
  the shader pairs at the end of the skipped-work warning's line
  (`; shaders skipped most: (vertex …, pixel …) xN`, a mesh draw's pair as
  `(mesh …, pixel …)`) when a draw is still skipped, the third kind in the
  asynchronous pipelines line (`+ N by the profile's sync-all rule`), the NVN
  formats in `same_shape`'s line (`NVN 0x… ↔ 0x…`) when a pair is learned
  again (a fresh `$DM_TEMP`: with the file held, the pair comes from it at the
  image's creation and nothing logs), and the run's first
  `learned view formats … : 44 ↔ 37 640x360` line listing the pairs the file
  holds — the pair above among them, learned by the second log's run.
- Diagnostics for the next capture: `DM_VK_PSO_SKIPLOG=n` names the first n
  targets a skipped draw was going into (serial, size, format, NVN target); the
  skipped-work warning names the shader pairs skipped most, a vertex draw's as
  (vertex, pixel) and a mesh draw's as (mesh, pixel) (`vka_draw.c` skip_pipe);
  the pipelines statistics line counts the draws not
  skipped by kind, the third being the profile's sync-all rule;
  `DM_VK_TEX_STATS=<fragment shader key>` scans a lit material's input
  textures' contents.
- current (hypothesis, not corrected): on the 7.2 GB device (`MemAvailable`
  1.1 GB at boot, image cache pruning from 4,300 live images) the engine's
  streaming may discard textures, which would leave silhouettes without
  texture. The 8 GB note above; a device with 12 GB would separate memory
  pressure from rendering.
