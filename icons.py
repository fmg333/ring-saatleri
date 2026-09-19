#!/usr/bin/env python3
"""logo.png'den bütün ikon dosyalarını üretir: web/PWA, Android (legacy + adaptive + temalı),
widget önizlemesi ve uygulama başlığındaki küçük amblem.

Kullanım (repo kökünde):  python icons.py        (gereken: pip install pillow)
Logoyu değiştirirsen aynı yere logo.png olarak koy ve script'i tekrar çalıştır;
kadraj bozulursa aşağıdaki BADGE kutusunu güncelle.

Eski, koddan çizilen ikon tasarımı icons_drawn.py dosyasında duruyor.
"""
import base64
import io
import os

import numpy as np
from PIL import Image, ImageDraw

SRC = "logo.png"
BADGE = (95, 94, 929, 927)      # logodaki dairesel rozetin kutusu
BG1, BG2 = (20, 22, 33), (9, 11, 18)   # rozetin dışında kalan zemin
FILL = 0.96                     # kare ikonlarda rozetin kapladığı oran
MONO_THRESHOLD = 120            # Android 13 temalı ikonu için parlaklık eşiği
SS = 2                          # süper örnekleme


def badge(px):
    return Image.open(SRC).convert("RGB").crop(BADGE).resize((px, px), Image.LANCZOS)


def backdrop(s):
    im = Image.new("RGB", (1, s))
    d = ImageDraw.Draw(im)
    for y in range(s):
        t = y / max(1, s - 1)
        d.point((0, y), tuple(int(BG1[i] + (BG2[i] - BG1[i]) * t) for i in range(3)))
    return im.resize((s, s), Image.BILINEAR).convert("RGBA")


def circle_mask(px, feather=0.004):
    m = Image.new("L", (px, px), 0)
    k = max(1, int(px * feather))
    ImageDraw.Draw(m).ellipse([k, k, px - 1 - k, px - 1 - k], fill=255)
    return m


def square_icon(size, radius_ratio=0.225, fill=FILL):
    """Kare ikon: zemin + ortada rozet. radius_ratio=0 ise köşeler yuvarlatılmaz (iOS kendi yapar)."""
    s = size * SS
    im = backdrop(s)
    px = int(s * fill)
    b = badge(px).convert("RGBA")
    b.putalpha(circle_mask(px))
    im.alpha_composite(b, ((s - px) // 2, (s - px) // 2))
    if radius_ratio:
        mask = Image.new("L", (s, s), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * radius_ratio), fill=255)
        im.putalpha(mask)
    return im.resize((size, size), Image.LANCZOS)


def adaptive_fg(size):
    """Ön katman: rozet, 108dp çerçevenin 72dp'lik görünür alanını tam dolduracak şekilde."""
    s = size * SS
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    px = int(s * 72 / 108)
    b = badge(px).convert("RGBA")
    b.putalpha(circle_mask(px))
    im.alpha_composite(b, ((s - px) // 2, (s - px) // 2))
    return im.resize((size, size), Image.LANCZOS)


def adaptive_mono(size):
    """Temalı ikon: rozetin parlak kısımlarından çıkarılan tek renkli çizim."""
    s = size * SS
    px = int(s * 72 / 108)
    lum = np.asarray(badge(px).convert("L")).astype(float)
    alpha = Image.fromarray(((lum > MONO_THRESHOLD) * 255).astype(np.uint8), "L")
    alpha = Image.fromarray(np.minimum(np.asarray(alpha), np.asarray(circle_mask(px))), "L")
    layer = Image.new("RGBA", (px, px), (255, 255, 255, 255))
    layer.putalpha(alpha)
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    im.alpha_composite(layer, ((s - px) // 2, (s - px) // 2))
    return im.resize((size, size), Image.LANCZOS)


def adaptive_bg(size):
    return backdrop(size * SS).resize((size, size), Image.LANCZOS)


def save(im, path):
    if os.path.dirname(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path, optimize=True)
    print(path)


def embed_header_logo(size=96, path="index.html"):
    """index.html başlığındaki amblemi (data URI) güncel logoyla değiştirir."""
    b = badge(size * 2).convert("RGBA")
    b.putalpha(circle_mask(size * 2))
    b = b.resize((size, size), Image.LANCZOS)
    buf = io.BytesIO()
    b.save(buf, "PNG", optimize=True)
    uri = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()

    html = open(path, encoding="utf-8").read()
    start, end = "<!--LOGO_START-->", "<!--LOGO_END-->"
    if start not in html:
        print(f"{path}: logo yeri bulunamadı, atlandı")
        return
    a, z = html.index(start) + len(start), html.index(end)
    html = html[:a] + '<img class="logo" alt="" src="' + uri + '">' + html[z:]
    open(path, "w", encoding="utf-8").write(html)
    print(f"{path} (başlık amblemi, {len(uri) // 1024} KB)")


RES = "android/app/src/main/res"
DENSITIES = [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]

if __name__ == "__main__":
    # Web / PWA
    save(square_icon(512), "icon-512.png")
    save(square_icon(192), "icon-192.png")
    save(square_icon(180, radius_ratio=0), "apple-touch-icon.png")   # iOS köşeyi kendi yuvarlar

    # Android: eski launcher ikonu + adaptive katmanlar (108dp) + temalı ikon
    for name, k in DENSITIES:
        save(square_icon(int(48 * k)), f"{RES}/mipmap-{name}/ic_launcher.png")
        save(adaptive_fg(int(108 * k)), f"{RES}/mipmap-{name}/ic_launcher_foreground.png")
        save(adaptive_bg(int(108 * k)), f"{RES}/mipmap-{name}/ic_launcher_background.png")
        save(adaptive_mono(int(108 * k)), f"{RES}/mipmap-{name}/ic_launcher_mono.png")

    # Widget seçicisindeki önizleme + uygulama başlığındaki amblem
    save(square_icon(320), f"{RES}/drawable-nodpi/widget_preview.png")
    embed_header_logo()
