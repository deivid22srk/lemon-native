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
- 2026-11-20, third capture (`0100F7E00C70E000-20261120-204814.log`, the
  audited build `ce55ba6`): the picture is the same with `pso_sync_all on` —
  the pipelines line counts 382 draws not skipped by the profile's sync-all
  rule, no `draw skipped, its pipeline not made yet` line (the
  `DM_VK_PSO_SKIPLOG` lines, default 8), and 878 "draws skipped waiting" that
  are compute dispatches, which the sync-all rule does not cover. So the
  kept-result look of a skipped draw was closed and the picture has another
  cause. The same capture: no `DLSS stand-in:` line (the game's upscale is its
  own compute, NVN2's OP_DLSS is not issued), the texture pool's feedback read
  94 IDs and made 9 images (2.3 MiB, its 8 reporting modules are the pool-only
  class, the materials' handles are traced statically), and the same picture
  with the game's data from a second source (`Hogwarts-Compilado`, the
  imported compiled pack) and a second driver (Turnip Amaral 27.0.0-devel
  beside T30 Mr_Purple): the fault is in the replay, not the game's dump, the
  driver build, or the APK's own build — recompiling the game's data changes
  nothing.
- current (hypothesis, corrected 2026-11-20): the engine's streamed compressed
  material textures are registered with empty storage and first sampled before
  their mips land, so they were classified GPU content (`ST_ZERO`,
  `vka_tex.c` `storage_mode`: the NVN2 first-sampled rule
  `nvn2_sampled_content` took only uncompressed formats) and the streamer's
  later CPU writes never reached the image — the creator's model samples
  saturated garbage (its flat green coat) or zeros (black skin), the cutscene
  draws flat silhouettes with a blown white region, the geometry, lighting and
  2D portraits (packaged, created with data) right. The capture shows no
  `written by the CPU before its first use` line at all: no texture took the
  first-sampled path, as the compressed exclusion predicts for a game whose
  streamed textures are all compressed. The fix lets compressed formats join
  the rule (the GPU cannot render into them, a copy still ends the tracking),
  and logs the class (`first sampled with its storage empty (NVN2)`). On the
  next capture the class's lines must appear while the creator loads, and the
  model's textures must fill; `DM_VK_STORAGE_FIRST_USE=0` is the A/B (the
  picture should return to the broken one). The memory-pressure hypothesis
  stays open as a second cause the fixed build can now separate (on the
  7.2 GB device, `MemAvailable` 1.1 GB at boot: a streamed texture that fills
  and is then evicted blackens late, not from the first frame).
- 2026-11-21 (the rodada 7 build, run `44848421069`): the same picture — the creator's model all black now, the
  cutscene's silhouettes — and the round-7 class is silent in the log (`0100F7E00C70E000-20261121-062840.log`: neither of
  its two lines, with `[vk]` output present 841 lines). The reason: the game runs on a **Switch 1** SDK (16.2.3; the
  loader's `sdk: built for the Switch 2 (spec: Ounce)` line is absent, so `vka_nvn2()` is 0 and the NVN2 first-sampled
  rule never runs for this game — the round-7 fix is dead code here). The pipelines line still shows sync-all working
  (425 not skipped by the profile's rule) and its "draws skipped waiting 791" is compute dispatches, as the third
  capture found. The profile key `tex_stream_track on` now makes a compressed texture in a pool the CPU can write
  tracked whatever its COMPRESSIBLE flag and whether its storage holds anything at the upload (`vka_texstream.h`, the
  pure rule `t_android_texstream` checks on Linux; the runtime profile's schema grew the field). On the next capture the
  `tracked by the profile's tex_stream_track (NVN1)` lines must appear while the creator loads and the model's textures
  must fill; `DM_VK_BCN_ZEROLOG=<n>` (default 8) names the compressed zero images the key misses with every door's
  state, so the capture either shows the tracked lines or names the door (an uncompressed culprit takes the
  `tex_flush_upload`/`tex_dirty_upload` keys instead, as Tears of the Kingdom does).

## Black characters/objects (textured UI fine) — type 1 texture handles with the sampler word in c2

<!-- PEANUTS(upstream-candidate) -->

Reported symptom (device log + screenshots, 2026-11-21): character models and other material-bound geometry render as solid
black silhouettes in gameplay, while the character-creation/menu screens, HUD, lighting and background all render correctly.
This is a **separate, independently plausible cause** from the streamed-compressed-texture one just above (both can produce
black character geometry; they have not been disambiguated against each other on a device, since this environment has no
Android/Vulkan device to run either capture on). This entry matches a cause already found and fixed upstream for this same
game on the Mac/Metal path: nxnative PR #890 (commit `699d815`) found that Hogwarts Legacy keeps its material textures'
64-bit NVN handles in its own uniform buffers (8 bytes apart) but binds some samplers with `BindSeparateSampler`, producing
a type-1 bindless handle whose two words are a uniform buffer word and a c2 separate-sampler-table word (whichever field
each is in) — a form `decode_ubo_tex` (`dmsh_build.py`) did not recognize, since it expected both words in game uniform
buffers. Undecoded, 46,782 of the game's 65,047 shaders had an unresolved material-texture binding, and gameplay drew black
(that PR's own wording: "gameplay drew black, with faint outlines, while text and menus were right").

`decode_ubo_tex` is shared/borrowed between the Mac (`dmsh_build.py`) and the Android/Vulkan (`vk_android_build.py`, via
`BORROWED`) chains, so the same gap applies to this fork's Vulkan path. The fix is ported here from that upstream commit:
a new branch in `decode_ubo_tex` recognizes the case where one word is a valid uniform-buffer slot (3..16) and the other is
a valid c2 separate-sampler-table offset, returning `('sep', slot)` for the sampler half instead of failing the decode; the
three call sites that consume its return value (`dmsh_build.py` `reflect`, `vk_android_build.py` `sampler_half` and
`classify`) bind that case as a plain separate sampler (`KIND_SAMP`), the same kind `decode_tex`'s own separate-sampler path
already uses.

**Status: ported and unit-tested against the two exact handle names named in the upstream PR
(`dm/tools/tests/test_decode_ubo_tex_sep_sampler.py`); not yet verified on an actual Android device or against the game's
real shader binaries** — this environment has no Android/Vulkan device, no copy of the game, and no license to one, so the
translated shader library and in-game rendering could not be built or played to confirm the fix end to end, or to check
whether it is a real contributor here versus already fully explained by the streamed-texture cause above. Editing
`dmsh_build.py` and `vk_android_build.py` is a chain change (`dmsh_chain.py --header` / `vk_android_build.py --header` were
re-run and committed with this change); a build after updating must retranslate every game's shader library, this one
included.

Upstream candidate: yes (the decoder is shared with the Mac path, where this exact fix is already merged for this game).

**Open:** a device capture of this build should confirm whether `shaders with a binding whose slot was not decoded` drops
to 0 for this game (it must, if the handle form above is present in this build's shaders) and whether any black-rendering
symptom remains once both this fix and the streamed-texture fix above are in the same build — that would mean a third cause.

