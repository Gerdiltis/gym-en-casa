"""Cut the avatar heads out of the user's character sheet (assets/avatar_sheet.webp).

Produces assets/avatar_head_side.png and assets/avatar_head_front.png (transparent background)
plus assets/avatar_heads.json with each head's neck pivot and chin, used by skeleton.py to
place and scale the head on the drawn body.

    tools/videogen/.venv/bin/python -I tools/videogen/extract_avatar.py
"""
from __future__ import annotations

import json
from collections import deque
from pathlib import Path

from PIL import Image, ImageFilter

HERE = Path(__file__).resolve().parent
ASSETS = HERE / "assets"

# (left, top, right, bottom) in the 1536×1024 sheet; bottom cuts through the neck, above the collar.
HEADS = {
    "side": {"box": (786, 74, 912, 209), "neck_x": 838},   # third figure (profile, facing right)
    "front": {"box": (386, 72, 482, 214), "neck_x": 434},  # first figure (front)
}
WHITE = 232


def remove_background(img: Image.Image) -> Image.Image:
    """Flood-fill near-white pixels connected to the border (keeps eye/teeth whites)."""
    img = img.convert("RGBA")
    w, h = img.size
    px = img.load()
    seen = [[False] * h for _ in range(w)]
    q = deque((x, y) for x in range(w) for y in (0, h - 1))
    q.extend((x, y) for y in range(h) for x in (0, w - 1))
    while q:
        x, y = q.popleft()
        if not (0 <= x < w and 0 <= y < h) or seen[x][y]:
            continue
        seen[x][y] = True
        r, g, b, _ = px[x, y]
        if min(r, g, b) < WHITE:
            continue
        px[x, y] = (255, 255, 255, 0)
        q.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    # soften the cut edge a little
    alpha = img.getchannel("A").filter(ImageFilter.GaussianBlur(0.7))
    img.putalpha(alpha)
    return img


def main():
    sheet = Image.open(ASSETS / "avatar_sheet.webp")
    meta = {}
    for name, spec in HEADS.items():
        left, top, right, bottom = spec["box"]
        head = remove_background(sheet.crop(spec["box"]))
        head.save(ASSETS / f"avatar_head_{name}.png")
        # neck pivot: where the neck meets the bottom edge of the crop (in crop pixels)
        meta[name] = {"size": [right - left, bottom - top], "pivot": [spec["neck_x"] - left, bottom - top]}
        print(name, head.size, "pivot", meta[name]["pivot"])
    (ASSETS / "avatar_heads.json").write_text(json.dumps(meta, indent=1))


if __name__ == "__main__":
    main()
