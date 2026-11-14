# Regenerates the binary image assets (icon + doodle wallpapers), which are not
# pushed through the GitHub API here. Run from the guftagu-app folder.
from PIL import Image, ImageDraw
import random, math

OUT = "app/src/main/res/drawable/"

def doodle(path, base_rgb, ink, seed):
    S = 240
    img = Image.new("RGB", (S, S), base_rgb)
    d = ImageDraw.Draw(img, "RGBA")
    rnd = random.Random(seed)
    for _ in range(30):
        x, y = rnd.uniform(0, S), rnd.uniform(0, S)
        a = rnd.randint(7, 16) if ink == (255, 255, 255) else rnd.randint(8, 16)
        col = ink + (a,)
        kind = rnd.choice(["dot", "circle", "plus", "arc", "tri", "wave"])
        r = rnd.uniform(4, 11)
        if kind == "dot":
            d.ellipse([x - r/2, y - r/2, x + r/2, y + r/2], fill=col)
        elif kind == "circle":
            d.ellipse([x - r, y - r, x + r, y + r], outline=col, width=2)
        elif kind == "plus":
            d.line([x - r, y, x + r, y], fill=col, width=2)
            d.line([x, y - r, x, y + r], fill=col, width=2)
        elif kind == "arc":
            d.arc([x - r, y - r, x + r, y + r], start=rnd.randint(0, 180), end=rnd.randint(200, 340), fill=col, width=2)
        elif kind == "tri":
            d.polygon([(x, y - r), (x - r, y + r), (x + r, y + r)], outline=col, width=2)
        else:
            pts = [(x + i * 4, y + math.sin(i * 1.2) * 3) for i in range(6)]
            d.line(pts, fill=col, width=2)
    img.save(path)

doodle(OUT + "wp_doodle_0.png", (236, 229, 221), (90, 78, 55), 44)   # classic light
doodle(OUT + "wp_doodle_1.png", (11, 20, 26), (255, 255, 255), 11)  # dark
doodle(OUT + "wp_doodle_2.png", (10, 25, 49), (255, 255, 255), 22)  # blue
doodle(OUT + "wp_doodle_3.png", (6, 40, 29), (255, 255, 255), 33)   # green

S = 512
grad = Image.new("RGBA", (S, S), (0, 0, 0, 0))
gd = ImageDraw.Draw(grad)
for yy in range(S):
    t = yy / S
    g = int(0xC2 - (0xC2 - 0x70) * t)
    b = int(0x98 - (0x98 - 0x5C) * t)
    gd.line([(0, yy), (S, yy)], fill=(0, g, b, 255))
mask = Image.new("L", (S, S), 0)
md = ImageDraw.Draw(mask)
md.rounded_rectangle([8, 8, S - 8, S - 8], radius=118, fill=255)
icon = Image.new("RGBA", (S, S), (0, 0, 0, 0))
icon.paste(grad, (0, 0), mask)
d = ImageDraw.Draw(icon)
d.rounded_rectangle([40, 26, S - 40, 210], radius=90, fill=(255, 255, 255, 26))
d.rounded_rectangle([96, 118, 416, 330], radius=58, fill=(255, 255, 255, 255))
d.polygon([(140, 322), (140, 410), (230, 322)], fill=(255, 255, 255, 255))
for cx in (176, 256, 336):
    d.ellipse([cx - 26, 198, cx + 26, 250], fill=(0, 130, 99, 255))
icon.save(OUT + "icon.png")
print("assets regenerated")
