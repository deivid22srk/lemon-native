// PEANUTS: whether a compressed (BCn) texture is tracked from its storage even though its class keeps it a zero image (vka_tex.c storage_mode, the game
// profile's tex_stream_track key), as pure logic, free of Android and Vulkan so a unit test on Linux can check it (dm/host/tests/t_android_texstream.c).
// Hogwarts Legacy (0100F7E00C70E000) runs on a Switch 1 SDK (16.2.3; the loader's "sdk: built for the Switch 2 (spec: Ounce)" line is absent from its run's
// log, so vka_nvn2() is 0), which means the NVN2 first-sampled rule (nvn2_sampled_content) never runs for it and the round-7 extension of that rule to
// compressed formats is dead code here. Its UE4 streamer still streams the same way: a compressed texture is registered over a reused pool slot, first
// sampled while the slot's storage is empty (the creator's black model) or still holds another texture's bytes (the flat green coat), and the streamer's
// CPU writes land later. The textures carry NVN_TEXTURE_FLAGS_COMPRESSIBLE — the flag the compressed branch and ST_FILL exclude, and kartnative's rule
// excluded with them, because on the Tegra the driver's own compression makes the storage's bytes non-authoritative. That does not hold here: this backend
// never applies a compression of its own, so the storage's bytes are what the GPU samples, and the flag changes nothing about what a view reads. The key
// makes such a texture tracked like its unflagged twin (storage_mode's compressed branch): the GPU cannot render into a compressed format (no attachment
// class to steal), a copy into its storage is the gw mechanism's (the GPU's content wins), and the write protection re-uploads the CPU's writes whatever
// the game's flushing. The test's words: the key, compressed, no shader-image/sparse flag, a pool, the CPU can write it, no image the GPU drew into over
// the storage, and a first use that is a sample or storage that holds something.
#ifndef VKA_TEXSTREAM_H
#define VKA_TEXSTREAM_H

#ifndef DM_GAME_TEX_STREAM_TRACK
#define DM_GAME_TEX_STREAM_TRACK 0   // a gen header the profile schema predates: off
#endif

typedef struct { int key, comp, flags_image_sparse, pool, pf_no_cpu, gpu_over, sampled, any_mapped; } vka_texstream_draw;

// 1 = the texture is tracked (ST_TRACK): uploaded from its storage, re-uploaded when the CPU writes its pages, whatever the COMPRESSIBLE flag
static inline int vka_tex_stream_track(const vka_texstream_draw *d) {
    if (!d->key || !d->comp || d->flags_image_sparse || !d->pool || d->pf_no_cpu || d->gpu_over) return 0;
    return d->sampled || d->any_mapped;
}
#endif
