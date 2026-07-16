#!/usr/bin/env python3
"""Resize section-card thumbnail masters into drawable-ready PNGs."""

from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
OUT_DIR = ROOT / "generated"
# Leanback row cards are landscape; keep APK size reasonable.
SIZE = (640, 360)

# source master name -> Android-safe drawable basename
MAPPING = {
    "all_movies.png": "all_movies.png",
    "all_TV-Shows.png": "all_tv_shows.png",
}


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for src_name, dest_name in MAPPING.items():
        src = ROOT / src_name
        if not src.is_file():
            raise SystemExit(f"Missing thumbnail master: {src}")
        img = Image.open(src).convert("RGBA")
        img = img.resize(SIZE, Image.Resampling.LANCZOS)
        dest = OUT_DIR / dest_name
        img.save(dest, "PNG", optimize=True)
        print(f"Wrote {dest} ({img.width}x{img.height})")


if __name__ == "__main__":
    main()
