#!/usr/bin/env python3
"""Generate SmartTublex branded master + mipmap-sized PNGs from images/logo/smarttublex.png."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "smarttublex.png"
OUT_MASTER = ROOT / "smarttublex_branded.png"
OUT_DIR = ROOT / "generated"

PRIMARY = "Beta Version"
DISCLAIMER = "unofficial SmartTube fork"
WHITE = (255, 255, 255, 255)
BLACK = (0, 0, 0, 255)

FONT_CANDIDATES = [
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "/System/Library/Fonts/Supplemental/Arial.ttf",
    "/Library/Fonts/Arial Unicode.ttf",
    "/System/Library/Fonts/Helvetica.ttc",
]


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    preferred = FONT_CANDIDATES if not bold else [
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        *FONT_CANDIDATES,
    ]
    for path in preferred:
        p = Path(path)
        if p.is_file():
            try:
                return ImageFont.truetype(str(p), size=size)
            except OSError:
                continue
    return ImageFont.load_default()


def fit_font(draw: ImageDraw.ImageDraw, text: str, max_width: int, start: int, bold: bool = False) -> ImageFont.ImageFont:
    size = start
    while size >= 8:
        font = load_font(size, bold=bold)
        bbox = draw.textbbox((0, 0), text, font=font)
        if bbox[2] - bbox[0] <= max_width:
            return font
        size -= 1
    return load_font(8, bold=bold)


def paste_symbol(canvas: Image.Image, symbol: Image.Image, max_side: int, top: int) -> int:
    """Paste symbol centered; return y just below symbol."""
    s = symbol.copy()
    s.thumbnail((max_side, max_side), Image.Resampling.LANCZOS)
    x = (canvas.width - s.width) // 2
    canvas.paste(s, (x, top), s if s.mode == "RGBA" else None)
    return top + s.height


def draw_centered(draw: ImageDraw.ImageDraw, text: str, y: int, font: ImageFont.ImageFont, width: int) -> int:
    bbox = draw.textbbox((0, 0), text, font=font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    x = (width - tw) // 2
    draw.text((x, y - bbox[1]), text, font=font, fill=WHITE)
    return y + th


def make_branded_master(symbol: Image.Image) -> Image.Image:
    w = 1254
    h = 1600
    canvas = Image.new("RGBA", (w, h), BLACK)
    draw = ImageDraw.Draw(canvas)
    bottom = paste_symbol(canvas, symbol.convert("RGBA"), max_side=1000, top=80)
    y = bottom + 48
    font_primary = fit_font(draw, PRIMARY, w - 80, start=72, bold=True)
    y = draw_centered(draw, PRIMARY, y, font_primary, w) + 16
    font_disc = fit_font(draw, DISCLAIMER, w - 80, start=36, bold=False)
    # Prefer single line; if too wide at min size, split
    bbox = draw.textbbox((0, 0), DISCLAIMER, font=font_disc)
    if bbox[2] - bbox[0] > w - 80:
        for line in ("unofficial", "SmartTube fork"):
            font_line = fit_font(draw, line, w - 80, start=34, bold=False)
            y = draw_centered(draw, line, y, font_line, w) + 8
    else:
        draw_centered(draw, DISCLAIMER, y, font_disc, w)
    return canvas.convert("RGB")


def compose_square(
    symbol: Image.Image,
    size: int,
    *,
    primary: bool,
    disclaimer: bool,
) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), BLACK)
    draw = ImageDraw.Draw(canvas)
    margin = max(4, size // 40)
    text_budget = 0
    if primary:
        text_budget += max(14, size // 9)
    if disclaimer:
        text_budget += max(10, size // 12)
    symbol_max = size - 2 * margin - text_budget - (8 if primary else 0)
    symbol_max = max(symbol_max, size // 2)
    bottom = paste_symbol(canvas, symbol.convert("RGBA"), max_side=symbol_max, top=margin)
    y = bottom + max(4, size // 40)
    if primary:
        font_p = fit_font(draw, PRIMARY, size - 2 * margin, start=max(10, size // 10), bold=True)
        y = draw_centered(draw, PRIMARY, y, font_p, size) + max(2, size // 80)
    if disclaimer:
        font_d = fit_font(draw, DISCLAIMER, size - 2 * margin, start=max(8, size // 16), bold=False)
        bbox = draw.textbbox((0, 0), DISCLAIMER, font=font_d)
        if bbox[2] - bbox[0] <= size - 2 * margin:
            draw_centered(draw, DISCLAIMER, y, font_d, size)
        else:
            for line in ("unofficial", "SmartTube fork"):
                font_line = fit_font(draw, line, size - 2 * margin, start=max(7, size // 18), bold=False)
                y = draw_centered(draw, line, y, font_line, size) + 2
    return canvas.convert("RGB")


def compose_banner(symbol: Image.Image, width: int = 640, height: int = 360) -> Image.Image:
    canvas = Image.new("RGBA", (width, height), BLACK)
    draw = ImageDraw.Draw(canvas)
    # Symbol on the left, text on the right
    sym = symbol.convert("RGBA")
    sym.thumbnail((280, 280), Image.Resampling.LANCZOS)
    sx = 40
    sy = (height - sym.height) // 2
    canvas.paste(sym, (sx, sy), sym)
    text_left = sx + sym.width + 28
    text_width = width - text_left - 24
    font_p = fit_font(draw, PRIMARY, text_width, start=42, bold=True)
    font_d = fit_font(draw, DISCLAIMER, text_width, start=22, bold=False)
    bp = draw.textbbox((0, 0), PRIMARY, font=font_p)
    bd = draw.textbbox((0, 0), DISCLAIMER, font=font_d)
    ph = bp[3] - bp[1]
    dh = bd[3] - bd[1]
    gap = 12
    block_h = ph + gap + dh
    y = (height - block_h) // 2
    draw.text((text_left, y - bp[1]), PRIMARY, font=font_p, fill=WHITE)
    y2 = y + ph + gap
    # split disclaimer if needed
    if bd[2] - bd[0] > text_width:
        y_line = y2
        for line in ("unofficial", "SmartTube fork"):
            fl = fit_font(draw, line, text_width, start=20, bold=False)
            bl = draw.textbbox((0, 0), line, font=fl)
            draw.text((text_left, y_line - bl[1]), line, font=fl, fill=WHITE)
            y_line += (bl[3] - bl[1]) + 4
    else:
        draw.text((text_left, y2 - bd[1]), DISCLAIMER, font=font_d, fill=WHITE)
    return canvas.convert("RGB")


def main() -> None:
    if not SRC.is_file():
        raise SystemExit(f"Missing source logo: {SRC}")
    symbol = Image.open(SRC)

    master = make_branded_master(symbol)
    master.save(OUT_MASTER, "PNG")
    print(f"Wrote {OUT_MASTER}")

    OUT_DIR.mkdir(parents=True, exist_ok=True)

    icon = compose_square(symbol, 320, primary=True, disclaimer=True)
    icon.save(OUT_DIR / "app_icon.png", "PNG")
    icon.save(OUT_DIR / "app_icon_alt.png", "PNG")

    compose_banner(symbol).save(OUT_DIR / "app_banner.png", "PNG")

    logo = compose_square(symbol, 180, primary=True, disclaimer=False)
    for name in ("app_logo.png", "app_logo_semi_red.png", "app_logo_semi_grey.png"):
        logo.save(OUT_DIR / name, "PNG")

    # Density launchers: symbol + Beta Version when size allows
    for density, side in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144)):
        use_primary = side >= 96
        img = compose_square(symbol, side, primary=use_primary, disclaimer=False)
        dest = OUT_DIR / f"ic_launcher_{density}.png"
        img.save(dest, "PNG")
        print(f"Wrote {dest}")

    print(f"Wrote assets under {OUT_DIR}")


if __name__ == "__main__":
    main()
