#!/usr/bin/env python3
"""Generates the app icon set and the splash emblem for MUET MCQ Practice.

The PNG assets are generated (not stored in git as binaries here).
Run once before building in Android Studio:

    pip install Pillow
    python3 tools/generate_icons.py

Outputs:
    app/src/main/res/mipmap-*/ic_launcher.png / ic_launcher_round.png
    app/src/main/res/drawable-nodpi/emblem.png
    app-logo-512.png  (Play Store icon)
"""
import math
import os
from PIL import Image, ImageDraw

GOLD = (245, 184, 29, 255)
GOLD_D = (217, 166, 46, 255)
CREAM = (255, 246, 222, 255)
NAVY_T = (16, 36, 78)
NAVY_B = (7, 17, 43)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "app", "src", "main", "res")


def leaf(u, angle_deg):
    lw, lh = int(78 * u), int(26 * u)
    img = Image.new("RGBA", (lw * 2, lh * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([lw // 2, lh // 2, lw // 2 + lw, lh // 2 + lh], fill=GOLD_D)
    return img.rotate(angle_deg, expand=True, resample=Image.BICUBIC)


def draw_emblem(S, with_bg=False):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    cx, cy = S // 2, int(S * 0.52)
    u = S / 1000.0
    if with_bg:
        bg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        bd = ImageDraw.Draw(bg)
        for y in range(S):
            t = y / max(S - 1, 1)
            c = tuple(int(NAVY_T[i] + (NAVY_B[i] - NAVY_T[i]) * t) for i in range(3)) + (255,)
            bd.line([(0, y), (S, y)], fill=c)
        mask = Image.new("L", (S, S), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, S, S], radius=int(S * 0.2), fill=255)
        img.paste(bg, (0, 0), mask)
        d = ImageDraw.Draw(img)
        w = max(int(S * 0.022), 3)
        d.rounded_rectangle([w * 1.6, w * 1.6, S - w * 1.6, S - w * 1.6],
                            radius=int(S * 0.16), outline=GOLD, width=w)
    # laurel wreath under the book
    Rx, Ry = 335 * u, 315 * u
    ecy = cy + int(25 * u)
    for side in (-1, 1):
        for i in range(7):
            a = math.radians(i * 12.5)
            x = cx - side * Rx * math.sin(a)
            y = ecy + Ry * math.cos(a)
            lf = leaf(u, -side * (20 + i * 11))
            img.paste(lf, (int(x - lf.width / 2), int(y - lf.height / 2)), lf)
    d = ImageDraw.Draw(img)
    # open book
    spine_x = cx
    book_top = cy + int(60 * u)
    page_l = [(spine_x, book_top), (spine_x - int(215 * u), book_top - int(35 * u)),
              (spine_x - int(215 * u), book_top + int(115 * u)), (spine_x, book_top + int(150 * u))]
    page_r = [(spine_x, book_top), (spine_x + int(215 * u), book_top - int(35 * u)),
              (spine_x + int(215 * u), book_top + int(115 * u)), (spine_x, book_top + int(150 * u))]
    d.polygon(page_l, fill=CREAM, outline=GOLD, width=max(int(6 * u), 2))
    d.polygon(page_r, fill=CREAM, outline=GOLD, width=max(int(6 * u), 2))
    d.line([(spine_x, book_top), (spine_x, book_top + int(150 * u))], fill=GOLD_D, width=max(int(6 * u), 2))
    for k in range(3):
        yy = book_top + int((35 + k * 32) * u)
        d.line([(spine_x - int(180 * u), yy), (spine_x - int(35 * u), yy + int(22 * u))],
               fill=(190, 160, 90, 255), width=max(int(5 * u), 1))
        d.line([(spine_x + int(35 * u), yy + int(22 * u)), (spine_x + int(180 * u), yy)],
               fill=(190, 160, 90, 255), width=max(int(5 * u), 1))
    # graduation cap
    cap_cy = cy - int(120 * u)
    board = [(cx, cap_cy - int(62 * u)), (cx + int(215 * u), cap_cy),
             (cx, cap_cy + int(62 * u)), (cx - int(215 * u), cap_cy)]
    d.polygon(board, fill=(10, 20, 44, 255), outline=GOLD, width=max(int(6 * u), 2))
    d.polygon([(cx - int(85 * u), cap_cy + int(28 * u)), (cx + int(85 * u), cap_cy + int(28 * u)),
               (cx + int(85 * u), cap_cy + int(105 * u)), (cx - int(85 * u), cap_cy + int(105 * u))],
              fill=(10, 20, 44, 255), outline=GOLD, width=max(int(5 * u), 2))
    tx = cx + int(215 * u)
    d.line([(tx, cap_cy), (tx, cap_cy + int(120 * u))], fill=GOLD, width=max(int(9 * u), 2))
    r = int(20 * u)
    d.ellipse([tx - r, cap_cy + int(120 * u) - r, tx + r, cap_cy + int(120 * u) + r], fill=GOLD)
    return img


def main():
    for folder, s in [("mipmap-mdpi", 48), ("mipmap-hdpi", 72), ("mipmap-xhdpi", 96),
                      ("mipmap-xxhdpi", 144), ("mipmap-xxxhdpi", 192)]:
        big = draw_emblem(880, with_bg=True)
        out_dir = os.path.join(RES, folder)
        os.makedirs(out_dir, exist_ok=True)
        big.resize((s, s), Image.LANCZOS).save(os.path.join(out_dir, "ic_launcher.png"))
        ic = big.resize((s, s), Image.LANCZOS)
        mask = Image.new("L", (s, s), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, s, s], fill=255)
        out = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        out.paste(ic, (0, 0), mask)
        out.save(os.path.join(out_dir, "ic_launcher_round.png"))
    nodpi = os.path.join(RES, "drawable-nodpi")
    os.makedirs(nodpi, exist_ok=True)
    draw_emblem(880, with_bg=False).save(os.path.join(nodpi, "emblem.png"))
    draw_emblem(880, with_bg=True).save(os.path.join(ROOT, "app-logo-512.png"))
    print("Icons + emblem generated.")


if __name__ == "__main__":
    main()
