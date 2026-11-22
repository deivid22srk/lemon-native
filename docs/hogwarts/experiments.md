# Hogwarts Legacy — experiment panel (round 1)

The brief asks for runtime debug flags, default-off, scoped to this game, so one APK tests many
hypotheses without a rebuild. The project already ships that panel: every `DM_VK_*` switch read
through `VKA_ENV` is **live** (re-read at the next frame boundary after a `env KEY=VALUE` line in
the control file, `ctl.txt`), and the launcher merges `env-0100F7E00C70E000.txt` (per-game
scoping, no restart). This document maps the brief's 16 requested flags onto the mechanisms that
exist, marks the three added this round, and names the two that remain future work. All switches
are documented in `docs/env_switches.tsv`; the log labels live overrides in the `stats` line.

## How to set a flag for this game only

- Whole-run: a line `DM_VK_…=…` in `env-0100F7E00C70E000.txt` (the game's folder next to
  `env.txt`) — set before launch, per-game, everything else unaffected.
- Live A/B without restart: `adb shell "echo 'env DM_VK_…=…' >> /data/data/…/files/ctl.txt"`
  (the replay applies it at the next frame boundary; restart-only keys are labelled so in the
  log and refused politely).

## The brief's flags → mechanisms

| Brief flag | Mechanism | Status | Cost when on |
|---|---|---|---|
| `force_fp32` | the chain emits full-FP32 SPIR-V; there is no fp16 path to disable (pipeline map §1) — the flag has nothing to do at runtime | n/a (documented) | none |
| `precise_math` | rebuild the library with `XLATE_REDUCED=0` (every FP op gets `NoContraction`, no FMA contraction); a chain change → new library, the update flow installs it | build-time | shader compile + potential perf loss on the affected ops only |
| `conservative_barriers` | `DM_VK_BAR_NARROW=0` (all game barriers full) **+** `DM_VK_BAR_KEEP=0` (no barriers held inside passes) | exists, live | more barriers: frame-time cost, measured by the pace lines |
| `force_load_store` | game passes already always `LOAD/STORE`; the risky part is render-area narrowing: `DM_VK_RP_AREA=0` restores full-area passes everywhere | exists, live | tiler efficiency loss on ≥16 M-pixel passes |
| `disable_memory_aliasing` | placed-memory pools are the design; the A/B for the aliasing-adjacent class is `DM_VK_PLACE_PART_MB=<smaller>` (fewer joins) and `DM_VK_MEM_FILEBACK=0` (no file-backed pools) | exists, live/restart | memory footprint up |
| `present_extra_sync` | `DM_VK_PRESENT_PREWAIT=1` (pre-wait the timeline before present) and `DM_VK_PRESENT_GATE_WAIT=<ms>` | exists, live | pacing latency up |
| `nan_visualizer` | paint NaN/Inf outputs magenta in the final fragment stage | **future round 2** (a SPIR-V patch keyed like `VKA_FS_RETYPE`) | compile pass over affected modules |
| `hdr_rt_format` | render targets are the game's own formats, mapped faithfully; forcing a different RT format would change what the game's shaders read back — no safe host-side switch | n/a (documented) | — |
| `taa_history_reset` | TAA is the game's own compute; the host cannot reset its history. The reachable cousin is the A3 exposure-history path via `DM_VK_CS_ASYNC=0` (no skipped dispatches at all) | n/a (documented) | — |
| `lod_bias` | **`DM_VK_SAMP_LOD_BIAS=<float>`** added to every sampler's NVN bias (clamped to the device max) | **added this round** (`sampler_of`, `vka_tex.c`) | sharper/noisier minification; sampling cost ~neutral |
| `max_anisotropy` | **`DM_VK_SAMP_ANISO=<n>`** raises anisotropy onto samplers **registered after the change** (the game's already-registered states keep their handles until it re-registers them — a scene change does; the cache keys on the flags, `vka_tex.c`), never past the device max | **added this round** | measurable fill-rate cost; watch the pace lines |
| `texture_mip_floor` | **`DM_VK_SAMP_MIP_FLOOR=<float>`** (0–14; a NaN or absurd value reads as off) keeps samplers registered after the change out of the low mips: min LOD floor, and on the mipmap path `maxLod` rises with it only when the floor is active — with the flag off both sampler paths are bit-identical to the old code | **added this round** | low mips unused: more texture-cache misses, potential perf loss |
| `shader_hash_override` | `DM_VK_SPV_OVERRIDE=<key>=<file>,…` patches a named module's SPIR-V at library load (restart-only, `vka_shlib.c:148-161`) | exists, restart | none unless used |
| `driver_version` | `DM_VK_DRIVER=<folder\|system>` selects the packaged driver (restart); a second driver installs beside `WN-Turnip-1.19-b_Axxx`; `TU_DEBUG=gmem`/`sysmem` reaches the driver through the same env channel | exists, restart | whole-driver behaviour change |
| `frame_stats_log` | `DM_VK_STATS=<n>` (replay stats every n frames), the `stats` ctl command (one line with every live override), the pace/longframe lines always on | exists, live | log volume |
| `frame_dump_on_artifact` | the replay's own pixel capture, no screen recording needed: `screenshot <path>` (a PNG of the next presented frame, written by the present thread), `passdump <n>` (the frame's passes), `capture <n>` (a window's per-frame statistics line), `DM_VK_IMG_DUMP=<prefix>` (images dumped as they are recorded) | exists, live | one-frame stall + storage |

## What the user gets in the round-1 APK

- The three new sampler flags (registered as live in `vka_ctl.c`'s table, catalogued in
  `docs/env_switches.tsv`, one-time `[vk]` log line when first applied).
- Everything in the table above that already existed — the A/B matrix in [triage.md](triage.md)
  uses only existing switches plus these.

## Offline tests run this round

- `dm/tools/tests/test_env_switches.py` — the table lists the new switches (passing).
- `dm/tools/tests/test_advanced_settings.py` — repaired a pre-existing failure
  (`DM_VK_BCN_ZEROLOG` had no advanced-settings row; the generator now writes it, tier
  developer) — passing.
- No GPU here: the sampler flags' effect is device-side by design. `spirv-val`/
  `lavapipe` differential runs need the translated modules and a software Vulkan; the frame
  analysis tooling (Python, ffmpeg) lives in `scripts/` and `.work/` and is documented in the
  triage.
