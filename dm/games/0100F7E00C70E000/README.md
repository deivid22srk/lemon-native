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
  `0100F7E00C70E000-20261120-071515.log`, and a second run the same afternoon,
  `0100F7E00C70E000-20261120-164406.log`, the same picture on the same build).
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
- 2026-11-20, second log: the operations that
  [Refracting materials flat green through a shared image of two formats](../../../docs/reference/rendering.md#refracting-materials-flat-green-through-a-shared-image-of-two-formats-android)
  had left unaudited are audited now (the DLSS stand-in's evaluation, its last
  stores and its fallback blit go by the textures' formats through scratch
  images; `same_shape` names the textures' NVN formats, and the run lists the
  view-format pairs its file holds). That run learned the pair `44 37 640 360`
  (B8G8R8A8 and R8G8B8A8 textures over one 640x360 memory — UE4's BGRA8/RGBA8
  colour targets).
- Diagnostics for the next capture: `DM_VK_PSO_SKIPLOG=n` names the first n
  targets a skipped draw was going into (serial, size, format, NVN target); the
  skipped-work warning names the (vertex, pixel) shader pairs skipped most
  (`vka_draw.c` skip_pipe); the pipelines statistics line counts the draws not
  skipped by kind, the third being the profile's sync-all rule;
  `DM_VK_TEX_STATS=<fragment shader key>` scans a lit material's input
  textures' contents.
- current (hypothesis, not corrected): on the 7.2 GB device (`MemAvailable`
  1.1 GB at boot, image cache pruning from 4,300 live images) the engine's
  streaming may discard textures, which would leave silhouettes without
  texture. The 8 GB note above; a device with 12 GB would separate memory
  pressure from rendering.
