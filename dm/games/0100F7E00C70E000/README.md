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

## Rendering

- **Found on this game**, first device run (Turnip T30, build `c3168b1`, 2D/UI
  right, 30 FPS, GPU 11–15 ms):
  - [Refracting materials flat green through a shared image of two formats](../../../docs/reference/rendering.md#refracting-materials-flat-green-through-a-shared-image-of-two-formats-android)
    (current): the log's one picture-changing condition is the learned
    view-format pair `44 37 640 360` (`$DM_TEMP/vk_view_formats.txt`: B8G8R8A8
    and R8G8B8A8 textures over one 640x360 memory, learned mid-run — UE4's
    BGRA8/RGBA8 colour targets). The operations that entry's fix had left
    unaudited are audited now.
- current: 3D wrong on the same run. Character creator: the model flat bright
  green (0,255,0), head and hands black; cave cutscene: character silhouettes
  without texture, flat grey-green scenery, one blown-out white area, irregular
  black ceiling polygons. No red line names a cause (66,578-shader library
  whole, no pipeline failed), so the next run's log must say which shaders the
  missing pipelines belong to: the skipped-work warning names the (vertex,
  pixel) pairs skipped most now, and `DM_VK_TEX_STATS` a suspect texture's
  contents. 7,981 draws were skipped waiting for their pipelines (7 % of the
  last window's draws) — irregular black polygons are those draws' likely mark,
  not a cause of the flat green.
- current (hypothesis, not corrected here): on the 7.2 GB device
  (`MemAvailable` 1.1 GB at boot, image cache pruning from 4,300 live images)
  the engine's streaming may discard textures, which would leave silhouettes
  without texture. The 8 GB note above; a device with 12 GB would separate
  memory pressure from rendering.

