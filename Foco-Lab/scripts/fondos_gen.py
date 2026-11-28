"""Fondos digitales de Foco: 8 escenas planas con capas, sintetizadas con los colores de las fotos."""
import math, random, sys, os
from PIL import Image, ImageDraw, ImageFilter

W, H = 1080, 2340
OUT = sys.argv[1]


def hx(c):
    c = c.lstrip('#')
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def sky(stops):
    """stops: [(pos 0..1, color)] de arriba abajo."""
    im = Image.new('RGB', (W, H))
    px = im.load()
    col = []
    for y in range(H):
        t = y / (H - 1)
        for i in range(len(stops) - 1):
            p0, c0 = stops[i]
            p1, c1 = stops[i + 1]
            if p0 <= t <= p1:
                col.append(mix(hx(c0), hx(c1), (t - p0) / max(1e-6, p1 - p0)))
                break
        else:
            col.append(hx(stops[-1][1]))
    d = ImageDraw.Draw(im)
    for y, c in enumerate(col):
        d.line([(0, y), (W, y)], fill=c)
    return im


def glow(im, cx, cy, r, color, strength=0.55, core=None):
    layer = Image.new('RGB', (W, H), hx(color))
    mask = Image.new('L', (W, H), 0)
    md = ImageDraw.Draw(mask)
    for i in range(40, 0, -1):
        rr = r * (1 + i * 0.12)
        a = int(255 * strength * (1 - i / 40) ** 2)
        md.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=a)
    mask = mask.filter(ImageFilter.GaussianBlur(r * 0.4))
    im.paste(layer, (0, 0), mask)
    if core:
        d = ImageDraw.Draw(im)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=hx(core))


def ridge(base, amp, seed, freq=1.0, sharp=0.0):
    rnd = random.Random(seed)
    waves = [(rnd.uniform(0.6, 1.0) / (k + 1), rnd.uniform(0, 6.28), (k + 1) * freq) for k in range(5)]
    pts = []
    for x in range(0, W + 1, 6):
        u = x / W * math.pi * 2
        v = sum(a * math.sin(u * f + p) for a, p, f in waves)
        if sharp:
            v = v + sharp * (abs(math.sin(u * 2.3 * freq + seed)) - 0.5)
        pts.append((x, base - amp * v))
    return pts


def layer(im, pts, top, bottom):
    """Rellena bajo la línea con un degradado vertical top→bottom."""
    mask = Image.new('L', (W, H), 0)
    ImageDraw.Draw(mask).polygon(pts + [(W, H), (0, H)], fill=255)
    ymin = int(min(p[1] for p in pts))
    grad = sky([(0, '#%02x%02x%02x' % hx(top)), (max(0.01, ymin / H), '#%02x%02x%02x' % hx(top)), (1, '#%02x%02x%02x' % hx(bottom))])
    im.paste(grad, (0, 0), mask)


def stars(im, n, seed, top_frac=0.55):
    rnd = random.Random(seed)
    d = ImageDraw.Draw(im)
    for _ in range(n):
        x, y = rnd.uniform(0, W), rnd.uniform(0, H * top_frac)
        r = rnd.choice([1, 1, 1, 2, 2, 3])
        b = rnd.randint(150, 255)
        d.ellipse([x - r, y - r, x + r, y + r], fill=(b, b, min(255, b + 15)))


def grain(im, amount=6, seed=1):
    rnd = random.Random(seed)
    noise = Image.effect_noise((W, H), 40).convert('L')
    noise = noise.point(lambda v: 128 + (v - 128) * amount // 40)
    return Image.composite(im, im.point(lambda v: min(255, v + amount)), noise.point(lambda v: 255 if v > 128 else 0))


def save(im, name):
    im = grain(im)
    im.save(os.path.join(OUT, 'theme_%s.webp' % name), 'WEBP', quality=82, method=6)


# ---------- Café del Alba (07–09, cielo claro) ----------
im = sky([(0, '#f6e7d6'), (0.45, '#efd5bb'), (0.62, '#e9b98f'), (1, '#c9926b')])
glow(im, 760, 1180, 95, '#fff1d9', 0.8, core='#fff6e6')
layer(im, ridge(1380, 70, 3, 0.8), '#d9b598', '#c79e80')
layer(im, ridge(1560, 90, 7, 1.1), '#b88b6b', '#a37658')
layer(im, ridge(1760, 80, 11, 0.9), '#8f6249', '#7a5039')
layer(im, ridge(1980, 60, 13, 1.3), '#5c3d2e', '#3b2b23')
save(im, 'cafe')

# ---------- Horizonte del Alba (mañana con reto) ----------
im = sky([(0, '#121a26'), (0.35, '#2b3a55'), (0.55, '#c86a5c'), (0.62, '#f0a070'), (1, '#1a222d')])
glow(im, 540, 1330, 120, '#ffb27a', 0.75, core='#ffd2a1')
layer(im, ridge(1360, 110, 21, 1.2, 0.6), '#4a4560', '#2e3045')
layer(im, ridge(1580, 120, 23, 1.0, 0.5), '#2b2d40', '#1d2030')
layer(im, ridge(1820, 90, 29, 1.4), '#1a1d2a', '#111720')
save(im, 'horizonte')

# ---------- Cascada Serena (día tranquilo) ----------
im = sky([(0, '#8fa39a'), (0.35, '#6c7f68'), (0.6, '#3b4a3a'), (1, '#161f19')])
layer(im, ridge(1100, 140, 31, 0.7, 0.8), '#4c5a45', '#3a4636')
# acantilados y cascada
d = ImageDraw.Draw(im)
fall = Image.new('L', (W, H), 0)
fd = ImageDraw.Draw(fall)
fd.polygon([(470, 1050), (610, 1050), (650, 1900), (430, 1900)], fill=210)
fall = fall.filter(ImageFilter.GaussianBlur(14))
im.paste(sky([(0, '#e8efe9'), (1, '#b9cbbf')]), (0, 0), fall)
layer(im, [(0, 1000), (200, 1040), (430, 1120), (450, 1900), (0, 1900)], '#2c3a2c', '#1d271f')
layer(im, [(650, 1900), (660, 1120), (860, 1030), (W, 990), (W, 1900)], '#2c3a2c', '#1d271f')
glow(im, 540, 1900, 160, '#dfe9e2', 0.55)
layer(im, ridge(1960, 70, 37, 1.6), '#33402c', '#22301f')
layer(im, ridge(2160, 50, 41, 2.0), '#1b2418', '#121a14')
save(im, 'cascada')

# ---------- Dunas de Enfoque (modo Enfoque) ----------
im = sky([(0, '#7fbbe0'), (0.42, '#b3d0dc'), (0.55, '#e8c9a0'), (1, '#371b04')])
glow(im, 300, 900, 80, '#fff6df', 0.7, core='#fff9ec')
layer(im, ridge(1300, 60, 51, 0.5), '#d9944a', '#c27a33')
layer(im, ridge(1520, 110, 53, 0.6), '#b8682a', '#9a531d')
layer(im, ridge(1780, 120, 57, 0.55), '#8b4a19', '#6d3712')
layer(im, ridge(2040, 90, 59, 0.7), '#5a2c0d', '#371b04')
save(im, 'dunas')

# ---------- Ruta de Lectura (racha productiva) ----------
im = sky([(0, '#1c1a17'), (0.38, '#3b3024'), (0.52, '#d98a2b'), (0.56, '#f0b45a'), (1, '#1c140c')])
glow(im, 540, 1250, 90, '#ffc874', 0.7, core='#ffdc9e')
layer(im, ridge(1260, 80, 61, 1.0), '#4a3824', '#3a2c1d')
layer(im, ridge(1400, 60, 67, 1.3), '#2e2418', '#241c14')
road = Image.new('L', (W, H), 0)
ImageDraw.Draw(road).polygon([(520, 1330), (560, 1330), (900, H), (180, H)], fill=255)
im.paste(sky([(0, '#5a4630'), (0.6, '#6e5438'), (1, '#8a6a45')]), (0, 0), road)
line = Image.new('L', (W, H), 0)
ld = ImageDraw.Draw(line)
for i in range(9):
    t0, t1 = (i + 0.2) / 9, (i + 0.6) / 9
    y0, y1 = 1330 + (H - 1330) * t0 ** 1.8, 1330 + (H - 1330) * t1 ** 1.8
    w0, w1 = 2 + 10 * t0, 2 + 10 * t1
    ld.polygon([(540 - w0, y0), (540 + w0, y0), (540 + w1, y1), (540 - w1, y1)], fill=230)
im.paste((240, 190, 110), (0, 0), line)
save(im, 'ruta')

# ---------- Luna de Ocaso (17:30–20:30) ----------
im = sky([(0, '#1c232e'), (0.35, '#3d3a58'), (0.52, '#a8607a'), (0.6, '#e8875e'), (1, '#141a22')])
stars(im, 60, 71, 0.3)
glow(im, 720, 980, 110, '#ffd9c2', 0.6, core='#fbe7d8')
layer(im, ridge(1380, 100, 73, 1.1, 0.4), '#5a4466', '#3e3250')
layer(im, ridge(1600, 110, 79, 0.9, 0.3), '#33294a', '#251f38')
layer(im, ridge(1860, 80, 83, 1.3), '#1e1a2c', '#141a22')
save(im, 'luna')

# ---------- Cumbre Nocturna (noche) ----------
im = sky([(0, '#070b14'), (0.4, '#101a2c'), (0.6, '#1d2c44'), (1, '#0d151f')])
stars(im, 260, 91, 0.6)
glow(im, 300, 620, 70, '#cfe1f2', 0.22)
moon = Image.new('L', (W, H), 0)
md = ImageDraw.Draw(moon)
md.ellipse([230, 550, 370, 690], fill=255)
md.ellipse([262, 534, 402, 674], fill=0)  # luna creciente: se recorta con otro círculo
im.paste((238, 245, 251), (0, 0), moon.filter(ImageFilter.GaussianBlur(1)))
layer(im, ridge(1300, 180, 93, 0.9, 1.2), '#2a3a52', '#1c2a3d')
layer(im, ridge(1560, 150, 97, 1.1, 0.9), '#1a2638', '#131d2b')
layer(im, ridge(1840, 90, 101, 1.4), '#0f1722', '#0a1018')
save(im, 'cumbre')

# ---------- Fricción Ígnea (solo si la eliges) ----------
im = sky([(0, '#150605'), (0.4, '#350b06'), (0.6, '#6a1408'), (1, '#150b06')])
glow(im, 540, 1180, 130, '#ff5a1f', 0.6)
layer(im, [(0, 1700), (300, 1500), (470, 1200), (610, 1200), (780, 1500), (W, 1700), (W, H), (0, H)], '#2e0b06', '#150b06')
lava = Image.new('L', (W, H), 0)
ld = ImageDraw.Draw(lava)
ld.polygon([(480, 1200), (600, 1200), (560, 1500), (620, 1900), (540, 1900), (500, 1480)], fill=230)
lava = lava.filter(ImageFilter.GaussianBlur(6))
im.paste(sky([(0, '#ffb347'), (0.55, '#ff6a1a'), (1, '#c21a05')]), (0, 0), lava)
layer(im, ridge(2050, 70, 111, 1.5), '#1a0a06', '#0d0503')
save(im, 'lava')
print('ok')

# ---------- v31, desbloqueables ----------
# Aurora (7 días seguidos al 100 %)
im = sky([(0, '#050b12'), (0.45, '#0b1a22'), (0.7, '#12303a'), (1, '#081419')])
stars(im, 200, 131, 0.55)
for k, (col, y0, amp) in enumerate([('#5ee0a8', 520, 120), ('#6fd6c8', 700, 90), ('#9a7ee0', 420, 70)]):
    band = Image.new('L', (W, H), 0)
    bd = ImageDraw.Draw(band)
    for x in range(0, W, 4):
        y = y0 + amp * math.sin(x / W * 5.2 + k * 1.7) + 40 * math.sin(x / W * 13 + k)
        bd.line([(x, y - 160), (x, y + 60)], fill=150 - k * 30, width=4)
    band = band.filter(ImageFilter.GaussianBlur(38))
    im.paste(hx(col), (0, 0), band)
layer(im, ridge(1500, 140, 133, 1.0, 0.9), '#10262c', '#0b1c21')
layer(im, ridge(1760, 110, 137, 1.3, 0.6), '#0a1a1f', '#071216')
layer(im, ridge(2020, 70, 139, 1.6), '#061014', '#04090c')
save(im, 'aurora')

# Mar al alba (14 días seguidos al 100 %)
im = sky([(0, '#2a3a5a'), (0.3, '#6a7aa8'), (0.48, '#f0b49a'), (0.56, '#ffd9b0'), (0.57, '#7fa6c4'), (1, '#1e2a3a')])
glow(im, 540, 1300, 110, '#ffe0b8', 0.75, core='#fff0d8')
sea = Image.new('L', (W, H), 0)
ImageDraw.Draw(sea).rectangle([0, 1330, W, H], fill=255)
im.paste(sky([(0, '#7fa6c4'), (0.55, '#3d5f80'), (1, '#1e2a3a')]), (0, 0), sea)
d = ImageDraw.Draw(im)
rnd = random.Random(141)
for i in range(70):
    y = 1340 + (i / 70) ** 1.6 * (H - 1340)
    w = 30 + (i / 70) * 260
    x = 540 + rnd.uniform(-1, 1) * (60 + i * 6)
    d.line([(x - w / 2, y), (x + w / 2, y)], fill=(255, 214, 170) if abs(x - 540) < 140 + i * 2 else (150, 185, 210), width=2 + i // 25)
isl = Image.new('L', (W, H), 0)
ImageDraw.Draw(isl).polygon([(0, 1332), (0, 1270), (90, 1250), (210, 1290), (300, 1332)], fill=255)
ImageDraw.Draw(isl).polygon([(820, 1332), (900, 1296), (1000, 1282), (1080, 1300), (1080, 1332)], fill=255)
im.paste((58, 72, 104), (0, 0), isl)
save(im, 'mar')
print('ok2')
