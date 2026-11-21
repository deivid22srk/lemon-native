"""decode_ubo_tex (dmsh_build.py, shared with vk_android_build.py via BORROWED): a type-1 bindless texture handle whose two words are
a game uniform buffer word and a c2 separate-sampler-table word, in either order (Hogwarts Legacy 1.0.5, Unreal Engine 4 / SDK 17:
46,782 of 65,047 shaders bind their material textures this way; undecoded, the symptom is solid black characters/objects while text,
menus and lighting still render - see dm/games/0100F7E00C70E000/README.md and docs/reference/shaders.md). Ported from nxnative PR #890
(commit 699d815), run and verified on Mac/Metal for this game by that PR; this test exercises the ported Python logic only - it does not
build or run shaders, and the fix is NOT yet device-verified on Android/Vulkan.

The function is taken from the tool's source by name, as the delta tests take theirs; the tool itself expects a game profile at import,
so the full module is not imported here.
"""
import ast
from pathlib import Path
import re
import unittest

import dmsh_abi as abi

REPO = Path(__file__).resolve().parents[3]


def tool_function(path, name, extra_globals=None):
    src = (REPO / path).read_text(encoding='utf-8')
    node = next(n for n in ast.parse(src).body if isinstance(n, ast.FunctionDef) and n.name == name)
    ns = {'re': re, 'abi': abi}
    if extra_globals: ns.update(extra_globals)
    exec(compile(ast.Module([node], []), Path(path).name, 'exec'), ns)
    fn = ns[name]
    assert callable(fn)
    return fn


# decode_ubo_tex also reads these module-level names from dmsh_build.py; TEX_BASE/IMG_BASE/SEPTEX_BASE/SEPTEX_SLOT0 are unused by
# decode_ubo_tex itself but harmless to provide for parity with the real module namespace.
_ABI_GLOBALS = {'TEX_BASE': abi.C2_TEX, 'IMG_BASE': abi.C2_IMG, 'IMG_SLOTS': abi.C2_IMG_SLOTS,
                'SEPTEX_BASE': abi.C2_SEPTEX, 'SEPTEX_SLOT0': abi.SEPTEX_SLOT0, 'SAMP_BASE': abi.C2_SAMP}


class DecodeUboTexSepSampler(unittest.TestCase):
    decode_ubo_tex = staticmethod(tool_function('dm/gfx/tools/dmsh_build.py', 'decode_ubo_tex', _ABI_GLOBALS))

    def test_hogwarts_texture_word_in_uniform_buffer_sampler_in_c2(self):
        # fp_t_cb196612_10568002: the exact example from nxnative PR #890's dmsh_build.py comment (Hogwarts Legacy 1.0.5)
        name = 'fp_t_cb196612_10568002'
        got = self.decode_ubo_tex(name)
        self.assertIsNotNone(got, 'must decode: this is the exact example from nxnative PR #890')
        tex, samp = got
        self.assertEqual(tex, (1, 8))          # c[4] -> uniform slot (4-3)=1, word 2 -> byte offset 8
        self.assertEqual(samp, ('sep', 0))     # separate sampler slot 0

    def test_hogwarts_texture_word_in_c2_sampler_in_uniform_buffer(self):
        # fp_t_cb589826_1002015A: the OR's-order case from the same PR comment, c2 word 0x15A in the texture field, c[8] word 8 the sampler
        name = 'fp_t_cb589826_1002015A'
        got = self.decode_ubo_tex(name)
        self.assertIsNotNone(got, 'must decode: the c2-word-in-texture-field case named in nxnative PR #890')
        tex, samp = got
        self.assertEqual(tex, (5, 32))         # c[8] -> uniform slot (8-3)=5, word 8 -> byte offset 32
        self.assertEqual(samp, ('sep', 0))     # separate sampler slot 0 (same c2 word 0x15A as the other direction)

    def test_regression_type0_combined_handle_unaffected(self):
        # Type 0 (one word, low 20 bits texture ID, high 12 bits sampler ID) must still decode as before: both halves point at the
        # same uniform slot/offset (Hogwarts Legacy's own shaders also use 1,850 of this simpler form; many other games use it too)
        name = 'fp_t_cb7_2A'
        tex, samp = self.decode_ubo_tex(name)
        self.assertEqual(tex, (4, 168))
        self.assertEqual(samp, (4, 168))

    def test_regression_type3_constant_sampler_unaffected(self):
        # Type 3 (SeparateConstantSamplerHandle): the sampler part is ('id', ID), unaffected by the ty==1 branch
        name = 'fp_t_cb4_30410010'   # tb=4 word 0x10, ty=3, constant sampler id 0x104
        tex, samp = self.decode_ubo_tex(name)
        self.assertEqual(tex, (1, 64))
        self.assertEqual(samp, ('id', 260))

    def test_still_none_for_non_bindless_names(self):
        self.assertIsNone(self.decode_ubo_tex('cp_i_cb3_0_'))   # an image name, not decode_ubo_tex's shape
        self.assertIsNone(self.decode_ubo_tex('cp_s3'))         # a storage buffer name
        self.assertIsNone(self.decode_ubo_tex('not_a_match'))

    def test_type1_both_words_out_of_range_still_none(self):
        # Neither word is a valid uniform slot (3..16) nor a valid c2 separate-sampler offset: must still refuse, not guess
        name = 'fp_t_cb131074_10000001'   # tb=2 (not a valid ubo slot, and o0=1 is not a c2-aligned sampler offset either), sb=1
        self.assertIsNone(self.decode_ubo_tex(name))


if __name__ == '__main__':
    unittest.main()
