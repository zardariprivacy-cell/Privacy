# Regenerates res/drawable/icon.png (binary files are not pushed through the GitHub API here)
from PIL import Image, ImageDraw
S = 512
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.rounded_rectangle([16, 16, S-16, S-16], radius=110, fill=(0, 168, 132, 255))
d.rounded_rectangle([96, 120, 416, 330], radius=56, fill=(255, 255, 255, 255))
d.polygon([(140, 322), (140, 408), (228, 322)], fill=(255, 255, 255, 255))
for cx in (176, 256, 336):
    d.ellipse([cx-26, 199, cx+26, 251], fill=(0, 140, 110, 255))
img.save("app/src/main/res/drawable/icon.png")
print("icon.png regenerated")
