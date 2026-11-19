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
