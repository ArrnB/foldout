"""Synthetic test photos for Foldout's page detector, with known page corners.

Each scene is drawn at 1200x1600, given lighting, blur and sensor noise, then
shrunk to 320 px on the long side (like the app does) and written as raw RGBA
plus a JSON file with the true corners at that size.
"""
import json, math, random, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = sys.argv[1]
W, H = 1200, 1600
rng = random.Random(7)


def texture(base, amp, scale):
    small = np.random.default_rng(rng.randrange(1 << 30)).normal(0, amp, (H // scale + 1, W // scale + 1, 1))
    big = np.kron(small, np.ones((scale, scale, 1)))[:H, :W]
    return np.clip(np.array(base, float)[None, None, :] + big, 0, 255)


def wood():
    a = texture((92, 64, 44), 18, 40)
    stripes = (np.sin(np.linspace(0, 40, W))[None, :, None] * 14)
    return np.clip(a + stripes, 0, 255)


def lighting(img, strength):
    yy, xx = np.mgrid[0:H, 0:W]
    g = 1 - strength * ((xx / W) * 0.6 + (yy / H) * 0.4)
    return np.clip(img * g[..., None], 0, 255)


def draw_page(img, quad, paper=(240, 236, 226), text=True, picture=False):
    pil = Image.fromarray(img.astype(np.uint8))
    d = ImageDraw.Draw(pil)
    d.polygon([tuple(p) for p in quad], fill=paper)
    # Content: lines of "text" and optionally a dark picture, mapped into the quad.
    def lerp(u, v):
        tl, tr, br, bl = [np.array(p, float) for p in quad]
        top = tl + (tr - tl) * u
        bot = bl + (br - bl) * u
        return tuple(top + (bot - top) * v)
    if text:
        for k in range(18):
            v = 0.1 + k * 0.045
            if picture and 0.45 < v < 0.75:
                continue
            d.line([lerp(0.1, v), lerp(rng.uniform(0.6, 0.9), v)], fill=(40, 40, 45), width=7)
    if picture:
        d.polygon([lerp(0.12, 0.47), lerp(0.88, 0.47), lerp(0.88, 0.73), lerp(0.12, 0.73)], fill=(35, 50, 70))
    return np.array(pil, float)


def finish(img, noise=6, blur=1.2):
    pil = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(blur))
    a = np.array(pil, float) + np.random.default_rng(rng.randrange(1 << 30)).normal(0, noise, (H, W, 3))
    return np.clip(a, 0, 255).astype(np.uint8)


def rotated_rect(cx, cy, w, h, deg):
    t = math.radians(deg)
    c, s = math.cos(t), math.sin(t)
    pts = [(-w / 2, -h / 2), (w / 2, -h / 2), (w / 2, h / 2), (-w / 2, h / 2)]
    return [(cx + x * c - y * s, cy + x * s + y * c) for x, y in pts]


scenes = []

# 1-3: rotated pages on wood
for i, deg in enumerate([0, 8, -14]):
    q = rotated_rect(600, 820, 820, 1100, deg)
    scenes.append((f"wood_rot{deg}", finish(draw_page(lighting(wood(), 0.25), q)), q))

# 4-5: perspective (photo taken at an angle), uneven light
for name, q in [("persp_a", [(260, 300), (980, 360), (1080, 1420), (120, 1330)]),
                ("persp_b", [(330, 250), (900, 260), (1110, 1460), (90, 1480)])]:
    bg = lighting(texture((70, 72, 80), 10, 30), 0.45)
    scenes.append((name, finish(draw_page(bg, q)), q))

# 6: white page on a light table (low brightness difference, soft shadow edge)
q = rotated_rect(600, 800, 800, 1080, 5)
bg = texture((205, 203, 198), 6, 50)
pil = Image.fromarray(bg.astype(np.uint8))
shadow = [(x + 10, y + 14) for x, y in q]
ImageDraw.Draw(pil).polygon(shadow, fill=(165, 163, 158))
scenes.append(("light_table", finish(draw_page(np.array(pil, float), q, paper=(246, 245, 242)), noise=4), q))

# 7: page with a big dark picture on it
q = rotated_rect(600, 800, 860, 1150, -6)
scenes.append(("picture_page", finish(draw_page(lighting(wood(), 0.2), q, picture=True)), q))

# 8: cluttered desk: a mug, a pen and a phone next to the page
q = rotated_rect(560, 760, 700, 960, 10)
img = draw_page(lighting(texture((60, 62, 58), 12, 25), 0.3), q)
pil = Image.fromarray(img.astype(np.uint8)); d = ImageDraw.Draw(pil)
d.ellipse((900, 1250, 1150, 1500), fill=(200, 60, 50))
d.line([(80, 1450), (420, 1380)], fill=(20, 20, 160), width=18)
d.rectangle((60, 120, 260, 480), fill=(15, 15, 18))
scenes.append(("cluttered", finish(np.array(pil, float)), q))

# 9: small page far away
q = rotated_rect(620, 760, 420, 560, 18)
scenes.append(("small_far", finish(draw_page(lighting(wood(), 0.2), q)), q))

# 10-11: no page at all: plain texture, and a dark room with a lamp glow
scenes.append(("no_page_wood", finish(lighting(wood(), 0.3)), None))
yy, xx = np.mgrid[0:H, 0:W]
glow = 40 + 120 * np.exp(-(((xx - 700) / 400) ** 2 + ((yy - 500) / 500) ** 2))
scenes.append(("no_page_glow", finish(np.repeat(glow[..., None], 3, axis=2)), None))

index = []
for name, arr, quad in scenes:
    pil = Image.fromarray(arr)
    s = 320 / max(W, H)
    w, h = round(W * s), round(H * s)
    small = pil.resize((w, h), Image.BILINEAR).convert("RGBA")
    open(f"{OUT}/{name}.rgba", "wb").write(small.tobytes())
    pil.save(f"{OUT}/{name}.jpg", quality=88)
    index.append({"name": name, "w": w, "h": h,
                  "quad": None if quad is None else [[x * s, y * s] for x, y in quad]})
# ---------------------------------------------------------------- harder scenes
hard = []
yy, xx = np.mgrid[0:H, 0:W]
# Shadow (e.g. the phone) across part of the page
q = rotated_rect(600, 800, 820, 1100, 4)
img = draw_page(lighting(wood(), 0.2), q)
img = img * np.where((xx + yy * 0.5) < 700, 0.55, 1.0)[..., None]
hard.append(("shadow_band", finish(img), q))
# A thumb covering part of the bottom-left corner
q = rotated_rect(600, 800, 820, 1100, -3)
pil = Image.fromarray(draw_page(lighting(wood(), 0.2), q).astype(np.uint8))
ImageDraw.Draw(pil).ellipse((120, 1240, 330, 1520), fill=(214, 160, 130))
hard.append(("thumb", finish(np.array(pil, float)), q))
# Dark page (kraft/blue) on a light desk
q = rotated_rect(600, 800, 800, 1080, 7)
hard.append(("dark_on_light", finish(draw_page(texture((225, 222, 215), 6, 40), q, paper=(70, 90, 130), text=False)), q))
# Low light: dim and very noisy
q = rotated_rect(600, 800, 820, 1100, -9)
hard.append(("low_light", finish(draw_page(lighting(wood(), 0.3), q) * 0.45, noise=16), q))
# Two overlapping pages: the front one is the target
q2 = rotated_rect(520, 700, 700, 950, -12)
q = rotated_rect(640, 860, 760, 1020, 6)
img = draw_page(lighting(wood(), 0.2), q2, paper=(225, 222, 210))
hard.append(("two_pages", finish(draw_page(img, q)), q))
# Patterned tablecloth (checks)
check = ((xx // 80 + yy // 80) % 2)[..., None] * np.array([150, 40, 40]) + 60
q = rotated_rect(600, 800, 820, 1100, 11)
hard.append(("checks", finish(draw_page(check.astype(float), q)), q))
# Page running off the bottom of the frame: its lower corners are not in the photo
q = rotated_rect(600, 1000, 900, 1300, 0)
# Expected: the visible part of the page, cut off at the bottom edge of the photo.
hard.append(("off_frame", finish(draw_page(lighting(wood(), 0.2), q)), [(150, 350), (1050, 350), (1050, 1600), (150, 1600)]))

for name, arr, quad in hard:
    scenes.append((name, arr, quad))
    pil = Image.fromarray(arr)
    s = 320 / max(W, H)
    w, h = round(W * s), round(H * s)
    small = pil.resize((w, h), Image.BILINEAR).convert("RGBA")
    open(f"{OUT}/{name}.rgba", "wb").write(small.tobytes())
    pil.save(f"{OUT}/{name}.jpg", quality=88)
    index.append({"name": name, "w": w, "h": h, "hard": True,
                  "quad": None if quad is None else [[x * s, y * s] for x, y in quad]})

json.dump(index, open(f"{OUT}/index.json", "w"))
print(len(index), "scenes")
