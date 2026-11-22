# Assassin's Creed III Remastered (`01007F600B134000`)

Base game 1.0.0 (v0), SDK 5.4.0. Ubisoft's AnvilNext (the `scimitar` runtime) on **NVN**. Modules `main`, `subsdk1` (GLSLC, NVN's runtime shader compiler), `subsdk2` (NintendoSDK_movie) and `sdk`; `subsdk0` is NVIDIA's OpenGL driver and is never loaded (main imports nothing from it). The RomFS holds 265 files and 11.65 GB: `ac3/` (the `*.forge` world archives, 6.86 GB, plus 133 movies and 2 sound banks), `acl/` (the add-on content's data, 1.38 GB, with 40 movies), `ac3/videos/` and `acl/videos/` together 173 `.bik`/`.mp4` movies (1.39 GB), `nro/` and `.nrr/`, and 13 per-language epilepsy-warning clips plus a Ubisoft logo under `videos/`.

**Status: provisional, never run on a device.** The profile comes from the survey of a dump made from the user's own NSP (`scripts/build_ipa_from_nsp.py --survey-only`, notes in the profile's `game.tsv`). The build IDs, modules, RomFS marker, memory partition and shader source are read from the game files. Nothing has been booted: no phone run, no Mac run, no shader library, no prewarm seed. Treat it as a starting point for bring-up, not as a supported title.

## Builds

| Module | Build ID | What |
|---|---|---|
| `main` | `10ac5e3aefdf16d10130195845171cee` | the game |
| `subsdk1` | `11975fb8bdba5b7bf8128785e47dc9d5` | GLSLC |
| `subsdk2` | `41097c438a36afd2e879fa6f5e0f0850` | NintendoSDK_movie |
| `sdk` | `26ffb122262ca25e4c016c0af2f92a29` | nnSdk 5.4.0 |

The display version (1.0.0) is the base package's `control.nacp`.

## Graphics and shaders

- The game renders with NVN and compiles shaders at run time with its own **GLSLC** in `subsdk1`: `main` imports `glslcCompile`, `glslcCompileSpecialized`, `glslcFinalize` and the other GLSLC entry points, and the loose NROs in `nro/` import the compiler as well. No game file holds those outputs.
- The profile's `shader_groups` has `/glslc_capture/=runtime`, so the Android Build step captures the outputs by booting the game with `DM_GLSLC_CAPTURE` and translates them, the same way as [Fire Emblem: Three Houses](../010055D009F78000/README.md#files-and-shaders) (see also [Built-in shaders compiled with the game's own GLSLC](../../../docs/reference/shaders.md#built-in-shaders-compiled-with-the-games-own-glslc)).
- The only BNSH seen in the survey is `GameBg.bnsh` at the RomFS root; the rest of the shaders are the runtime GLSLC outputs. `shader_map.tsv` and the other tables are empty until a build captures and splits them.
- The survey found no `.pak`, Unity, Unreal or SPIR-V content: there is nothing else for `shader_split.py` to read.

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

## Open

- Every runtime step: first boot, SDK 5 NVN / service layouts, audio (Wwise), movie playback (`subsdk2`), DLC (`acl/`), the Forge archives, and the GLSLC capture's completeness.
- `romfs_marker ac3/DataNX.forge` and `pad_layout full` are checks/guesses from the files; no device run confirms them.
- `bf_want.tsv`, `pso_manifest.tsv`, `shader_map.tsv` and the other shader tables are empty; there is no `pso_pairs.tsv`, no `patches.py` and no prewarm seed. No frame-rate work has been done.
