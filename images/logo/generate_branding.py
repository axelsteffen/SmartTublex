#!/usr/bin/env python3
"""Generate SmartTublex sized PNGs from the full-logo masters (no text overlay).

Sources are already complete brand marks (icon + wordmark + tagline):
  - smarttublex.png       → release
  - smarttublex_beta.png  → beta

Usage:
  generate_branding.py                # beta → generated/ (default)
  generate_branding.py --variant both
  generate_branding.py --variant release
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
SRC_RELEASE = ROOT / "smarttublex.png"
SRC_BETA = ROOT / "smarttublex_beta.png"
OUT_BETA = ROOT / "generated"
OUT_RELEASE = ROOT / "generated_release"
BLACK = (0, 0, 0)
# Ignore near-black pixels when detecting the mark's content bounds
CONTENT_LUMA_THRESHOLD = 12
# Keep a thin margin around auto-trimmed content
TRIM_MARGIN_RATIO = 0.02

# nodpi / launcher sizes expected by packageWrapperApk
SQUARE_ASSETS = {
    "app_icon.png": 320,
    "app_icon_alt.png": 320,
    "app_logo.png": 180,
    "app_logo_semi_red.png": 180,
    "app_logo_semi_grey.png": 180,
}
LAUNCHER_DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
}
BANNER_SIZE = (640, 360)


def trim_content(src: Image.Image) -> Image.Image:
    """Crop near-black padding so the mark fills more of the target frame."""
    img = src.convert("RGB")
    mask = img.convert("L").point(lambda p: 255 if p > CONTENT_LUMA_THRESHOLD else 0)
    bbox = mask.getbbox()
    if bbox is None:
        return img
    left, top, right, bottom = bbox
    mw = max(1, int(round((right - left) * TRIM_MARGIN_RATIO)))
    mh = max(1, int(round((bottom - top) * TRIM_MARGIN_RATIO)))
    left = max(0, left - mw)
    top = max(0, top - mh)
    right = min(img.width, right + mw)
    bottom = min(img.height, bottom + mh)
    return img.crop((left, top, right, bottom))


def fit_contain(src: Image.Image, box: tuple[int, int], fill: tuple[int, int, int] = BLACK) -> Image.Image:
    """Scale src to fit inside box, centered on a solid fill (letterbox / pillarbox)."""
    canvas = Image.new("RGB", box, fill)
    img = src.convert("RGB")
    fitted = img.copy()
    fitted.thumbnail(box, Image.Resampling.LANCZOS)
    x = (box[0] - fitted.width) // 2
    y = (box[1] - fitted.height) // 2
    canvas.paste(fitted, (x, y))
    return canvas


def write_variant(src_path: Path, out_dir: Path) -> None:
    if not src_path.is_file():
        raise SystemExit(f"Missing source logo: {src_path}")
    logo = trim_content(Image.open(src_path))
    out_dir.mkdir(parents=True, exist_ok=True)

    master = fit_contain(logo, (1254, 1600))
    master_path = ROOT / (
        "smarttublex_branded.png" if out_dir == OUT_BETA else "smarttublex_branded_release.png"
    )
    master.save(master_path, "PNG")
    print(f"Wrote {master_path}")

    for name, side in SQUARE_ASSETS.items():
        fit_contain(logo, (side, side)).save(out_dir / name, "PNG")

    fit_contain(logo, BANNER_SIZE).save(out_dir / "app_banner.png", "PNG")

    for density, side in LAUNCHER_DENSITIES.items():
        dest = out_dir / f"ic_launcher_{density}.png"
        fit_contain(logo, (side, side)).save(dest, "PNG")
        print(f"Wrote {dest}")

    print(f"Wrote assets under {out_dir}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--variant",
        choices=("beta", "release", "both"),
        default="beta",
        help="Which source logo to size (default: beta → generated/)",
    )
    args = parser.parse_args()

    if args.variant in ("beta", "both"):
        write_variant(SRC_BETA, OUT_BETA)
    if args.variant in ("release", "both"):
        write_variant(SRC_RELEASE, OUT_RELEASE)


if __name__ == "__main__":
    main()
