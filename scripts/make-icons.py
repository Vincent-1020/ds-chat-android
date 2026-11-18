#!/usr/bin/env python3
"""生成 "DS" 字母标的应用图标。

白底 + 蓝色 DS 字样，不使用任何第三方商标素材。

- 自适应图标前景：透明背景 + 蓝色 DS，宽度占画布 33.3%
  （108dp 画布里可见区是 72dp，33.3% x 108/72 = 50%，与方形图标观感一致）
- 传统图标 / 商店大图：白色圆角方块 + 蓝色 DS（宽度 50%）

用法：python scripts/make-icons.py
依赖：Pillow
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

REPO = Path(__file__).resolve().parent.parent
RES = REPO / "android" / "res"
WEB_ASSET = REPO / "web" / "src" / "assets" / "logo.png"

TEXT = "DS"
BRAND_BLUE = (77, 107, 254, 255)
TILE_RADIUS_RATIO = 0.225
SUPERSAMPLE = 4

ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

FOREGROUND_RATIO = 0.333  # 相对 108dp 画布
LEGACY_RATIO = 0.50  # 相对图标可见区域

FONT_CANDIDATES = [
    "C:/Windows/Fonts/segoeuib.ttf",
    "C:/Windows/Fonts/arialbd.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
]


def load_font(size: int) -> ImageFont.FreeTypeFont:
    for path in FONT_CANDIDATES:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    raise SystemExit("找不到可用的粗体字体，请修改 FONT_CANDIDATES")


def fit_font(text: str, target_width: int, max_size: int) -> tuple[ImageFont.FreeTypeFont, int]:
    """二分找出让文字宽度刚好接近 target_width 的字号。"""
    lo, hi = 8, max_size
    best = lo
    while lo <= hi:
        mid = (lo + hi) // 2
        font = load_font(mid)
        width = sum(font.getbbox(ch)[2] - font.getbbox(ch)[0] for ch in text)
        if width <= target_width:
            best = mid
            lo = mid + 1
        else:
            hi = mid - 1
    return load_font(best), best


def draw_glyphs(size: int, ratio: float) -> Image.Image:
    """透明画布上居中绘制蓝色 DS。"""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    font, _ = fit_font(TEXT, int(size * ratio), size)
    widths = [font.getbbox(ch)[2] - font.getbbox(ch)[0] for ch in TEXT]
    total = sum(widths)
    cap = font.getbbox("D")
    cap_height = cap[3] - cap[1]
    x = (size - total) / 2
    y = (size - cap_height) / 2 - cap[1]
    draw = ImageDraw.Draw(canvas)
    for ch, width in zip(TEXT, widths):
        bbox = font.getbbox(ch)
        draw.text((x - bbox[0], y), ch, font=font, fill=BRAND_BLUE)
        x += width
    return canvas


def white_tile(size: int, ratio: float) -> Image.Image:
    """白色圆角方块 + 蓝色 DS。"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle(
        [0, 0, size - 1, size - 1], radius=int(size * TILE_RADIUS_RATIO), fill=(255, 255, 255, 255)
    )
    img.alpha_composite(draw_glyphs(size, ratio))
    return img


def main() -> None:
    # 自适应图标前景（白色底由 colors.xml 提供）
    for density, px in ADAPTIVE.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        big = draw_glyphs(px * SUPERSAMPLE, FOREGROUND_RATIO)
        big.resize((px, px), Image.LANCZOS).save(folder / "ic_launcher_foreground.png")
        print(f"  foreground {density:8s} {px}px")

    # 传统图标：白色圆角方块
    for density, px in LEGACY.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        big = white_tile(px * SUPERSAMPLE, LEGACY_RATIO)
        big.resize((px, px), Image.LANCZOS).save(folder / "ic_launcher.png")
        print(f"  legacy     {density:8s} {px}px")

    # 商店/文档用大图
    white_tile(2048, LEGACY_RATIO).resize((512, 512), Image.LANCZOS).save(REPO / "android" / "icon-512.png")
    print("  store      512px")

    # 网页端首屏标识（与图标同款）
    WEB_ASSET.parent.mkdir(parents=True, exist_ok=True)
    white_tile(512, LEGACY_RATIO).resize((128, 128), Image.LANCZOS).save(WEB_ASSET)
    print(f"  web logo   {WEB_ASSET.relative_to(REPO)}")

    print("done")


if __name__ == "__main__":
    sys.exit(main())
