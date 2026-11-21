# Hogwarts Legacy — pipeline map (round 1)

Where the graphics layer sits, with the sites a per-game fix or a debug flag can hook into.
Written from the sources (file:line at the branch tip); the symptom hypotheses live in
[triage.md](triage.md), the flag panel in [experiments.md](experiments.md).

## 1. Shader translation (Maxwell → SPIR-V, offline at pack time)

- Front end: the vendored Ryujinx translator (`dm/third_party/ryujinx/.../Spirv`),
  driven by `dm/gfx/tools/dmsh_xlate/Program.cs` (`TranslationOptions` at :253-274); decode and
  job setup by `dmsh_build.py:500-531`; the Android library built by `vk_android_build.py`
  (device answers `subgroup_size=64, ssbo_align=64, gather_bias=8` at :794-800); SPIR-V rewrites
  (HMMA lowering, `promote_locals`, `gmem_sink`) in `vk_android_spv.py` (`hmma_lds` :1755).
- **Precision: full FP32.** No `RelaxedPrecision` is emitted (the generator declares the
  capability, nothing uses it); fp16 exists only in the HMMA.16816 path where halves are explicit
  and every accumulation round is stated. `NoContraction` is decorated on every FP op **unless**
  `XLATE_REDUCED=1` (the default, `dmsh_build.py:529`) — reduced precision means "fewer
  NoContraction decorations, FMA contraction allowed", not fp16. A different `XLATE_REDUCED`
  changes the chain digest → a different shader library: it is a build-time choice, not a runtime
  flag (this is why the brief's `force_fp32` maps to a library rebuild, not a switch).
- The host's own FPCR flush-to-zero leaking into driver threads was fixed behind
  `VKA_HOST_FPCR=0` (`vka_fpenv.h:1-42`); pipeline caches carry the `-fp0` generation suffix
  (`vka_fpenv.h:44-51`) so the compile-env change gets its own cache.
- On-device load: `vka_shlib.c:25-79` (magic/ABI/chain checks), `vka_shader.c:104-153`
  (`lib_load`, fallback chain), runtime SPIR-V patches in `vka_spvpatch.c` (vertex-input retype,
  one-lane subgroup), float-output retype keyed in `vka_pso.c:493-523`. `DM_VK_SPV_OVERRIDE`
  pairs patch a named module's SPIR-V at load — the `shader_hash_override` hook.

## 2. Vulkan replay (NVN command streams → Vulkan)

- **Dynamic rendering** by default (`vka_draw.c` `rp_begin` :623-812; classic VkRenderPass behind
  `DM_VK_CLASSIC_RP`, :336-386). Every attachment starts `LOAD/STORE`; only a pending clear
  covering the whole attachment **and** the whole render area promotes to `CLEAR`; partial-area
  clears become `vkCmdClearAttachments` inside the pass. `storeOp` is always STORE for game
  passes; `DONT_CARE` exists only in the host's present-path FSR image (`vka_present.c:362`) and
  classic-pass stencil rows.
- `DM_VK_RP_AREA=1` (default) narrows a ≥16 M-pixel pass's render area to the scissor corner
  with LOAD/STORE (:662-673); contents outside the area are undefined per spec, and the 2-miss
  full-area fallback (:666-668) only guards repeated misses — the A2 A/B.
- Images are created `UNDEFINED` and transitioned to `GENERAL` once; everything stays GENERAL
  after (`vka_tex.c:617,1428,1536,1724,1750,2985`).
- Barriers: full `ALL_COMMANDS→ALL_COMMANDS, WRITE→READ|WRITE` at pass ends/prepares/copies
  (`vka_barrier_at`, `vka_tex.c:159-170`); narrowed compute→compute barriers between dispatches
  (`vka_gbar.c:24-33`, off with `DM_VK_BAR_NARROW=0`); barriers kept inside passes when
  `DM_VK_BAR_KEEP` (default 1, `vka_draw.c:776-786`) with hazard-deferred pass ends.
- Submission chunking: `DM_VK_CHUNK=600` draws splits a frame's recording into parts; mid-pass
  splits reopen with LOAD (`DM_VK_CHUNK_PASS`, `vka_exec.c:652-670`); a pass whose render area is
  ≥16 M pixels is **never** split mid-pass, depth or not (`vka_exec.c:661-662` — the guard is on
  the render area itself; the comment cites the 6400x6400 ink map that would cost ~400 MB to
  store and load on a tiler).
- Memory: guest RAM becomes placed Vulkan memory lazily (`vka_mem.c:1046-1235`); the
  file-backed pool line ("moved 363.6 MiB in 399 pools") is the MAP_SHARED swap mechanism
  (`vka_mem.c:1683-1766`), not image aliasing. Texel-buffer views over placed memory are
  re-made whenever the segment table's generation moves (`vka_draw.c:1559-1571`, the 512-slot
  cache and its `[RED]`).
- Present: timeline semaphore spine (`vka.h:120-123`), host-signalled when the Turnip/KGSL
  zero-timeout fence probe waits (`DM_VK_TL_HOST`, `vka_dev.c:426-496` — **engaged in this
  capture**, log line 194); present thread with per-frame command buffers, acquire semaphores,
  `GOOGLE_display_timing` grid (`vka_present.c:766-1025`); `DM_VK_PRESENT_PREWAIT` /
  `DM_VK_PRESENT_GATE_WAIT` (30 ms on Turnip) are the present-sync experiment switches.

## 3. Textures, samplers, streaming

- Format table `fmt_ent_of` (`vka_tex.c:42-84`): NVN plain formats → RGBA8/RGBA16F/B10G11R11/
  RGB9E5; BC1-BC7 and ASTC map to their Vulkan compressed formats; depth/stencil with
  feature fallback. sRGB twins via `srgb_twin` (:86-91); raw-UNORM twins for texel buffers/present
  (:93-99).
- **Learned view formats**: an image used through two formats gets an alt-format list, persisted
  to `$DM_TEMP/vk_view_formats.txt` (the list lives per image, `vkfl` helpers in
  `vka_fmtlist.h:11-46`; the learn/query functions `vl_learn`/`vl_formats` are in
  `vka_tex.c:379-392`, attached at image creation `vka_tex.c:569`). Hogwarts' learned `44 ↔ 37`
  pair (BGRA8/RGBA8 over one memory) is
  the A4 suspect; `DM_VK_VIEW_LEARN=0` is the A/B.
- Samplers: `sampler_of` (`vka_tex.c:2871-2916`) — NVN LOD bias (clamped to the device max),
  anisotropy enabled when NVN asks and clamped to the device max, min/max LOD, border colour,
  min/max reduction via `samplerFilterMinmax`. **Round-1 additions**: `DM_VK_SAMP_LOD_BIAS`,
  `DM_VK_SAMP_ANISO`, `DM_VK_SAMP_MIP_FLOOR` — runtime, live, default-off, part of the cache
  key (the flag block above `sampler_of`).
- Streaming classes `ST_ZERO/ST_FILL/ST_TRACK/ST_SPARSE` (`vka_tex.c:712-806`); the profile's
  `tex_stream_track on` tracks compressed textures in CPU-writable pools (pure rule in
  `vka_texstream.h:24-27`). In this capture the class stayed silent and the BCN zero images are
  all unsampled (triage) — the streamed-texture hypothesis has no confirming evidence here.
- Buffer textures: this fork's texel-buffer views over guest memory (`vka_draw.c:1508-1573`,
  GPU/CPU scratch copies, `DM_VK_TBUF*` switches) beside upstream's image-copy path
  (`vka_tbuf.c:46-82`).

## 4. HDR / post-processing

- Render target formats are the game's own, mapped faithfully (UE4 BGRA8/RGBA8 colour pair
  `44 ↔ 37`, HDR intermediates via the format table). The host never re-runs exposure/bloom/
  tonemapping — those are the game's compute shaders. Hogwarts issues no NVN2 OP_DLSS (profile
  README :72-74), so the SGSR1 stand-in (`DM_VK_NDSR`, `vka_ndsr_sgsr.h`, dispatch site
  `vka_draw.c:3145-3310`) only sits at the game's own upscale dispatch — it is not in the
  exposure path.
- Consequence (A3): every exposure/eye-adaptation/bloom dependency is a **compute** dispatch —
  the pipeline-compile skip class (`DM_VK_CS_ASYNC`, `vka_pso.c:1545-1608`) can leave a
  histogram/history buffer unwritten, and the game keeps the stale state (the kept-result audit,
  `docs/reference/rendering.md:1608-1631`).

## 5. Profiles and switches

- Compile-time profile: `dm/games/<id>/game.tsv` → `game.py header` (validate :131-470, emit
  `game_profile.h` defines :439-455). Hogwarts' keys: `pso_sync_all on` (:33-39), 
  `tex_stream_track on` (:46), memory map, modules, shader groups.
- Runtime profile: `gen_runtime_profile.py` packs the scalars into `profile.bin`
  (`profile_rt.c:48-98` loads it once — nothing in the profile re-reads at runtime).
- **Live switches**: `VKA_ENV("DM_VK_*", def)` (`vka.h:63-79`) re-reads after every
  `vka_env_gen` bump; the launcher merges `env.txt`, `env-<title ID>.txt` (per-game override —
  the hogwarts-scoped experiment channel), the game's extra-environment and `DM_GAME_ENV`;
  the control file (`DM_CTL`, read by `hle_hid.c:225-274`) applies `env KEY=VALUE` lines at the
  next frame boundary (`vka_ctl.c:75-111`) — no restart for anything read through `VKA_ENV`
  or listed in `g_live` (`vka_ctl.c:68-72`). Restart-only switches are classified in
  `g_restart` (:63-66). Catalogue: `docs/env_switches.tsv` (794 switches).
- New per-game fix template: profile key → `game.py` validation → `gen_runtime_profile.py`
  SCALAR → `profile_rt.h` field → `game_profile_generic.h` macro → a pure-logic header + a
  `dm/host/tests/t_android_*.c` test → the gate at its site with its own log line (the pattern
  of `vkps_why`/`vka_tex_stream_track`).

## 6. Driver loading

`DM_VK_DRIVER` names a packaged driver folder (adrenotools private namespace,
`vka_driver.c:87-121`); system fallback on failure with a `[RED]`. Turnip-specific defaults key
on driver ID: mesh-shader avoidance, subgroup GS single-lane, shuffle-LDS, present gate wait
30 ms. Restart-only (re-launch applies a new driver). The readiness check probes the same driver
before launch (`vka_readiness.c`).

## 7. Where a fix hooks (decision table)

| Symptom | Natural hook |
|---|---|
| A1/A4 material-texture content | texture-content diagnostics first (`DM_VK_TEX_STATS`), then a profile-scoped sampling/format rule in `vka_tex.c` following the `tex_stream_track` template |
| A2 band | runtime A/B switches that already exist (`DM_VK_RP_AREA`, `DM_VK_BAR_KEEP`, `DM_VK_BAR_NARROW`, present sync) + replay frame capture |
| A3 blowout | `DM_VK_CS_ASYNC=0` A/B; if proven, a profile key to sync the exposure chain's compute, or a prewarm seed so nothing skips |
| A5 texture quality | the round-1 `DM_VK_SAMP_*` flags; a later profile key if a default should change |
