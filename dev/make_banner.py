#!/usr/bin/env python3
"""Compose the Modrinth description banner (3:1) from Blockbench renders in dev/icon/banner_frames/.

Outputs: dist/banner.png (static, STILL_FRAME) and dist/banner-animated.gif (full loop).
Art: dev/icon/build_scene.js rendered with FGA.camera(0.36) -> banner_frames/ (64 frames, 1600px, 1:1, no resampling).
Background, title and tagline: dev/icon/sprites/banner_*.aseprite, scaled up nearest-neighbour. Clouds scroll across the
192px grid like the icon's (make_icon.cloud_layer); the banner loop is 3 art loops (192 frames), so the far layer at
1 px/frame wraps the full banner width and no cloud visibly repeats."""
import sys
from pathlib import Path

from PIL import Image, ImageFilter

from make_icon import cloud_layer, write_gif

ROOT = Path(__file__).resolve().parent.parent
ICON = ROOT / "dev" / "icon"
SPRITES = ICON / "sprites"
DIST = ROOT / "dist"
W, H = 1536, 512
GRID_W = 192
FPS = 25
LOOPS = 3
STILL_FRAME = 0
ART_OFFSET = (400, -522)  # banner = frame + offset: the bob's extent centred on x 1200 and vertically
OUTLINE = 17
LAYERS = (("banner_bg", 8, (0, 0)), ("banner_title_1", 8, (72, 104)), ("banner_title_2", 8, (72, 224)),
          ("banner_tagline", 6, (74, 360)))
# (sprite, x at frame 0, y, grid px per frame, wrap width, in front of the ghast); speed * LOOPS * 64 must be a multiple of wrap
CLOUDS = [
    ("cloud_far", 10, 4, 1, 192, False), ("cloud_far", 64, 57, 1, 192, False), ("cloud_far", 118, 2, 1, 192, False),
    ("cloud_far", 168, 30, 1, 192, False), ("cloud_far", 40, 30, 1, 192, False),
    ("cloud_mid", 30, 7, 2, 384, False), ("cloud_mid", 140, 49, 2, 384, False), ("cloud_mid", 250, 22, 2, 384, False),
    ("cloud_mid", 330, 54, 2, 384, False),
    ("cloud_front", 170, 54, 3, 576, True), ("cloud_front", 460, 52, 3, 576, True),
]
GIF_LIMIT = 5 * 1024 * 1024
GRID = 8
CORNER = (4, 2, 1, 1)  # pixel-art rounded corners: cells cut from the edge per row, on the 8px grid; symmetric, so rows = columns


def sprite(name: str, scale: int) -> Image.Image:
    im = Image.open(SPRITES / f"{name}.png").convert("RGBA")
    return im.resize((im.width * scale, im.height * scale), Image.NEAREST)


def art(frame: Path) -> Image.Image:
    im = Image.open(frame).convert("RGBA")
    dx, dy = ART_OFFSET
    return im.crop((-dx, -dy, W - dx, H - dy))


def corner_mask() -> Image.Image:
    """255 inside the banner, 0 in the stepped corners."""
    gw, gh = W // GRID, H // GRID
    m = Image.new("L", (gw, gh), 255)
    for row, cut in enumerate(CORNER):
        for x in range(cut):
            for p in ((x, row), (gw - 1 - x, row), (x, gh - 1 - row), (gw - 1 - x, gh - 1 - row)):
                m.putpixel(p, 0)
    return m.resize((W, H), Image.NEAREST)


def compose(a: Image.Image, bg: Image.Image, text: list[tuple[Image.Image, tuple[int, int]]], frame: int) -> Image.Image:
    out = bg.copy()
    out.alpha_composite(cloud_layer(frame, False, GRID_W, CLOUDS).resize((W, H), Image.NEAREST))
    outline = Image.new("RGBA", (W, H), (10, 12, 18, 0))
    outline.putalpha(a.getchannel("A").filter(ImageFilter.MaxFilter(OUTLINE)))
    out.alpha_composite(outline)
    out.alpha_composite(a)
    out.alpha_composite(cloud_layer(frame, True, GRID_W, CLOUDS).resize((W, H), Image.NEAREST))
    for im, pos in text:
        out.alpha_composite(im, pos)
    return out


def main() -> None:
    files = sorted((ICON / "banner_frames").glob("frame_*.png"))
    if not files:
        sys.exit("no frames in dev/icon/banner_frames - render them from Blockbench first (FGA.camera(0.36))")
    n = len(files) * LOOPS
    for name, _, _, speed, wrap, _ in CLOUDS:
        if speed * n % wrap:
            sys.exit(f"{name}: {speed} px/frame x {n} frames does not wrap {wrap}")
    (bg_name, bg_scale, _), *text_layers = LAYERS
    bg = sprite(bg_name, bg_scale)
    text = [(sprite(name, scale), pos) for name, scale, pos in text_layers]
    arts = [art(f) for f in files]
    frames = [compose(arts[i % len(arts)], bg, text, i).convert("RGB") for i in range(n)]

    DIST.mkdir(exist_ok=True)
    mask = corner_mask()
    still = frames[STILL_FRAME].convert("RGBA")
    still.putalpha(mask)
    still.save(DIST / "banner.png", optimize=True)

    out = DIST / "banner-animated.gif"
    colours = write_gif(frames, out, FPS, key_mask=mask.point(lambda v: 255 - v))

    size = out.stat().st_size
    print(f"dist/banner.png (frame {STILL_FRAME}); {len(frames)} frames, {colours} colours -> {out.relative_to(ROOT)} "
          f"{size / 1024:.0f} KiB ({'OK' if size <= GIF_LIMIT else 'OVER'} Modrinth 5 MiB gallery limit)")
    if size > GIF_LIMIT:
        sys.exit(1)


if __name__ == "__main__":
    main()
