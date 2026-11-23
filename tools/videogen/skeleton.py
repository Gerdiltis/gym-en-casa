"""2D skeleton engine: keyframed poses -> flat-style frames (Pillow).

World coordinates: x to the right (the figure faces +x in side view), y UP, floor at y = 0.
Angles in degrees:
  * limb direction  d(a): 0 = pointing down, +90 = forward (+x), 180 = up, -90 = back.
  * torso direction td(t): 0 = up, +90 = forward (horizontal, head towards +x), -90 = back.

Limb specs (near/far arm `an`/`af`, near/far leg `ln`/`lf`; front view: `ar`/`al`/`lr`/`ll`):
  ("fk", upper, lower)                absolute angles of both segments
  ("ik", x, y[, sign])                end effector (hand / ankle) target in world coords
  ("ikj", joint, dx, dy[, sign])      target relative to an already solved joint
IK sign: +1 = joint on the counter-clockwise side of anchor->target (knees forward when standing),
         -1 = clockwise (elbows back when the arm hangs).
"""
from __future__ import annotations

import math
from PIL import Image, ImageDraw

SIZE = 480          # layout space (square); poses and layout are authored in these units
SS = 2              # supersampling factor
FLOOR = 425         # floor line in layout space

BG = (26, 26, 26)
FLOOR_FILL = (20, 20, 20)
FLOOR_LINE = (52, 52, 52)
SKIN = (226, 226, 226)
SKIN_FAR = (150, 150, 150)
RED = (229, 57, 53)
RED_DARK = (183, 28, 28)
STEEL = (158, 158, 158)
STEEL_DARK = (97, 97, 97)
EYE = (26, 26, 26)

L = dict(torso=105, neck=12, head=21, ua=60, fa=56, th=80, sh=78, foot=22)
W = dict(torso=32, neck=11, ua=15, fa=13, th=21, sh=16, foot=9, hand=8)


def d(a):
    r = math.radians(a)
    return (math.sin(r), -math.cos(r))


def td(t):
    r = math.radians(t)
    return (math.sin(r), math.cos(r))


def add(p, q):
    return (p[0] + q[0], p[1] + q[1])


def mul(p, k):
    return (p[0] * k, p[1] * k)


def ik(s, t, l1, l2, sign):
    dx, dy = t[0] - s[0], t[1] - s[1]
    dist = math.hypot(dx, dy)
    dist_c = max(abs(l1 - l2) + 1e-3, min(l1 + l2 - 1e-3, dist))
    base = math.atan2(dy, dx)
    cos_a = (l1 * l1 + dist_c * dist_c - l2 * l2) / (2 * l1 * dist_c)
    ang = base + sign * math.acos(max(-1.0, min(1.0, cos_a)))
    j = (s[0] + l1 * math.cos(ang), s[1] + l1 * math.sin(ang))
    ex, ey = t[0] - j[0], t[1] - j[1]
    n = math.hypot(ex, ey) or 1.0
    return j, (j[0] + ex / n * l2, j[1] + ey / n * l2)


def _limb(spec, anchor, l1, l2, default_sign, joints, mirror=False):
    kind = spec[0]
    if kind == "fk":
        a1, a2 = spec[1], spec[2]
        d1, d2 = d(a1), d(a2)
        if mirror:
            d1, d2 = (-d1[0], d1[1]), (-d2[0], d2[1])
        mid = add(anchor, mul(d1, l1))
        return mid, add(mid, mul(d2, l2))
    if kind == "ik":
        sign = spec[3] if len(spec) > 3 else default_sign
        return ik(anchor, (spec[1], spec[2]), l1, l2, sign)
    if kind == "ikj":
        sign = spec[4] if len(spec) > 4 else default_sign
        base = joints[spec[1]]
        return ik(anchor, (base[0] + spec[2], base[1] + spec[3]), l1, l2, sign)
    raise ValueError(spec)


SIDE_DEFAULTS = {
    "torso": 0.0, "head": 0.0, "curve": 0.0,
    "ln": ("fk", 0, 0), "lf": ("fk", 0, 0), "an": ("fk", 4, 2), "af": ("fk", -4, -2),
    "fn": 90.0, "ff": 90.0,
}

FRONT_DEFAULTS = {
    "ar": ("fk", 8, 4), "al": ("fk", 8, 4), "lr": ("fk", 4, 4), "ll": ("fk", 4, 4), "head": 0.0, "lean": 0.0,
}


def solve_side(p):
    p = {**SIDE_DEFAULTS, **p}
    hip = p["hip"]
    t = p["torso"]
    if "torso_to" in p:
        tx, ty = p["torso_to"]
        t = math.degrees(math.atan2(tx - hip[0], ty - hip[1]))
    sh = add(hip, mul(td(t), L["torso"]))
    hd = td(t + p["head"])
    neck = add(sh, mul(hd, L["neck"]))
    head = add(neck, mul(hd, L["head"]))
    j = {"hip": hip, "shoulder": sh, "neck": neck, "head": head, "t": t, "curve": p["curve"], "hd": hd}
    for name, anchor, l1, l2, sign in (("ln", "hip", L["th"], L["sh"], 1), ("lf", "hip", L["th"], L["sh"], 1),
                                       ("an", "shoulder", L["ua"], L["fa"], -1), ("af", "shoulder", L["ua"], L["fa"], -1)):
        mid, end = _limb(p[name], j[anchor], l1, l2, sign, j)
        j[name + "_mid"], j[name + "_end"] = mid, end
    j["ln_toe"] = add(j["ln_end"], mul(d(p["fn"]), L["foot"]))
    j["lf_toe"] = add(j["lf_end"], mul(d(p["ff"]), L["foot"]))
    return j


def solve_front(p):
    p = {**FRONT_DEFAULTS, **p}
    hip = p["hip"]
    # lean: side bend of the torso in degrees (+ = towards the figure's right, i.e. screen right)
    lr = math.radians(p["lean"])
    up = (math.sin(lr), math.cos(lr))
    side = (math.cos(lr), -math.sin(lr))
    sh = add(hip, mul(up, L["torso"] * 0.92))
    neck = add(sh, mul(up, L["neck"]))
    head = add(neck, mul(up, L["head"] + 2))
    j = {"hip": hip, "shoulder": sh, "neck": neck, "head": head, "lean": p["lean"]}
    j["sh_r"] = add(add(sh, mul(side, 24)), mul(up, -4))
    j["sh_l"] = add(add(sh, mul(side, -24)), mul(up, -4))
    j["hip_r"], j["hip_l"] = add(hip, (13, 0)), add(hip, (-13, 0))
    for name, anchor, l1, l2, sign, mirror in (
        ("lr", "hip_r", L["th"], L["sh"], 1, False), ("ll", "hip_l", L["th"], L["sh"], -1, True),
        ("ar", "sh_r", L["ua"], L["fa"], -1, False), ("al", "sh_l", L["ua"], L["fa"], 1, True),
    ):
        mid, end = _limb(p[name], j[anchor], l1, l2, sign, j, mirror)
        j[name + "_mid"], j[name + "_end"] = mid, end
    return j


# ---------------------------------------------------------------- interpolation

def _lerp(a, b, u):
    if isinstance(a, (int, float)) and isinstance(b, (int, float)):
        return a + (b - a) * u
    if isinstance(a, tuple) and isinstance(b, tuple) and len(a) == len(b):
        return tuple(_lerp(x, y, u) for x, y in zip(a, b))
    return a  # strings (joint names, spec kinds)


def pose_at(keys, u, defaults, ease="smooth"):
    """keys: list of (time 0..1, pose dict), last time must be 1.0."""
    for (t0, p0), (t1, p1) in zip(keys, keys[1:]):
        if t0 <= u <= t1:
            f = 0.0 if t1 == t0 else (u - t0) / (t1 - t0)
            if ease == "smooth":
                f = f * f * (3 - 2 * f)
            a = {**defaults, **p0}
            b = {**defaults, **p1}
            return {k: _lerp(a[k], b.get(k, a[k]), f) for k in set(a) | set(b) if k in a}
    return {**defaults, **keys[-1][1]}


# ---------------------------------------------------------------- drawing

class Canvas:
    """Drawing surface. Coordinates are authored in a 480-unit layout space; `out` is the output size in px."""

    def __init__(self, offx, scale, out=SIZE, bg=BG, floor_fill=FLOOR_FILL, floor_line=FLOOR_LINE, ink=None, ink_w=0.0):
        self.out = out
        self.px = out / SIZE
        self.img = Image.new("RGBA", (out * SS, out * SS), bg + (255,))
        self.dr = ImageDraw.Draw(self.img)
        self.offx = offx
        self.scale = scale
        self.ink = ink          # outline colour for props (illustrated style), None = no outline
        self.ink_w = ink_w      # outline width in layout units
        fy = FLOOR * self.px * SS
        self.dr.rectangle([0, fy, out * SS, out * SS], fill=floor_fill)
        self.dr.line([0, fy, out * SS, fy], fill=floor_line, width=max(1, int(2 * self.px * SS)))

    def pt(self, p):
        x = SIZE / 2 + (p[0] + self.offx) * self.scale
        y = FLOOR - p[1] * self.scale
        return (x * self.px * SS, y * self.px * SS)

    def w(self, width):
        return max(1, int(round(width * self.scale * self.px * SS)))

    def blend_poly(self, pts, color, alpha):
        """Translucent polygon (muscle highlights) composited onto the canvas."""
        xy = [self.pt(p) for p in pts]
        x0 = int(max(0, min(x for x, _ in xy))); y0 = int(max(0, min(y for _, y in xy)))
        x1 = int(min(self.img.width, max(x for x, _ in xy) + 1)); y1 = int(min(self.img.height, max(y for _, y in xy) + 1))
        if x1 <= x0 or y1 <= y0:
            return
        tile = Image.new("RGBA", (x1 - x0, y1 - y0), (0, 0, 0, 0))
        ImageDraw.Draw(tile).polygon([(x - x0, y - y0) for x, y in xy], fill=color + (int(255 * alpha),))
        self.img.alpha_composite(tile, (x0, y0))

    def seg(self, a, b, width, color):
        if self.ink is not None and color != self.ink:
            self._seg(a, b, width + 2 * self.ink_w, self.ink)
        self._seg(a, b, width, color)

    def _seg(self, a, b, width, color):
        pa, pb = self.pt(a), self.pt(b)
        wpx = self.w(width)
        self.dr.line([pa, pb], fill=color, width=wpx)
        r = wpx / 2
        for x, y in (pa, pb):
            self.dr.ellipse([x - r, y - r, x + r, y + r], fill=color)

    def poly(self, pts, width, color):
        for a, b in zip(pts, pts[1:]):
            self.seg(a, b, width, color)

    def circle(self, c, radius, color, outline=None, owidth=0):
        """outline: None = automatic (props outline in illustrated style), False = none, colour = that colour."""
        if outline is None and self.ink is not None and color != self.ink:
            outline, owidth = self.ink, self.ink_w
        if outline is False:
            outline = None
        x, y = self.pt(c)
        r = radius * self.scale * self.px * SS
        self.dr.ellipse([x - r, y - r, x + r, y + r], fill=color, outline=outline, width=self.w(owidth) if outline else 0)

    def rect(self, x0, y0, x1, y1, color, radius=0):
        a, b = self.pt((x0, y1)), self.pt((x1, y0))
        ol = dict(outline=self.ink, width=self.w(self.ink_w)) if self.ink is not None else {}
        if radius:
            self.dr.rounded_rectangle([a, b], radius=radius * self.scale * self.px * SS, fill=color, **ol)
        else:
            self.dr.rectangle([a, b], fill=color, **ol)

    def polygon(self, pts, color, outline=None, owidth=0.0):
        if outline is None and self.ink is not None and color != self.ink:
            outline, owidth = self.ink, self.ink_w
        if outline is not None and outline is not False:
            self.dr.polygon([self.pt(p) for p in pts], fill=color, outline=outline, width=self.w(owidth))
        else:
            self.dr.polygon([self.pt(p) for p in pts], fill=color)

    def result(self):
        return self.img.convert("RGB").resize((self.out, self.out), Image.LANCZOS)


def torso_points(j, n=8):
    hip, sh = j["hip"], j["shoulder"]
    dx, dy = sh[0] - hip[0], sh[1] - hip[1]
    back = (-dy, dx)  # CCW perpendicular (length = torso length)
    mid = add(mul(add(hip, sh), 0.5), mul(back, j["curve"]))
    pts = []
    for i in range(n + 1):
        u = i / n
        pts.append((
            (1 - u) ** 2 * hip[0] + 2 * (1 - u) * u * mid[0] + u * u * sh[0],
            (1 - u) ** 2 * hip[1] + 2 * (1 - u) * u * mid[1] + u * u * sh[1],
        ))
    return pts


def draw_side(cv, j):
    def arm(n, color):
        cv.seg(j["shoulder"], j[n + "_mid"], W["ua"], color)
        cv.seg(j[n + "_mid"], j[n + "_end"], W["fa"], color)
        cv.circle(j[n + "_end"], W["hand"], color)

    def leg(n, color):
        cv.seg(j["hip"], j[n + "_mid"], W["th"], color)
        cv.seg(j[n + "_mid"], j[n + "_end"], W["sh"], color)
        cv.seg(j[n + "_end"], j[n + "_toe"], W["foot"], color)

    arm("af", SKIN_FAR)
    leg("lf", SKIN_FAR)
    cv.poly(torso_points(j), W["torso"], RED)
    cv.seg(j["shoulder"], j["neck"], W["neck"], SKIN)
    cv.circle(j["head"], L["head"], SKIN)
    hd = j["hd"]
    fwd = (hd[1], -hd[0])  # clockwise perpendicular = face direction
    cv.circle(add(add(j["head"], mul(fwd, 10)), mul(hd, 3)), 2.6, EYE)
    # shorts: dark band at the hip
    cv.circle(j["hip"], W["torso"] / 2 + 1, STEEL_DARK)
    leg("ln", SKIN)
    arm("an", SKIN)


def draw_front(cv, j):
    for n, anchor in (("ll", "hip_l"), ("lr", "hip_r")):
        cv.seg(j[anchor], j[n + "_mid"], W["th"], SKIN)
        cv.seg(j[n + "_mid"], j[n + "_end"], W["sh"], SKIN)
        side = 1 if n == "lr" else -1
        cv.seg(j[n + "_end"], add(j[n + "_end"], (14 * side, -2)), W["foot"], SKIN)
    hip, sh = j["hip"], j["shoulder"]
    cv.polygon([add(hip, (-20, -6)), add(hip, (20, -6)), add(sh, (26, 4)), add(sh, (-26, 4))], RED)
    cv.circle(add(sh, (24, -2)), 9, RED)
    cv.circle(add(sh, (-24, -2)), 9, RED)
    cv.rect(hip[0] - 22, hip[1] - 14, hip[0] + 22, hip[1] + 6, STEEL_DARK, radius=6)
    cv.seg(sh, j["neck"], W["neck"], SKIN)
    cv.circle(j["head"], L["head"], SKIN)
    for n, anchor in (("al", "sh_l"), ("ar", "sh_r")):
        cv.seg(j[anchor], j[n + "_mid"], W["ua"], SKIN)
        cv.seg(j[n + "_mid"], j[n + "_end"], W["fa"], SKIN)
        cv.circle(j[n + "_end"], W["hand"], SKIN)


def figure_points(j):
    pts = [v for k, v in j.items() if isinstance(v, tuple) and len(v) == 2 and k != "hd"]
    return pts
