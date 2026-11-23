"""Illustrated avatar renderer: anatomical body shapes, comic outline, the avatar's real head
(cut from the user's character sheet by extract_avatar.py) and the trained muscles marked in red.

Uses the same solved joints as skeleton.draw_side / draw_front, so every existing exercise
animation works unchanged.
"""
from __future__ import annotations

import json
import math
from functools import lru_cache
from pathlib import Path

from PIL import Image

import muscles as M
import skeleton as K
from skeleton import add, mul

ASSETS = Path(__file__).resolve().parent / "assets"

# Palette taken from the reference character sheet
BG = (243, 244, 247)
FLOOR_FILL = (229, 231, 236)
FLOOR_LINE = (204, 207, 214)
INK = (24, 24, 30)
SKIN = (233, 180, 143)
SKIN_SHADE = (214, 157, 122)
NAVY = (35, 43, 69)
NAVY_SHADE = (24, 30, 50)
SOCK = (22, 22, 24)
SHOE = (42, 46, 54)
SOLE = (236, 236, 236)
RED = (229, 57, 53)
OUTLINE_W = 2.2       # comic outline, layout units (≈3.3 px at 720)
FAR_DIM = 0.80        # far-side limbs are drawn darker
HEAD_HEIGHT = 64.0    # sprite height in layout units (neck base → hair top)


def dim(c, f):
    return tuple(int(v * f) for v in c)


def norm(v):
    n = math.hypot(*v) or 1.0
    return (v[0] / n, v[1] / n)


def ccw(v):
    return (-v[1], v[0])


def cw(v):
    return (v[1], -v[0])


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


# ---------------------------------------------------------------- shapes

def capsule(cv, a, b, wa, wb, color, expand=0.0):
    """Tapered segment a→b with round ends. `expand` grows it (used for the outline pass)."""
    d = norm((b[0] - a[0], b[1] - a[1]))
    n = ccw(d)
    ra, rb = wa / 2 + expand, wb / 2 + expand
    pts = [add(a, mul(n, ra)), add(b, mul(n, rb)), add(b, mul(n, -rb)), add(a, mul(n, -ra))]
    cv.polygon(pts, color, outline=False)
    cv.circle(a, ra, color, outline=False)
    cv.circle(b, rb, color, outline=False)


def band(a, b, wa, wb, t0, t1, side_n, inner, outer):
    """Polygon covering the strip between `inner` and `outer` (fractions of the half width) on side `side_n`."""
    p0, p1 = lerp(a, b, t0), lerp(a, b, t1)
    w0 = (wa + (wb - wa) * t0) / 2
    w1 = (wa + (wb - wa) * t1) / 2
    return [add(p0, mul(side_n, w0 * inner)), add(p1, mul(side_n, w1 * inner)),
            add(p1, mul(side_n, w1 * outer)), add(p0, mul(side_n, w0 * outer))]


def spindle(a, b, wa, wb, t0, t1, side_n, inner, outer, k=12):
    """Muscle-belly shaped band: widest in the middle, tapering at both ends."""
    edge_out, edge_in = [], []
    for i in range(k + 1):
        s = i / k
        t = t0 + (t1 - t0) * s
        p = lerp(a, b, t)
        w = (wa + (wb - wa) * t) / 2
        bulge = 0.25 + 0.75 * math.sin(math.pi * s)
        mid = (inner + outer) / 2
        half = (outer - inner) / 2 * bulge
        edge_out.append(add(p, mul(side_n, w * (mid + half))))
        edge_in.append(add(p, mul(side_n, w * (mid - half))))
    return edge_out + edge_in[::-1]


def shade_side(d):
    """Light comes from the upper left: the shaded half is the one facing right/down."""
    n = ccw(d)
    return n if (n[0] - n[1]) > 0 else (-n[0], -n[1])


# ---------------------------------------------------------------- head sprite

@lru_cache(maxsize=None)
def head_meta():
    return json.loads((ASSETS / "avatar_heads.json").read_text())


@lru_cache(maxsize=None)
def head_sprite(view, px_height):
    """Head image scaled to px_height and padded so the neck pivot is the image centre."""
    img = Image.open(ASSETS / f"avatar_head_{view}.png").convert("RGBA")
    meta = head_meta()[view]
    k = px_height / img.height
    img = img.resize((max(1, round(img.width * k)), max(1, round(img.height * k))), Image.LANCZOS)
    px, py = meta["pivot"][0] * k, meta["pivot"][1] * k
    half = int(max(px, img.width - px, py, img.height - py)) + 2
    canvas = Image.new("RGBA", (half * 2, half * 2), (0, 0, 0, 0))
    canvas.alpha_composite(img, (int(half - px), int(half - py)))
    return canvas


def paste_head(cv, view, anchor, angle_deg):
    """angle_deg: head tilt from vertical, + = towards the facing direction (clockwise on screen)."""
    px_h = HEAD_HEIGHT * cv.scale * cv.px * K.SS
    spr = head_sprite(view, int(px_h))
    if abs(angle_deg) > 0.5:
        spr = spr.rotate(-angle_deg, resample=Image.BICUBIC)
    x, y = cv.pt(anchor)
    cv.img.alpha_composite(spr, (int(x - spr.width / 2), int(y - spr.height / 2)))


# ---------------------------------------------------------------- muscle highlights

class Highlighter:
    def __init__(self, muscle_groups, u):
        self.zones = M.zones_for(muscle_groups)
        self.whole = M.whole_body(muscle_groups)
        pulse = math.sin(math.pi * 2 * u) ** 2
        self.a_primary = 0.55 + 0.18 * pulse
        self.a_secondary = 0.30 + 0.08 * pulse

    def outline(self):
        return RED if self.whole else INK

    def apply(self, cv, part, a, b, wa, wb, front_n, far=False):
        for (zpart, side, t0, t1, frac), primary in self.zones:
            if zpart != part:
                continue
            alpha = (self.a_primary if primary else self.a_secondary) * (0.6 if far else 1.0)
            if side == "front":
                pts = spindle(a, b, wa, wb, t0, t1, front_n, -0.1, 0.9)
            elif side == "back":
                pts = spindle(a, b, wa, wb, t0, t1, (-front_n[0], -front_n[1]), -0.1, 0.9)
            elif side == "center":
                pts = spindle(a, b, wa, wb, t0, t1, front_n, -frac, frac)
            else:
                pts = spindle(a, b, wa, wb, t0, t1, front_n, -0.88, 0.88)
            cv.blend_poly(pts, RED, alpha)


# ---------------------------------------------------------------- side view

ARM_W = (15, 12, 12, 9)      # upper (shoulder, elbow), forearm (elbow, wrist)
LEG_W = (25, 17, 17, 11)     # thigh (hip, knee), shin (knee, ankle)
HAND_R = 6.5


def _arm(cv, j, n, hl, far):
    f = FAR_DIM if far else 1.0
    sh, el, wr = j["shoulder"], j[n + "_mid"], j[n + "_end"]
    ink = hl.outline()
    # outline pass
    capsule(cv, sh, el, ARM_W[0] + 2, ARM_W[1], ink, OUTLINE_W)
    capsule(cv, el, wr, ARM_W[2], ARM_W[3], ink, OUTLINE_W)
    cv.circle(wr, HAND_R + OUTLINE_W, ink, outline=False)
    # fills
    capsule(cv, sh, el, ARM_W[0], ARM_W[1], dim(SKIN, f))
    capsule(cv, el, wr, ARM_W[2], ARM_W[3], dim(SKIN, f))
    cv.circle(wr, HAND_R, dim(SKIN, f), outline=False)
    d1 = norm((el[0] - sh[0], el[1] - sh[1]))
    d2 = norm((wr[0] - el[0], wr[1] - el[1]))
    cv.polygon(spindle(sh, el, ARM_W[0], ARM_W[1], 0.2, 0.98, shade_side(d1), 0.4, 0.95), dim(SKIN_SHADE, f), outline=False)
    cv.polygon(spindle(el, wr, ARM_W[2], ARM_W[3], 0.05, 0.9, shade_side(d2), 0.4, 0.95), dim(SKIN_SHADE, f), outline=False)
    # short sleeve
    sl = lerp(sh, el, 0.42)
    capsule(cv, sh, sl, ARM_W[0] + 3, ARM_W[0] + 1, dim(NAVY, f))
    hl.apply(cv, "upper", sh, el, ARM_W[0] + 1, ARM_W[1], ccw(d1), far)
    hl.apply(cv, "fore", el, wr, ARM_W[2], ARM_W[3], ccw(d2), far)


def _leg(cv, j, n, toe, hl, far):
    f = FAR_DIM if far else 1.0
    hip, kn, an = j["hip"], j[n + "_mid"], j[n + "_end"]
    ink = hl.outline()
    capsule(cv, hip, kn, LEG_W[0], LEG_W[1], ink, OUTLINE_W)
    capsule(cv, kn, an, LEG_W[2], LEG_W[3], ink, OUTLINE_W)
    capsule(cv, an, toe, 12, 10, ink, OUTLINE_W)
    capsule(cv, hip, kn, LEG_W[0], LEG_W[1], dim(SKIN, f))
    capsule(cv, kn, an, LEG_W[2], LEG_W[3], dim(SKIN, f))
    d1 = norm((kn[0] - hip[0], kn[1] - hip[1]))
    d2 = norm((an[0] - kn[0], an[1] - kn[1]))
    cv.polygon(spindle(kn, an, LEG_W[2], LEG_W[3], 0.05, 0.62, shade_side(d2), 0.4, 0.95), dim(SKIN_SHADE, f), outline=False)
    # shorts over the upper thigh, sock over the lower shin
    sh_end = lerp(hip, kn, 0.58)
    capsule(cv, hip, sh_end, LEG_W[0] + 3, LEG_W[0] - 1, dim(NAVY, f))
    cv.polygon(band(hip, sh_end, LEG_W[0] + 3, LEG_W[0] - 1, 0.1, 0.95, shade_side(d1), 0.4, 0.98), dim(NAVY_SHADE, f), outline=False)
    capsule(cv, lerp(kn, an, 0.62), an, LEG_W[3] + 2, LEG_W[3] + 1, dim(SOCK, f))
    # shoe with a white sole
    capsule(cv, an, toe, 12, 10, dim(SHOE, f))
    dt = norm((toe[0] - an[0], toe[1] - an[1]))
    down = cw(dt) if cw(dt)[1] < 0 else ccw(dt)
    cv._seg(add(an, mul(down, 4.5)), add(toe, mul(down, 4)), 2.6, dim(SOLE, f))
    hl.apply(cv, "thigh", hip, kn, LEG_W[0], LEG_W[1], ccw(d1), far)
    hl.apply(cv, "shin", kn, an, LEG_W[2], LEG_W[3], ccw(d2), far)


def _torso_shape(j):
    pts = K.torso_points(j, n=10)
    widths = [30, 29, 28, 28, 29, 31, 33, 34, 34, 33, 31]
    front, back = [], []
    last = len(pts) - 1
    for i, p in enumerate(pts):
        q, r = pts[min(i + 1, last)], pts[max(i - 1, 0)]
        n = cw(norm((q[0] - r[0], q[1] - r[1])))  # anatomical front
        w = widths[i] / 2
        front.append(add(p, mul(n, w)))
        back.append(add(p, mul(n, -w)))
    return pts, front, back


def draw_side(cv, j, muscle_groups, u):
    hl = Highlighter(muscle_groups, u)
    ink = hl.outline()
    # ground shadow
    xs = [j["ln_end"][0], j["lf_end"][0], j["hip"][0]]
    cx = sum(xs) / len(xs)
    lowest = min(j["ln_end"][1], j["lf_end"][1], j["hip"][1])
    if lowest < 40:
        cv.blend_poly([(cx + 70 * math.cos(a), 2 + 5 * math.sin(a)) for a in [i * math.pi / 12 for i in range(24)]], (0, 0, 0), 0.10)

    _arm(cv, j, "af", hl, far=True)
    _leg(cv, j, "lf", j["lf_toe"], hl, far=True)

    # torso (shirt) + shorts
    pts, front, back = _torso_shape(j)
    outline_poly = front + back[::-1]
    cv.polygon(outline_poly, NAVY, outline=ink, owidth=OUTLINE_W)
    cv.circle(j["hip"], 16, NAVY, outline=ink, owidth=OUTLINE_W)
    cv.polygon(front + back[::-1], NAVY, outline=False)
    d = norm((j["shoulder"][0] - j["hip"][0], j["shoulder"][1] - j["hip"][1]))
    sh_n = shade_side(d)
    cv.polygon(band(j["hip"], j["shoulder"], 30, 32, 0.05, 0.95, sh_n, 0.45, 0.95), NAVY_SHADE, outline=False)
    # shirt hem and small red logo on the chest
    hem_a, hem_b = front[2], back[2]
    cv._seg(hem_a, hem_b, 1.6, NAVY_SHADE)
    fr = cw(d)
    logo = add(lerp(j["hip"], j["shoulder"], 0.72), mul(fr, 9))
    cv.circle(logo, 2.6, RED, outline=False)
    # muscle zones on the torso
    hl.apply(cv, "torso", j["hip"], j["shoulder"], 30, 33, cw(d))
    hl.apply(cv, "glute", add(j["hip"], mul(cw(d), -4)), lerp(j["hip"], j["shoulder"], 0.18), 30, 30, cw(d))

    # neck + real head. Upright or leaning, people keep the head fairly level: damp the tilt;
    # lying down (torso near horizontal or beyond) the head follows the body.
    hd = j["hd"]
    angle = math.degrees(math.atan2(hd[0], hd[1]))
    if abs(j["t"]) < 70:
        angle *= 0.45
    hd = (math.sin(math.radians(angle)), math.cos(math.radians(angle)))
    neck_base = add(j["shoulder"], mul(hd, -3))
    capsule(cv, neck_base, add(j["shoulder"], mul(hd, 10)), 12, 11, ink, OUTLINE_W)
    capsule(cv, neck_base, add(j["shoulder"], mul(hd, 10)), 12, 11, SKIN)
    anchor = add(j["shoulder"], mul(hd, 7))
    if abs(j["t"]) >= 70:
        # lying down: keep the back of the head on the floor, not through it
        anchor = (anchor[0], max(anchor[1], 0.52 * HEAD_HEIGHT))
    paste_head(cv, "side", anchor, angle)

    _leg(cv, j, "ln", j["ln_toe"], hl, far=False)
    _arm(cv, j, "an", hl, far=False)


# ---------------------------------------------------------------- front view

def draw_front(cv, j, muscle_groups, u):
    hl = Highlighter(muscle_groups, u)
    ink = hl.outline()
    hip, sh = j["hip"], j["shoulder"]
    cv.blend_poly([(hip[0] + 60 * math.cos(a), 2 + 5 * math.sin(a)) for a in [i * math.pi / 12 for i in range(24)]], (0, 0, 0), 0.10)

    for n, anchor in (("ll", "hip_l"), ("lr", "hip_r")):
        a, k, e = j[anchor], j[n + "_mid"], j[n + "_end"]
        side = 1 if n == "lr" else -1
        toe = add(e, (14 * side, -2))
        capsule(cv, a, k, LEG_W[0], LEG_W[1], ink, OUTLINE_W)
        capsule(cv, k, e, LEG_W[2], LEG_W[3], ink, OUTLINE_W)
        capsule(cv, e, toe, 12, 10, ink, OUTLINE_W)
        capsule(cv, a, k, LEG_W[0], LEG_W[1], SKIN)
        capsule(cv, k, e, LEG_W[2], LEG_W[3], SKIN)
        capsule(cv, lerp(k, e, 0.62), e, LEG_W[3] + 2, LEG_W[3] + 1, SOCK)
        capsule(cv, e, toe, 12, 10, SHOE)
        cv._seg(add(e, (0, -4.5)), add(toe, (0, -4)), 2.6, SOLE)
        d1 = norm((k[0] - a[0], k[1] - a[1]))
        d2 = norm((e[0] - k[0], e[1] - k[1]))
        inner = (-side, 0)
        for (zpart, zside, t0, t1, frac), primary in hl.zones:
            alpha = hl.a_primary if primary else hl.a_secondary
            if zpart == "thigh":
                if zside == "center":   # adductors: inner half
                    cv.blend_poly(spindle(a, k, LEG_W[0], LEG_W[1], t0, t1, inner, 0.0, 0.92), RED, alpha)
                else:
                    cv.blend_poly(spindle(a, k, LEG_W[0], LEG_W[1], t0, t1, ccw(d1), -0.92, 0.92), RED, alpha * (0.6 if zside == "back" else 1))
            elif zpart == "shin":
                cv.blend_poly(spindle(k, e, LEG_W[2], LEG_W[3], t0, t1, ccw(d2), -0.92, 0.92), RED, alpha * 0.8)

    # torso: shirt trapezoid + shorts
    body = [add(hip, (-21, -4)), add(hip, (21, -4)), add(sh, (27, 2)), add(sh, (-27, 2))]
    cv.polygon(body, NAVY, outline=ink, owidth=OUTLINE_W)
    cv.rect(hip[0] - 24, hip[1] - 16, hip[0] + 24, hip[1] + 8, NAVY_SHADE, radius=6)
    cv.circle(add(sh, (25, -3)), 10, NAVY, outline=ink, owidth=OUTLINE_W)
    cv.circle(add(sh, (-25, -3)), 10, NAVY, outline=ink, owidth=OUTLINE_W)
    cv.polygon(body, NAVY, outline=False)
    cv.circle(add(sh, (12, -16)), 2.6, RED, outline=False)
    t_len = sh[1] - hip[1]
    half = lambda y: 21 + (27 - 21) * (y - hip[1]) / max(1, t_len)

    def ellipse(cx, cy, rx, ry, alpha):
        cv.blend_poly([(cx + rx * math.cos(k * math.pi / 14), cy + ry * math.sin(k * math.pi / 14)) for k in range(28)], RED, alpha)

    for (zpart, zside, t0, t1, frac), primary in hl.zones:
        alpha = hl.a_primary if primary else hl.a_secondary
        if zpart == "glute":
            for s_ in (-1, 1):
                ellipse(hip[0] + s_ * 13, hip[1] - 4, 9, 9, alpha * 0.7)
            continue
        if zpart != "torso":
            continue
        y0, y1 = hip[1] + t_len * t0, hip[1] + t_len * t1
        cy, ry = (y0 + y1) / 2, (y1 - y0) / 2
        hw = half(cy)
        if zside == "center":                      # obliques: both flanks
            for s_ in (-1, 1):
                ellipse(hip[0] + s_ * hw * 0.7, cy, hw * 0.26, ry, alpha)
        elif zside == "back":                      # back muscles peek out at the sides
            for s_ in (-1, 1):
                ellipse(hip[0] + s_ * hw * 0.72, cy, hw * 0.24, ry, alpha * 0.55)
        elif t0 >= 0.5:                            # chest: two pecs
            for s_ in (-1, 1):
                ellipse(hip[0] + s_ * hw * 0.42, cy, hw * 0.4, ry * 0.9, alpha)
        else:                                      # abs / core
            ellipse(hip[0], cy, hw * 0.42, ry, alpha)

    # neck + head
    capsule(cv, add(sh, (0, -2)), j["neck"], 13, 12, ink, OUTLINE_W)
    capsule(cv, add(sh, (0, -2)), j["neck"], 13, 12, SKIN)
    paste_head(cv, "front", add(sh, (0, 6)), 0)

    for n, anchor in (("al", "sh_l"), ("ar", "sh_r")):
        a, el, wr = j[anchor], j[n + "_mid"], j[n + "_end"]
        capsule(cv, a, el, ARM_W[0] + 2, ARM_W[1], ink, OUTLINE_W)
        capsule(cv, el, wr, ARM_W[2], ARM_W[3], ink, OUTLINE_W)
        cv.circle(wr, HAND_R + OUTLINE_W, ink, outline=False)
        capsule(cv, a, el, ARM_W[0], ARM_W[1], SKIN)
        capsule(cv, el, wr, ARM_W[2], ARM_W[3], SKIN)
        cv.circle(wr, HAND_R, SKIN, outline=False)
        capsule(cv, a, lerp(a, el, 0.42), ARM_W[0] + 3, ARM_W[0] + 1, NAVY)
        d1 = norm((el[0] - a[0], el[1] - a[1]))
        d2 = norm((wr[0] - el[0], wr[1] - el[1]))
        for (zpart, zside, t0, t1, frac), primary in hl.zones:
            alpha = hl.a_primary if primary else hl.a_secondary
            if zpart == "upper":
                cv.blend_poly(spindle(a, el, ARM_W[0] + 1, ARM_W[1], t0, t1, ccw(d1), -0.92, 0.92), RED, alpha * (0.6 if zside == "back" else 1))
            elif zpart == "fore":
                cv.blend_poly(spindle(el, wr, ARM_W[2], ARM_W[3], t0, t1, ccw(d2), -0.92, 0.92), RED, alpha)
