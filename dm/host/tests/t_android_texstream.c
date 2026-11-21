// PEANUTS: whether a compressed texture is tracked from its storage whatever its COMPRESSIBLE flag and whether its storage holds anything at the
// upload (dm/gfx/vk_android/vka_texstream.h, vka_tex.c storage_mode, the game profile's tex_stream_track): the key off, every door that keeps the
// texture a zero image, and the two cases the key exists for — Hogwarts Legacy (a Switch 1 game, so the NVN2 first-sampled rule never runs there)
// first samples its streamed compressed textures with empty storage (the creator's black model) or over a slot that still holds another texture's
// bytes (the flat green coat)
#include <stdio.h>
#include "../../gfx/vk_android/vka_texstream.h"

static int fails;
#define CHECK(c, ...) do { if (!(c)) { fails++; printf("FAIL %s:%d: ", __FILE__, __LINE__); printf(__VA_ARGS__); printf("\n"); } } while (0)

static vka_texstream_draw draw(int key, int comp, int flags_image_sparse, int pool, int pf_no_cpu, int gpu_over, int sampled, int any_mapped) {
    vka_texstream_draw d = {0}; d.key = key; d.comp = comp; d.flags_image_sparse = flags_image_sparse; d.pool = pool;
    d.pf_no_cpu = pf_no_cpu; d.gpu_over = gpu_over; d.sampled = sampled; d.any_mapped = any_mapped; return d;
}

int main(void) {
    vka_texstream_draw d;
    // The key off (every profile but Hogwarts Legacy's today): nothing changes, whatever the texture is
    d = draw(0, 1, 0, 1, 0, 0, 1, 0); CHECK(vka_tex_stream_track(&d) == 0, "the key off");
    d = draw(0, 1, 0, 1, 0, 0, 0, 1); CHECK(vka_tex_stream_track(&d) == 0, "the key off, storage with data");
    d = draw(0, 1, 0, 1, 0, 0, 0, 0); CHECK(vka_tex_stream_track(&d) == 0, "the key off, storage empty");
    // Hogwarts's two cases, the key on
    d = draw(1, 1, 0, 1, 0, 0, 1, 0); CHECK(vka_tex_stream_track(&d) == 1, "first sampled with its slot's zeros");
    d = draw(1, 1, 0, 1, 0, 0, 0, 1); CHECK(vka_tex_stream_track(&d) == 1, "the slot still holds another texture's bytes");
    d = draw(1, 1, 0, 1, 0, 0, 1, 1); CHECK(vka_tex_stream_track(&d) == 1, "sampled with data");
    // Not sampled and nothing in the storage: no first use to follow, so nothing to track
    d = draw(1, 1, 0, 1, 0, 0, 0, 0); CHECK(vka_tex_stream_track(&d) == 0, "not sampled, storage empty");
    // The doors: not a compressed format, a shader image or sparse flag, no pool, a pool the CPU cannot write, an image the GPU drew into over the storage
    d = draw(1, 0, 0, 1, 0, 0, 1, 0); CHECK(vka_tex_stream_track(&d) == 0, "not compressed: the NVN2 rule's or the CPU's class, not this one");
    d = draw(1, 1, 1, 1, 0, 0, 1, 0); CHECK(vka_tex_stream_track(&d) == 0, "a shader image or sparse flag");
    d = draw(1, 1, 0, 0, 0, 0, 1, 0); CHECK(vka_tex_stream_track(&d) == 0, "no pool");
    d = draw(1, 1, 0, 1, 1, 0, 1, 0); CHECK(vka_tex_stream_track(&d) == 0, "a pool the CPU cannot write");
    d = draw(1, 1, 0, 1, 0, 1, 1, 0); CHECK(vka_tex_stream_track(&d) == 0, "an image the GPU drew into over the storage");
    if (fails) { printf("%d checks failed\n", fails); return 1; }
    printf("t_android_texstream: OK\n");
    return 0;
}
