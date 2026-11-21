// LEMON: the sampler experiment flags' pure logic (vka_tex.c sampler_of, docs/hogwarts/experiments.md): the
// DM_VK_SAMP_LOD_BIAS / DM_VK_SAMP_ANISO / DM_VK_SAMP_MIP_FLOOR values parsed, clamped and applied to a
// sampler's parameters. Free of Vulkan and of the replay, so the host test (dm/host/tests/t_android_sampdbg.c)
// checks the invariants the device cannot: with every flag off the result is bit-identical to what sampler_of
// computed before the flags existed, and minLod <= maxLod holds on both filter paths whenever the flag is on.
#ifndef VKA_SAMPDBG_H
#define VKA_SAMPDBG_H

#include <stdlib.h>

typedef struct { float lod_bias; float aniso; float mip_floor; } samp_dbg;

// One switch's value as a number; unset or empty is off (0)
static inline float vka_sampdbg_f(const char *s) { return s && *s ? strtof(s, NULL) : 0.0f; }
static inline int vka_sampdbg_i(const char *s) { return s && *s ? (int)strtol(s, NULL, 0) : 0; }

// A NaN or an absurd floor is the flag off (14 = log2 of the largest dimension the API allows)
static inline void vka_sampdbg_clamp(samp_dbg *d) {
    if (!(d->mip_floor >= 0.0f && d->mip_floor <= 14.0f)) d->mip_floor = 0.0f;
}

// The NVN sampler parameters with the flags applied. Off (all-zero flags): bit-identical to what sampler_of
// computed before the flags existed. On: the bias adds and stays within the device's range, the anisotropy
// floor rises without passing the device's max, the mip floor raises minLod, and maxLod never goes below
// minLod (VUID-VkSamplerCreateInfo-maxLod-01973) — on the mipmap path only when the floor is active, so a
// game state with lod_max < lod_min still passes through untouched with the flag off.
static inline void vka_sampdbg_apply(const samp_dbg *dbg, float nvn_bias, float nvn_aniso, float nvn_lod_min,
                                     float nvn_lod_max, int mip, float dev_max_bias, float dev_max_aniso,
                                     float *bias, float *aniso, float *lod_min, float *lod_max) {
    float a = nvn_aniso > 1 ? (nvn_aniso < dev_max_aniso ? nvn_aniso : dev_max_aniso) : 1;
    if (dbg->aniso > 1) { float f = dbg->aniso < dev_max_aniso ? dbg->aniso : dev_max_aniso; if (f > a) a = f; }
    float b = nvn_bias + dbg->lod_bias; if (b > dev_max_bias) b = dev_max_bias; if (b < -dev_max_bias) b = -dev_max_bias;
    float lo = nvn_lod_min; if (dbg->mip_floor > 0.0f && lo < dbg->mip_floor) lo = dbg->mip_floor;
    float hi = mip ? (dbg->mip_floor > 0.0f && lo > nvn_lod_min && nvn_lod_max < lo ? lo : nvn_lod_max)
                   : (lo > nvn_lod_min ? lo : nvn_lod_min < 0.25f ? 0.25f : nvn_lod_min);
    *bias = b; *aniso = a; *lod_min = lo; *lod_max = hi;
}

#endif
