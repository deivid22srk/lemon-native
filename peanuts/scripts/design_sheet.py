#!/usr/bin/env python3
"""Lemon design-spec sheet: renders the design system's tokens (palette, type, radii, the hero card's
anatomy) from the repository's OWN resources — the bn_* colors parse out of values[-night]/colors.xml and
every glyph is drawn with the shipped Manrope/JetBrains Mono TTFs. It is a spec render, not app
screenshots: DESIGN-ui.md section 4 and docs/design-system.md cite it as this SDK-less environment's
visual evidence, and docs/ui-scorecard.md records that decision. Run: python3 peanuts/scripts/design_sheet.py
(needs Pillow). The hero's title draws the title tier (24, weight 800) as Tokens and HeroCard define it."""
import re
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

REPO = Path(__file__).resolve().parents[2]
RES = REPO / 'android/app/src/main/res'
FONT = RES / 'font'
OUT = REPO / 'docs/images/lemon-design-sheet.png'

def palette(path):
    return {m.group(1): '#' + m.group(2)[2:8] for m in re.finditer(r'<color name="(\w+)">#([0-9A-Fa-f]{8})</color>', path.read_text())}

light = palette(RES / 'values/colors.xml')
dark = palette(RES / 'values-night/colors.xml')
dark.update({k: v for k, v in light.items() if k.startswith('bn_overlay_')})   # the overlay set is stable

def font(size, mono=False):
    f = FONT / ('jbmono_medium.ttf' if mono else 'manrope_extrabold.ttf' if size >= 24 else 'manrope_semibold.ttf' if size >= 15 else 'manrope_regular.ttf')
    return ImageFont.truetype(str(f), size)

W = 1560
SWATCHES = [('bn_bg', 'background'), ('bn_card', 'surface-1 / cards'), ('bn_tint', 'surface-2 / chips'),
            ('bn_card_stroke', 'outline / hairlines'), ('bn_accent_tint', 'primary container'), ('bn_hero_end', 'hero leaf end'),
            ('bn_accent', 'primary lemon'), ('bn_on_accent', 'on primary'), ('bn_secondary', 'leaf support'),
            ('bn_accent_warm', 'citric warm'), ('bn_ok', 'success'), ('bn_warn', 'caution'),
            ('bn_danger', 'danger'), ('bn_text', 'text high'), ('bn_text_2', 'text muted'), ('bn_badge', 'badge circle')]

def swatch_row(d, y, pal, label, page_text=None, page_text2=None):
    d.text((24, y), label, font=font(20), fill=page_text or pal['bn_text'])
    x = 24
    for name, role in SWATCHES:
        c = pal[name]
        d.rounded_rectangle((x, y + 34, x + 88, y + 110), radius=14, fill=pal[name],
                            outline=pal['bn_card_stroke'], width=1)
        d.text((x, y + 118), name.replace('bn_', ''), font=font(13, name in ('bn_accent_warm',)), fill=page_text or pal['bn_text'])
        d.text((x, y + 138), c, font=font(12, mono=True), fill=page_text2 or pal['bn_text_2'])
        x += 96
    return y + 172

def type_row(d, y, pal, label):
    d.text((24, y), label, font=font(20), fill=pal['bn_text'])
    samples = [('Display 26/800', 26), ('Title 24/800', 24), ('Heading 18/700', 18), ('Body 15/400', 15), ('Small 13/400', 13), ('Caption 12/500', 12)]
    yy = y + 36
    for name, size in samples:
        d.text((24, yy), name, font=font(size), fill=pal['bn_text'])
        yy += size + 12
    d.text((360, y + 36), 'lemon-native-1.0.0  0100F7E00C70E000  98.2 %', font=font(16, mono=True), fill=pal['bn_text'])
    d.text((360, y + 66), 'JetBrains Mono Medium — numeric readouts, versions, hashes', font=font(13), fill=pal['bn_text_2'])
    d.text((360, y + 96), 'Manrope 400 → 800 · the 4/8 dp grid · 48 dp touch floor', font=font(15), fill=pal['bn_text'])
    return y + 208

def ramp(d, x0, y0, x1, y1, pal, inset=0):
    c0 = tuple(int(pal['bn_accent_tint'][1:][j:j+2], 16) for j in (0, 2, 4))
    c1 = tuple(int(pal['bn_hero_end'][1:][j:j+2], 16) for j in (0, 2, 4))
    for i in range(inset, x1 - x0 - inset):
        t = i / max(1, (x1 - x0 - inset - 1))
        cc = tuple(int(a + (b - a) * t) for a, b in zip(c0, c1))
        d.line((x0 + i, y0 + inset, x0 + i, y1 - 1 - inset), fill=cc)

def hero_content(d, x0, y0, pal):
    ix = x0 + 16
    d.rounded_rectangle((ix, y0 + 16, ix + 64, y0 + 80), radius=14, fill=pal['bn_tint'])
    d.text((ix + 22, y0 + 30), 'Z', font=font(30), fill=pal['bn_text'])
    d.text((ix + 80, y0 + 18), "The Legend of Zelda: Link's\u2026", font=font(24), fill=pal['bn_text'])   # the app's title truncates (maxLines 2, ellipsis end)
    d.text((ix + 80, y0 + 56), 'Ready to play \u00b7 Companion on the second screen', font=font(13), fill=pal['bn_text'])
    d.rounded_rectangle((ix, y0 + 104, ix + 110, y0 + 132), radius=12, fill=pal['bn_ok_tint'])
    d.text((ix + 14, y0 + 110), 'Ready', font=font(13), fill=pal['bn_ok'])
    d.rounded_rectangle((ix, y0 + 142, ix + 92, y0 + 178), radius=14, fill=pal['bn_accent'])
    d.text((ix + 26, y0 + 150), 'Play', font=font(15), fill=pal['bn_on_accent'])
    d.rounded_rectangle((ix + 104, y0 + 142, ix + 206, y0 + 178), radius=14, fill=pal['bn_tint'])
    d.text((ix + 120, y0 + 150), 'Manage', font=font(15), fill=pal['bn_text'])

def hero_mock(d, y, pal, label):
    d.text((24, y), label, font=font(20), fill=pal['bn_text'])
    x0, y0, x1, y1 = 24, y + 34, 520, y + 34 + 190
    ramp(d, x0, y0, x1, y1, pal)
    d.rounded_rectangle((x0, y0, x1, y1), radius=20, outline=pal['bn_card_stroke'], width=1)
    hero_content(d, x0, y0, pal)
    fx0, fy0, fx1, fy1 = 560, y + 34, 1056, y + 34 + 190
    ramp(d, fx0, fy0, fx1, fy1, pal)
    d.rounded_rectangle((fx0, fy0, fx1, fy1), radius=20, outline=pal['bn_accent'], width=3)
    hero_content(d, fx0, fy0, pal)
    d.text((fx0 + 16, fy1 + 8), 'focused: the gradient stays under the accent ring, r 20 (HeroCard.focusBackground)', font=font(12), fill=pal['bn_text'])
    return y + 250

rows = [('Palette (dark, the reference — values-night/colors.xml)', 'dark_row', None),
        ('Palette (light — values/colors.xml)', 'light_row', None),
        ('Type scale (Manrope in res/font, OFL) and the mono tier', 'type_dark', None),
        ('The hero card: default and focused, full width gradient (the tokens, drawn as specified)', 'hero_dark', None)]
H = 64 + 172 * 2 + 208 + 250 + 66
img = Image.new('RGB', (W, H), dark['bn_bg'])
d = ImageDraw.Draw(img)
d.text((24, 16), 'Lemon Native — design system sheet (a spec render from res/, not app screenshots)', font=font(22), fill=dark['bn_accent'])
y = 64
for label, kind, _ in rows:
    if kind == 'dark_row': y = swatch_row(d, y, dark, label)
    elif kind == 'light_row':
        y = swatch_row(d, y, light, label, dark['bn_text'], dark['bn_text_2'])
    elif kind == 'type_dark': y = type_row(d, y, dark, label)
    elif kind == 'hero_dark': y = hero_mock(d, y, dark, label)
d.text((24, H - 34), 'check-palette.py proves every annotated pair (CI); radii: chips/inputs 12 · cards 20 · sheets/dialogs 28 (bn_dialog_bg)',
       font=font(14, mono=True), fill=dark['bn_text_2'])
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print('sheet:', OUT, img.size)
