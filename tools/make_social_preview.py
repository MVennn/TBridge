#!/usr/bin/env python
"""Generate TBridge brand assets from the geometry in assets/icon.svg.

Outputs (all under assets/ by default):
  logo.png           512x512
  social-preview.png 1280x640
  icon.ico           16/32/48/64/128/256

Portable by design: no machine-specific font paths. Text uses Pillow built-in
scalable font (ImageFont.load_default(size=...)), so CI and any dev box render
the same image. Requires Pillow >= 10.1; only this script needs it.

Usage:
  python tools/make_social_preview.py [output_dir]
"""

import os
import sys
from PIL import Image, ImageDraw, ImageFont

# ---------------------------------------------------------------- palette
BG_TOP = (43, 50, 66)      # #2B3242
BG_BOT = (22, 26, 34)      # #161A22
ACC_TOP = (255, 194, 74)   # #FFC24A
ACC_BOT = (255, 122, 0)    # #FF7A00
STROKE = (237, 241, 247)   # #EDF1F7
TEXT_HI = (245, 247, 251)
TEXT_LO = (163, 172, 190)

W, H = 1280, 640
SS = 2  # supersampling for crisp text


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def vertical_gradient(size, top, bot):
    img = Image.new("RGB", size)
    d = ImageDraw.Draw(img)
    h = size[1]
    for y in range(h):
        d.line([(0, y), (size[0], y)], fill=lerp(top, bot, y / max(1, h - 1)))
    return img


def font(size):
    # Pillow >= 10.1 ships an embedded scalable Aileron font; no OS path needed.
    return ImageFont.load_default(size=size)


def draw_icon(size):
    """Render the icon.svg motif at `size` px, on the shared vertical gradient."""
    s = size / 512.0
    img = Image.new("RGBA", (size, size))
    img.paste(vertical_gradient((size, size), BG_TOP, BG_BOT), (0, 0))
    d = ImageDraw.Draw(img)

    def box(x, y, w, h, r):
        d.rounded_rectangle([x * s, y * s, (x + w) * s, (y + h) * s],
                           radius=r * s, outline=STROKE, width=max(1, int(20 * s)))

    # four node tiles
    for (x, y) in ((88, 120), (274, 120), (88, 288), (274, 288)):
        box(x, y, 150, 112, 18)

    # the bridge: horizontal accent bar + drop to the lower row
    d.line([(163 * s, 232 * s), (349 * s, 232 * s)], fill=ACC_TOP, width=max(1, int(26 * s)))
    d.line([(256 * s, 232 * s), (256 * s, 288 * s)], fill=ACC_BOT, width=max(1, int(26 * s)))
    for (cx, cy, col) in ((163, 232, ACC_TOP), (349, 232, ACC_TOP), (256, 288, ACC_BOT)):
        r = 22 * s
        d.ellipse([(cx * s - r, cy * s - r), (cx * s + r, cy * s + r)], fill=col)

    # round the outer corners like the SVG rx=112
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1],
                                          radius=112 * s, fill=255)
    img.putalpha(mask)
    return img


def radial_mask(size, ease=2.6):
    w, h = size
    m = Image.new("L", size, 0)
    px = m.load()
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    maxd = max(1.0, (cx ** 2 + cy ** 2) ** 0.5)
    for yy in range(h):
        for xx in range(w):
            t = min(1.0, ((xx - cx) ** 2 + (yy - cy) ** 2) ** 0.5 / maxd)
            px[xx, yy] = int(255 * (max(0.0, 1.0 - t) ** ease))
    return m


def glow(canvas, center, radius, color, max_alpha):
    mask = radial_mask((256, 256)).resize((radius * 2, radius * 2), Image.LANCZOS)
    mask = mask.point(lambda v: v * max_alpha // 255)
    layer = Image.new("RGBA", (radius * 2, radius * 2), color + (0,))
    layer.putalpha(mask)
    canvas.alpha_composite(layer, (int(center[0] - radius), int(center[1] - radius)))


def pill(d, x, y, text, f, h, padx, fill, outline, tcolor):
    bb = d.textbbox((0, 0), text, font=f)
    w = (bb[2] - bb[0]) + padx * 2
    d.rounded_rectangle([x, y, x + w, y + h], radius=h // 2, fill=fill, outline=outline, width=2)
    d.text((x + padx, y + h / 2), text, font=f, fill=tcolor, anchor="lm")
    return w


def social_preview():
    w, h = W * SS, H * SS
    img = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    img.paste(vertical_gradient((w, h), BG_TOP, BG_BOT), (0, 0))
    glow(img, (200 * SS, 300 * SS), 330 * SS, (255, 138, 42), 26)
    glow(img, (980 * SS, 250 * SS), 350 * SS, (255, 122, 30), 14)

    tile = 172 * SS
    logo = draw_icon(tile)
    img.alpha_composite(logo, (108 * SS, (h - tile) // 2))
    ImageDraw.Draw(img).rounded_rectangle(
        [108 * SS, (h - tile) // 2, 108 * SS + tile - 1, (h - tile) // 2 + tile - 1],
        radius=int(112 * tile / 512), outline=(255, 255, 255, 28), width=2)

    d = ImageDraw.Draw(img)
    tx = 342 * SS
    f_title = font(92 * SS)
    f_sub = font(38 * SS)
    f_pill = font(24 * SS)
    f_tag = font(26 * SS)
    title = "TBridge"
    sub = "In-process UI test bridge for LSPosed"
    tb = d.textbbox((0, 0), title, font=f_title)
    sb = d.textbbox((0, 0), sub, font=f_sub)
    pill_h = 48 * SS
    gap1, gap2 = 16 * SS, 32 * SS
    block_h = (tb[3] - tb[1]) + gap1 + (sb[3] - sb[1]) + gap2 + pill_h
    y = (h - block_h) // 2
    d.text((tx, y - tb[1]), title, font=f_title, fill=TEXT_HI, anchor="la")
    y += (tb[3] - tb[1]) + gap1
    d.text((tx, y - sb[1]), sub, font=f_sub, fill=TEXT_LO, anchor="la")
    y += (sb[3] - sb[1]) + gap2
    px = tx + 2 * SS
    for label in ("LSPosed", "Android 8.1+", "Apache-2.0"):
        pw = pill(d, px, y, label, f_pill, pill_h, 24 * SS,
                  fill=(255, 255, 255, 12), outline=(210, 216, 228, 70), tcolor=(206, 213, 225, 255))
        px += pw + 16 * SS
    d.text((w - 58 * SS, h - 52 * SS), "Ask the View, not the screenshot.",
           font=f_tag, fill=(255, 159, 69, 215), anchor="rs")
    return img.resize((W, H), Image.LANCZOS).convert("RGB")


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join("assets")
    # allow running from repo root or from tools/
    if not os.path.isdir(out):
        candidate = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), out)
        if os.path.isdir(candidate):
            out = candidate
    os.makedirs(out, exist_ok=True)

    logo = draw_icon(512).convert("RGBA")
    # flatten onto opaque gradient so PNG has no stray alpha
    flat = Image.new("RGBA", (512, 512), (0, 0, 0, 255))
    flat.paste(vertical_gradient((512, 512), BG_TOP, BG_BOT), (0, 0))
    flat.alpha_composite(logo)
    flat.convert("RGBA").save(os.path.join(out, "logo.png"), "PNG")

    social_preview().save(os.path.join(out, "social-preview.png"), "PNG")
    draw_icon(256).convert("RGBA").save(os.path.join(out, "icon.ico"), "ICO",
                                        sizes=[(16, 16), (32, 32), (48, 48),
                                               (64, 64), (128, 128), (256, 256)])
    for f in ("logo.png", "social-preview.png", "icon.ico"):
        print("saved:", os.path.join(out, f))


if __name__ == "__main__":
    main()
