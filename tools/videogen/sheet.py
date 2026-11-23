"""Contact sheet for visual review: one row per exercise, 4 phases per row.

    tools/videogen/.venv/bin/python -I tools/videogen/sheet.py out/sheet.png [slug ...]
"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from PIL import Image, ImageDraw  # noqa: E402

from exercises import EXERCISES  # noqa: E402
from render import layout, render_frame, solve, FPS  # noqa: E402

CELL = 180
PHASES = (0.0, 0.25, 0.5, 0.75)


def main(out, slugs):
    todo = [e for e in EXERCISES if not slugs or e["slug"] in slugs]
    sheet = Image.new("RGB", (CELL * len(PHASES) + 220, CELL * len(todo)), (10, 10, 10))
    dr = ImageDraw.Draw(sheet)
    for row, e in enumerate(todo):
        n = max(2, round(e["dur"] * FPS))
        offx, scale = layout(e, [solve(e, i / n) for i in range(n)])
        dr.text((8, row * CELL + 8), e["slug"], fill=(255, 255, 255))
        for col, u in enumerate(PHASES):
            img = render_frame(e, solve(e, u), offx, scale, u).resize((CELL, CELL))
            sheet.paste(img, (220 + col * CELL, row * CELL))
    path = Path(out)
    if not path.is_absolute():
        path = HERE / path
    sheet.save(path)
    print(path)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
