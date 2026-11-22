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
- 2026-11-21, the rodada 8 capture (`0100F7E00C70E000-20261121-074846.log`, build `f2afda6`, the same
  picture on the device): the round-8 tracking is silent too — no `tracked by the profile's
  tex_stream_track (NVN1)` line (0 of them) — and `DM_VK_BCN_ZEROLOG`'s 8 compressed zero images all have
  `sampled 0 cpuw 0` (32x32/64x64/8x8, formats 0x49/0x80/0x8e, pool 1): none of them is a sampled material
  texture, so the streamed-compressed-texture hypothesis has no confirming evidence in this capture. The
  decisive lines are the library's own: `v-7a5123a36213/dmsh_vk.lib: this host's version 7a5123a36213 is
  missing (the app's shader update makes it); playing version dfebef5d68a9, the same ABI cb5a89b39291` —
  **every capture so far played the pre-type-1-fix library** (its 66,578 shaders carry the unresolved
  material bindings the section below names), so the type-1 decode fix has never reached the GPU. The pack
  has no delta manifest row for its older versions, so `Ondevice.autoUpdate` never ran
  (`deltaShaders < 0`) and the game list showed only the quiet `Ready · update` chip while the game's own
  page offers the update (`GameSettings.shaderUpdateCard`): updating the shaders on the phone (a long job
  for 66,578 shaders) and starting the game again is how the fix lands. Rodada 9 makes the case loud
  instead of silent: the replay's playing-an-older-same-ABI-library line is `[RED]` now (it was `[vk]`
  info; the legacy pre-versioned case already was), `log_rules.tsv`'s `shader-update` rule is `red` with
  player text naming the update, and the game list adds the `Shaders are one update behind` warn chip for
  exactly this state (playable, no known delta).

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


## "O conteúdo do jogo não foi totalmente instalado" — the day-one update is missing (not a host bug)

<!-- PEANUTS: device report 2026-11-22 (Banana Native) — diagnosis, no code change -->

Reported by the tester (2026-11-22) on Banana Native (`peanuts-native-0.1.5-dev`,
`c823a1c50426`): over the game's menu the game shows "O conteúdo do jogo não foi
totalmente instalado. Visita a Nintendo eShop para fazer o download do conteúdo mais
recente do jogo." with an "Ir para a Nintendo eShop" button (B = back to the main
menu). The tester asks whether this is a Lemon Native bug, the game demanding an
update, or something else.

Measurements from `0100F7E00C70E000-20261122-080206.log` (the session's screenshot,
`Screenshot_20261122-082942.png`, shows the dialog):

- The pack mounts the **base package only**: `DM_ROMFS=…/2Hogwarts/Hogwarts
  Legacy.nup:base/romfs`, and the host reports the title as
  `Hogwarts Legacy 0100F7E00C70E000 version 0`; `DLC: 0 links in DM_AOC_DIR` and
  `aoc: reporting 0 add-on contents {}` (the tester owns no add-on content — the
  correct answer, and the game queries aoc once at boot only).
- The game **runs and plays with the base content**: ~29 minutes of real gameplay
  (pace lines at 30 fps, 1,700–2,700 draws a frame, GPU 13–24 ms busy) with 20
  autosave commits spread over the session; the gate appears at the end, blocking
  further story progress.
- Clicking "Ir para a Nintendo eShop" six times: each click reaches the host as
  `ec::ShowShopApplicationInformation (cut): the host has no shop` — the game's own
  dialog, and the offline host correctly has no shop to open (the button can do
  nothing here).
- **No content service is involved in the gate**: no arp, no nim (0 matches in the
  host and the log), no repeated aoc query; and no `[RED]` line about a missing
  file or a failed open — the base RomFS is complete and the host mounted it
  cleanly. Whatever the game found missing was never in the dump.

Why this is the game by design: the Switch release ships **~7 GB on the cart and
requires an 8 GB day-one download** ("Hogwarts Legacy Physical & Digital Switch
Release Will Require 8GB Day 1 Update", NintendoLife, 2023-10-02; r/HarryPotterGame:
"Yes, you have to update. The disc doesn't have the full game"). The base package
alone is not the whole game; on a real console with the cart and no download the
same screen appears. The game's own check (pak presence or its version, the log
cannot distinguish) is **hipótese**; the data side — the update content is absent
from this dump — is **provado**. Lemon Native will show the same screen with the
same pack: it is the data, not the app version.

Next step: the tester supplies the update content (the ~8 GB patch, title
`0100F7E00C70E000` version > 0 — e.g. an NSP-Box plaintext NSP of the update beside
the game in its folder, then re-add/Prepare; a folder dump as `updates/<v>/` works
too). The pack then mounts the update's own full RomFS (android/NUP.md DumpLayout:
"an update alone that carries the whole game"). One open item on our side: the
profile knows the **v0 build only** ("Base game v0 only" above); if the update's
main build IDs differ (a day-one patch that size almost certainly changed code),
the profile needs those builds added (shader tables from the update's modules,
`display_versions` rows) before the pack accepts the update.

## Audio reported muffled ("abafado", like a cinema recording)

<!-- PEANUTS: device report 2026-11-22; no fix yet — measure first (DM_AUDIO_DUMP) -->

Reported by the tester (2026-11-22): character speech sounds muffled/echoey —
"como um filme gravado no cinema". A gameplay video was shared but cannot be
listened to here; the measurements below are from the device log.

Measurements from `0100F7E00C70E000-20261122-072915.log` (build `eeb5be5`,
Turnip T30):

- The game opens **both** an audren renderer (`OpenAudioRenderer rate 48000
  samples per frame 240 mix buffers 6 ... voices 32 sinks 1 effects 0`,
  original device mode 0 → CPU/manual) and a plain AudioOut
  (`main+0x585ef70`, no release event, the game polls) — the Wwise output, the
  arrangement Fire Emblem Engage shows ([Several outputs at
  once](../../../docs/reference/audio.md#several-outputs-at-once-audio-n)).
- The audren renderer is **silent**: `renderer 0 rendered 36000 frames, 0 of
  them not silent` (the counter checks the final int16 samples — the canonical
  check for "the game's sound reaches the output"). Everything audible is
  audio #1's.
- audio #1 is healthy: 4 underruns total (16 ms zero-padded, first 2000
  callbacks only), then 0; ~3.9 blocks queued at callback; 48 kHz PCM16 into
  an AAudio stream at 48 kHz PCM16 — **no resampler** (rate matches), no
  downmix (channels 2), no tone shaping: the host path is a memcpy
  pass-through (`pull_x` → `aa_feeder` ring → `aa_take`).
- So the host does not shape this signal. A muffled sound must already be in
  the game's own mix — the Switch port's compressed audio assets are a known
  quality aspect of this title — or in the device's output chain (Bluetooth
  A2DP is a common muffled-sound cause on Android).

Next diagnostic: `DM_AUDIO_DUMP=<file>` writes every PCM block the game
appends (16-bit, 48 kHz, stereo, ~11.5 MB/min of gameplay) — RMS per second,
clipping share, spectral flatness and FFT peaks ([Checking audio without
listening](../../../docs/reference/audio.md#checking-audio-without-listening))
tell whether the game's PCM is already low-bandwidth (assets) or full-band
(then the muffling sits downstream of the host: device/output chain).
