#!/usr/bin/env python3
"""Generate SmartTublex sidebar icons matching upstream SmartTube style.

Upstream reference: SmartTube/common/src/main/res/drawable-nodpi/icon_*.png
  - 301×301 PNG
  - mode LA (luminance + alpha) or white-on-transparent
  - filled solid glyphs (not wireframe outlines)
  - ~40–50px padding; content ~66–74% of canvas
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "generated"

# Match SmartTube drawable-nodpi sidebar icons.
SIZE = 301
# Content inset ≈ icon_home bbox padding.
PAD = 48
FG = (255, 255)  # L, A — opaque white
CLEAR = (0, 0)  # transparent


def blank() -> Image.Image:
    return Image.new("LA", (SIZE, SIZE), CLEAR)


def save(img: Image.Image, name: str) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    # Keep LA like icon_home / icon_playlist.
    if img.mode != "LA":
        img = img.convert("LA")
    path = OUT / name
    img.save(path, "PNG", optimize=True)
    print(f"Wrote {path} ({img.size[0]}x{img.size[1]} {img.mode})")


def draw_movies(d: ImageDraw.ImageDraw) -> None:
    """Film strip — solid body with sprocket cutouts (filled Material style)."""
    left, top, right, bottom = PAD, PAD + 28, SIZE - PAD, SIZE - PAD - 28
    d.rectangle((left, top, right, bottom), fill=FG)
    # Sprocket holes (transparent cutouts).
    hole_w, hole_h = 22, 28
    gap = 18
    y = top + 16
    while y + hole_h < bottom - 12:
        d.rectangle((left + 10, y, left + 10 + hole_w, y + hole_h), fill=CLEAR)
        d.rectangle((right - 10 - hole_w, y, right - 10, y + hole_h), fill=CLEAR)
        y += hole_h + gap
    # Center frame divider cutouts.
    mid_l = left + 48
    mid_r = right - 48
    d.rectangle((mid_l, top + 22, mid_r, top + 36), fill=CLEAR)
    d.rectangle((mid_l, (top + bottom) // 2 - 7, mid_r, (top + bottom) // 2 + 7), fill=CLEAR)
    d.rectangle((mid_l, bottom - 36, mid_r, bottom - 22), fill=CLEAR)


def draw_tv_shows(d: ImageDraw.ImageDraw) -> None:
    """TV set — filled screen + stand."""
    # Screen body
    screen = (PAD + 8, PAD + 20, SIZE - PAD - 8, SIZE - PAD - 70)
    d.rectangle(screen, fill=FG)
    # Inner bezel cutout (dark screen area) — keep a thick white frame.
    inset = 22
    d.rectangle(
        (screen[0] + inset, screen[1] + inset, screen[2] - inset, screen[3] - inset),
        fill=CLEAR,
    )
    # Stand neck + base
    cx = SIZE // 2
    neck_w, neck_h = 28, 36
    d.rectangle((cx - neck_w // 2, screen[3], cx + neck_w // 2, screen[3] + neck_h), fill=FG)
    base_w, base_h = 120, 22
    d.rectangle(
        (cx - base_w // 2, screen[3] + neck_h - 4, cx + base_w // 2, screen[3] + neck_h + base_h),
        fill=FG,
    )


def draw_watchlist(d: ImageDraw.ImageDraw) -> None:
    """Bookmark — solid filled ribbon (like Material bookmark)."""
    left, right = PAD + 52, SIZE - PAD - 52
    top, bottom = PAD + 8, SIZE - PAD - 8
    notch = 58
    d.polygon(
        [
            (left, top),
            (right, top),
            (right, bottom),
            ((left + right) // 2, bottom - notch),
            (left, bottom),
        ],
        fill=FG,
    )


def draw_photos(d: ImageDraw.ImageDraw) -> None:
    """Photo frame — solid outer frame, landscape silhouette cutouts."""
    outer = (PAD + 4, PAD + 28, SIZE - PAD - 4, SIZE - PAD - 28)
    d.rectangle(outer, fill=FG)
    inset = 20
    inner = (outer[0] + inset, outer[1] + inset, outer[2] - inset, outer[3] - inset)
    d.rectangle(inner, fill=CLEAR)
    # Mountains (filled white inside the frame).
    base_y = inner[3] - 8
    d.polygon(
        [
            (inner[0] + 8, base_y),
            (inner[0] + 70, inner[1] + 55),
            (inner[0] + 120, base_y),
        ],
        fill=FG,
    )
    d.polygon(
        [
            (inner[0] + 90, base_y),
            (inner[0] + 155, inner[1] + 35),
            (inner[2] - 8, base_y),
        ],
        fill=FG,
    )
    # Sun
    sun_r = 18
    sx, sy = inner[2] - 40, inner[1] + 38
    d.ellipse((sx - sun_r, sy - sun_r, sx + sun_r, sy + sun_r), fill=FG)


def draw_albums(d: ImageDraw.ImageDraw) -> None:
    """Stacked albums — solid front card + L-stack behind (like icon_playlist)."""
    stroke = 18
    gap = 16
    front = (PAD + 40, PAD + 24, SIZE - PAD - 16, SIZE - PAD - 40)
    # Two L-shaped layers behind (bottom-left), open toward top-right.
    for i in (2, 1):
        ox = i * gap
        oy = i * gap
        x0 = front[0] - ox
        y0 = front[1] + oy
        x1 = front[2] - ox
        y1 = front[3] + oy
        # left vertical bar
        d.rectangle((x0, y0, x0 + stroke, y1), fill=FG)
        # bottom horizontal bar
        d.rectangle((x0, y1 - stroke, x1, y1), fill=FG)

    d.rectangle(front, fill=FG)
    # Play triangle cutout.
    cx = (front[0] + front[2]) // 2 + 4
    cy = (front[1] + front[3]) // 2
    d.polygon(
        [
            (cx - 22, cy - 32),
            (cx - 22, cy + 32),
            (cx + 34, cy),
        ],
        fill=CLEAR,
    )


def main() -> None:
    builders = {
        "icon_movies.png": draw_movies,
        "icon_tv_shows.png": draw_tv_shows,
        "icon_watchlist.png": draw_watchlist,
        "icon_photos.png": draw_photos,
        "icon_albums.png": draw_albums,
    }
    for name, draw in builders.items():
        img = blank()
        draw(ImageDraw.Draw(img))
        save(img, name)


if __name__ == "__main__":
    main()
