"""Render every exercise to a short looping MP4 + WebP thumbnail and write the catalog JSON.

Usage (from the repo root):
    tools/videogen/.venv/bin/python -I tools/videogen/render.py            # all exercises
    tools/videogen/.venv/bin/python -I tools/videogen/render.py squat ...  # only these slugs
    tools/videogen/.venv/bin/python -I tools/videogen/render.py --to-app   # also copy into the APK assets
"""
from __future__ import annotations

import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import illustrated as IL  # noqa: E402
import props as P  # noqa: E402
import skeleton as K  # noqa: E402
from exercises import EXERCISES  # noqa: E402

OUT = HERE / "out"
APP_ASSETS = HERE.parent.parent / "app" / "src" / "main" / "assets"
FPS = 24
# Output formats. "illustrated" = avatar with real head and muscles marked (v2); "flat" = original stick figure.
STYLES = {
    "illustrated": dict(out=720, crf=26, thumb=360),
    "flat": dict(out=480, crf=30, thumb=240),
}
STYLE = "illustrated"
META_KEYS = ("slug", "name", "description", "muscle_groups", "equipment", "type", "difficulty", "unilateral", "pregnancy")


def solve(exercise, u):
    front = exercise["view"] == "front"
    defaults = K.FRONT_DEFAULTS if front else K.SIDE_DEFAULTS
    pose = K.pose_at(exercise["keys"], u, defaults, exercise["ease"])
    return K.solve_front(pose) if front else K.solve_side(pose)


def layout(exercise, frames):
    """Horizontal offset + scale so figure and props fit the square canvas."""
    xs, ys = [], []
    for j in frames:
        for p in K.figure_points(j):
            xs.append(p[0]); ys.append(p[1])
        ys.append(j["head"][1] + K.L["head"])
        for prop in exercise["props"]:
            ext = P.dynamic_extent(prop, j)
            if ext:
                xs.extend(ext)
    for prop in exercise["props"]:
        ext = P.static_extent(prop)
        if ext and prop[0] != "mat":
            xs.extend(ext)
    margin = 30
    x0, x1 = min(xs) - margin, max(xs) + margin
    top = max(ys) + (28 if STYLE == "illustrated" else 15)
    # the illustrated avatar is zoomed in more: it fills the frame like the reference cards
    cap = 1.3 if STYLE == "illustrated" else 1.0
    scale = min(cap, K.SIZE / (x1 - x0), (K.FLOOR - 20) / top)
    return -(x0 + x1) / 2, scale


def render_frame(exercise, j, offx, scale, u=0.0, style=None):
    style = style or STYLE
    front = exercise["view"] == "front"
    if style == "illustrated":
        cv = K.Canvas(offx, scale, out=STYLES[style]["out"], bg=IL.BG, floor_fill=IL.FLOOR_FILL,
                      floor_line=IL.FLOOR_LINE, ink=IL.INK, ink_w=1.8)
    else:
        cv = K.Canvas(offx, scale, out=STYLES[style]["out"])
    for prop in exercise["props"]:
        if prop[0] not in P.FRONT_KINDS:
            P.draw_back(cv, prop, j)
    if style == "illustrated":
        (IL.draw_front if front else IL.draw_side)(cv, j, exercise["muscle_groups"], u)
    else:
        (K.draw_front if front else K.draw_side)(cv, j)
    for prop in exercise["props"]:
        if prop[0] in P.FRONT_KINDS:
            P.draw_front_props(cv, prop, j)
    return cv.result()


def render(exercise):
    n = max(2, round(exercise["dur"] * FPS))
    frames = [solve(exercise, i / n) for i in range(n)]  # [0, 1): last frame != first -> seamless loop
    offx, scale = layout(exercise, frames)
    slug = exercise["slug"]
    (OUT / "videos").mkdir(parents=True, exist_ok=True)
    (OUT / "thumbs").mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for i, j in enumerate(frames):
            render_frame(exercise, j, offx, scale, i / n).save(f"{tmp}/{i:04d}.png")
        video = OUT / "videos" / f"{slug}.mp4"
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-framerate", str(FPS), "-i", f"{tmp}/%04d.png",
             "-c:v", "libx264", "-preset", "veryslow", "-crf", str(STYLES[STYLE]["crf"]), "-pix_fmt", "yuv420p", "-tune", "animation",
             "-movflags", "+faststart", "-an", str(video)],
            check=True,
        )
    thumb_j = solve(exercise, exercise["thumb"])
    t = STYLES[STYLE]["thumb"]
    render_frame(exercise, thumb_j, offx, scale, exercise["thumb"]).resize((t, t), K.Image.LANCZOS).save(OUT / "thumbs" / f"{slug}.webp", quality=82)
    return video.stat().st_size


def write_catalog():
    catalog = []
    for e in EXERCISES:
        item = {k: e[k] for k in META_KEYS}
        item["video_path"] = f"videos/{e['slug']}.mp4"
        item["thumb_path"] = f"thumbs/{e['slug']}.webp"
        catalog.append(item)
    (OUT / "exercises.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=1), encoding="utf-8")
    return catalog


def copy_to_app():
    for sub in ("videos", "thumbs"):
        dst = APP_ASSETS / sub
        dst.mkdir(parents=True, exist_ok=True)
        for f in (OUT / sub).iterdir():
            shutil.copy2(f, dst / f.name)
    (APP_ASSETS / "catalog").mkdir(parents=True, exist_ok=True)
    shutil.copy2(OUT / "exercises.json", APP_ASSETS / "catalog" / "exercises.json")


def main(argv):
    global STYLE
    if "--flat" in argv:
        STYLE = "flat"
    to_app = "--to-app" in argv
    slugs = [a for a in argv if not a.startswith("--")]
    todo = [e for e in EXERCISES if not slugs or e["slug"] in slugs]
    sizes = {}
    for e in todo:
        sizes[e["slug"]] = render(e)
        print(f"{e['slug']:<34} {sizes[e['slug']] / 1024:6.1f} KB")
    write_catalog()
    if sizes:
        print(f"{len(sizes)} vídeos · media {sum(sizes.values()) / len(sizes) / 1024:.1f} KB · total {sum(sizes.values()) / 1024:.0f} KB")
    if to_app:
        copy_to_app()
        print(f"copiado a {APP_ASSETS}")


if __name__ == "__main__":
    main(sys.argv[1:])
