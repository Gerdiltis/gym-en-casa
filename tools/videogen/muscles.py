"""Which body zones light up for each muscle group (illustrated style).

A zone is (part, side, t0, t1, frac):
  part  "thigh" | "shin" | "upper" (upper arm) | "fore" (forearm) | "torso" | "glute"
  side  "front" | "back" | "full" | "center"  (front/back = anatomical front/back half of the segment)
  t0,t1 fraction along the segment (thigh: hip→knee, torso: hip→shoulder…)
  frac  width fraction for "center" zones
"""
from __future__ import annotations

ZONES: dict[str, list[tuple]] = {
    "cuadriceps": [("thigh", "front", 0.08, 0.92, 1)],
    "piernas": [("thigh", "front", 0.08, 0.92, 1), ("shin", "back", 0.08, 0.62, 1)],
    "isquios": [("thigh", "back", 0.10, 0.90, 1)],
    "gluteos": [("glute", "back", 0, 1, 1)],
    "gemelos": [("shin", "back", 0.08, 0.62, 1)],
    "aductores": [("thigh", "center", 0.10, 0.80, 0.5)],
    "pecho": [("torso", "front", 0.55, 0.90, 1)],
    "espalda": [("torso", "back", 0.45, 0.92, 1)],
    "lumbar": [("torso", "back", 0.12, 0.45, 1)],
    "abdominales": [("torso", "front", 0.15, 0.55, 1)],
    "core": [("torso", "front", 0.12, 0.58, 1)],
    "oblicuos": [("torso", "center", 0.15, 0.60, 0.55)],
    "hombros": [("upper", "full", 0.0, 0.38, 1)],
    "biceps": [("upper", "front", 0.25, 0.95, 1)],
    "triceps": [("upper", "back", 0.20, 0.95, 1)],
    "brazos": [("upper", "full", 0.30, 1.0, 1), ("fore", "full", 0.0, 0.90, 1)],
}

# Whole-body work: no single zone, the body outline turns red instead.
WHOLE_BODY = {"cardio", "cuerpo_completo"}


def zones_for(muscles: list[str]) -> list[tuple[tuple, bool]]:
    """[(zone, is_primary)], primary muscle first."""
    out = []
    for i, m in enumerate(muscles):
        for z in ZONES.get(m, []):
            out.append((z, i == 0))
    return out


def whole_body(muscles: list[str]) -> bool:
    return bool(muscles) and muscles[0] in WHOLE_BODY
