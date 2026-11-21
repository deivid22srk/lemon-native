// LEMON: the sampler experiment flags' pure logic (dm/gfx/vk_android/vka_sampdbg.h): the flags parse, clamp
// and apply, with the flag off bit-identical to the old sampler_of and minLod <= maxLod on both filter paths
// when it is on (the Critic's round-1 VUID-VkSamplerCreateInfo-maxLod-01973 finding)
#include <stdio.h>
#include "../../gfx/vk_android/vka_sampdbg.h"

static int fails;
#define CHECK(c, ...) do { if (!(c)) { fails++; printf("FAIL %s:%d: ", __FILE__, __LINE__); printf(__VA_ARGS__); printf("\n"); } } while (0)

static samp_dbg d;

int main(void) {
    float b, a, lo, hi;
    // Parsing: unset or empty is off, junk is off; the floor clamps NaN and absurd values away
    CHECK(vka_sampdbg_f(NULL) == 0.0f && vka_sampdbg_f("") == 0.0f, "unset/empty float is off");
    CHECK(vka_sampdbg_f("-0.5") == -0.5f, "a negative bias parses");
    CHECK(vka_sampdbg_i(NULL) == 0 && vka_sampdbg_i("") == 0, "unset/empty int is off");
    d = (samp_dbg){0, 0, strtof("nan", NULL)}; vka_sampdbg_clamp(&d);
    CHECK(d.mip_floor == 0.0f, "a NaN floor is the flag off");
    d = (samp_dbg){0, 0, 1e30f}; vka_sampdbg_clamp(&d);
    CHECK(d.mip_floor == 0.0f, "an absurd floor is the flag off");
    d = (samp_dbg){0, 0, 14.0f}; vka_sampdbg_clamp(&d);
    CHECK(d.mip_floor == 14.0f, "the floor's top of range stays");

    // Flags off: bit-identical to the old sampler_of on both paths, including a game state with lod_max < lod_min
    d = (samp_dbg){0, 0, 0};
    vka_sampdbg_apply(&d, 0.25f, 1, 0.0f, 16.0f, 1, 4.0f, 16.0f, &b, &a, &lo, &hi);
    CHECK(b == 0.25f && a == 1 && lo == 0.0f && hi == 16.0f, "off, mip path unchanged");
    vka_sampdbg_apply(&d, 0.25f, 1, 0.0f, 16.0f, 0, 4.0f, 16.0f, &b, &a, &lo, &hi);
    CHECK(b == 0.25f && a == 1 && lo == 0.0f && hi == 0.25f, "off, non-mip path takes the 0.25 floor of maxLod");
    vka_sampdbg_apply(&d, 0.0f, 1, 3.0f, 1.0f, 1, 4.0f, 16.0f, &b, &a, &lo, &hi);
    CHECK(lo == 3.0f && hi == 1.0f, "off, a game state with lod_max < lod_min passes through untouched");
    vka_sampdbg_apply(&d, 8.0f, 2, 1.0f, 10.0f, 0, 4.0f, 16.0f, &b, &a, &lo, &hi);
    CHECK(b == 4.0f && a == 2 && lo == 1.0f && hi == 1.0f, "off, bias clamps to the device max, maxLod keeps the NVN non-mip value");

    // Flags on: the bias adds within the device range, the anisotropy floor rises without passing the device max
    d = (samp_dbg){-1.0f, 0, 0};
    vka_sampdbg_apply(&d, 0.25f, 1, 0.0f, 16.0f, 0, 4.0f, 16.0f, &b, &a, &lo, &hi);
    CHECK(b == -0.75f, "a negative LOD bias adds");
    d = (samp_dbg){0, 8, 0};
    vka_sampdbg_apply(&d, 0.0f, 1, 0.0f, 16.0f, 0, 4.0f, 16.0f, &b, &a, &lo, &hi);
    CHECK(a == 8.0f, "the anisotropy floor applies to a sampler with none");
    vka_sampdbg_apply(&d, 0.0f, 4, 0.0f, 16.0f, 0, 4.0f, 16.0f, &b, &a, &lo, &hi);
    CHECK(a == 8.0f, "the anisotropy floor raises a lower NVN value");
    vka_sampdbg_apply(&d, 0.0f, 16, 0.0f, 16.0f, 0, 4.0f, 16.0f, &b, &a, &lo, &hi);
    CHECK(a == 16.0f, "the floor never lowers a higher value");

    // The mip floor: minLod rises, and maxLod follows on the mipmap path so min <= max holds; the non-mip
    // path's fixed 0.25 floor of maxLod must rise too
    d = (samp_dbg){0, 0, 4.0f};
    vka_sampdbg_apply(&d, 0.0f, 1, 0.0f, 16.0f, 1, 4.0f, 16.0f, &b, &a, &lo, &hi);
    CHECK(lo == 4.0f && hi == 16.0f, "mip floor under lod_max: only minLod rises");
    d = (samp_dbg){0, 0, 6.0f};
    vka_sampdbg_apply(&d, 0.0f, 1, 0.0f, 4.0f, 1, 4.0f, 16.0f, &b, &a, &lo, &hi);
    CHECK(lo == 6.0f && hi == 6.0f, "mip floor above lod_max: maxLod follows so min <= max");
    vka_sampdbg_apply(&d, 0.0f, 1, 0.0f, 1.0f, 0, 4.0f, 16.0f, &b, &a, &lo, &hi);
    CHECK(lo == 6.0f && hi == 6.0f, "the non-mip path's maxLod rises past its 0.25 floor too");
    d = (samp_dbg){0, 0, 2.0f};
    vka_sampdbg_apply(&d, 0.0f, 1, 3.0f, 1.0f, 1, 4.0f, 16.0f, &b, &a, &lo, &hi);
    CHECK(lo == 3.0f && hi == 1.0f, "a floor under the game's own minLod changes nothing");

    if (fails) { printf("%d checks failed\n", fails); return 1; }
    printf("t_android_sampdbg: OK\n");
    return 0;
}
