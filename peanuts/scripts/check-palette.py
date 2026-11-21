#!/usr/bin/env python3
"""Lemon palette contrast check (WCAG 2.1 relative luminance), run by CI (peanuts.yml).

Reads android/app/src/main/res/values/colors.xml (light) and values-night/colors.xml (dark
reference), strips the alpha byte for the ratio (translucent surfaces are drawn over opaque
composites the annotations describe), and checks every annotated pair: text needs 4.5:1 (AA
normal), UI shapes and icons 3:1. Fails loudly listing every pair that misses its minimum, so
no hex is hand-edited without the numbers being re-proven.
"""
import re
import sys
from pathlib import Path

RES = Path(__file__).resolve().parents[2] / 'android/app/src/main/res'

def load(path):
    out = {}
    for m in re.finditer(r'<color name="(\w+)">#([0-9A-Fa-f]{8})</color>', path.read_text(encoding='utf-8')):
        h = m.group(2)
        out[m.group(1)] = tuple(int(h[i:i+2], 16) / 255 for i in (2, 4, 6))
    return out

def lum(c):
    def lin(v):
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = (lin(v) for v in c)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b

def ratio(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)

TEXT, SHAPE = 4.5, 3.0

# every annotated pair: (a, b, minimum, what draws it)
CORE_PAIRS = [
    ('bn_text', 'bn_bg', TEXT, 'body text on the screen'),
    ('bn_text', 'bn_card', TEXT, 'body text on cards'),
    ('bn_text', 'bn_tint', TEXT, 'body text on tonal chips'),
    ('bn_text_2', 'bn_bg', TEXT, 'secondary text on the screen'),
    ('bn_text_2', 'bn_card', TEXT, 'secondary text on cards'),
    ('bn_text_2', 'bn_tint', TEXT, 'secondary text on tonal chips'),
    ('bn_accent', 'bn_bg', SHAPE, 'accent as fill/focus ring on the screen'),
    ('bn_accent', 'bn_card', SHAPE, 'accent as fill/focus ring on cards'),
    ('bn_accent', 'bn_accent_tint', TEXT, 'accent label on the selected container'),
    ('bn_accent', 'bn_card_stroke', SHAPE, 'focus ring reads against the hairline'),
    ('bn_on_accent', 'bn_accent', TEXT, 'label on the accent fill'),
    ('bn_on_banana', 'bn_banana', TEXT, 'text on the brand accent'),
    ('bn_on_banana', 'bn_banana_deep', TEXT, 'text on the pressed brand accent'),
    ('bn_ok', 'bn_card', TEXT, 'success text on cards'),
    ('bn_ok', 'bn_ok_tint', TEXT, 'success text on its container'),
    ('bn_on_leaf', 'bn_leaf', TEXT, 'text on the leaf green'),
    ('bn_danger', 'bn_card', TEXT, 'error text on cards'),
    ('bn_danger', 'bn_danger_tint', TEXT, 'error text on the danger container'),
    ('bn_danger', 'bn_warn_tint', TEXT, 'error text on the warning container'),
    ('bn_warn', 'bn_card', TEXT, 'warning text on cards'),
    ('bn_warn', 'bn_warn_tint', TEXT, 'warning text on its container'),
    ('bn_secondary', 'bn_bg', SHAPE, 'leaf support as a shape on the screen'),
    ('bn_secondary', 'bn_card', SHAPE, 'leaf support as a shape on cards'),
    ('bn_accent_warm', 'bn_card', SHAPE, 'citric accent on cards'),
    ('bn_badge_glyph', 'bn_badge', SHAPE, 'the icon lemon on the header badge circle'),
    ('bn_text', 'bn_accent_tint', TEXT, 'hero headline on the gradient\'s lemon end'),
    ('bn_text', 'bn_hero_end', TEXT, 'hero headline on the gradient\'s leaf end'),
    # the hero's caption lines are bn_text, not bn_text_2: bn_text_2 misses 4.5:1 on the gradient's ends
    # in both modes (3.5:1 / 3.2:1), so the contract (DESIGN-ui.md section 4) forbids it there and the hero
    # draws bn_text only: a prohibition the checker cannot express, only the annotated rule can
]

# the overlay set is stable (it renders over the running game in both modes) and lives in values/ only
OVERLAY_PAIRS = [
    ('bn_overlay_text', 'bn_overlay_card', TEXT, 'overlay text on its card'),
    ('bn_overlay_text_2', 'bn_overlay_card', TEXT, 'overlay secondary text on its card'),
    ('bn_overlay_accent', 'bn_overlay_card', TEXT, 'overlay accent on its card'),
    ('bn_overlay_ok', 'bn_overlay_card', SHAPE, 'overlay success on its card'),
    ('bn_overlay_warn', 'bn_overlay_card', SHAPE, 'overlay warning on its card'),
    ('bn_overlay_danger', 'bn_overlay_card', SHAPE, 'overlay danger on its card'),
]

# the header badge's glyph: ic_lemon's body (#FFE74A), both modes
BADGE_GLYPH = (1.0, 0.9058823529411765, 0.2901960784313726)

def check(name, colors, pairs):
    bad, rows = [], []
    for a, b, minr, what in pairs:
        ca = dict(colors); ca['bn_badge_glyph'] = BADGE_GLYPH
        if a not in ca or b not in ca:
            bad.append(f'{name}: missing color {a if a not in ca else b}')
            continue
        r = ratio(ca[a], ca[b])
        rows.append((a, b, r, minr, what))
        if r < minr:
            bad.append(f'{name}: {a} on {b} = {r:.2f}:1 < {minr} ({what})')
    return bad, rows

bad_all = []
for name, rel in [('light', 'values/colors.xml'), ('dark', 'values-night/colors.xml')]:
    colors = load(RES / rel)
    if name == 'dark':
        for k in colors:
            if k.startswith('bn_overlay_'):
                bad_all.append(f'dark: values-night redefines {k} (the overlay set is stable and lives in values/ only)')
    bad, rows = check(name, colors, CORE_PAIRS if name == 'dark' else CORE_PAIRS + OVERLAY_PAIRS)
    print(f'== {name} ==')
    for a, b, r, minr, what in rows:
        mark = 'ok ' if r >= minr else 'FAIL'
        print(f'  [{mark}] {a:20s} on {b:18s} {r:5.2f}:1  (min {minr})  {what}')
    bad_all += bad

if bad_all:
    print('\nFAILED:')
    print('\n'.join(bad_all))
    sys.exit(1)
print('\nPALETTE OK')
