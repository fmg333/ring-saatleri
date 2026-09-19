#!/usr/bin/env python3
"""Uygulama ikonlarını üretir: web/PWA, Android (legacy + adaptive + temalı) ve widget önizlemesi.

Tasarım: üç ringi temsil eden üç renkli halka; ortada kızıl haleli kukuletalı karanlık figür,
altında otobüs silueti. Figür jenerik bir fantezi siluetidir, mevcut bir karakter değildir.

Kullanım (repo kökünde):  python icons.py        (gereken: pip install pillow)
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

NAVY1, NAVY2 = (13, 29, 54), (30, 62, 110)               # zemin degradesi
RING = [(80, 142, 255), (46, 196, 120), (255, 146, 66)]  # R1, R2, R3
WHITE = (255, 255, 255, 255)
DARK = (8, 16, 32, 255)                                  # figür silueti
EYE = (255, 196, 90, 255)                                # gözler
GLOW = (224, 74, 40, 255)                                # figürün arkasındaki kızıl hale
HOLE = (0, 0, 0, 0)                                      # boşluklar: saydam, zemin görünsün
SS = 4                                                    # süper örnekleme

PAD, WIDTH, GAP = 0.12, 0.088, 24     # halkanın kenar boşluğu / kalınlığı / parça arası açı
FIGURE = True                         # False → sade halka + ortada otobüs
FIG_POS, FIG_W = 0.365, 0.20          # figürün dikey konumu ve genişliği
GLOW_R = 0.16                         # hale yarıçapı
BUS_POS, BUS_W = 0.645, 0.34          # otobüsün dikey konumu ve genişliği
FG_SCALE = 0.82                       # adaptive ön katman: dairesel maskeye sığacak ölçek


def _p(s, v, scale):
    """Orana göre konumu merkez etrafında ölçekler."""
    return s / 2 + (s * v - s / 2) * scale


def gradient(s):
    im = Image.new("RGB", (1, s))
    d = ImageDraw.Draw(im)
    for y in range(s):
        t = y / max(1, s - 1)
        d.point((0, y), tuple(int(NAVY1[i] + (NAVY2[i] - NAVY1[i]) * t) for i in range(3)))
    return im.resize((s, s), Image.BILINEAR).convert("RGBA")


def _glow(im, cx, cy, r):
    lay = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(lay).ellipse([cx - r, cy - r, cx + r, cy + r], fill=GLOW)
    return Image.alpha_composite(im, lay.filter(ImageFilter.GaussianBlur(r * 0.20)))


def _ring(d, s, scale, colors):
    pad = s / 2 - (s / 2 - s * PAD) * scale
    w = max(1, int(s * WIDTH * scale))
    box = [pad, pad, s - pad, s - pad]
    rc = (s - 2 * pad) / 2 - w / 2                # yay orta çizgisinin yarıçapı
    for k, c in enumerate(colors):
        a0, a1 = -90 + k * 120 + GAP / 2, -90 + (k + 1) * 120 - GAP / 2
        d.arc(box, start=a0, end=a1, fill=c, width=w)
        for a in (a0, a1):                        # yuvarlak uçlar
            x = s / 2 + rc * math.cos(math.radians(a))
            y = s / 2 + rc * math.sin(math.radians(a))
            d.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=c)


def _hood(d, cx, cy, w, col, eye):
    """Kukuletalı figür: sivri kukuleta, omuzdan aşağı açılan pelerin, yarık gözler."""
    h = w * 1.45
    y0, y1 = cy - h / 2, cy + h / 2
    d.polygon([(cx - w * 0.30, y0 + h * 0.42), (cx + w * 0.30, y0 + h * 0.42),
               (cx + w * 0.80, y1), (cx - w * 0.80, y1)], fill=col)                    # pelerin
    d.ellipse([cx - w * 0.80, y1 - h * 0.13, cx + w * 0.80, y1 + h * 0.06], fill=col)  # etek
    d.ellipse([cx - w * 0.34, y0 + h * 0.14, cx + w * 0.34, y0 + h * 0.56], fill=col)  # kukuleta
    d.polygon([(cx, y0), (cx - w * 0.33, y0 + h * 0.34), (cx + w * 0.33, y0 + h * 0.34)], fill=col)
    d.polygon([(cx - w * 0.30, y0 + h * 0.30), (cx + w * 0.30, y0 + h * 0.30),
               (cx + w * 0.34, y0 + h * 0.50), (cx - w * 0.34, y0 + h * 0.50)], fill=col)
    ey = y0 + h * 0.38
    for g in (-1, 1):
        d.polygon([(cx + g * w * 0.06, ey + w * 0.03), (cx + g * w * 0.21, ey - w * 0.05),
                   (cx + g * w * 0.21, ey + w * 0.02), (cx + g * w * 0.06, ey + w * 0.09)], fill=eye)


def _bus(d, s, cy, wd, body):
    """Yandan otobüs silueti: gövde dolu, pencere ve tekerlek içleri saydam."""
    cx = s / 2
    h = wd * 0.62
    x0, y0, x1, y1 = cx - wd / 2, cy - h / 2, cx + wd / 2, cy + h / 2
    d.rounded_rectangle([x0, y0, x1, y1 - h * 0.10], radius=wd * 0.16, fill=body)

    pw, ph = wd * 0.78, h * 0.30                  # pencere şeridi
    wy = y0 + h * 0.14
    d.rounded_rectangle([cx - pw / 2, wy, cx + pw / 2, wy + ph], radius=ph * 0.35, fill=HOLE)
    d.line([(cx - pw * 0.08, wy), (cx - pw * 0.08, wy + ph)], fill=body, width=max(1, int(wd * 0.05)))

    rw = wd * 0.115                               # tekerlekler
    for fx in (cx - wd * 0.27, cx + wd * 0.27):
        fy = y1 - h * 0.12
        d.ellipse([fx - rw, fy - rw, fx + rw, fy + rw], fill=body)
        d.ellipse([fx - rw * 0.45, fy - rw * 0.45, fx + rw * 0.45, fy + rw * 0.45], fill=HOLE)


def glyph(s, scale=1.0, mono=False):
    """Saydam zemin üzerine hale + halka + figür + otobüs. Boşluklar saydam bırakılır."""
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    if FIGURE and not mono:
        im = _glow(im, s / 2, _p(s, FIG_POS + 0.02, scale), s * GLOW_R * scale)

    d = ImageDraw.Draw(im)
    _ring(d, s, scale, [WHITE] * 3 if mono else [c + (255,) for c in RING])
    if FIGURE:
        # temalı ikon tek renk: figür beyaz, gözler boşluk olarak kalır
        _hood(d, s / 2, _p(s, FIG_POS, scale), s * FIG_W * scale,
              WHITE if mono else DARK, HOLE if mono else EYE)
    _bus(d, s, _p(s, BUS_POS if FIGURE else 0.5, scale), s * BUS_W * scale, WHITE)
    return im


def squircle(size, radius_ratio=0.225):
    """Köşeleri yuvarlatılmış, degrade zeminli ikon (web + eski Android launcher)."""
    s = size * SS
    im = Image.alpha_composite(gradient(s), glyph(s))
    if radius_ratio:
        mask = Image.new("L", (s, s), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * radius_ratio), fill=255)
        im.putalpha(mask)
    return im.resize((size, size), Image.LANCZOS)


def adaptive_fg(size, mono=False):
    """Adaptive icon ön katmanı: 108dp çerçevede, daireye kırpılınca da tam görünür."""
    s = size * SS
    return glyph(s, scale=FG_SCALE, mono=mono).resize((size, size), Image.LANCZOS)


def adaptive_bg(size):
    return gradient(size * SS).resize((size, size), Image.LANCZOS)


def save(im, path):
    if os.path.dirname(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    print(path)


RES = "android/app/src/main/res"
DENSITIES = [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]

if __name__ == "__main__":
    # Web / PWA
    save(squircle(512), "icon-512.png")
    save(squircle(192), "icon-192.png")
    save(squircle(180, radius_ratio=0), "apple-touch-icon.png")   # iOS köşeyi kendi yuvarlar

    # Android: eski launcher ikonu + adaptive katmanlar (108dp) + Android 13 temalı ikon
    for name, k in DENSITIES:
        save(squircle(int(48 * k)), f"{RES}/mipmap-{name}/ic_launcher.png")
        save(adaptive_fg(int(108 * k)), f"{RES}/mipmap-{name}/ic_launcher_foreground.png")
        save(adaptive_bg(int(108 * k)), f"{RES}/mipmap-{name}/ic_launcher_background.png")
        save(adaptive_fg(int(108 * k), mono=True), f"{RES}/mipmap-{name}/ic_launcher_mono.png")

    # Widget seçicisinde görünen önizleme
    save(squircle(320), f"{RES}/drawable-nodpi/widget_preview.png")
