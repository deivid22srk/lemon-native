# Hogwarts Legacy — triage (round 1)

Source material: the user's ZIP (`releases/download/Log/Logapp.zip`): one app log,
`0100F7E00C70E000-20261121-172921.log` (3,107 lines, ~244 s of gameplay), and one gameplay video,
`screen-20261121-173341.mp4` (1600x720, 140.7 s). Analysis frames and crops live under `.work/`
(never committed). Confidence levels per brief rule 5: `proved` / `likely` / `hypothesis`.

## Machine, driver, API

| Fact | Value | Evidence |
|---|---|---|
| SoC / GPU | Snapdragon with Adreno (TM) 660 | readiness line, log line 47 |
| Driver | packaged **Turnip**, `WN-Turnip-1.19-b_Axxx`, reported as `turnip Mesa driver (Mesa 26.3.0-devel (git-30da39187d))`, driver ID 18 (MESA_TURNIP), version 0x6802063 | log lines 28, 47, 55; loaded via libadrenotools (`vka_driver.c:87-121`) |
| Vulkan API | 1.3.363 | readiness line (log line 47) |
| RAM | 7.2 GiB (Android SDK 34), `MemAvailable` 1.1 GiB at boot | readiness, line 314 |
| Panel / pacing | 60.15 Hz panel, game paces at **interval 2 (30 fps)**, GPU busy p50/p90 ≈ 17–35 ms in gameplay, worse in heavy scenes | pace lines (e.g. 604, 536) |
| Subgroups / SSBO | shader library header: "64-lane subgroups, storage buffers at 64" | log line 305 |
| Host-signalled timelines | engaged: "a zero-timeout fence query waited for the GPU (20.4 ms)" → `DM_VK_TL_HOST` path | log line 194 |
| Shader library | **`v-7a5123a36213`, 66,578 shaders, 85,242 modules, 848.4 MB (chain f82a38bc8494)** — the post-type-1-fix library | log line 305 |

The run plays the **fixed** shader library (the rodada-8 capture's missing `v-7a5123a36213`): the
type-1 sep-sampler decode fix and `tex_stream_track` are live on the GPU in this capture. The
symptoms below are what remains **after** those fixes — the picture has moved from the flat
green/black silhouettes of 2026-11-20 to localized texture blotches and exposure blowout.

## Texture formats observed

- Compressed zero images (`DM_VK_BCN_ZEROLOG=8`): formats `0x8e`, `0x80`, sizes 32x32/64x64/8x8,
  pool 1, all `sampled 0 cpuw 0` — none is a sampled material texture (same verdict as rodada 8,
  log lines 288-293).
- The `44 ↔ 37` (B8G8R8A8_UNORM ↔ R8G8B8A8_UNORM) learned view-format pair over one 640x360
  memory is the UE4 BGRA8/RGBA8 colour-target pair documented in the profile README; this log's
  run continues from the persisted `vk_view_formats.txt` (`$DM_TEMP`), so no fresh learning line
  appears. A4's A/B remains `DM_VK_VIEW_LEARN=0`.
- Buffer textures bound as texel buffers over guest memory, first `texture ID 4523, 1024 elements,
  VkFormat 99` (`VK_FORMAT_R32_SINT`); once, at log t≈241.7 s, all 512 texel-buffer view slots were
  live (`[RED] vk: more than 512 texel buffer views at once`, line 2988) — the view cache
  (`g_tbv[512]`, `vka_draw.c:1452`) is invalidated wholesale on every placed-segment-table
  generation change, and scratch-copy views are created fresh per submission, so bursts of live
  views are expected; consequence: reads that miss the cache take the slow path (a perf event, not
  a correctness one). `hypothesis (perf, not the reported artifacts)`.

## Recurring warnings (no VK_ERROR, no device lost, no validation-layer output in the ZIP)

1. `[RED] gfx: mtl_* not yet on Android` × 13 — Metal-only statistics tools absent; cosmetic.
2. `[RED] vk: ADPF session of N threads not made` × 2 — Android power-hint session unavailable; perf-only.
3. `draws skipped waiting` in the pipelines lines: **compute dispatches skipped while their
   pipelines build** (`DM_VK_CS_ASYNC` default 1, `vka_pso.c:1545-1549`): 5 by frame 300, 25 by
   1500, **664 by frame 2400, 673 by 2700, 1,009 by 3000**; the profile's sync-all rule covers
   graphics draws only. 66,578 shaders means the first minutes compile a huge set; the exposure /
   eye-adaptation chain is compute — see A3.
4. Pipeline cache misses dominate early: "cache-only tries 12 hit / 741 missed" by frame 3000
   (line 2062); one long compile (compute shader `76315f0e514ba35a`, 1.2 s, background).
5. Pace dips to 3.5–5.0 fps in the heavy scene windows (frames 2343, 2980: replay recording
   156–267 ms/frame) — shader-compile warmup cost, expected on a first run with an empty pipeline
   cache.

## Log ↔ video calibration

- Log starts 17:29:21 (file name), video recording finalized 17:33:41 after 140.7 s → recording
  began ≈ 17:31:20 ≈ **log t≈119 s**. The log's last entry is frame 5933 at **t≈259.05 s**
  (line 3106, the log ends there; the run was stopped by the user) → the calibration closes
  almost exactly: 259.05 − 119 ≈ 140.0 s ≈ the video's duration. **The log covers the whole
  video (0:00–2:20); the 2:16 band report (≈ log t≈255 s) is inside it.**
- The log's last window (t≈244–259 s, log lines 3000–3107) is unremarkable: pace 25–26 fps,
  GPU busy p50/p90 ≈ 35/65 ms, 1,200–1,300 draws/frame, no `[RED]`, no compiles, no
  texture uploads, pool placement idle. **A band at 2:16 left no trace in the replay's
  statistics** — consistent with a sub-frame transient (a present/swapchain glitch would not
  change the frame's counters) or with content the counters do not see. The per-frame
  pixel capture (`shot`/`screenshot`, [experiments.md](experiments.md)) is the instrument that
  can freeze it.
- Video content: t0–11 s the London carriage cutscene (Fig, the protagonist's face close-up =
  A4's window); t12–21 s studio logos; t21–64 s the shore/dragon-attack sequence; t65–120 s the
  cave interior walk toward the exit (A3's blown white, A1's blotches on Fig); t121–141 s the
  cliff-top arrival vista (A2's report time). The recording is VFR at a 25.5 fps average
  (3,595 frames / 140.73 s): a one-frame transient can fall between captured frames (~39 ms).
- The game renders its 16:9 frame centred in the 1600x720 recording with black pillarboxes
  (≈ x 160–1440): the launcher presents 1:1, no stretch. The black bars are presentation, not an
  artifact.

## Symptom-by-symptom

### A1 — white blocks on Fig (1:36, 1:44, 1:47, 1:53, 1:58) — P0

Confirmed in frames (`.work/frames/a1_fig_*`, crops `sheets/a1_fig_heads.png`,
`a1_top_hit_zoom.png`): irregular **whitish blotches on Fig's hair and coat** at 1:44/1:47 —
clusters of saturated-white 8x8 px tiles (detector: up to 886 tiles in one frame at 1:58, when the
blown exit behind him dominates; every tile sits in x≈700–1100 — the region the exit and Fig's
upper body share — and none in x<700, so the detector alone does not separate "model-attached"
from "screen-space": that reading rests on the crops, where the blotches sit on the hair across
different views). The blotches are stable across adjacent frames at the same scene moment, and
NOT present on the protagonist's model in the same frames, nor on the environment. Two
overlapping contributions:

1. the hair/coat texture blotches themselves (a material-texture content/sampling class — the
   same family as A4), `likely`;
2. from 1:53 on, the cave exit's clipped white seen behind/around his silhouette (A3's
   contribution), `proved` (frames).

Not fp16: the shader chain emits full-FP32 SPIR-V with `NoContraction` decorations (only the
HMMA.16816 path uses halves, `vk_android_spv.py:1755`); no `RelaxedPrecision` is emitted
(pipeline map §1.2). Hypotheses (b)–(e) of the brief stay open; the first experiments are the
texture class (A/B `DM_VK_TEX_STATS=<lit material fs key>` on the hair texture,
`DM_VK_STORAGE_FIRST_USE=0`), then `DM_VK_BAR_KEEP=0`/`DM_VK_BAR_NARROW=0` for the sync class.

### A2 — irregular horizontal black band across the landscape (2:16) — P0

**Not reproduced in the recorded pixels.** Searched: the whole video at 1 fps (141 thumbs, by
row-mean luminance steps), the 2:14.5–2:21 window at full recorded rate (161 PNG frames, by
per-row dark-run length in the game area: runs ≥ 250 px at luminance < 30, then < 95, in sky rows
with bright rows above and below — the detectors' thresholds as run; the 1 fps pass and the
full-rate pass were inline analyses recorded in the round's scripts, `rank_frames.py` and
`detect_artifacts.py` use the denser-window thresholds), and the log's t≈244–259 s tail for any
statistical trace (none: see calibration). Zero pixel hits. The dark horizontal features at 2:16
are the natural haze layers and the subtitle backdrop; the black side bars are pillarboxing.
Verdict: a transient shorter than one recorded frame (VFR at a 25.5 fps average, a ~39 ms glitch
can fall between frames), or at a time outside the reported window, or device-local (not
captured). `hypothesis`. The A/B matrix from the code audit stays
the plan: `DM_VK_RP_AREA=0` (render-area narrowing on ≥16 M-pixel passes leaves contents outside
the area undefined — the 2-miss fallback only guards repeated misses), `DM_VK_BAR_KEEP=0`,
`DM_VK_BAR_NARROW=0`, `DM_VK_PRESENT_PREWAIT=1`/`DM_VK_PRESENT_GATE_WAIT`, plus
frame-capture evidence: the replay's own pixel capture needs no recording to freeze a 1-frame
band — `shot <path>` (a PNG of the next presented frame), `passdump <n>`, `screenshot [path]`
and `DM_VK_IMG_DUMP=<prefix>` (see [experiments.md](experiments.md)). No `[gmem]`/sysmem switch exists in the app today;
`TU_DEBUG=gmem` can be set from `env-<title ID>.txt` for a driver-behaviour A/B (Turnip ships a
sysmem path whose artifacts are documented upstream — v25.2.0-r5 release note: "If full screen
artifacting happens, try using the [gmem] tagged driver").

### A3 — cave exit blown to pure white (1:05–1:57) — P1

Confirmed: the exit renders as a uniform clipped-white region (~247–255) with **clean polygonal
edges that follow the rock geometry** (crop `sheets/a3_exit_edge_zoom.png`); a barely-visible grey
blob and an object silhouette show inside it. The exterior itself renders correctly from outside
(t121+: blue sky, sea, the tower). Interpretation: eye-adaptation/exposure computed for the dark
interior clips the bright sky — partially intentional (the Switch original also shows a bright
exit) but the total loss of every gradient is consistent with the exposure chain having kept a
skipped/garbage state: the exposure/eye-adaptation path is compute, and this run skipped
**1,009 compute dispatches by frame 3000** while their pipelines built (`DM_VK_CS_ASYNC=1`);
a histogram/history buffer the game never rewrites keeps what a skipped dispatch never wrote —
the documented kept-result mechanism (`docs/reference/rendering.md:1608-1631`).
`likely` for the excess (beyond the intentional part). Experiments: `DM_VK_CS_ASYNC=0` for a whole
run; `DM_VK_PREWARM` seed recorded, then a run with a warm pipeline cache — if the blowout
shrinks with warm caches, the skipped-dispatch class is proven.

### A4 — red pixel patches on the protagonist's cheeks (0:03–0:04) — P1

Confirmed in the face close-up (`sheets/a4_face_zoom.png`, 3x): soft-edged but pixel-stepped
reddish blotches on both cheeks and at the hairline; eyes, brows, mouth, hair all render
correctly. Class: the same material-texture content family as A1 (a skin detail/blush texture
sampled with wrong content or a partial channel mix), `likely`; the learned `44 ↔ 37`
channel-order pair over shared memory is the profile's documented risk (an R/B swap reads as
red-dominant patches). Experiments: `DM_VK_VIEW_LEARN=0` (kills the learned twins, forces per-use
formats), then `DM_VK_TEX_STATS=<fs key>` on the face material.

### A5 — generally low-resolution textures — P2 (improvement)

Expected from the Switch port (its assets are 720p-era) — the emulator's own contribution is the
mip/aniso path: samplers take the NVN anisotropy when the game asks for it (`sampler_of`,
`vka_tex.c:2889`), and the new `DM_VK_SAMP_*` flags (this round) allow raising anisotropy, biasing
LOD and flooring the mip level at runtime. The UE4 texture-pool size is the game's own decision
(no `ue_texture_pool` key in this profile). No stability risk accepted for this class.

## Suspect shaders / pipelines

- No failed pipeline or shader compile (0 across all statistics lines) — the suspect set is not
  compile-time.
- Compute dispatches of the exposure/eye-adaptation and bloom chain (skipped-while-building, A3).
- The lit-material fragment shaders sampling the streamed compressed material textures (A1/A4);
  names retrievable with `DM_VK_TEX_STATS=<fragment shader key>` and `DM_VK_PSO_SKIPLOG=16`.
- The 512-texel-buffer-view burst at log t≈241.7 s (texture ID 4523, R32_SINT particle-buffer
  class) — perf watch only.

## Known upstream driver bugs consulted

- Freedreno/Turnip docs: "In most cases random GPU faults and rendering artifacts are caused by
  some kind of undefined behavior" (Mesa freedreno documentation) — the A2 A/B matrix (barriers,
  render area, aliasing) targets exactly that class.
- Turnip release note (v25.2.0-r5, Mesa-Turnip-Builder): full-screen artifacting → try the
  `[gmem]` build / `TU_DEBUG=gmem`; community guidance to toggle `TU_DEBUG` gmem/sysmem
  (r/retroid v26.0.0-r3 thread). Applied as an experiment, never silently.
- Vulkan tile-based-rendering best practices (docs.vulkan.org): `DONT_CARE`/narrow render areas
  on tilers leave attachment contents undefined outside the area — the `DM_VK_RP_AREA=0` A/B.

## What this triage does NOT have

- No device validation (rule 5): every fix path below is a runtime A/B for the user to run.
- No logcat / Vulkan validation-layer output in the ZIP (the app log only); no shader dumps.
- No frame capture from the replay (the `capture` command) — requested in the validation plan,
  because a 1-frame band cannot be hunted with a 30 fps screen recording.
