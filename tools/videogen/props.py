"""Flat props drawn around the figure. Static props go behind the figure, held props on top."""
from __future__ import annotations

import math

from skeleton import RED, RED_DARK, STEEL, STEEL_DARK, add, mul

CHAIR_SEAT = 78
WOOD = (120, 120, 120)


BALL = (200, 200, 200)
BALL_EDGE = (130, 130, 130)
BALL_SHINE = (228, 228, 228)


def _pt(j, ref):
    """ref = joint name, (joint name, dx, dy) or (x, y) world point."""
    if isinstance(ref, str):
        return j[ref]
    if isinstance(ref[0], str):
        return add(j[ref[0]], (ref[1], ref[2]))
    return ref


def ball_geom(prop, j):
    """("ball", x, r[, y]) static (resting on the floor unless y given) or
    ("ball", joint, r, dx[, dy]) following a joint; dy None/omitted = resting on the floor."""
    ref, r = prop[1], prop[2]
    if isinstance(ref, str):
        dx = prop[3] if len(prop) > 3 else 0
        dy = prop[4] if len(prop) > 4 else None
        base = j[ref]
        return (base[0] + dx, r if dy is None else base[1] + dy), r
    return (ref, prop[3] if len(prop) > 3 else r), r


def dynamic_extent(prop, j):
    """x-range of props that move with the figure (per frame), or None."""
    if prop[0] == "ball" and isinstance(prop[1], str):
        (x, _), r = ball_geom(prop, j)
        return (x - r, x + r)
    return None


def draw_ball(cv, c, r):
    cv.circle(c, r, BALL, outline=BALL_EDGE, owidth=2)
    # red stripe through the centre; it turns as the ball rolls (rolling to +x = clockwise)
    phi = math.radians(25) - c[0] / r
    n = (math.cos(phi), math.sin(phi))
    t = (-n[1], n[0])
    w = r * 0.2
    pts = []
    for side in (1, -1):
        for i in range(9):
            b = (-w + 2 * w * i / 8) * side
            a = math.sqrt(max(0.0, r * r - b * b)) * 0.96 * side
            pts.append(add(c, add(mul(t, a), mul(n, b))))
    cv.polygon(pts, RED)
    cv.circle(add(c, (-0.38 * r, 0.42 * r)), 0.13 * r, BALL_SHINE)


def static_extent(prop):
    """x-range a static prop occupies (for centering), or None."""
    kind = prop[0]
    if kind in ("chair", "step", "sofa", "mat"):
        return (prop[1], prop[2])
    if kind == "wall":
        return (prop[1], prop[1] + 18)
    if kind == "foam":
        return (prop[1] - prop[2], prop[1] + prop[2])
    if kind == "post":
        return (prop[1] - 6, prop[1] + 12)
    if kind == "ball" and not isinstance(prop[1], str):
        return (prop[1] - prop[2], prop[1] + prop[2])
    return None


def draw_back(cv, prop, j):
    kind = prop[0]
    if kind == "mat":
        cv.rect(prop[1], -1, prop[2], 4, RED_DARK, radius=2)
    elif kind == "chair":
        x0, x1, back = prop[1], prop[2], prop[3] if len(prop) > 3 else "left"
        cv.rect(x0 + 2, 0, x0 + 8, CHAIR_SEAT - 6, STEEL_DARK)
        cv.rect(x1 - 8, 0, x1 - 2, CHAIR_SEAT - 6, STEEL_DARK)
        cv.rect(x0, CHAIR_SEAT - 8, x1, CHAIR_SEAT, WOOD, radius=3)
        if back == "left":
            cv.rect(x0, CHAIR_SEAT, x0 + 8, CHAIR_SEAT + 82, STEEL_DARK, radius=3)
        elif back == "right":
            cv.rect(x1 - 8, CHAIR_SEAT, x1, CHAIR_SEAT + 82, STEEL_DARK, radius=3)
    elif kind == "step":
        cv.rect(prop[1], 0, prop[2], prop[3], WOOD, radius=4)
        cv.rect(prop[1], prop[3] - 5, prop[2], prop[3], STEEL, radius=3)
    elif kind == "sofa":
        x0, x1, seat = prop[1], prop[2], prop[3]
        cv.rect(x0, 8, x1, seat, STEEL_DARK, radius=8)
        cv.rect(x0 + 4, seat - 14, x1 - 2, seat, WOOD, radius=7)
        cv.rect(x0, seat - 10, x0 + 24, seat + 70, STEEL_DARK, radius=8)
        cv.rect(x0 + 4, 0, x0 + 10, 8, STEEL_DARK)
        cv.rect(x1 - 10, 0, x1 - 4, 8, STEEL_DARK)
    elif kind == "wall":
        cv.rect(prop[1], 0, prop[1] + 18, 420, (44, 44, 44))
        cv.rect(prop[1] + (0 if prop[2] == "right" else 14), 0, prop[1] + (4 if prop[2] == "right" else 18), 420, (64, 64, 64))
    elif kind == "post":
        cv.rect(prop[1], 0, prop[1] + 12, 420, (44, 44, 44))
        cv.circle((prop[1] + 2, prop[2]), 6, STEEL)
    elif kind == "ball":
        c, r = ball_geom(prop, j)
        draw_ball(cv, c, r)
    elif kind == "foam":
        x, r = prop[1], prop[2]
        cv.circle((x, r), r, STEEL)
        cv.circle((x, r), r * 0.55, STEEL_DARK)
        cv.circle((x, r), r * 0.25, RED)
    elif kind == "backpack":
        # strapped to the upper back (side view)
        hip, sh = j["hip"], j["shoulder"]
        dx, dy = sh[0] - hip[0], sh[1] - hip[1]
        n = math.hypot(dx, dy) or 1
        back = (-dy / n, dx / n)
        along = (dx / n, dy / n)
        c = add(add(hip, mul((dx, dy), 0.62)), mul(back, 26))
        half_l, half_w = 30, 16
        pts = [add(add(c, mul(along, sa * half_l)), mul(back, sb * half_w)) for sa, sb in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
        cv.polygon(pts, RED_DARK)
    elif kind == "towel":
        for n in prop[1:]:
            toe = j[n + "_toe"]
            end = j[n + "_end"]
            cx = (toe[0] + end[0]) / 2
            cv.rect(cx - 20, -1, cx + 20, 4, (200, 200, 200), radius=2)


def draw_front_props(cv, prop, j):
    kind = prop[0]
    if kind == "band":
        # polyline through any number of refs
        cv.poly([_pt(j, r) for r in prop[1:]], 4, RED)
    elif kind == "dumbbell":
        hand = j[prop[1]]
        orient = prop[2] if len(prop) > 2 else "h"
        if orient == "v":
            cv.seg(add(hand, (0, -16)), add(hand, (0, 16)), 6, STEEL)
            cv.rect(hand[0] - 13, hand[1] + 12, hand[0] + 13, hand[1] + 22, RED, radius=3)
            cv.rect(hand[0] - 13, hand[1] - 22, hand[0] + 13, hand[1] - 12, RED, radius=3)
        else:
            cv.seg(add(hand, (-16, 0)), add(hand, (16, 0)), 6, STEEL)
            cv.rect(hand[0] - 22, hand[1] - 13, hand[0] - 12, hand[1] + 13, RED, radius=3)
            cv.rect(hand[0] + 12, hand[1] - 13, hand[0] + 22, hand[1] + 13, RED, radius=3)
    elif kind == "bottle":
        hand = j[prop[1]]
        cv.rect(hand[0] - 7, hand[1] - 20, hand[0] + 7, hand[1] + 14, (170, 190, 200), radius=5)
        cv.rect(hand[0] - 4, hand[1] + 14, hand[0] + 4, hand[1] + 20, RED, radius=2)
    elif kind == "backpack_hand":
        hand = j[prop[1]]
        cv.rect(hand[0] - 18, hand[1] - 44, hand[0] + 18, hand[1] - 4, RED_DARK, radius=8)
        cv.seg(add(hand, (-8, -6)), add(hand, (8, -6)), 4, STEEL_DARK)


FRONT_KINDS = {"band", "dumbbell", "bottle", "backpack_hand"}
