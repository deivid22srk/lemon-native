# Assassin's Creed III Remastered (`01007F600B134000`)

Base game 1.0.0 (v0), SDK 5.4.0. Ubisoft's AnvilNext (the `scimitar` runtime) on **NVN**. Modules `main`, `subsdk1` (GLSLC, NVN's runtime shader compiler), `subsdk2` (NintendoSDK_movie) and `sdk`; `subsdk0` is NVIDIA's OpenGL driver and is never loaded (main imports nothing from it). The RomFS holds 265 files and 11.65 GB: `ac3/` (the `*.forge` world archives, 6.86 GB, plus 133 movies and 2 sound banks), `acl/` (the add-on content's data, 1.38 GB, with 40 movies), `ac3/videos/` and `acl/videos/` together 173 `.bik`/`.mp4` movies (1.39 GB), `nro/` and `.nrr/`, and 13 per-language epilepsy-warning clips plus a Ubisoft logo under `videos/`.

**Status: provisional, never played on a device.** The profile comes from the survey of a dump made from the user's own NSP (`scripts/build_ipa_from_nsp.py --survey-only`, notes in the profile's `game.tsv`). The build IDs, modules, RomFS marker and memory partition are read from the game files. A first on-device Build imported the NSP and rewrote the code, then stopped at the shader split because the profile's table was empty (see Graphics and shaders); that table is now filled from a full split of the dump. The game itself has not booted: no phone run, no GLSLC capture, no prewarm seed. Treat it as a starting point for bring-up, not as a supported title.

## Builds

| Module | Build ID | What |
|---|---|---|
| `main` | `10ac5e3aefdf16d10130195845171cee` | the game |
| `subsdk1` | `11975fb8bdba5b7bf8128785e47dc9d5` | GLSLC |
| `subsdk2` | `41097c438a36afd2e879fa6f5e0f0850` | NintendoSDK_movie |
| `sdk` | `26ffb122262ca25e4c016c0af2f92a29` | nnSdk 5.4.0 |

The display version (1.0.0) is the base package's `control.nacp`.

## Graphics and shaders

- The game renders with NVN. A full `shader_split.py` of the dump reads **1009 shaders**: 1005 BNSH programs inside the three NRO modules in `nro/` (`launcher_f.nro`, `scimitar_ac3_dll_bundleengine_nx_f.nro`, `scimitar_acl_dll_bundleengine_nx_f.nro`) and 4 in `GameBg.bnsh` at the RomFS root. That full split is the profile's `shader_map.tsv` (1009 rows), `shader_facts.tsv`, `shader_pairs.tsv` and `pso_pairs.tsv`.
- Six more BNSH variations in the `*.forge` archives are unreadable: their vertex code area's header magic does not match the stage, so `shader_split.py` leaves them out. They are the only shader shapes the tool could not read in this dump.
- `main` imports `glslcCompile`, `glslcCompileSpecialized`, `glslcFinalize` and the other GLSLC entry points, and the NROs call the compiler in `subsdk1` too. Any further outputs those calls make at run time are captured during Build through the profile's `/glslc_capture/=runtime` group (`DM_GLSLC_CAPTURE`, `hle_glslc.c`), as for [Fire Emblem: Three Houses](../010055D009F78000/README.md#files-and-shaders). None had been captured when the tables above were made.
- The first on-device Build with empty shader tables stopped at the split: a targeted split selects only the RomFS files named in `shader_map.tsv`, selected 0 of 265, and the app refuses a pack with an empty shader library (`build/01007F600B134000.log`, 2026-11-22). The tables above fix that; no other dump is needed.

## Files

- `ac3/*.forge` are Ubisoft's Forge world archives (Boston, New York, the Frontier, the naval maps, the Homestead and so on), `ac3/DataNX.forge` the shared data. The add-on's `acl/` tree has its own `.forge`, sound banks and movies.
- Audio is Wwise (`.pck` sound banks, 1.64 GB) and movies are Bink (`.bik`, 116 files) and MP4 (71 files).
- `bootsequence.json` and `scimitar.ini` at the root drive the engine's launch; `GameBg.bnsh` / `GameBg.bntx` are the background picture.

## Memory

| Setting | Value | Why |
|---|---|---|
| `mem_reserve` | `0x100000000` | `main` sets a heap of at most the pool − 64 MB: a 3328 MB heap region |
| `mem_alias` | `0x10000000` | 256 MB alias region, as the other profiles of the same SDK |
| `mem_stack` | `0x10000000` | 256 MB stack |
| `app_pool` | `0xCD500000` | the standard 4 GB Switch application pool |
| `sys_resource` | `0x0` | read from `main.npdm`: no virtual address memory |

These are the survey's guesses, not measurements. SDK 5 is older than every profile's SDK 16 object layout: NVN object sizes, the API version and service layouts differ by SDK, so the host may need SDK 5 fixes before the game reaches its frame loop ([NVN objects](../../../docs/reference/nvn-objects.md), [SDK differences](../../../docs/reference/services.md)).

## Bring-up

- **Empty shader tables (resolved 2026-11-22):** the first on-device Build reached the split with an empty `shader_map.tsv`, selected 0 of 265 RomFS files and stopped; the full split of the dump and the tables now committed are described under [Graphics and shaders](#graphics-and-shaders).
- **`FATAL gate overflow` (resolved 2026-11-22):** the first launch loaded `main`, `subsdk1`, `subsdk2` and `sdk` (136 host gates), then aborted while loading the NRO DLLs: one has 6251 rewritten gates and another 6178, 12565 in all, over the host's old 8192 limit. The host's gate table was enlarged to 16384 registrations ([Gate table capacity](../../../docs/reference/rewriter.md#gate-table-capacity)). No gameplay has been seen yet.
- **Launcher abort after the NVN API query (open, 2026-11-23):** with the gate table large enough the boot got further: `nnMain` mounts the RomFS, takes `nv::InitializeGraphics` (128 MB pool) and `vi::SetLayerScalingMode`, loads the launcher NRO (`launcher_f.nro`, 0 gates), which maps three 1 MB thread stacks and one 128 KB one, then reaches the first movie: the host's `movie::Decoder` takeover builds an AVC decoder (type 1, output 3 ABGR, mode 2 native texture), the client registers its four events, `Configure` reports 1920×1080 and `GetOutputBufferSize` 3110400. The launcher then sets `vi::SetLayerScalingMode 2` again, reads the NVN API version (`53.313`, the SDK 5 answer) and aborts: `Break#1 reason=0 Result=0 svc at sdk+0xd980c`, called from `sdk+0x4c9b8` ← `nro2+0xd748` ← `nro2+0xebc0` ← `nro2+0x11858` ← `main+0x3dbc` — the launcher's own assert through `nn::diag::Abort`, reached before any `Decoder::Start` and before the window-origin NVN call. No `[RED]` line, no unimplemented NVN lookup and no `UNIMPL` stub preceded it, and the NVN API answer is the one Bastion (same SDK 5.4.0) takes, so the failed check is not one the host answers visibly. SDK 5.4.0's abort code reaches `svcBreak` without a symbol the host takes over (the `nn::diag::detail::AbortImpl` names are newer), and its messages go out as `svcOutputDebugString`, which the host dropped unless `DM_DBG` was set — so the log named nothing but the offsets. The host now keeps the last 24 guest diag strings and prints them with every Break report, and dumps the break payload's first 64 bytes (`hle_svc.c`), where some SDK builds pass the assert text; the next run of this game should name the failed check.

## Open

- Every runtime step: first boot, SDK 5 NVN / service layouts, audio (Wwise), movie playback (`subsdk2`), DLC (`acl/`), the Forge archives, and the GLSLC capture's completeness.
- `romfs_marker ac3/DataNX.forge` and `pad_layout full` are checks/guesses from the files; no device run confirms them.
- The 1009-key table is from a full split of the base dump; no device run has confirmed it, and no GLSLC runtime capture has been made yet. `bf_want.tsv` and `pso_manifest.tsv` are empty; there is no `patches.py` and no prewarm seed. No frame-rate work has been done.
